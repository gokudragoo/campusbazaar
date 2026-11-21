package app.campusbazaar.data.session

import app.campusbazaar.chat.NexconnManager
import app.campusbazaar.data.api.CampusBazaarApi
import app.campusbazaar.data.api.CampusDto
import app.campusbazaar.data.api.SessionResponse
import app.campusbazaar.data.api.UserDto
import app.campusbazaar.data.api.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class NeedsCampus(val session: Session) : AuthState
    data class Ready(val session: Session) : AuthState
}

/**
 * Single source of truth for "who is signed in". Restores the encrypted session on launch,
 * connects Nexconn once, refreshes the Nexconn token when it is rejected, and signs out on 401.
 */
class SessionManager(
    private val store: SessionStore,
    private val api: () -> CampusBazaarApi,
    private val nexconn: NexconnManager,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val mutex = Mutex()

    @Volatile var current: Session? = null
        private set

    val apiToken: String? get() = current?.apiToken
    val me: UserDto? get() = current?.user
    val campus: CampusDto? get() = current?.campus

    init {
        nexconn.onTokenInvalid = { scope.launch { reconnectWithFreshToken() } }
    }

    suspend fun restore() {
        val session = store.load()
        apply(session)
        if (session != null) {
            startChat(session)
            // Refresh profile/campus in the background; a 401 here signs the user out.
            scope.launch {
                apiCall { api().me() }.onSuccess { me -> update { it.copy(user = me.user, campus = me.campus) } }
            }
        }
    }

    suspend fun onSignedIn(response: SessionResponse) {
        val session = Session(
            apiToken = response.token,
            user = response.user,
            campus = response.campus,
            nexconnAppKey = response.nexconn.appKey,
            nexconnUserId = response.nexconn.userId,
            nexconnToken = response.nexconn.token,
        )
        store.save(session)
        apply(session)
        startChat(session)
    }

    suspend fun update(transform: (Session) -> Session) = mutex.withLock {
        val session = current ?: return@withLock
        val next = transform(session)
        store.save(next)
        apply(next)
    }

    suspend fun setCampus(campus: CampusDto) = update { it.copy(campus = campus, user = it.user.copy(campusId = campus.id)) }
    suspend fun setUser(user: UserDto) = update { it.copy(user = user) }

    suspend fun signOut() {
        nexconn.disconnect()
        store.clear()
        apply(null)
    }

    fun onUnauthorized() {
        if (current != null) scope.launch { signOut() }
    }

    private fun apply(session: Session?) {
        current = session
        _state.value = when {
            session == null -> AuthState.SignedOut
            session.campus == null -> AuthState.NeedsCampus(session)
            else -> AuthState.Ready(session)
        }
    }

    private fun startChat(session: Session) {
        nexconn.ensureInitialized(session.nexconnAppKey)
        scope.launch { nexconn.connect(session.nexconnToken) { refreshNexconnToken() } }
    }

    /** Asks our server for a new Nexconn token (the SDK reported the old one invalid/expired). */
    suspend fun refreshNexconnToken(): String? {
        val fresh = apiCall { api().refreshNexconnToken() }.getOrNull() ?: return null
        update { it.copy(nexconnToken = fresh.token, nexconnUserId = fresh.userId, nexconnAppKey = fresh.appKey) }
        return fresh.token
    }

    private suspend fun reconnectWithFreshToken() {
        val token = refreshNexconnToken() ?: return
        nexconn.disconnect()
        nexconn.connect(token) { null }
    }

    /** Manual retry from the UI (for example after "signed in on another phone"). */
    fun reconnectChat() {
        val session = current ?: return
        scope.launch {
            nexconn.disconnect()
            nexconn.connect(session.nexconnToken) { refreshNexconnToken() }
        }
    }
}
