package app.campusbazaar.chat

import ai.nexconn.chat.NCEngine
import ai.nexconn.chat.channel.BaseChannel
import ai.nexconn.chat.channel.ChannelType
import ai.nexconn.chat.channel.DirectChannel
import ai.nexconn.chat.channel.model.ChannelIdentifier
import ai.nexconn.chat.channel.model.ChannelUnreadStatusSyncEvent
import ai.nexconn.chat.channel.model.TypingStatusChangedEvent
import ai.nexconn.chat.channel.query.MessagesQuery
import ai.nexconn.chat.error.NCError
import ai.nexconn.chat.handler.ChannelHandler
import ai.nexconn.chat.handler.ConnectHandler
import ai.nexconn.chat.handler.MessageHandler
import ai.nexconn.chat.handler.SendMediaMessageHandler
import ai.nexconn.chat.handler.SendMessageHandler
import ai.nexconn.chat.message.MediaMessageContent
import ai.nexconn.chat.message.Message
import ai.nexconn.chat.message.MessageContent
import ai.nexconn.chat.message.model.MessageMetadataDeletedEvent
import ai.nexconn.chat.message.model.MessageMetadataUpdatedEvent
import ai.nexconn.chat.message.model.MessageReadReceiptResponse
import ai.nexconn.chat.message.model.MessageReceiptResponseEvent
import ai.nexconn.chat.message.model.MessageReceivedEvent
import ai.nexconn.chat.message.model.MessagesUpdatedEvent
import ai.nexconn.chat.message.model.OfflineMessageSyncCompletedEvent
import ai.nexconn.chat.message.model.ReadReceiptInfo
import ai.nexconn.chat.model.ConnectionStatus
import ai.nexconn.chat.params.AreaCode
import ai.nexconn.chat.params.ChannelsQueryParams
import ai.nexconn.chat.params.ConnectParams
import ai.nexconn.chat.params.InitParams
import ai.nexconn.chat.params.LogLevel
import ai.nexconn.chat.params.PushConfig
import ai.nexconn.chat.params.SendMediaMessageParams
import ai.nexconn.chat.params.SendMessageParams
import android.content.Context
import android.util.Log
import app.campusbazaar.chat.messages.ALL_CUSTOM_MESSAGES
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Connection state as the UI understands it. */
sealed interface ChatConnection {
    data object Idle : ChatConnection
    data object Connecting : ChatConnection
    data object Connected : ChatConnection
    data object Offline : ChatConnection
    data object SignedInElsewhere : ChatConnection
    data class Failed(val message: String) : ChatConnection
}

/** Real-time events fanned out from the Nexconn SDK handlers. */
sealed interface ChatEvent {
    data class Received(val message: Message, val offline: Boolean) : ChatEvent
    data class MetadataChanged(val message: Message, val metadata: Map<String, String>) : ChatEvent
    data class MetadataRemoved(val message: Message, val keys: List<String>) : ChatEvent
    data class Updated(val messages: List<Message>) : ChatEvent
    data class Receipts(val responses: List<MessageReadReceiptResponse>) : ChatEvent
    data class Typing(val channel: ChannelIdentifier, val userIds: List<String>) : ChatEvent
    data class UnreadChanged(val channel: ChannelIdentifier?) : ChatEvent
}

class ChatException(val code: Int, message: String) : Exception(message)

private fun NCError.toException() = ChatException(code, friendlyChatError(code, message))

internal fun friendlyChatError(code: Int, raw: String?): String = when (code) {
    405 -> "This student isn't accepting messages from you."
    22406, 30001, 30002, 30003, 30004, 30010, 30011, 34006 -> "Chat is offline. Check your internet and try again."
    20604 -> "You're sending messages too fast. Wait a second."
    31004, 31020 -> "Your chat session expired. Reconnecting."
    else -> raw?.takeIf { it.isNotBlank() }?.let { "Chat error ($code): $it" } ?: "Chat error ($code)."
}

/**
 * Owns the Nexconn Chat SDK: initialisation (Singapore data center), custom message registration,
 * the single connect() per app lifecycle with token refresh, and suspend wrappers around the
 * callback APIs. Everything Nexconn-specific in the app goes through here.
 */
class NexconnManager(private val context: Context) {
    private val _connection = MutableStateFlow<ChatConnection>(ChatConnection.Idle)
    val connection: StateFlow<ChatConnection> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    @Volatile private var initializedKey: String? = null
    @Volatile private var handlersRegistered = false
    @Volatile private var connectedUserId: String? = null
    @Volatile private var connectInFlight = false

    val currentUserId: String? get() = connectedUserId ?: runCatching { NCEngine.getCurrentUserId() }.getOrNull()

    /**
     * Initialises the SDK for [appKey]. If the server has moved to a different App Key (tokens are
     * only valid for the key that issued them), disconnects and re-initialises with the new one.
     */
    fun ensureInitialized(appKey: String) {
        if (initializedKey == appKey) return
        if (initializedKey != null) disconnect()
        NCEngine.initialize(
            InitParams(context.applicationContext, appKey).apply {
                areaCode = AreaCode.SG
                enablePush = false // FCM needs the shared console; in-app notifications instead
                logLevel = LogLevel.WARN
            },
        )
        NCEngine.registerCustomMessages(ALL_CUSTOM_MESSAGES)
        if (!handlersRegistered) {
            registerHandlers()
            handlersRegistered = true
        }
        initializedKey = appKey
    }

    private fun registerHandlers() {
        NCEngine.addConnectionStatusHandler(TAG) { event ->
            _connection.value = when (event.status) {
                ConnectionStatus.CONNECTED -> ChatConnection.Connected
                ConnectionStatus.CONNECTING, ConnectionStatus.SUSPENDED -> ChatConnection.Connecting
                // The SDK has stopped reconnecting; the app reconnects when the network returns or on Retry.
                ConnectionStatus.TIMEOUT -> ChatConnection.Failed("Chat couldn't connect")
                ConnectionStatus.NETWORK_UNAVAILABLE -> ChatConnection.Offline
                ConnectionStatus.KICKED_OFFLINE_BY_OTHER_CLIENT -> ChatConnection.SignedInElsewhere
                ConnectionStatus.TOKEN_INCORRECT -> ChatConnection.Failed("Chat session expired")
                ConnectionStatus.CONN_USER_BLOCKED -> ChatConnection.Failed("This account can't use chat")
                ConnectionStatus.SIGNED_OUT, ConnectionStatus.USER_ABANDON, ConnectionStatus.UNCONNECTED -> ChatConnection.Idle
                else -> _connection.value
            }
            // connect() refreshes the token itself; only a mid-session rejection needs the session to step in.
            if (event.status == ConnectionStatus.TOKEN_INCORRECT && !connectInFlight) onTokenInvalid?.invoke()
        }
        NCEngine.addMessageHandler(TAG, object : MessageHandler {
            override fun onMessageReceived(event: MessageReceivedEvent) {
                _events.tryEmit(ChatEvent.Received(event.message, event.offline == true))
            }
            override fun onOfflineMessageSyncCompleted(event: OfflineMessageSyncCompletedEvent) {
                _events.tryEmit(ChatEvent.UnreadChanged(null))
            }
            override fun onMessageMetadataUpdated(event: MessageMetadataUpdatedEvent) {
                event.message?.let { _events.tryEmit(ChatEvent.MetadataChanged(it, event.metadata)) }
            }
            override fun onMessageMetadataDeleted(event: MessageMetadataDeletedEvent) {
                event.message?.let { _events.tryEmit(ChatEvent.MetadataRemoved(it, event.keys)) }
            }
            override fun onMessagesUpdated(event: MessagesUpdatedEvent) {
                _events.tryEmit(ChatEvent.Updated(event.messages))
            }
            override fun onMessageReceiptResponse(event: MessageReceiptResponseEvent) {
                _events.tryEmit(ChatEvent.Receipts(event.responses))
            }
        })
        NCEngine.addChannelHandler(TAG, object : ChannelHandler {
            override fun onTypingStatusChanged(event: TypingStatusChangedEvent) {
                _events.tryEmit(ChatEvent.Typing(event.channelIdentifier, event.userTypingStatus.map { it.userId }))
            }
            override fun onChannelUnreadStatusSync(event: ChannelUnreadStatusSyncEvent) {
                _events.tryEmit(ChatEvent.UnreadChanged(event.channelIdentifier))
            }
        })
    }

    /** Called when Nexconn reports the token is no longer valid mid-session (SessionManager refreshes it). */
    @Volatile var onTokenInvalid: (() -> Unit)? = null

    val isConnecting: Boolean get() = connectInFlight

    /**
     * Connects once per app lifecycle. If Nexconn rejects the token (invalid or expired), asks
     * [refreshToken] for a new one from our server and retries once.
     */
    suspend fun connect(token: String, refreshToken: suspend () -> String?): Result<String> {
        connectedUserId?.let { if (_connection.value == ChatConnection.Connected) return Result.success(it) }
        if (connectInFlight) return Result.failure(ChatException(34001, "Already connecting"))
        connectInFlight = true
        _connection.value = ChatConnection.Connecting
        try {
            var result = connectOnce(token)
            val err = result.exceptionOrNull() as? ChatException
            if (err != null && err.code in TOKEN_ERRORS) {
                val fresh = refreshToken()
                if (fresh != null) result = connectOnce(fresh)
            }
            result.onSuccess { connectedUserId = it }
            result.onFailure {
                val code = (it as? ChatException)?.code
                if (code != 34001) _connection.value = ChatConnection.Failed(it.message ?: "Chat unavailable")
            }
            return result
        } finally {
            connectInFlight = false
        }
    }

    // timeout = 0 keeps the SDK's own reconnect strategy; a positive timeout makes it give up for good.
    private suspend fun connectOnce(token: String): Result<String> = suspendCancellableCoroutine { cont ->
        NCEngine.connect(ConnectParams(token).apply { timeout = 0 }, object : ConnectHandler {
            override fun onResult(userId: String?, error: NCError?) {
                if (!cont.isActive) return
                if (error == null && userId != null) cont.resume(Result.success(userId))
                else cont.resume(Result.failure(error?.toException() ?: ChatException(-1, "Chat connection failed")))
            }
        })
    }

    fun disconnect() {
        runCatching { NCEngine.disconnect(false) }
        connectedUserId = null
        _connection.value = ChatConnection.Idle
    }

    /* --------------------------------------------------------------------- sending --- */

    suspend fun send(
        channel: BaseChannel,
        content: MessageContent,
        pushText: String,
        needReceipt: Boolean = false,
        metadata: Map<String, String>? = null,
        onAttached: (Message) -> Unit = {},
    ): Result<Message> = suspendCancellableCoroutine { cont ->
        val params = SendMessageParams(content).apply {
            pushConfig = PushConfig().apply { pushContent = pushText }
            this.needReceipt = needReceipt
            if (metadata != null) this.metadata = metadata
        }
        channel.sendMessage(params, object : SendMessageHandler {
            override fun onAttached(message: Message) = onAttached(message)
            override fun onResult(message: Message?, error: NCError?) {
                if (!cont.isActive) return
                if (error == null && message != null) cont.resume(Result.success(message))
                else cont.resume(Result.failure(error?.toException() ?: ChatException(-1, "Message not sent")))
            }
        })
    }

    suspend fun sendMedia(
        channel: BaseChannel,
        content: MediaMessageContent,
        pushText: String,
        onAttached: (Message) -> Unit = {},
        onProgress: (Message, Int) -> Unit = { _, _ -> },
    ): Result<Message> = suspendCancellableCoroutine { cont ->
        val params = SendMediaMessageParams(content).apply {
            pushConfig = PushConfig().apply { pushContent = pushText }
            needReceipt = true
        }
        channel.sendMediaMessage(params, object : SendMediaMessageHandler {
            override fun onAttached(message: Message) = onAttached(message)
            override fun onProgress(message: Message, progress: Int) = onProgress(message, progress)
            override fun onCanceled(message: Message) {
                if (cont.isActive) cont.resume(Result.failure(ChatException(-2, "Upload cancelled")))
            }
            override fun onResult(message: Message?, error: NCError?) {
                if (!cont.isActive) return
                if (error == null && message != null) cont.resume(Result.success(message))
                else cont.resume(Result.failure(error?.toException() ?: ChatException(-1, "Photo not sent")))
            }
        })
    }

    /* ------------------------------------------------------------- history & lists --- */

    suspend fun loadNextPage(query: MessagesQuery): Result<List<Message>> = suspendCancellableCoroutine { cont ->
        query.loadNextPage { page, error ->
            if (!cont.isActive) return@loadNextPage
            if (error == null) cont.resume(Result.success(page?.data.orEmpty()))
            else cont.resume(Result.failure(error.toException()))
        }
    }

    suspend fun directChannels(): Result<List<BaseChannel>> = channels(listOf(ChannelType.DIRECT))

    suspend fun channels(types: List<ChannelType>, pageSize: Int = 50): Result<List<BaseChannel>> = suspendCancellableCoroutine { cont ->
        val query = BaseChannel.createChannelsQuery(ChannelsQueryParams(types).apply { this.pageSize = pageSize; topPriority = true })
        query.loadNextPage { page, error ->
            if (!cont.isActive) return@loadNextPage
            if (error == null) cont.resume(Result.success(page?.data.orEmpty()))
            else cont.resume(Result.failure(error.toException()))
        }
    }

    suspend fun reloadChannel(channel: BaseChannel): BaseChannel? = suspendCancellableCoroutine { cont ->
        channel.reload { reloaded, error -> if (cont.isActive) cont.resume(if (error == null) reloaded else null) }
    }

    /* ------------------------------------------------- metadata, receipts, unread --- */

    suspend fun setMetadata(message: Message, values: Map<String, String>): Result<Unit> = suspendCancellableCoroutine { cont ->
        message.setMetadata(values) { error ->
            if (!cont.isActive) return@setMetadata
            if (error == null) cont.resume(Result.success(Unit)) else cont.resume(Result.failure(error.toException()))
        }
    }

    fun sendReadReceipts(channel: BaseChannel, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        channel.sendReadReceiptResponse(messageIds) { error -> if (error != null) Log.w(TAG, "read receipt failed: ${error.code}") }
    }

    suspend fun readReceiptInfo(channel: BaseChannel, messageIds: List<String>): List<ReadReceiptInfo> = suspendCancellableCoroutine { cont ->
        if (messageIds.isEmpty()) {
            cont.resume(emptyList()); return@suspendCancellableCoroutine
        }
        channel.getMessageReadReceiptInfo(messageIds) { info, _ -> if (cont.isActive) cont.resume(info.orEmpty()) }
    }

    fun clearUnread(channel: BaseChannel) {
        channel.clearUnreadCount { _, error ->
            if (error == null) _events.tryEmit(ChatEvent.UnreadChanged(ChannelIdentifier(channel.channelType, channel.channelId)))
        }
    }

    fun sendTyping(peerUserId: String) {
        runCatching { DirectChannel(peerUserId).sendTypingStatus("RC:TxtMsg") }
    }

    /* ------------------------------------------------------------------- blocklist --- */

    suspend fun block(userId: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        NCEngine.userModule.addToBlocklist(userId) { error ->
            if (cont.isActive) cont.resume(if (error == null) Result.success(Unit) else Result.failure(error.toException()))
        }
    }

    suspend fun unblock(userId: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        NCEngine.userModule.removeFromBlocklist(userId) { error ->
            if (cont.isActive) cont.resume(if (error == null) Result.success(Unit) else Result.failure(error.toException()))
        }
    }

    suspend fun isBlocked(userId: String): Boolean = suspendCancellableCoroutine { cont ->
        NCEngine.userModule.checkBlocked(userId) { blocked, _ -> if (cont.isActive) cont.resume(blocked == true) }
    }

    companion object {
        private const val TAG = "cbz-nexconn"
        private val TOKEN_ERRORS = setOf(31004, 31020)
    }
}
