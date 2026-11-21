package app.campusbazaar.ui.listing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.AppContainer
import app.campusbazaar.data.api.ListingDto
import app.campusbazaar.data.api.ListingUpdateRequest
import app.campusbazaar.data.api.OfferDto
import app.campusbazaar.data.api.UserDto
import app.campusbazaar.domain.Category
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.ListingStatus
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.ChatRoute
import app.campusbazaar.ui.SafetyRoute
import app.campusbazaar.ui.SellRoute
import app.campusbazaar.ui.UserRoute
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.ChoiceDialog
import app.campusbazaar.ui.components.ConfirmDialog
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.ListingPhoto
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.Panel
import app.campusbazaar.ui.components.PriceTag
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.RatingLine
import app.campusbazaar.ui.components.ReportDialog
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.components.TagSize
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListingDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    data class State(
        val listing: ListingDto? = null,
        val myOffers: List<OfferDto> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val busy: Boolean = false,
        val buyers: List<UserDto> = emptyList(),
        val gone: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts = _toasts.asSharedFlow()
    private val _openChat = MutableSharedFlow<ChatRoute>(extraBufferCapacity = 1)
    val openChat = _openChat.asSharedFlow()
    val myId: String? get() = c.session.me?.id

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            c.market.listing(id).onSuccess { r ->
                _state.update { it.copy(listing = r.listing, myOffers = r.myOffers, loading = false) }
                if (r.listing.sellerId == myId) {
                    c.market.myListings().onSuccess { list -> _state.update { s -> s.copy(buyers = list.firstOrNull { it.id == id }?.interestedBuyers.orEmpty()) } }
                }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun toggleSave() {
        val l = _state.value.listing ?: return
        val next = l.saved != true
        _state.update { it.copy(listing = l.copy(saved = next)) }
        viewModelScope.launch {
            c.market.setSaved(l.id, next)
                .onSuccess { _toasts.tryEmit(if (next) "Saved" else "Removed from saved") }
                .onFailure { e -> _state.update { it.copy(listing = l) }; _toasts.tryEmit(e.message ?: "Couldn't save") }
        }
    }

    fun chat(openOffer: Boolean) {
        val l = _state.value.listing ?: return
        val seller = l.seller ?: return
        _openChat.tryEmit(ChatRoute(peerId = seller.nexconnUserId, peerName = seller.name, listingId = l.id, openOffer = openOffer))
    }

    private fun mutate(block: suspend () -> Result<ListingDto>, done: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            block().onSuccess { updated ->
                _state.update { it.copy(listing = updated.copy(seller = it.listing?.seller, saved = it.listing?.saved), busy = false) }
                _toasts.tryEmit(done)
            }.onFailure { e -> _state.update { it.copy(busy = false) }; _toasts.tryEmit(e.message ?: "Couldn't update") }
        }
    }

    fun setReserved(reserved: Boolean) = mutate({ c.market.update(id, ListingUpdateRequest(status = if (reserved) "reserved" else "active")) }, if (reserved) "Marked reserved" else "Available again")
    fun markSold(buyerId: String?) = mutate({ c.market.markSold(id, buyerId) }, "Marked sold")

    fun delete() {
        viewModelScope.launch {
            c.market.delete(id).onSuccess { _state.update { it.copy(gone = true) }; _toasts.tryEmit("Listing deleted") }
                .onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't delete") }
        }
    }

    fun hide() {
        viewModelScope.launch {
            c.market.hide(id).onSuccess { _state.update { it.copy(gone = true) }; _toasts.tryEmit("Hidden from your feed") }
                .onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't hide") }
        }
    }

    fun report(reason: String, details: String) {
        val l = _state.value.listing ?: return
        viewModelScope.launch {
            c.market.report(l.sellerId, l.id, reason, details).onSuccess { _toasts.tryEmit("Thanks. We'll review this listing.") }
                .onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't send report") }
        }
    }

    fun blockSeller() {
        val seller = _state.value.listing?.seller ?: return
        viewModelScope.launch {
            c.nexconn.block(seller.nexconnUserId) // stops their direct messages (Nexconn blocklist)
            c.market.block(seller.id).onSuccess { _state.update { it.copy(gone = true) }; _toasts.tryEmit("${seller.name} is blocked") }
                .onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't block") }
        }
    }
}

@Composable
fun ListingDetailScreen(id: String, nav: NavHostController) {
    val vm = appViewModel(key = "listing-$id") { c, _ -> ListingDetailViewModel(c, id) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    var pickBuyer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(Unit) { vm.openChat.collect { nav.navigate(it) } }
    LaunchedEffect(state.gone) { if (state.gone) nav.popBackStack() }

    val listing = state.listing
    val mine = listing != null && listing.sellerId == vm.myId

    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (listing != null && !mine) {
                IconButton(onClick = vm::toggleSave) {
                    Icon(if (listing.saved == true) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (listing.saved == true) "Remove from saved" else "Save")
                }
            }
            if (listing != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = CB.Surface) {
                        if (mine) {
                            DropdownMenuItem(text = { Text("Delete listing", color = CB.Stamp) }, onClick = { menu = false; confirmDelete = true })
                        } else {
                            DropdownMenuItem(text = { Text("Hide from my feed") }, onClick = { menu = false; vm.hide() })
                            DropdownMenuItem(text = { Text("Report listing") }, onClick = { menu = false; reporting = true })
                            DropdownMenuItem(text = { Text("Block seller", color = CB.Stamp) }, onClick = { menu = false; confirmBlock = true })
                        }
                    }
                }
            }
        }

        when {
            state.loading && listing == null -> LoadingBlock()
            listing == null -> ErrorState(state.error ?: "This listing is no longer available.", vm::load)
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    PhotoPager(listing)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PriceTag(listing.price, size = TagSize.Large)
                            Spacer(Modifier.width(12.dp))
                            Text(if (listing.negotiable) "Open to offers" else "Fixed price", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
                        }
                        Text(listing.title, style = MaterialTheme.typography.headlineSmall)
                        FactsRow(listing)
                        if (listing.aiAssisted && listing.aiPriceMin != null && listing.aiPriceMax != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = CB.InkSoft, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "AI price check for used items like this: ${Money.rupees(listing.aiPriceMin)} to ${Money.rupees(listing.aiPriceMax)}",
                                    style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
                                )
                            }
                        }
                        if (listing.description.isNotBlank()) Text(listing.description, style = MaterialTheme.typography.bodyLarge)
                        listing.seller?.let { s ->
                            Panel {
                                Row(
                                    Modifier.fillMaxWidth().clickable { nav.navigate(UserRoute(s.id)) }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(s.name, s.avatarUrl, 48.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(if (mine) "You" else s.name, style = MaterialTheme.typography.titleMedium)
                                        listOfNotNull(s.hostel, s.department).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                                        }
                                        RatingLine(s.ratingAvg, s.ratingCount)
                                        Text(
                                            buildString {
                                                append("${s.soldCount} sold")
                                                s.responseRate?.let { append(", replies to $it% of chats") }
                                            },
                                            style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
                                        )
                                    }
                                    Icon(Icons.Outlined.ChevronRight, null, tint = CB.InkSoft)
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(CB.MarigoldWash, RoundedCornerShape(8.dp)).clickable { nav.navigate(SafetyRoute) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Shield, null, tint = CB.Ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Meet in a busy spot on campus and check the item before you pay. Read safety tips.", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                BottomActions(listing, mine, state, vm, nav, onPickBuyer = { pickBuyer = true })
            }
        }
    }

    if (confirmDelete) ConfirmDialog("Delete this listing?", "Students won't see it any more. Chats stay in your inbox.", "Delete", { confirmDelete = false; vm.delete() }, { confirmDelete = false }, danger = true)
    if (confirmBlock) ConfirmDialog(
        "Block ${listing?.seller?.name ?: "seller"}?",
        "They won't be able to message you, and you won't see each other's listings or wanted posts.",
        "Block", { confirmBlock = false; vm.blockSeller() }, { confirmBlock = false }, danger = true,
    )
    if (reporting) ReportDialog("listing", { r, d -> reporting = false; vm.report(r, d) }, { reporting = false })
    if (pickBuyer && listing != null) {
        val options = state.buyers.map { it.id to it.name } + ("" to "Someone outside CampusBazaar")
        ChoiceDialog(
            title = "Who bought it?",
            body = "Picking the buyer lets you both rate the deal.",
            options = options,
            confirm = "Mark sold",
            onConfirm = { id -> pickBuyer = false; vm.markSold(id.ifBlank { null }) },
            onDismiss = { pickBuyer = false },
        )
    }
}

@Composable
private fun PhotoPager(listing: ListingDto) {
    val photos = listing.photos.ifEmpty { listOf("") }
    val pager = rememberPagerState { photos.size }
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            ListingPhoto(photos[page], if (page == 0) listing.status else null, Modifier.fillMaxSize(), stampLarge = true)
        }
        if (photos.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(photos.size) { i ->
                    Box(Modifier.size(7.dp).background(if (i == pager.currentPage) CB.Ink else CB.Hairline, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun FactsRow(listing: ListingDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Fact(Icons.Outlined.LocalOffer, "${Category.of(listing.category).label}, ${Condition.of(listing.condition).label.lowercase()} condition")
        if (listing.meetupSpot.isNotBlank()) Fact(Icons.Outlined.Place, "Prefers to meet at ${listing.meetupSpot}")
        Fact(Icons.Outlined.TaskAlt, "Listed ${TimeText.relative(TimeText.parseIso(listing.createdAt))}")
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CB.InkSoft, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
    }
}

@Composable
private fun BottomActions(
    listing: ListingDto,
    mine: Boolean,
    state: ListingDetailViewModel.State,
    vm: ListingDetailViewModel,
    nav: NavHostController,
    onPickBuyer: () -> Unit,
) {
    val status = ListingStatus.of(listing.status)
    Column(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (mine) {
            when (status) {
                ListingStatus.Sold -> Text("Sold ${TimeText.relative(TimeText.parseIso(listing.soldAt))}. Nice deal.", style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Edit", { nav.navigate(SellRoute(editId = listing.id)) }, Modifier.weight(1f), icon = Icons.Outlined.Edit)
                    SecondaryButton(if (status == ListingStatus.Reserved) "Unreserve" else "Reserve", { vm.setReserved(status != ListingStatus.Reserved) }, Modifier.weight(1f), enabled = !state.busy)
                    PrimaryButton("Sold", onPickBuyer, Modifier.weight(1f), loading = state.busy)
                }
            }
        } else {
            when {
                status == ListingStatus.Sold -> Text("This item has been sold.", style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
                status == ListingStatus.Reserved && listing.reservedForId != vm.myId -> Column {
                    Text("Reserved for another buyer. You can still message the seller in case it falls through.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Chat with seller", { vm.chat(false) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Chat)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (listing.negotiable) SecondaryButton("Make offer", { vm.chat(true) }, Modifier.weight(1f), icon = Icons.Outlined.LocalOffer)
                    PrimaryButton("Chat with seller", { vm.chat(false) }, Modifier.weight(1.4f), icon = Icons.AutoMirrored.Outlined.Chat)
                }
            }
        }
    }
}

