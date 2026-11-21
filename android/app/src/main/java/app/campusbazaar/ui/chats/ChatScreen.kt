package app.campusbazaar.ui.chats

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import app.campusbazaar.chat.ChatBody
import app.campusbazaar.chat.ChatItem
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.ListingStatus
import app.campusbazaar.ui.ListingRoute
import app.campusbazaar.ui.UserRoute
import app.campusbazaar.ui.appContainer
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.ConfirmDialog
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.PriceTag
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.ReportDialog
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.components.TagSize
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.util.Permissions
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId

@Composable
fun ChatScreen(peerId: String, initialName: String, listingId: String?, openOffer: Boolean, nav: NavHostController) {
    val container = appContainer()
    val context = LocalContext.current
    val vm = appViewModel(key = "chat-$peerId") { c, _ -> ChatViewModel(c, context.applicationContext, peerId, initialName, listingId, openOffer) }
    val state by vm.state.collectAsState()
    val conn by container.nexconn.connection.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var confirmSold by remember { mutableStateOf(false) }

    DisposableEffect(peerId) {
        container.openChatPeer = peerId
        onDispose { if (container.openChatPeer == peerId) container.openChatPeer = null }
    }
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendImage(uri) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.sendLocation(state.activeThread?.listing?.meetupSpot.orEmpty())
        else Toast.makeText(context, "Location is off for CampusBazaar. You can still type the spot.", Toast.LENGTH_LONG).show()
    }

    val listState = rememberLazyListState()
    val reversed = state.items.asReversed()
    val nearTop by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearTop, state.items.size) { if (nearTop && state.items.isNotEmpty()) vm.loadOlder() }
    LaunchedEffect(state.items.lastOrNull()?.key) { if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(CB.Paper).imePadding()) {
        // Top bar
        Row(Modifier.background(CB.Surface).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = state.peerInternalId != null) { state.peerInternalId?.let { nav.navigate(UserRoute(it)) } }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.peerName, state.peerAvatar, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(state.peerName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        state.typing -> "typing…"
                        conn == app.campusbazaar.chat.ChatConnection.Connecting -> "Connecting…"
                        conn == app.campusbazaar.chat.ChatConnection.Offline -> "Offline. Messages send when you're back"
                        else -> state.deal?.peer?.let { p -> listOfNotNull(p.hostel, p.department).joinToString(", ").ifBlank { null } }
                    }
                    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (state.typing) CB.Ink else CB.InkSoft, maxLines = 1)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }, containerColor = CB.Surface) {
                    state.peerInternalId?.let { id -> DropdownMenuItem(text = { Text("View profile") }, onClick = { menu = false; nav.navigate(UserRoute(id)) }) }
                    DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; reporting = true })
                    if (state.blockedByMe) DropdownMenuItem(text = { Text("Unblock") }, onClick = { menu = false; vm.unblock() })
                    else DropdownMenuItem(text = { Text("Block", color = CB.Stamp) }, onClick = { menu = false; confirmBlock = true })
                }
            }
        }

        DealHeader(state, vm, onOpenListing = { nav.navigate(ListingRoute(it)) }, onSold = { confirmSold = true })

        Box(Modifier.weight(1f)) {
            if (state.loading && state.items.isEmpty()) {
                LoadingBlock()
            } else if (state.items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Say hello to ${state.peerName}", style = MaterialTheme.typography.titleMedium)
                    Text("Ask about the item, then send an offer card when you're ready.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                }
            } else {
                LazyColumn(state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), modifier = Modifier.fillMaxSize()) {
                    items(reversed.size, key = { reversed[it].key }) { idx ->
                        val item = reversed[idx]
                        val older = reversed.getOrNull(idx + 1)
                        Column {
                            if (older == null || !sameDay(older.sentTime, item.sentTime)) DaySeparator(item.sentTime)
                            MessageRow(item, state, vm, onOpenListing = { nav.navigate(ListingRoute(it)) }, onOpenImage = { viewer = it })
                        }
                    }
                    if (state.loadingOlder) item { LoadingBlock() }
                }
            }
        }

        if (state.blockedByMe) {
            Row(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = CB.Stamp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("You blocked ${state.peerName}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                QuietButton("Unblock", { vm.unblock() })
            }
        } else {
            InputBar(
                value = input,
                onChange = { input = it; if (it.isNotBlank()) vm.onTyping() },
                onSend = { vm.sendText(input); input = "" },
                onPhoto = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onLocation = {
                    if (Permissions.hasLocation(context)) vm.sendLocation(state.activeThread?.listing?.meetupSpot.orEmpty())
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                sendingLocation = state.sendingLocation,
            )
        }
    }

    when (val sheet = state.sheet) {
        is ChatSheet.MakeOffer -> OfferSheet("Make an offer", sheet.listing.title, sheet.listing.price, sheet.listing.negotiable, null, state.busy, vm::submitOffer, vm::dismissSheet)
        is ChatSheet.Counter -> OfferSheet("Counter offer", sheet.listingTitle, sheet.askingPrice, sheet.negotiable, sheet.theirAmount, state.busy, vm::submitCounter, vm::dismissSheet)
        is ChatSheet.Meetup -> MeetupSheet(sheet.listingTitle, sheet.defaultSpot, state.busy, vm::submitMeetup, vm::dismissSheet)
        is ChatSheet.Rate -> RateSheet(sheet.peerName, sheet.peerRole, sheet.listingTitle, state.busy, vm::submitRating, vm::dismissSheet)
        null -> Unit
    }
    if (confirmBlock) ConfirmDialog("Block ${state.peerName}?", "They won't be able to message you, and you won't see each other's listings.", "Block", { confirmBlock = false; vm.block() }, { confirmBlock = false }, danger = true)
    if (reporting) ReportDialog(state.peerName, { r, d -> reporting = false; vm.report(r, d) }, { reporting = false })
    if (confirmSold) {
        val title = state.activeThread?.listing?.title ?: "this item"
        ConfirmDialog("Mark as sold to ${state.peerName}?", "$title will leave the campus feed and you can both rate the deal.", "Mark sold", { confirmSold = false; vm.markSold() }, { confirmSold = false })
    }
    viewer?.let { url ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val z = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(z).toLocalDate() == Instant.ofEpochMilli(b).atZone(z).toLocalDate()
}

@Composable
private fun MessageRow(item: ChatItem, state: ChatViewModel.State, vm: ChatViewModel, onOpenListing: (String) -> Unit, onOpenImage: (String) -> Unit) {
    val body = item.body
    if (body is ChatBody.Deal) {
        DealPill(body, item.mine)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (item.mine) Alignment.End else Alignment.Start) {
        when (body) {
            is ChatBody.Text -> TextBubble(body.text, item.mine)
            is ChatBody.Image -> ImageBubble(body, item.mine, onOpenImage)
            is ChatBody.Location -> LocationBubble(body)
            is ChatBody.ListingCard -> ListingCardBubble(body) { onOpenListing(body.listingId) }
            is ChatBody.OfferCard -> {
                val st = vm.offerState(body)
                val me = vm.me.orEmpty()
                OfferCardBubble(
                    body, item.mine, st,
                    canRespond = DealRules.canRespond(st, body.fromUserId, me) && !item.mine,
                    busy = state.busy,
                    onAccept = { vm.accept(body.offerId) },
                    onCounter = { vm.openCounter(body.offerId) },
                    onDecline = { vm.decline(body.offerId) },
                )
            }
            is ChatBody.MeetupCard -> MeetupCardBubble(
                body, item.mine, vm.meetupState(body), state.busy,
                onConfirm = { vm.meetupAction(body.meetupId, "confirm") },
                onDecline = { vm.meetupAction(body.meetupId, "decline") },
                onCancel = { vm.meetupAction(body.meetupId, "cancel") },
            )
            is ChatBody.Notice -> TextBubble("${body.title}\n${body.body}", false)
            ChatBody.Unsupported -> TextBubble("This message needs a newer version of CampusBazaar.", item.mine)
            is ChatBody.Deal -> Unit
        }
        MessageMeta(item) { vm.retry(item.key) }
    }
}

/** Pinned deal context: which listing this chat is about and the next sensible action. */
@Composable
private fun DealHeader(state: ChatViewModel.State, vm: ChatViewModel, onOpenListing: (String) -> Unit, onSold: () -> Unit) {
    val threads = state.deal?.threads.orEmpty()
    val t = state.activeThread ?: return
    val l = t.listing ?: return
    val status = ListingStatus.of(l.status)
    val myId = vm.myInternalId
    Column(Modifier.fillMaxWidth().background(CB.Surface).border(width = 0.5.dp, color = CB.Hairline)) {
        if (threads.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(threads, key = { it.id }) { th -> SelectChip(th.listing?.title?.take(24) ?: "Item", th.id == t.id, { vm.selectThread(th.id) }) }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(CB.InkWash).clickable { onOpenListing(l.id) }) {
                l.photos.firstOrNull()?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { onOpenListing(l.id) }) {
                Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriceTag(l.price, size = TagSize.Small)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (status) {
                            ListingStatus.Reserved -> if (l.reservedForId == myId || (t.role == "seller" && l.reservedForId == state.peerInternalId)) "Reserved for this deal" else "Reserved"
                            ListingStatus.Sold -> "Sold"
                            else -> if (l.negotiable) "Open to offers" else "Fixed price"
                        },
                        style = MaterialTheme.typography.labelSmall, color = CB.InkSoft,
                    )
                }
            }
            val reservedHere = status == ListingStatus.Reserved && (l.reservedForId == myId || l.reservedForId == state.peerInternalId)
            val soldHere = status == ListingStatus.Sold && (l.soldToId == myId || l.soldToId == state.peerInternalId)
            when {
                soldHere && !t.iRated -> HeaderAction("Rate", vm::openRate, primary = true)
                soldHere -> Text("Rated", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
                t.role == "buyer" && status == ListingStatus.Active -> HeaderAction(if (l.negotiable) "Offer" else "Buy", vm::openMakeOffer, primary = true)
                t.role == "buyer" && reservedHere -> HeaderAction("Meetup", vm::openMeetup, primary = true)
                t.role == "seller" && (status == ListingStatus.Active || reservedHere) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderAction("Meetup", vm::openMeetup, primary = false)
                    HeaderAction("Sold", onSold, primary = true)
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit, primary: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .then(if (primary) Modifier.background(CB.Ink) else Modifier.border(1.dp, CB.Ink, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) Color.White else CB.Ink) }
}

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit, onPhoto: () -> Unit, onLocation: () -> Unit, sendingLocation: Boolean) {
    Row(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = onPhoto) { Icon(Icons.Outlined.AddPhotoAlternate, "Send a photo") }
        IconButton(onClick = onLocation, enabled = !sendingLocation) {
            if (sendingLocation) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CB.Ink)
            else Icon(Icons.Outlined.MyLocation, "Share my location")
        }
        Box(Modifier.weight(1f).heightIn(min = 44.dp).background(CB.Paper, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 11.dp)) {
            if (value.isEmpty()) Text("Message", style = MaterialTheme.typography.bodyLarge, color = CB.InkFaint)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 2000) onChange(it) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = CB.Ink),
                cursorBrush = SolidColor(CB.Ink),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(44.dp).background(if (value.isNotBlank()) CB.Ink else CB.InkWash, RoundedCornerShape(22.dp)),
        ) { Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = if (value.isNotBlank()) Color.White else CB.InkFaint, modifier = Modifier.size(20.dp)) }
    }
}
