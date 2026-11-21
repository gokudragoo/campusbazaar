package app.campusbazaar.ui.profile

import ai.nexconn.chat.channel.BaseChannel
import ai.nexconn.chat.channel.ChannelType
import ai.nexconn.chat.channel.SystemChannel
import ai.nexconn.chat.channel.model.ChannelIdentifier
import ai.nexconn.chat.params.MessagesQueryParams
import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.AppContainer
import app.campusbazaar.chat.ChatBody
import app.campusbazaar.chat.ChatMapper
import app.campusbazaar.data.api.ListingDto
import app.campusbazaar.data.api.ProfileResponse
import app.campusbazaar.data.api.ProfileUpdateRequest
import app.campusbazaar.data.api.UserDto
import app.campusbazaar.data.api.UserStatsDto
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.ActivityRoute
import app.campusbazaar.ui.BlockedRoute
import app.campusbazaar.ui.CampusPickerRoute
import app.campusbazaar.ui.ChatRoute
import app.campusbazaar.ui.EditProfileRoute
import app.campusbazaar.ui.ListingRoute
import app.campusbazaar.ui.MyListingsRoute
import app.campusbazaar.ui.SafetyRoute
import app.campusbazaar.ui.SavedRoute
import app.campusbazaar.ui.SellRoute
import app.campusbazaar.ui.WantedCreateRoute
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.ConfirmDialog
import app.campusbazaar.ui.components.EmptyState
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.Hairline
import app.campusbazaar.ui.components.ListingTile
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.Panel
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.RatingLine
import app.campusbazaar.ui.components.ReportDialog
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.util.Permissions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
private fun TopBar(title: String, nav: NavHostController, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/* ------------------------------------------------------------------------- Me --- */

class MeViewModel(private val c: AppContainer) : ViewModel() {
    val session = c.session
    private val _stats = MutableStateFlow<UserStatsDto?>(null)
    val stats = _stats.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch { c.auth.me().onSuccess { _stats.value = it.stats } }
    fun signOut() = viewModelScope.launch { c.session.signOut() }

    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()

    fun deleteAccount(password: String) = viewModelScope.launch {
        _deleting.value = true
        _deleteError.value = null
        c.auth.deleteAccount(password).onFailure { _deleteError.value = it.message ?: "Couldn't delete your account. Try again." }
        _deleting.value = false
    }

    fun clearDeleteError() { _deleteError.value = null }
}

@Composable
fun MeScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> MeViewModel(c) }
    val auth by vm.session.state.collectAsState()
    val stats by vm.stats.collectAsState()
    val session = vm.session.current
    val context = LocalContext.current
    var confirmOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(Permissions.canNotify(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    val user = session?.user ?: return
    @Suppress("UNUSED_EXPRESSION") auth

    Column(Modifier.fillMaxSize().background(CB.Paper).verticalScroll(rememberScrollState())) {
        TopBar("Profile", nav)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user.name, user.avatarUrl, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.headlineSmall)
                    Text(listOfNotNull(user.hostel, user.department).joinToString(", ").ifBlank { user.email ?: "" }, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                    Text(session.campus?.let { "${it.name}, ${it.city}" } ?: "", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                }
            }
            stats?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(if (s.ratingAvg == null) "–" else "%.1f".format(s.ratingAvg), if (s.ratingCount == 0) "No ratings" else "${s.ratingCount} ${if (s.ratingCount == 1) "rating" else "ratings"}", Modifier.weight(1f))
                    StatBox("${s.activeListings}", "Listed now", Modifier.weight(1f))
                    StatBox("${s.soldCount}", "Sold", Modifier.weight(1f))
                    StatBox(s.responseRate?.let { "$it%" } ?: "–", "Replies", Modifier.weight(1f))
                }
            }
            if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
                Column(Modifier.fillMaxWidth().background(CB.MarigoldWash, RoundedCornerShape(8.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Get notified about offers", style = MaterialTheme.typography.titleSmall)
                    Text("Allow notifications so new messages and offers show up while CampusBazaar is running in the background.", style = MaterialTheme.typography.bodySmall)
                    SecondaryButton("Allow notifications", { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
            }
            Panel {
                MenuRow(Icons.Outlined.Inventory2, "My listings") { nav.navigate(MyListingsRoute) }
                Hairline()
                MenuRow(Icons.Outlined.BookmarkBorder, "Saved items") { nav.navigate(SavedRoute) }
                Hairline()
                MenuRow(Icons.Outlined.NotificationsNone, "Activity") { nav.navigate(ActivityRoute) }
                Hairline()
                MenuRow(Icons.Outlined.Edit, "Edit profile") { nav.navigate(EditProfileRoute) }
                Hairline()
                MenuRow(Icons.Outlined.AccountBalance, "Switch campus") { nav.navigate(CampusPickerRoute(switching = true)) }
            }
            Panel {
                MenuRow(Icons.Outlined.Shield, "Safety tips") { nav.navigate(SafetyRoute) }
                Hairline()
                MenuRow(Icons.Outlined.Block, "Blocked students") { nav.navigate(BlockedRoute) }
                Hairline()
                MenuRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", danger = true) { confirmOut = true }
                Hairline()
                MenuRow(Icons.Outlined.DeleteForever, "Delete account", danger = true) { vm.clearDeleteError(); confirmDelete = true }
            }
            Text(
                "Push notifications aren't available yet, so alerts arrive while the app is open or running in the background.",
                style = MaterialTheme.typography.bodySmall, color = CB.InkFaint,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmOut) ConfirmDialog("Sign out?", "You'll need your email and password to sign back in.", "Sign out", { confirmOut = false; vm.signOut() }, { confirmOut = false })
    if (confirmDelete) DeleteAccountDialog(vm) { confirmDelete = false }
}

@Composable
private fun DeleteAccountDialog(vm: MeViewModel, onDismiss: () -> Unit) {
    val deleting by vm.deleting.collectAsState()
    val error by vm.deleteError.collectAsState()
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        containerColor = CB.Surface,
        title = { Text("Delete your account?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your profile, listings and wanted posts are removed for good, and you're signed out everywhere. Students you dealt with keep their chat history.",
                    style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft,
                )
                Field(
                    password, { password = it }, "Password",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    error = error,
                    enabled = !deleting,
                )
            }
        },
        confirmButton = { QuietButton(if (deleting) "Deleting…" else "Delete account", { vm.deleteAccount(password) }, enabled = password.isNotEmpty() && !deleting, danger = true) },
        dismissButton = { QuietButton("Cancel", onDismiss, enabled = !deleting) },
    )
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(CB.Surface, RoundedCornerShape(8.dp)).padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CB.InkSoft, maxLines = 1)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) CB.Stamp else CB.Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) CB.Stamp else CB.Ink, modifier = Modifier.weight(1f))
        if (!danger) Icon(Icons.Outlined.ChevronRight, null, tint = CB.InkSoft)
    }
}

/* --------------------------------------------------------------- Edit profile --- */

class EditProfileViewModel(private val c: AppContainer) : ViewModel() {
    val user: UserDto? = c.session.me
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _avatar = MutableStateFlow(user?.avatarUrl)
    val avatar = _avatar.asStateFlow()
    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun uploadAvatar(uri: android.net.Uri) {
        _uploading.value = true
        viewModelScope.launch {
            c.market.uploadPhoto(uri, kind = "avatar").onSuccess { _avatar.value = it }.onFailure { _messages.tryEmit(it.message ?: "Photo upload failed") }
            _uploading.value = false
        }
    }

    fun save(name: String, hostel: String, department: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            c.auth.updateProfile(ProfileUpdateRequest(name.trim(), hostel.trim(), department.trim(), _avatar.value))
                .onSuccess { _saved.tryEmit(Unit) }.onFailure { _messages.tryEmit(it.message ?: "Couldn't save") }
            _busy.value = false
        }
    }
}

@Composable
fun EditProfileScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> EditProfileViewModel(c) }
    val busy by vm.busy.collectAsState()
    val avatar by vm.avatar.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(vm.user?.name ?: "") }
    var hostel by rememberSaveable { mutableStateOf(vm.user?.hostel ?: "") }
    var department by rememberSaveable { mutableStateOf(vm.user?.department ?: "") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.uploadAvatar(uri) }
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(Unit) { vm.saved.collect { Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show(); nav.popBackStack() } }

    Column(Modifier.fillMaxSize().background(CB.Paper).imePadding()) {
        TopBar("Edit profile", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Avatar(name, avatar, 84.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(30.dp).background(CB.Ink, CircleShape).clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CameraAlt, "Change photo", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp)) }
                }
                Spacer(Modifier.width(16.dp))
                Text(if (uploading) "Uploading photo…" else "A clear photo helps buyers trust you.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
            Field(name, { name = it }, "Name", maxLength = 60, error = if (name.trim().length < 2) "Enter your name." else null)
            Field(hostel, { hostel = it }, "Hostel (optional)", placeholder = "e.g. Hostel 4, Room 212 block", maxLength = 60)
            Field(department, { department = it }, "Department (optional)", placeholder = "e.g. Mechanical, 2nd year", maxLength = 80)
        }
        Column(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton("Save", { if (name.trim().length >= 2 && !uploading) vm.save(name, hostel, department) }, Modifier.fillMaxWidth(), loading = busy)
        }
    }
}

/* ---------------------------------------------------------- Listings & saved --- */

class ListingsListViewModel(private val c: AppContainer, private val saved: Boolean) : ViewModel() {
    data class State(val items: List<ListingDto> = emptyList(), val loading: Boolean = true, val error: String? = null)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    init { load() }
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            (if (saved) c.market.saved() else c.market.myListings()).onSuccess { list -> _state.update { it.copy(items = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

@Composable
fun MyListingsScreen(nav: NavHostController) {
    val vm = appViewModel(key = "my-listings") { c, _ -> ListingsListViewModel(c, saved = false) }
    val state by vm.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf("active") }
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar("My listings", nav)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("active" to "Active", "reserved" to "Reserved", "sold" to "Sold").forEach { (id, label) ->
                val n = state.items.count { it.status == id }
                SelectChip("$label $n", filter == id, { filter = id })
            }
        }
        val shown = state.items.filter { it.status == filter }
        ListingGrid(state.loading, state.error, shown, vm::load, nav) {
            EmptyState(Icons.Outlined.Inventory2, "Nothing here", if (filter == "sold") "Items you mark sold show up here." else "List an item in under a minute.",
                action = if (filter == "active") ({ PrimaryButton("Sell something", { nav.navigate(SellRoute()) }, accent = true) }) else null)
        }
    }
}

@Composable
fun SavedScreen(nav: NavHostController) {
    val vm = appViewModel(key = "saved") { c, _ -> ListingsListViewModel(c, saved = true) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar("Saved items", nav)
        ListingGrid(state.loading, state.error, state.items, vm::load, nav) {
            EmptyState(Icons.Outlined.BookmarkBorder, "No saved items", "Tap the bookmark on a listing to keep it here while you decide.")
        }
    }
}

@Composable
private fun ListingGrid(loading: Boolean, error: String?, items: List<ListingDto>, retry: () -> Unit, nav: NavHostController, empty: @Composable () -> Unit) {
    when {
        loading && items.isEmpty() -> LoadingBlock()
        error != null && items.isEmpty() -> ErrorState(error, retry)
        items.isEmpty() -> empty()
        else -> LazyVerticalGrid(
            GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { l ->
                ListingTile(l.title, l.price, l.photos.firstOrNull(), l.status, "${Condition.of(l.condition).label}, ${TimeText.relative(TimeText.parseIso(l.createdAt))}", { nav.navigate(ListingRoute(l.id)) })
            }
        }
    }
}

/* -------------------------------------------------------------- Public profile --- */

class UserProfileViewModel(private val c: AppContainer, private val userId: String) : ViewModel() {
    data class State(val profile: ProfileResponse? = null, val loading: Boolean = true, val error: String? = null)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toasts = _toasts.asSharedFlow()
    val isMe get() = c.session.me?.id == userId
    init { load() }
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            c.market.profile(userId).onSuccess { p -> _state.update { it.copy(profile = p, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
    fun block() = viewModelScope.launch {
        val p = _state.value.profile ?: return@launch
        c.nexconn.block(p.user.nexconnUserId)
        c.market.block(userId).onSuccess { _toasts.tryEmit("${p.user.name} is blocked"); load() }.onFailure { _toasts.tryEmit(it.message ?: "Couldn't block") }
    }
    fun unblock() = viewModelScope.launch {
        val p = _state.value.profile ?: return@launch
        c.nexconn.unblock(p.user.nexconnUserId)
        c.market.unblock(userId).onSuccess { _toasts.tryEmit("${p.user.name} is unblocked"); load() }
    }
    fun report(reason: String, details: String) = viewModelScope.launch {
        c.market.report(userId, null, reason, details).onSuccess { _toasts.tryEmit("Thanks. We'll review this.") }.onFailure { _toasts.tryEmit(it.message ?: "Couldn't send report") }
    }
}

@Composable
fun UserProfileScreen(userId: String, nav: NavHostController) {
    val vm = appViewModel(key = "user-$userId") { c, _ -> UserProfileViewModel(c, userId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    val p = state.profile

    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar(p?.user?.name ?: "Profile", nav) {
            if (p != null && !vm.isMe) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }, containerColor = CB.Surface) {
                    DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; reporting = true })
                    if (p.blocked) DropdownMenuItem(text = { Text("Unblock") }, onClick = { menu = false; vm.unblock() })
                    else DropdownMenuItem(text = { Text("Block", color = CB.Stamp) }, onClick = { menu = false; confirmBlock = true })
                }
            }
        }
        when {
            state.loading && p == null -> LoadingBlock()
            p == null -> ErrorState(state.error ?: "Couldn't load this profile.", vm::load)
            else -> LazyVerticalGrid(
                GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.user.name, p.user.avatarUrl, 64.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(p.user.name, style = MaterialTheme.typography.headlineSmall)
                                listOfNotNull(p.user.hostel, p.user.department).joinToString(", ").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft) }
                                Text("Joined ${TimeText.relative(TimeText.parseIso(p.user.joinedAt))}", style = MaterialTheme.typography.bodySmall, color = CB.InkFaint)
                            }
                        }
                        RatingLine(p.user.ratingAvg, p.user.ratingCount)
                        Text(buildString {
                            append("${p.user.soldCount} sold, ${p.user.activeListings} listed now")
                            p.user.responseRate?.let { append(", replies to $it% of chats") }
                        }, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                        if (p.blocked) Text("You and this student have blocked each other's listings.", style = MaterialTheme.typography.bodySmall, color = CB.Stamp)
                        else if (!vm.isMe) SecondaryButton("Message", { nav.navigate(ChatRoute(p.user.nexconnUserId, p.user.name)) })
                        if (p.ratings.isNotEmpty()) {
                            Text("Ratings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            Panel {
                                p.ratings.forEachIndexed { i, r ->
                                    if (i > 0) Hairline()
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            repeat(r.stars) { Icon(Icons.Outlined.Star, null, tint = CB.Ink, modifier = Modifier.size(14.dp)) }
                                            Spacer(Modifier.width(8.dp))
                                            Text("${r.rater?.name ?: "Student"}, as ${r.rateeRole}", style = MaterialTheme.typography.labelSmall, color = CB.InkSoft)
                                        }
                                        if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        if (p.listings.isNotEmpty()) Text("Listings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(p.listings, key = { it.id }) { l ->
                    ListingTile(l.title, l.price, l.photos.firstOrNull(), l.status, Condition.of(l.condition).label, { nav.navigate(ListingRoute(l.id)) })
                }
            }
        }
    }
    if (reporting) ReportDialog(p?.user?.name ?: "student", { r, d -> reporting = false; vm.report(r, d) }, { reporting = false })
    if (confirmBlock) ConfirmDialog("Block ${p?.user?.name}?", "They won't be able to message you, and you won't see each other's listings or wanted posts.", "Block", { confirmBlock = false; vm.block() }, { confirmBlock = false }, danger = true)
}

/* -------------------------------------------------------------------- Blocked --- */

class BlockedViewModel(private val c: AppContainer) : ViewModel() {
    data class State(val users: List<UserDto> = emptyList(), val loading: Boolean = true, val error: String? = null)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        c.market.blocks().onSuccess { u -> _state.update { it.copy(users = u, loading = false) } }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }
    fun unblock(u: UserDto) = viewModelScope.launch {
        c.nexconn.unblock(u.nexconnUserId)
        c.market.unblock(u.id).onSuccess { load() }
    }
}

@Composable
fun BlockedScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> BlockedViewModel(c) }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar("Blocked students", nav)
        when {
            state.loading && state.users.isEmpty() -> LoadingBlock()
            state.error != null && state.users.isEmpty() -> ErrorState(state.error!!, { vm.load() })
            state.users.isEmpty() -> EmptyState(Icons.Outlined.Block, "No one is blocked", "If someone makes you uncomfortable, block them from their profile or the chat menu.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Panel {
                        state.users.forEachIndexed { i, u ->
                            if (i > 0) Hairline()
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(u.name, u.avatarUrl, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(u.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                QuietButton("Unblock", { vm.unblock(u) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------- Activity --- */

data class ActivityItem(val key: String, val title: String, val body: String, val time: Long, val listingId: String?, val peerUserId: String?, val kind: String)

/** Activity inbox: notices the server bot sends into the Nexconn system channel. */
class ActivityViewModel(private val c: AppContainer) : ViewModel() {
    data class State(val items: List<ActivityItem> = emptyList(), val loading: Boolean = true)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { c.nexconn.events.collect { e -> if (e is app.campusbazaar.chat.ChatEvent.Received && e.message.channelIdentifier?.channelType == ChannelType.SYSTEM) load() } }
    }

    fun load() = viewModelScope.launch {
        val senders = c.nexconn.channels(listOf(ChannelType.SYSTEM), 20).getOrNull().orEmpty().map { it.channelId }
            .ifEmpty { listOfNotNull(c.noticeSenderId) }
        val all = mutableListOf<ActivityItem>()
        for (sender in senders) {
            val query = BaseChannel.createMessagesQuery(MessagesQueryParams(ChannelIdentifier(ChannelType.SYSTEM, sender)).apply { pageSize = 50; isAscending = false })
            c.nexconn.loadNextPage(query).onSuccess { msgs ->
                msgs.forEach { m ->
                    val item = ChatMapper.toItem(m, null)
                    val body = item.body
                    if (body is ChatBody.Notice) all += ActivityItem(item.key, body.title, body.body, item.sentTime, body.listingId, body.peerUserId, body.kind)
                    else if (body is ChatBody.Text) all += ActivityItem(item.key, "CampusBazaar", body.text, item.sentTime, null, null, "text")
                }
            }
            c.nexconn.clearUnread(SystemChannel(sender))
        }
        _state.value = State(all.sortedByDescending { it.time }.distinctBy { it.key }, loading = false)
    }
}

@Composable
fun ActivityScreen(nav: NavHostController) {
    val vm = appViewModel { c, _ -> ActivityViewModel(c) }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar("Activity", nav)
        when {
            state.loading && state.items.isEmpty() -> LoadingBlock()
            state.items.isEmpty() -> EmptyState(Icons.Outlined.NotificationsNone, "Nothing yet", "Reservations, sales, ratings and responses to your wanted posts appear here.",
                action = { SecondaryButton("Post a wanted request", { nav.navigate(WantedCreateRoute) }) })
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.key }) { a ->
                    Column(
                        Modifier.fillMaxWidth().background(CB.Surface, RoundedCornerShape(8.dp))
                            .clickable(enabled = a.peerUserId != null || a.listingId != null) {
                                if (a.peerUserId != null) nav.navigate(ChatRoute(a.peerUserId)) else a.listingId?.let { nav.navigate(ListingRoute(it)) }
                            }.padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(if (a.kind == "rate_deal") CB.Marigold else CB.Ink, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(a.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(TimeText.short(a.time), style = MaterialTheme.typography.labelSmall, color = CB.InkFaint)
                        }
                        Text(a.body, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft, modifier = Modifier.padding(top = 4.dp, start = 16.dp))
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------------- Safety --- */

@Composable
fun SafetyScreen(nav: NavHostController) {
    val tips = listOf(
        "Meet on campus, in daylight, somewhere busy" to "Library entrances, canteens and the main gate security desk are good spots. Avoid hostel rooms of people you don't know.",
        "Check the item before you pay" to "Switch on the calculator, flip through the book, ride the cycle around. It's normal to take a few minutes.",
        "Pay in person, never in advance" to "Don't send UPI payments or deposits before you have the item. Anyone asking for advance payment is a red flag: report them.",
        "Keep the conversation in CampusBazaar" to "Chat history here helps if something goes wrong. Be careful sharing your phone number or room number early on.",
        "Bring a friend for expensive items" to "For laptops, phones or cycles, ask a friend to come along and tell someone where you're going.",
        "Trust your instincts" to "If a deal feels off, walk away. You can block and report any student from the chat menu or their profile.",
    )
    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        TopBar("Safety tips", nav)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tips) { (title, body) ->
                Column(Modifier.fillMaxWidth().background(CB.Surface, RoundedCornerShape(8.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
                }
            }
            item {
                Text("In an emergency on campus, contact your campus security office first.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
