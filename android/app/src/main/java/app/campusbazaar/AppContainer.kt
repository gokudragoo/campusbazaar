package app.campusbazaar

import ai.nexconn.chat.channel.ChannelType
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.campusbazaar.chat.ChatEvent
import app.campusbazaar.chat.ChatMapper
import app.campusbazaar.chat.NexconnManager
import app.campusbazaar.chat.messages.NoticeMessage
import app.campusbazaar.data.api.ApiClient
import app.campusbazaar.data.api.CampusBazaarApi
import app.campusbazaar.data.api.appJson
import app.campusbazaar.data.repo.AuthRepository
import app.campusbazaar.data.repo.DealRepository
import app.campusbazaar.data.repo.MarketRepository
import app.campusbazaar.data.repo.UserDirectory
import app.campusbazaar.data.session.SessionManager
import app.campusbazaar.data.session.SessionStore
import app.campusbazaar.util.NetworkMonitor
import app.campusbazaar.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** A banner shown at the top of the app for messages that arrive outside the open chat. */
data class InAppBanner(val title: String, val text: String, val peerUserId: String?)

/**
 * Manual constructor-injection container (no reflection or annotation processing, which keeps
 * builds light on this machine). Created once in [CampusBazaarApp]; ViewModels get it through
 * their factories.
 */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val nexconn = NexconnManager(context)
    private val sessionStore = SessionStore(context, appJson)
    private lateinit var apiRef: CampusBazaarApi
    val session = SessionManager(sessionStore, { apiRef }, nexconn, appScope)
    val httpClient = ApiClient.okHttp(context, tokenProvider = { session.apiToken }, onUnauthorized = { session.onUnauthorized() })
    val api: CampusBazaarApi = ApiClient.api(httpClient).also { apiRef = it }

    val auth = AuthRepository(api, session)
    val market = MarketRepository(context, api)
    val deals = DealRepository(api)
    val users = UserDirectory(api)
    val network = NetworkMonitor(context)

    /** Nexconn user id of the chat currently on screen (no banner/notification for it). */
    @Volatile var openChatPeer: String? = null
    @Volatile var noticeSenderId: String? = null

    private val _banners = MutableSharedFlow<InAppBanner>(extraBufferCapacity = 8)
    val banners: SharedFlow<InAppBanner> = _banners.asSharedFlow()

    fun start() {
        Notifier.createChannels(context)
        appScope.launch { session.restore() }
        appScope.launch { market.config().onSuccess { noticeSenderId = it.noticeSenderId } }
        appScope.launch { nexconn.events.collect { if (it is ChatEvent.Received && !it.offline) onIncoming(it) } }
    }

    /** In-app real-time notifications: banner in the foreground, system notification in the background. */
    private suspend fun onIncoming(event: ChatEvent.Received) {
        val message = event.message
        val me = nexconn.currentUserId
        if (message.senderUserId == me) return
        val type = message.channelIdentifier?.channelType ?: return
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        when (type) {
            ChannelType.DIRECT -> {
                val peer = message.channelIdentifier.channelId
                if (foreground && openChatPeer == peer) return
                val name = users.resolve(listOf(peer))[peer]?.name ?: "A student"
                val text = ChatMapper.preview(message, me)
                if (foreground) _banners.tryEmit(InAppBanner(name, text, peer))
                else Notifier.show(context, peer.hashCode(), Notifier.CHANNEL_MESSAGES, name, text, peer)
            }
            ChannelType.SYSTEM -> {
                val notice = message.content as? NoticeMessage ?: return
                if (foreground) _banners.tryEmit(InAppBanner(notice.title, notice.body, null))
                else Notifier.show(context, message.sentTime.toInt(), Notifier.CHANNEL_ACTIVITY, notice.title, notice.body, null)
            }
            else -> Unit // Campus Square messages don't interrupt
        }
    }
}
