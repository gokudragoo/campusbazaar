package app.campusbazaar.ui

import ai.nexconn.chat.channel.ChannelType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.campusbazaar.AppContainer
import app.campusbazaar.InAppBanner
import app.campusbazaar.chat.ChatEvent
import app.campusbazaar.data.session.AuthState
import app.campusbazaar.ui.auth.SignInScreen
import app.campusbazaar.ui.auth.SignUpScreen
import app.campusbazaar.ui.auth.WelcomeScreen
import app.campusbazaar.ui.browse.BrowseScreen
import app.campusbazaar.ui.campus.CampusPickerScreen
import app.campusbazaar.ui.chats.ChatScreen
import app.campusbazaar.ui.chats.ConversationsScreen
import app.campusbazaar.ui.listing.ListingDetailScreen
import app.campusbazaar.ui.profile.ActivityScreen
import app.campusbazaar.ui.profile.BlockedScreen
import app.campusbazaar.ui.profile.EditProfileScreen
import app.campusbazaar.ui.profile.MeScreen
import app.campusbazaar.ui.profile.MyListingsScreen
import app.campusbazaar.ui.profile.SafetyScreen
import app.campusbazaar.ui.profile.SavedScreen
import app.campusbazaar.ui.profile.UserProfileScreen
import app.campusbazaar.ui.sell.CameraScreen
import app.campusbazaar.ui.sell.SellScreen
import app.campusbazaar.ui.square.SquareScreen
import app.campusbazaar.ui.square.SubchannelScreen
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.ui.wanted.WantedCreateScreen
import app.campusbazaar.ui.wanted.WantedScreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun AppRoot(container: AppContainer, pendingPeer: String?, pendingActivity: Boolean, onPendingHandled: () -> Unit) {
    val auth by container.session.state.collectAsStateWithLifecycle()
    when (val state = auth) {
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(CB.Paper))
        AuthState.SignedOut -> AuthFlow()
        is AuthState.NeedsCampus -> CampusPickerScreen(switching = false, onDone = {}, onBack = null)
        is AuthState.Ready -> MainFlow(container, pendingPeer, pendingActivity, onPendingHandled)
    }
}

@Composable
private fun AuthFlow() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> { WelcomeScreen(onSignUp = { nav.navigate(SignUpRoute) }, onSignIn = { nav.navigate(SignInRoute) }) }
        composable<SignInRoute> { SignInScreen(onBack = { nav.popBackStack() }, onCreateAccount = { nav.navigate(SignUpRoute) { popUpTo(WelcomeRoute) } }) }
        composable<SignUpRoute> { SignUpScreen(onBack = { nav.popBackStack() }, onSignIn = { nav.navigate(SignInRoute) { popUpTo(WelcomeRoute) } }) }
    }
}

/** Unread counts for the Chats tab (direct channels) and the Activity bell (system channel). */
@OptIn(FlowPreview::class)
class BadgesViewModel(private val c: AppContainer) : ViewModel() {
    private val _chats = MutableStateFlow(0)
    val chats: StateFlow<Int> = _chats.asStateFlow()
    private val _activity = MutableStateFlow(0)
    val activity: StateFlow<Int> = _activity.asStateFlow()

    init {
        viewModelScope.launch { c.nexconn.connection.collect { refresh() } }
        // Debounced, never suspending inside collect: a slow collector would back up the shared event flow.
        viewModelScope.launch {
            c.nexconn.events
                .filter { it is ChatEvent.Received || it is ChatEvent.UnreadChanged }
                .debounce(250)
                .collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            c.nexconn.directChannels().onSuccess { list -> _chats.value = list.sumOf { it.unreadCount } }
            c.nexconn.channels(listOf(ChannelType.SYSTEM), 20).onSuccess { list -> _activity.value = list.sumOf { it.unreadCount } }
        }
    }
}

enum class HomeTab(val label: String, val icon: ImageVector) {
    Browse("Browse", Icons.Outlined.Storefront),
    Wanted("Wanted", Icons.Outlined.Search),
    Sell("Sell", Icons.Outlined.AddCircleOutline),
    Chats("Chats", Icons.Outlined.ChatBubbleOutline),
    Square("Square", Icons.Outlined.Forum),
}

@Composable
private fun MainFlow(container: AppContainer, pendingPeer: String?, pendingActivity: Boolean, onPendingHandled: () -> Unit) {
    val nav = rememberNavController()
    val badges = appViewModel { c, _ -> BadgesViewModel(c) }
    var banner by remember { mutableStateOf<InAppBanner?>(null) }

    LaunchedEffect(Unit) { container.banners.collect { banner = it; delay(4500); banner = null } }
    LaunchedEffect(pendingPeer, pendingActivity) {
        if (pendingPeer != null) nav.navigate(ChatRoute(pendingPeer))
        else if (pendingActivity) nav.navigate(ActivityRoute)
        if (pendingPeer != null || pendingActivity) onPendingHandled()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = HomeRoute) {
            composable<HomeRoute> { HomeScaffold(nav, badges) }
            composable<ListingRoute> { ListingDetailScreen(id = it.toRoute<ListingRoute>().id, nav = nav) }
            composable<ChatRoute> { entry ->
                val r = entry.toRoute<ChatRoute>()
                ChatScreen(peerId = r.peerId, initialName = r.peerName, listingId = r.listingId, openOffer = r.openOffer, nav = nav)
            }
            composable<SellRoute> { SellScreen(nav = nav, editId = it.toRoute<SellRoute>().editId) }
            composable<CameraRoute> { CameraScreen(nav = nav) }
            composable<SubchannelRoute> { entry ->
                val r = entry.toRoute<SubchannelRoute>()
                SubchannelScreen(channelId = r.channelId, subchannelId = r.subchannelId, name = r.name, nav = nav)
            }
            composable<UserRoute> { UserProfileScreen(userId = it.toRoute<UserRoute>().id, nav = nav) }
            composable<MeRoute> { MeScreen(nav) }
            composable<EditProfileRoute> { EditProfileScreen(nav) }
            composable<MyListingsRoute> { MyListingsScreen(nav) }
            composable<SavedRoute> { SavedScreen(nav) }
            composable<BlockedRoute> { BlockedScreen(nav) }
            composable<ActivityRoute> { ActivityScreen(nav) }
            composable<SafetyRoute> { SafetyScreen(nav) }
            composable<WantedCreateRoute> { WantedCreateScreen(nav) }
            composable<CampusPickerRoute> {
                CampusPickerScreen(
                    switching = true,
                    // Recreate Home so Browse, Wanted and Square load the new campus.
                    onDone = { nav.navigate(HomeRoute) { popUpTo(HomeRoute) { inclusive = true } } },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            banner?.let { b ->
                BannerCard(b) {
                    banner = null
                    if (b.peerUserId != null) nav.navigate(ChatRoute(b.peerUserId)) else nav.navigate(ActivityRoute)
                }
            }
        }
    }
}

@Composable
private fun BannerCard(banner: InAppBanner, onClick: () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(CB.Ink, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(CB.Marigold, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(banner.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            Text(banner.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

@Composable
private fun HomeScaffold(nav: NavHostController, badges: BadgesViewModel) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Browse) }
    val chatUnread by badges.chats.collectAsState()
    val activityUnread by badges.activity.collectAsState()

    Scaffold(
        containerColor = CB.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = CB.Surface, tonalElevation = 0.dp, modifier = Modifier.border(width = 0.5.dp, color = CB.Hairline)) {
                HomeTab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected && t != HomeTab.Sell,
                        onClick = { if (t == HomeTab.Sell) nav.navigate(SellRoute()) else tab = t },
                        icon = {
                            if (t == HomeTab.Sell) {
                                Box(Modifier.size(width = 46.dp, height = 30.dp).background(CB.Marigold, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                                    Icon(t.icon, contentDescription = null, tint = CB.Ink, modifier = Modifier.size(20.dp))
                                }
                            } else if (t == HomeTab.Chats && chatUnread > 0) {
                                BadgedBox(badge = { Badge(containerColor = CB.Stamp, contentColor = Color.White) { Text(if (chatUnread > 99) "99+" else "$chatUnread") } }) {
                                    Icon(t.icon, contentDescription = null)
                                }
                            } else {
                                Icon(t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CB.Ink,
                            selectedTextColor = CB.Ink,
                            indicatorColor = CB.InkWash,
                            unselectedIconColor = CB.InkSoft,
                            unselectedTextColor = CB.InkSoft,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                HomeTab.Browse -> BrowseScreen(nav, activityUnread = activityUnread, bellIcon = Icons.Outlined.NotificationsNone)
                HomeTab.Wanted -> WantedScreen(nav)
                HomeTab.Chats -> ConversationsScreen(nav)
                HomeTab.Square -> SquareScreen(nav)
                HomeTab.Sell -> Unit
            }
        }
    }
}
