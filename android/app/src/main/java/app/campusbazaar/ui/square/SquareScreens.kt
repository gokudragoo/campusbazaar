package app.campusbazaar.ui.square

import ai.nexconn.chat.channel.BaseChannel
import ai.nexconn.chat.channel.ChannelType
import ai.nexconn.chat.channel.CommunityChannel
import ai.nexconn.chat.channel.CommunitySubChannel
import ai.nexconn.chat.channel.model.CommunitySubChannelIdentifier
import ai.nexconn.chat.channel.query.MessagesQuery
import ai.nexconn.chat.message.ImageMessage
import ai.nexconn.chat.message.Message
import ai.nexconn.chat.message.TextMessage
import ai.nexconn.chat.params.MessagesQueryParams
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.AppContainer
import app.campusbazaar.chat.ChatBody
import app.campusbazaar.chat.ChatConnection
import app.campusbazaar.chat.ChatEvent
import app.campusbazaar.chat.ChatItem
import app.campusbazaar.chat.ChatMapper
import app.campusbazaar.chat.SendState
import app.campusbazaar.data.api.SquareResponse
import app.campusbazaar.data.api.UserDto
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.SubchannelRoute
import app.campusbazaar.ui.appContainer
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.chats.ConnectionStrip
import app.campusbazaar.ui.chats.ImageBubble
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.Hairline
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.Panel
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.util.ImageCompressor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Campus Square: the campus's Nexconn community channel and its subchannels. */
class SquareViewModel(private val c: AppContainer) : ViewModel() {
    data class State(val square: SquareResponse? = null, val unread: Map<String, Int> = emptyMap(), val loading: Boolean = true, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            c.nexconn.events.collect { e -> if (e is ChatEvent.Received && e.message.channelIdentifier?.channelType == ChannelType.COMMUNITY) refreshUnread() }
        }
        viewModelScope.launch { c.nexconn.connection.collect { if (it == ChatConnection.Connected) refreshUnread() } }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Server makes sure we're a member and that all subchannels exist (Platform Chat API).
            c.market.square().onSuccess { sq -> _state.update { it.copy(square = sq, loading = false) }; refreshUnread() }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private fun refreshUnread() {
        val channelId = _state.value.square?.channelId ?: return
        viewModelScope.launch {
            val subs = suspendCancellableCoroutine<List<CommunitySubChannel>> { cont ->
                CommunityChannel(channelId).getSubChannels { list, _ -> if (cont.isActive) cont.resume(list.orEmpty()) }
            }
            _state.update { it.copy(unread = subs.associate { s -> s.subChannelId to s.unreadCount }) }
        }
    }
}

@Composable
fun SquareScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> SquareViewModel(c) }
    val state by vm.state.collectAsState()
    val container = appContainer()
    val conn by container.nexconn.connection.collectAsState()
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Text("Campus Square", style = MaterialTheme.typography.headlineMedium)
            Text(
                state.square?.campus?.let { "Open discussion for ${it.name}. ${it.memberCount} ${if (it.memberCount == 1) "student" else "students"}." }
                    ?: "Open discussion for your campus",
                style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
            )
        }
        ConnectionStrip(conn, onReconnect = { container.session.reconnectChat() })
        when {
            state.loading && state.square == null -> LoadingBlock()
            state.square == null -> ErrorState(state.error ?: "Couldn't open Campus Square.", vm::load)
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Panel {
                        val sq = state.square!!
                        sq.subchannels.forEachIndexed { i, sub ->
                            if (i > 0) Hairline()
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(SubchannelRoute(sq.channelId, sub.id, sub.name)) }.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(36.dp).background(CB.MarigoldWash, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Tag, null, tint = CB.Ink, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sub.name, style = MaterialTheme.typography.titleMedium)
                                    Text(sub.description, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                                }
                                val unread = state.unread[sub.id] ?: 0
                                if (unread > 0) {
                                    Text(if (unread > 99) "99+" else "$unread", style = MaterialTheme.typography.labelSmall, color = Color.White,
                                        modifier = Modifier.background(CB.Stamp, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = CB.InkSoft)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Be kind. No selling here: post items in Sell so they show up in Browse. Report anything unsafe from a student's profile.",
                        style = MaterialTheme.typography.bodySmall, color = CB.InkSoft, modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

class SubchannelViewModel(private val c: AppContainer, private val appContext: Context, channelId: String, private val subchannelId: String) : ViewModel() {
    data class State(val items: List<ChatItem> = emptyList(), val senders: Map<String, UserDto> = emptyMap(), val loading: Boolean = true, val hasMore: Boolean = true, val loadingOlder: Boolean = false)

    // Note the SDK argument order: CommunitySubChannel(subChannelId, channelId).
    private val channel = CommunitySubChannel(subchannelId, channelId)
    private val identifier = CommunitySubChannelIdentifier(channelId, subchannelId)
    private val messages = LinkedHashMap<String, Message>()
    private val failed = HashSet<String>()
    private var query: MessagesQuery = BaseChannel.createMessagesQuery(MessagesQueryParams(identifier).apply { pageSize = 40; isAscending = false })
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts = _toasts.asSharedFlow()
    private val me get() = c.nexconn.currentUserId
    private val myName get() = c.session.me?.name ?: "A student"

    private var poller: Job? = null

    init {
        loadMore(initial = true)
        viewModelScope.launch {
            c.nexconn.events.collect { e ->
                when {
                    e is ChatEvent.Received && isHere(e.message) -> {
                        put(e.message)
                        publish()
                        c.nexconn.clearUnread(channel)
                    }
                    // The SDK only attaches the subchannel when the message carries one; otherwise it
                    // reports just the community, so fetch the newest page to see whether it was ours.
                    e is ChatEvent.Received && isThisCommunity(e.message) -> refreshLatest()
                    e is ChatEvent.UnreadChanged && e.channel?.channelId == identifier.channelId -> refreshLatest()
                }
            }
        }
    }

    /** While the subchannel is on screen, also check for new messages every few seconds. */
    fun setOnScreen(visible: Boolean) {
        poller?.cancel()
        poller = if (visible) viewModelScope.launch { while (isActive) { delay(4000); refreshLatest() } } else null
    }

    private fun isHere(m: Message): Boolean = (m.channelIdentifier as? CommunitySubChannelIdentifier)?.let { it.channelId == identifier.channelId && it.subChannelId == subchannelId } == true

    private fun isThisCommunity(m: Message): Boolean =
        m.channelIdentifier?.let { it.channelType == ChannelType.COMMUNITY && it.channelId == identifier.channelId } == true

    /** Merges the newest page of history (picks up messages the live event didn't attribute to us). */
    private fun refreshLatest() {
        viewModelScope.launch {
            val latest = BaseChannel.createMessagesQuery(MessagesQueryParams(identifier).apply { pageSize = 20; isAscending = false })
            c.nexconn.loadNextPage(latest).onSuccess { page ->
                val before = messages.keys.toSet()
                page.forEach(::put)
                if (messages.keys != before) {
                    publish()
                    c.nexconn.clearUnread(channel)
                }
            }
        }
    }

    private fun key(m: Message) = m.messageId?.takeIf { it.isNotBlank() } ?: "local-${m.clientId}"

    private fun put(m: Message) {
        if (m.clientId != 0) messages.remove("local-${m.clientId}")
        messages[key(m)] = m
    }

    fun loadMore(initial: Boolean = false) {
        if (query.isLoading || (!initial && !_state.value.hasMore)) return
        _state.update { it.copy(loadingOlder = !initial) }
        viewModelScope.launch {
            c.nexconn.loadNextPage(query).onSuccess { page ->
                page.forEach(::put)
                _state.update { it.copy(loading = false, loadingOlder = false, hasMore = query.hasMore) }
                publish()
                if (initial) c.nexconn.clearUnread(channel)
            }.onFailure { e ->
                _state.update { it.copy(loading = false, loadingOlder = false) }
                _toasts.tryEmit(e.message ?: "Couldn't load messages")
            }
        }
    }

    private fun publish() {
        val list = messages.values.sortedBy { it.sentTime }.map { m ->
            val item = ChatMapper.toItem(m, me)
            if (key(m) in failed) item.copy(state = SendState.Failed) else item
        }
        _state.update { it.copy(items = list) }
        val unknown = list.map { it.senderId }.filter { it.isNotBlank() && it !in _state.value.senders }.distinct()
        if (unknown.isNotEmpty()) viewModelScope.launch {
            val resolved = c.users.resolve(unknown)
            _state.update { it.copy(senders = it.senders + resolved) }
        }
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            var k: String? = null
            c.nexconn.send(channel, TextMessage(t), "$myName: ${t.take(80)}", onAttached = { m -> k = key(m); viewModelScope.launch { put(m); publish() } })
                .onSuccess { put(it); publish() }
                .onFailure { e -> k?.let(failed::add); publish(); _toasts.tryEmit(e.message ?: "Message not sent") }
        }
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { ImageCompressor.compressToCacheFile(appContext, uri) }.getOrNull() } ?: run {
                _toasts.tryEmit("That photo couldn't be read."); return@launch
            }
            var k: String? = null
            c.nexconn.sendMedia(channel, ImageMessage().apply { localPath = Uri.fromFile(file).toString() }, "$myName sent a photo",
                onAttached = { m -> k = key(m); viewModelScope.launch { put(m); publish() } })
                .onSuccess { put(it); publish() }
                .onFailure { e -> k?.let(failed::add); publish(); _toasts.tryEmit(e.message ?: "Photo not sent") }
        }
    }
}

@Composable
fun SubchannelScreen(channelId: String, subchannelId: String, name: String, nav: NavHostController) {
    val context = LocalContext.current
    val vm = appViewModel(key = "sub-$channelId-$subchannelId") { c, _ -> SubchannelViewModel(c, context.applicationContext, channelId, subchannelId) }
    val state by vm.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val reversed = state.items.asReversed()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendImage(uri) }
    val nearTop by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearTop, state.items.size) { if (nearTop && state.items.isNotEmpty()) vm.loadMore() }
    LaunchedEffect(state.items.lastOrNull()?.key) { if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) }
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LifecycleResumeEffect(vm) {
        vm.setOnScreen(true)
        onPauseOrDispose { vm.setOnScreen(false) }
    }

    Column(Modifier.fillMaxSize().background(CB.Paper).imePadding()) {
        Row(Modifier.fillMaxWidth().background(CB.Surface).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Column {
                Text("#$name", style = MaterialTheme.typography.titleMedium)
                Text("Campus Square", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> LoadingBlock()
                state.items.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("#$name is quiet", style = MaterialTheme.typography.titleMedium)
                    Text("Start the conversation. Everyone on your campus can read and reply.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                }
                else -> LazyColumn(state = listState, reverseLayout = true, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(reversed.size, key = { reversed[it].key }) { i ->
                        val item = reversed[i]
                        val older = reversed.getOrNull(i + 1)
                        val grouped = older != null && older.senderId == item.senderId && item.sentTime - older.sentTime < 5 * 60_000
                        SquareMessage(item, state.senders[item.senderId], showHeader = !grouped)
                    }
                    if (state.loadingOlder) item { LoadingBlock() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.AddPhotoAlternate, "Send a photo") }
            Box(Modifier.weight(1f).heightIn(min = 44.dp).background(CB.Paper, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (input.isEmpty()) Text("Message #$name", style = MaterialTheme.typography.bodyLarge, color = CB.InkFaint)
                BasicTextField(input, { if (it.length <= 2000) input = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = CB.Ink), cursorBrush = SolidColor(CB.Ink), maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { vm.sendText(input); input = "" }, enabled = input.isNotBlank(),
                modifier = Modifier.size(44.dp).background(if (input.isNotBlank()) CB.Ink else CB.InkWash, RoundedCornerShape(22.dp))) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = if (input.isNotBlank()) Color.White else CB.InkFaint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SquareMessage(item: ChatItem, sender: UserDto?, showHeader: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = if (showHeader) 10.dp else 2.dp)) {
        if (showHeader) Avatar(sender?.name ?: "Student", sender?.avatarUrl, 34.dp) else Spacer(Modifier.width(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.mine) "You" else sender?.name ?: "Student", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(TimeText.short(item.sentTime), style = MaterialTheme.typography.labelSmall, color = CB.InkFaint)
                }
            }
            when (val b = item.body) {
                is ChatBody.Text -> Text(b.text, style = MaterialTheme.typography.bodyLarge)
                is ChatBody.Image -> ImageBubble(b, false) {}
                else -> Text(ChatMapper.preview(null, null).ifBlank { "Shared something" }, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
            }
            if (item.state == SendState.Failed) Text("Not sent", style = MaterialTheme.typography.labelSmall, color = CB.Stamp)
            if (item.state == SendState.Sending) Text("Sending…", style = MaterialTheme.typography.labelSmall, color = CB.InkFaint)
        }
    }
}
