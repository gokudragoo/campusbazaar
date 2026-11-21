package app.campusbazaar.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.campusbazaar.data.api.ListingDto
import app.campusbazaar.data.repo.MarketRepository
import app.campusbazaar.data.session.SessionManager
import app.campusbazaar.domain.Category
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.SortOrder
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.ActivityRoute
import app.campusbazaar.ui.CampusPickerRoute
import app.campusbazaar.ui.ListingRoute
import app.campusbazaar.ui.MeRoute
import app.campusbazaar.ui.SellRoute
import app.campusbazaar.ui.appContainer
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Avatar
import app.campusbazaar.ui.components.EmptyState
import app.campusbazaar.ui.components.ErrorState
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.ListingTile
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.OfflineBanner
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedFilters(
    val query: String = "",
    val category: String? = null,
    val conditions: Set<String> = emptySet(),
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val sort: SortOrder = SortOrder.Newest,
) {
    val activeCount: Int get() = conditions.size + (if (minPrice != null || maxPrice != null) 1 else 0) + (if (sort != SortOrder.Newest) 1 else 0)
}

class BrowseViewModel(private val market: MarketRepository, private val session: SessionManager, private val saved: SavedStateHandle) : ViewModel() {
    data class State(
        val filters: FeedFilters = FeedFilters(),
        val items: List<ListingDto> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val nextCursor: String? = null,
    )

    private val _state = MutableStateFlow(
        State(filters = FeedFilters(query = saved["q"] ?: "", category = saved["category"])),
    )
    val state = _state.asStateFlow()
    private var job: Job? = null
    val campusName: String get() = session.campus?.name ?: "Your campus"
    val me get() = session.me

    init { load() }

    fun setQuery(q: String) {
        saved["q"] = q
        _state.update { it.copy(filters = it.filters.copy(query = q)) }
        load(debounce = true)
    }

    fun setCategory(id: String?) {
        saved["category"] = id
        _state.update { it.copy(filters = it.filters.copy(category = id)) }
        load()
    }

    fun applyFilters(f: FeedFilters) {
        _state.update { it.copy(filters = f) }
        load()
    }

    fun refresh() = load(refreshing = true)

    private fun load(debounce: Boolean = false, refreshing: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            _state.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null) }
            val f = _state.value.filters
            market.feed(f.query, f.category, f.conditions, f.minPrice, f.maxPrice, f.sort.id, null)
                .onSuccess { page -> _state.update { it.copy(items = page.listings, nextCursor = page.nextCursor, loading = false, refreshing = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val f = s.filters
            market.feed(f.query, f.category, f.conditions, f.minPrice, f.maxPrice, f.sort.id, cursor)
                .onSuccess { page ->
                    _state.update { st -> st.copy(items = (st.items + page.listings).distinctBy { it.id }, nextCursor = page.nextCursor, loadingMore = false) }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}


@Composable
fun BrowseScreen(nav: NavHostController, activityUnread: Int, bellIcon: ImageVector) {
    val vm = appViewModel { c, h -> BrowseViewModel(c.market, c.session, h) }
    val state by vm.state.collectAsState()
    val online by appContainer().network.isOnline.collectAsState(initial = true)
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= grid.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

    Column(Modifier.fillMaxSize().background(CB.Paper)) {
        Column(Modifier.background(CB.Paper).statusBarsPadding()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { nav.navigate(CampusPickerRoute(switching = true)) }) {
                    Text("Browsing", style = MaterialTheme.typography.labelSmall, color = CB.InkSoft)
                    Text(vm.campusName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { nav.navigate(ActivityRoute) }) {
                    if (activityUnread > 0) {
                        BadgedBox(badge = { Badge(containerColor = CB.Stamp) { Text("$activityUnread") } }) { Icon(bellIcon, "Activity") }
                    } else Icon(bellIcon, "Activity")
                }
                IconButton(onClick = { nav.navigate(MeRoute) }) {
                    Avatar(vm.me?.name ?: "", vm.me?.avatarUrl, size = 32.dp)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchBox(state.filters.query, vm::setQuery, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { showFilters = true }, modifier = Modifier.size(48.dp).background(CB.Surface, RoundedCornerShape(8.dp))) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Filters")
                    }
                    if (state.filters.activeCount > 0) {
                        Badge(containerColor = CB.Ink, modifier = Modifier.align(Alignment.TopEnd)) { Text("${state.filters.activeCount}") }
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SelectChip("All", state.filters.category == null, { vm.setCategory(null) }) }
                items(Category.entries) { c -> SelectChip(c.label, state.filters.category == c.id, { vm.setCategory(if (state.filters.category == c.id) null else c.id) }) }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (!online) OfflineBanner()

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> LoadingBlock()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, vm::refresh)
                state.items.isEmpty() -> FeedEmpty(state.filters, onSell = { nav.navigate(SellRoute()) }, onClear = { vm.applyFilters(FeedFilters()) })
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    state = grid,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { l ->
                        ListingTile(
                            title = l.title,
                            price = l.price,
                            photo = l.photos.firstOrNull(),
                            status = l.status,
                            meta = "${Condition.of(l.condition).label}, ${TimeText.relative(TimeText.parseIso(l.createdAt))}",
                            onClick = { nav.navigate(ListingRoute(l.id)) },
                        )
                    }
                    if (state.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LoadingBlock() }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(state.filters, onDismiss = { showFilters = false }, onApply = { vm.applyFilters(it); showFilters = false })
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(48.dp).background(CB.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = CB.InkSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search books, calculators, cycles…", style = MaterialTheme.typography.bodyMedium, color = CB.InkFaint)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 80) onChange(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = CB.Ink),
                cursorBrush = SolidColor(CB.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Clear search", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun FeedEmpty(filters: FeedFilters, onSell: () -> Unit, onClear: () -> Unit) {
    val filtered = filters.query.isNotBlank() || filters.category != null || filters.activeCount > 0
    if (filtered) {
        EmptyState(
            Icons.Outlined.Search,
            "Nothing matches yet",
            "Try a different word or fewer filters. You can also post a Wanted request so sellers find you.",
            action = { SecondaryButton("Clear filters", onClear) },
        )
    } else {
        EmptyState(
            Icons.Outlined.Inventory2,
            "Be the first seller on your campus",
            "No one has listed anything here yet. Snap a photo of an old book or calculator and it's up in a minute.",
            action = { PrimaryButton("Sell something", onSell, accent = true) },
        )
    }
}

@Composable
private fun FilterSheet(initial: FeedFilters, onDismiss: () -> Unit, onApply: (FeedFilters) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var conditions by remember { mutableStateOf(initial.conditions) }
    var min by remember { mutableStateOf(initial.minPrice?.toString() ?: "") }
    var max by remember { mutableStateOf(initial.maxPrice?.toString() ?: "") }
    var sort by remember { mutableStateOf(initial.sort) }
    val minV = Money.parse(min)
    val maxV = Money.parse(max)
    val rangeError = if (minV != null && maxV != null && minV > maxV) "Minimum is higher than maximum." else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = CB.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            Text("Sort", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { s -> SelectChip(s.label, sort == s, { sort = s }) }
            }
            Text("Price (₹)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(min, { min = it.filter(Char::isDigit) }, "Minimum", Modifier.weight(1f), keyboardType = KeyboardType.Number, maxLength = 6)
                Field(max, { max = it.filter(Char::isDigit) }, "Maximum", Modifier.weight(1f), keyboardType = KeyboardType.Number, maxLength = 6, error = rangeError)
            }
            Text("Condition", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Condition.entries.forEach { c ->
                    SelectChip(c.label, c.id in conditions, { conditions = if (c.id in conditions) conditions - c.id else conditions + c.id })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                SecondaryButton("Reset", { onApply(initial.copy(conditions = emptySet(), minPrice = null, maxPrice = null, sort = SortOrder.Newest)) }, Modifier.weight(1f))
                PrimaryButton("Show results", {
                    if (rangeError == null) onApply(initial.copy(conditions = conditions, minPrice = minV, maxPrice = maxV, sort = sort))
                }, Modifier.weight(1f))
            }
        }
    }
}

