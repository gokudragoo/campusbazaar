package app.campusbazaar.ui.wanted

import ai.nexconn.chat.channel.DirectChannel
import ai.nexconn.chat.message.TextMessage
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.AppContainer
import app.campusbazaar.chat.messages.ListingCardMessage
import app.campusbazaar.data.api.ListingDto
import app.campusbazaar.data.api.WantedCreateRequest
import app.campusbazaar.data.api.WantedDto
import app.campusbazaar.domain.Category
import app.campusbazaar.domain.InputRules
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.ChatRoute
import app.campusbazaar.ui.WantedCreateRoute
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.ConfirmDialog
import app.campusbazaar.ui.components.EmptyState
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WantedViewModel(private val c: AppContainer) : ViewModel() {
    data class State(
        val mine: Boolean = false,
        val category: String? = null,
        val items: List<WantedDto> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val myListings: List<ListingDto> = emptyList(),
        val responding: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts = _toasts.asSharedFlow()
    private val _openChat = MutableSharedFlow<ChatRoute>(extraBufferCapacity = 1)
    val openChat = _openChat.asSharedFlow()
    val myId get() = c.session.me?.id

    init { load() }

    fun setMine(mine: Boolean) { _state.update { it.copy(mine = mine) }; load() }
    fun setCategory(id: String?) { _state.update { it.copy(category = id) }; load() }
    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            c.market.wanted(s.category, s.mine, null).onSuccess { page -> _state.update { it.copy(items = page.wanted, loading = false, refreshing = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }

    fun loadMyListings() {
        viewModelScope.launch { c.market.myListings().onSuccess { list -> _state.update { it.copy(myListings = list.filter { l -> l.status == "active" }) } } }
    }

    /**
     * Responds to a wanted post: the server records it (and notifies the poster through the
     * system channel), then we open the direct chat with a listing card and the note.
     */
    fun respond(post: WantedDto, listing: ListingDto?, note: String) {
        if (_state.value.responding) return
        _state.update { it.copy(responding = true) }
        viewModelScope.launch {
            c.market.respondWanted(post.id, listing?.id, note).onSuccess { r ->
                c.users.put(r.author)
                val channel = DirectChannel(r.author.nexconnUserId)
                val myName = c.session.me?.name ?: "A student"
                if (listing != null) {
                    c.nexconn.send(channel, ListingCardMessage().apply {
                        listingId = listing.id; title = listing.title; price = listing.price
                        photo = listing.photos.firstOrNull().orEmpty(); condition = listing.condition; negotiable = listing.negotiable
                    }, "$myName has ${listing.title} for your wanted post", needReceipt = true)
                }
                val text = "About your wanted post \"${post.title}\"" + if (note.isNotBlank()) ": $note" else ""
                c.nexconn.send(channel, TextMessage(text), "$myName: $text", needReceipt = true)
                _state.update { it.copy(responding = false, items = it.items.map { w -> if (w.id == post.id) w.copy(respondedByMe = true, responseCount = w.responseCount + if (r.firstResponse) 1 else 0) else w }) }
                _openChat.tryEmit(ChatRoute(r.author.nexconnUserId, r.author.name))
            }.onFailure { e -> _state.update { it.copy(responding = false) }; _toasts.tryEmit(e.message ?: "Couldn't respond") }
        }
    }

    fun setOpen(post: WantedDto, open: Boolean) {
        viewModelScope.launch {
            c.market.setWantedStatus(post.id, open).onSuccess { updated -> _state.update { it.copy(items = it.items.map { w -> if (w.id == post.id) updated.copy(author = w.author) else w }) } }
                .onFailure { _toasts.tryEmit(it.message ?: "Couldn't update") }
        }
    }

    fun delete(post: WantedDto) {
        viewModelScope.launch {
            c.market.deleteWanted(post.id).onSuccess { _state.update { it.copy(items = it.items.filterNot { w -> w.id == post.id }) }; _toasts.tryEmit("Request deleted") }
                .onFailure { _toasts.tryEmit(it.message ?: "Couldn't delete") }
        }
    }
}

@Composable
fun WantedScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> WantedViewModel(c) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var responding by remember { mutableStateOf<WantedDto?>(null) }
    var deleting by remember { mutableStateOf<WantedDto?>(null) }
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(Unit) { vm.openChat.collect { responding = null; nav.navigate(it) } }

    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wanted", style = MaterialTheme.typography.headlineMedium)
                Text("What students on your campus are looking for", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
            PrimaryButton("Post", { nav.navigate(WantedCreateRoute) }, icon = Icons.Outlined.Add)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Campus requests", !state.mine, { vm.setMine(false) })
            SelectChip("My requests", state.mine, { vm.setMine(true) })
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> LoadingBlock()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, vm::refresh)
                state.items.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            Icons.Outlined.ManageSearch,
                            if (state.mine) "You haven't asked for anything" else "No requests yet",
                            if (state.mine) "Need a drafter, a lab coat or last year's notes? Post a request and sellers will message you."
                            else "When someone needs a book or a cooler, it shows up here. Have something in mind? Post the first request.",
                            action = { PrimaryButton("Post a request", { nav.navigate(WantedCreateRoute) }) },
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.id }) { w ->
                        WantedCard(
                            w, mine = w.userId == vm.myId,
                            onRespond = { vm.loadMyListings(); responding = w },
                            onToggle = { vm.setOpen(w, w.status != "open") },
                            onDelete = { deleting = w },
                        )
                    }
                }
            }
        }
    }

    responding?.let { post ->
        RespondSheet(post, state.myListings, state.responding, onSubmit = { listing, note -> vm.respond(post, listing, note) }, onDismiss = { responding = null })
    }
    deleting?.let { post ->
        ConfirmDialog("Delete this request?", "\"${post.title}\" will be removed from the Wanted board.", "Delete", { deleting = null; vm.delete(post) }, { deleting = null }, danger = true)
    }
}

@Composable
private fun WantedCard(w: WantedDto, mine: Boolean, onRespond: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CB.Surface, RoundedCornerShape(8.dp)).border(1.dp, CB.Hairline, RoundedCornerShape(8.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Category.of(w.category).label, style = MaterialTheme.typography.labelSmall, color = CB.InkSoft, modifier = Modifier.weight(1f))
            w.budget?.let { Text("Budget ${Money.rupees(it)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(CB.MarigoldWash, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        Text(w.title, style = MaterialTheme.typography.titleMedium)
        if (w.description.isNotBlank()) Text(w.description, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!mine) {
                Avatar(w.author?.name ?: "Student", w.author?.avatarUrl, 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(w.author?.name ?: "Student", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
            }
            Text(TimeText.relative(TimeText.parseIso(w.createdAt)), style = MaterialTheme.typography.bodySmall, color = CB.InkFaint, modifier = Modifier.weight(1f))
            if (mine) {
                Text("${w.responseCount} ${if (w.responseCount == 1) "response" else "responses"}", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuietButton(if (w.status == "open") "Mark found" else "Reopen", onToggle)
                QuietButton("Delete", onDelete, danger = true)
            }
            if (w.status != "open") Text("Closed. Hidden from the campus board.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
        } else if (w.respondedByMe) {
            Text("You responded. Check Chats for replies.", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
        } else {
            QuietButton("I have this", onRespond)
        }
    }
}

@Composable
private fun RespondSheet(post: WantedDto, listings: List<ListingDto>, busy: Boolean, onSubmit: (ListingDto?, String) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<ListingDto?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CB.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Respond to \"${post.title}\"", style = MaterialTheme.typography.headlineSmall)
            if (listings.isNotEmpty()) {
                Text("Attach one of your listings (optional)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listings.forEach { l -> SelectChip("${l.title.take(28)} ${Money.rupees(l.price)}", picked?.id == l.id, { picked = if (picked?.id == l.id) null else l }) }
                }
            } else {
                Text("Tip: list the item first so they can see photos and send you an offer.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
            Field(note, { note = it }, "Message", placeholder = "e.g. I have one, barely used. Can meet at the library.", singleLine = false, minLines = 2, maxLength = 300)
            PrimaryButton("Send and open chat", { if (picked != null || note.isNotBlank()) onSubmit(picked, note.trim()) }, Modifier.fillMaxWidth(), enabled = picked != null || note.isNotBlank(), loading = busy)
        }
    }
}

class WantedCreateViewModel(private val c: AppContainer) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun create(title: String, description: String, category: String, budget: Int?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            c.market.createWanted(WantedCreateRequest(title.trim(), description.trim(), category, budget)).onSuccess { _done.tryEmit(Unit) }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }
}

@Composable
fun WantedCreateScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> WantedCreateViewModel(c) }
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var budget by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.done.collect { Toast.makeText(context, "Request posted to your campus", Toast.LENGTH_SHORT).show(); nav.popBackStack() } }

    Column(Modifier.fillMaxSize().background(CB.Paper).imePadding()) {
        Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Close") }
            Text("Post a request", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            error?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodyMedium) }
            Field(title, { title = it }, "What do you need?", placeholder = "e.g. Engineering Graphics drafter", error = if (submitted) InputRules.titleError(title) else null, maxLength = 80)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c -> SelectChip(c.label, category == c.id, { category = c.id }) }
                }
                if (submitted && category == null) Text("Pick a category.", color = CB.Stamp, style = MaterialTheme.typography.bodySmall)
            }
            Field(budget, { budget = it.filter(Char::isDigit).take(6) }, "Budget (optional)", leading = { Text("₹", style = MaterialTheme.typography.titleMedium) }, keyboardType = KeyboardType.Number)
            Field(description, { description = it }, "Details (optional)", placeholder = "Edition, size, when you need it", singleLine = false, minLines = 3, maxLength = 800)
        }
        Column(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton("Post to campus", {
                submitted = true
                if (InputRules.titleError(title) == null && category != null) vm.create(title, description, category!!, Money.parse(budget))
            }, Modifier.fillMaxWidth(), loading = busy)
        }
    }
}

