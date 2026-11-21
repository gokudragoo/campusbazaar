package app.campusbazaar.ui.campus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.campusbazaar.data.api.CampusDto
import app.campusbazaar.data.repo.AuthRepository
import app.campusbazaar.data.session.SessionManager
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.Hairline
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.Panel
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CampusPickerViewModel(private val repo: AuthRepository, private val session: SessionManager) : ViewModel() {
    data class State(
        val query: String = "",
        val results: List<CampusDto> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val joiningId: String? = null,
        val creating: Boolean = false,
        val createError: String? = null,
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    val currentCampusId: String? get() = session.campus?.id

    init { search("") }

    fun search(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(if (q.isBlank()) 0 else 300)
            _state.update { it.copy(loading = true, error = null) }
            repo.searchCampuses(q).onSuccess { list -> _state.update { it.copy(results = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun join(campus: CampusDto) {
        if (_state.value.joiningId != null) return
        _state.update { it.copy(joiningId = campus.id, error = null) }
        viewModelScope.launch {
            repo.joinCampus(campus.id).onSuccess { _state.update { it.copy(joiningId = null, done = true) } }
                .onFailure { e -> _state.update { it.copy(joiningId = null, error = e.message) } }
        }
    }

    fun create(name: String, city: String) {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            repo.createCampus(name, city).onSuccess { _state.update { it.copy(creating = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(creating = false, createError = e.message) } }
        }
    }

    fun signOut() = viewModelScope.launch { session.signOut() }
}

@Composable
fun CampusPickerScreen(switching: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val vm = appViewModel { c, _ -> CampusPickerViewModel(c.auth, c.session) }
    val state by vm.state.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newCity by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(Modifier.fillMaxSize().background(CB.Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            else Spacer(Modifier.width(16.dp))
            Spacer(Modifier.weight(1f))
            if (!switching) QuietButton("Sign out", { vm.signOut() })
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (switching) "Switch campus" else "Which campus are you on?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Everything you see, from listings to Campus Square, is from students on the same campus.",
                style = MaterialTheme.typography.bodyMedium,
                color = CB.InkSoft,
            )
            Spacer(Modifier.height(18.dp))
        }

        if (adding) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add your campus", style = MaterialTheme.typography.titleLarge)
                Field(newName, { newName = it }, "Campus name", placeholder = "e.g. NIT Tiruchirappalli",
                    error = if (submitted && newName.trim().length < 3) "Enter the full campus name." else null, maxLength = 100)
                Field(newCity, { newCity = it }, "City", placeholder = "e.g. Tiruchirappalli",
                    error = if (submitted && newCity.trim().length < 2) "Enter the city." else null, maxLength = 60)
                state.createError?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "If the campus already exists we'll add you to it instead of creating a copy.",
                    style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
                )
                PrimaryButton("Add and join", {
                    submitted = true
                    if (newName.trim().length >= 3 && newCity.trim().length >= 2) vm.create(newName, newCity)
                }, Modifier.fillMaxWidth(), loading = state.creating)
                SecondaryButton("Back to search", { adding = false }, Modifier.fillMaxWidth())
            }
            return@Column
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Field(
                state.query, vm::search, "Search campuses", placeholder = "Name or city",
                leading = { Icon(Icons.Outlined.Search, null, tint = CB.InkSoft) }, maxLength = 80,
            )
            state.error?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(14.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            if (state.loading && state.results.isEmpty()) item { LoadingBlock() }
            if (state.results.isNotEmpty()) {
                item {
                    Panel {
                        state.results.forEachIndexed { i, campus ->
                            if (i > 0) Hairline()
                            CampusRow(campus, current = campus.id == vm.currentCampusId, joining = state.joiningId == campus.id) { vm.join(campus) }
                        }
                    }
                }
            }
            if (!state.loading && state.results.isEmpty()) {
                item {
                    Text(
                        if (state.query.isBlank()) "No campuses yet. Be the first to add yours." else "No campus matches \"${state.query}\".",
                        style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Add my campus", {
                    newName = state.query
                    adding = true
                }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CampusRow(campus: CampusDto, current: Boolean, joining: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !current && !joining, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AccountBalance, null, tint = CB.Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(campus.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${campus.city}, ${campus.memberCount} ${if (campus.memberCount == 1) "student" else "students"}",
                style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
            )
        }
        when {
            current -> Text("Your campus", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
            joining -> Text("Joining…", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
            else -> Icon(Icons.Outlined.ChevronRight, null, tint = CB.InkSoft)
        }
    }
}
