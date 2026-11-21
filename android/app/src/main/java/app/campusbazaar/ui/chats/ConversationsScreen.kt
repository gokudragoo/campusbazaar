package app.campusbazaar.ui.chats

import ai.nexconn.chat.channel.BaseChannel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.AppContainer
import app.campusbazaar.chat.ChatConnection
import app.campusbazaar.chat.ChatEvent
import app.campusbazaar.chat.ChatMapper
import app.campusbazaar.data.api.ThreadSummaryDto
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.ChatRoute
import app.campusbazaar.ui.appContainer
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.EmptyState
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.Hairline
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationRow(
    val peerId: String,
    val name: String,
    val avatar: String?,
    val preview: String,
    val time: Long,
    val unread: Int,
    val about: String?,
)

/** Nexconn conversation list (direct channels) enriched with names and the item being discussed. */
@OptIn(FlowPreview::class)
class ConversationsViewModel(private val c: AppContainer) : ViewModel() {
    data class State(val rows: List<ConversationRow> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var threads: List<ThreadSummaryDto> = emptyList()
    private var job: Job? = null

    init {
        viewModelScope.launch { c.nexconn.connection.collect { if (it == ChatConnection.Connected) load() } }
        viewModelScope.launch {
            c.nexconn.events
                .filter { it is ChatEvent.Received || it is ChatEvent.UnreadChanged }
                .debounce(200)
                .collect { load(silent = true) }
        }
        load()
    }

    fun refresh() = load(refreshing = true, withThreads = true)

    private fun load(silent: Boolean = false, refreshing: Boolean = false, withThreads: Boolean = threads.isEmpty()) {
        job?.cancel()
        job = viewModelScope.launch {
            if (!silent) _state.update { it.copy(refreshing = refreshing, error = null) }
            if (withThreads) c.deals.threads().onSuccess { threads = it }
            c.nexconn.directChannels().onSuccess { channels -> buildRows(channels) }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = if (it.rows.isEmpty()) e.message else null) } }
        }
    }

    private suspend fun buildRows(channels: List<BaseChannel>) {
        val me = c.nexconn.currentUserId
        val ids = channels.map { it.channelId }
        val users = c.users.resolve(ids)
        val rows = channels.map { ch ->
            val user = users[ch.channelId]
            val about = threads.filter { it.peer?.nexconnUserId == ch.channelId }.maxByOrNull { it.lastActivityAt }?.listing?.title
            ConversationRow(
                peerId = ch.channelId,
                name = user?.name ?: "Student",
                avatar = user?.avatarUrl,
                preview = ChatMapper.preview(ch.latestMessage, me),
                time = ch.latestMessage?.sentTime ?: ch.operationTime,
                unread = ch.unreadCount,
                about = about,
            )
        }.sortedByDescending { it.time }
        _state.update { it.copy(rows = rows, loading = false, refreshing = false, error = null) }
    }
}

@Composable
fun ConversationsScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> ConversationsViewModel(c) }
    val state by vm.state.collectAsState()
    val container = appContainer()
    val conn by container.nexconn.connection.collectAsState()

    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Text("Chats", style = MaterialTheme.typography.headlineMedium)
            Text("Your conversations with buyers and sellers", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
        }
        ConnectionStrip(conn, onReconnect = { container.session.reconnectChat() })
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.rows.isEmpty() -> LoadingBlock()
                state.error != null && state.rows.isEmpty() -> ErrorState(state.error!!, vm::refresh)
                state.rows.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            Icons.Outlined.ChatBubbleOutline,
                            "No chats yet",
                            "When you message a seller, or a buyer messages you about your listing, the conversation shows up here.",
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.peerId }) { row ->
                        ConversationItem(row) { nav.navigate(ChatRoute(row.peerId, row.name)) }
                        Hairline(Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStrip(conn: ChatConnection, onReconnect: () -> Unit) {
    val text = when (conn) {
        ChatConnection.Connecting -> "Connecting to chat…"
        ChatConnection.Offline -> "You're offline. New messages will arrive when you reconnect."
        ChatConnection.SignedInElsewhere -> "You signed in on another phone, so chat paused here."
        is ChatConnection.Failed -> "Chat is unavailable: ${conn.message}"
        else -> null
    } ?: return
    Row(Modifier.fillMaxWidth().background(CB.InkWash).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = CB.Ink, modifier = Modifier.weight(1f))
        if (conn == ChatConnection.SignedInElsewhere || conn is ChatConnection.Failed) QuietButton("Reconnect", onReconnect)
    }
}

@Composable
private fun ConversationItem(row: ConversationRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(row.name, row.avatar, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(TimeText.short(row.time), style = MaterialTheme.typography.labelSmall, color = if (row.unread > 0) CB.Ink else CB.InkFaint)
            }
            row.about?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = CB.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.preview.ifBlank { "No messages yet" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (row.unread > 0) FontWeight(600) else FontWeight(400)),
                    color = if (row.unread > 0) CB.Ink else CB.InkSoft,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (row.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (row.unread > 99) "99+" else "${row.unread}",
                        style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.background(CB.Stamp, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

