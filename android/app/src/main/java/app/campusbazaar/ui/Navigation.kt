package app.campusbazaar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.campusbazaar.AppContainer
import app.campusbazaar.CampusBazaarApp
import kotlinx.serialization.Serializable

/* Type-safe navigation routes. Arguments survive process death through the back stack. */

@Serializable object WelcomeRoute
@Serializable object SignInRoute
@Serializable object SignUpRoute
@Serializable data class CampusPickerRoute(val switching: Boolean = false)
@Serializable object HomeRoute
@Serializable data class ListingRoute(val id: String)
@Serializable data class ChatRoute(val peerId: String, val peerName: String = "", val listingId: String? = null, val openOffer: Boolean = false)
@Serializable data class SellRoute(val editId: String? = null)
@Serializable object CameraRoute
@Serializable data class SubchannelRoute(val channelId: String, val subchannelId: String, val name: String)
@Serializable data class UserRoute(val id: String)
@Serializable object MeRoute
@Serializable object EditProfileRoute
@Serializable object MyListingsRoute
@Serializable object SavedRoute
@Serializable object BlockedRoute
@Serializable object ActivityRoute
@Serializable object SafetyRoute
@Serializable object WantedCreateRoute

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as CampusBazaarApp).container

/** ViewModel factory backed by the manual DI container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}
