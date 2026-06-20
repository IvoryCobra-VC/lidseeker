package com.lidseeker.app.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.DiscoverCategories
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.SearchResult
import com.lidseeker.app.ui.AppCard
import com.lidseeker.app.ui.FullScreenState
import com.lidseeker.app.ui.LoadingState
import com.lidseeker.app.ui.MediaRow
import com.lidseeker.app.ui.PullToRefresh
import com.lidseeker.app.ui.RequestButton
import com.lidseeker.app.ui.repoFactory
import kotlinx.coroutines.launch

class DiscoverViewModel(private val repo: Repository) : ViewModel() {
    var items by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var categories by mutableStateOf(DiscoverCategories())
        private set
    var genre by mutableStateOf<String?>(null)     // active genre filter
        private set
    var decade by mutableStateOf<Int?>(null)       // active decade filter
        private set
    val requestState = mutableStateMapOf<String, String>()

    /** First load — same as a reload with no filters active. */
    fun start() = load()

    /** Toggle a genre chip; keeps any active decade so filters combine. */
    fun toggleGenre(g: String) {
        genre = if (genre == g) null else g
        load()
    }

    /** Toggle a decade chip; keeps any active genre so filters combine. */
    fun toggleDecade(d: Int) {
        decade = if (decade == d) null else d
        load()
    }

    /** "New" chip — clear both filters back to the newest feed. */
    fun clearFilters() {
        if (genre == null && decade == null) return
        genre = null
        decade = null
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                items = repo.discover(genre, decade)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load Discover"
            } finally {
                loading = false
            }
        }
        // Re-narrow the chips to combinations available under the current filters.
        viewModelScope.launch {
            runCatching { repo.discoverCategories(genre, decade) }
                .onSuccess { categories = it }
        }
    }

    /** Awaitable reload of the current feed — used by pull-to-refresh. */
    suspend fun refreshNow() {
        try {
            items = repo.discover(genre, decade)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load Discover"
        }
        runCatching { repo.discoverCategories(genre, decade) }
            .onSuccess { categories = it }
    }

    fun request(r: SearchResult) {
        requestState[r.foreignId] = "loading"
        viewModelScope.launch {
            requestState[r.foreignId] = try {
                val res = repo.request("album", r.foreignId)
                if (res.status == "error") "error" else "done"
            } catch (e: Exception) {
                "error"
            }
        }
    }
}

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
) {
    val vm: DiscoverViewModel = viewModel(factory = repoFactory { DiscoverViewModel(it) })
    LaunchedEffect(Unit) { vm.start() }
    val filtered = vm.genre != null || vm.decade != null

    Column(modifier.fillMaxSize()) {
        CategoryChips(
            categories = vm.categories,
            selectedGenre = vm.genre,
            selectedDecade = vm.decade,
            onNew = vm::clearFilters,
            onToggleGenre = vm::toggleGenre,
            onToggleDecade = vm::toggleDecade,
        )
        when {
            vm.loading -> LoadingState()
            vm.error != null && vm.items.isEmpty() -> FullScreenState(
                icon = Icons.Filled.CloudOff,
                title = "Couldn't load Discover",
                message = vm.error,
                iconTint = MaterialTheme.colorScheme.error,
                actionLabel = "Try again",
                onAction = vm::load,
            )
            vm.items.isEmpty() -> FullScreenState(
                icon = Icons.Filled.Explore,
                title = if (filtered) "Nothing in this category" else "Nothing new yet",
                message = if (filtered)
                    "No unowned albums here right now. Try another genre or decade."
                else
                    "New releases from artists in your library will show up here.",
            )
            else -> PullToRefresh(onRefresh = { vm.refreshNow() }) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.items, key = { it.foreignId }) { a ->
                        AppCard {
                            MediaRow(
                                imageUrl = a.imageUrl,
                                title = a.title,
                                subtitle = listOfNotNull(a.artist, a.year?.toString())
                                    .joinToString(" · "),
                                trailing = {
                                    RequestButton(
                                        inLibrary = a.inLibrary,
                                        requested = a.requested,
                                        state = vm.requestState[a.foreignId],
                                        onClick = { vm.request(a) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal row of browse chips: New, then decades, then top genres. A decade
 * and a genre can be active at once (they AND together); "New" clears both.
 */
@Composable
private fun CategoryChips(
    categories: DiscoverCategories,
    selectedGenre: String?,
    selectedDecade: Int?,
    onNew: () -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleDecade: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "new") {
            FilterChip(
                selected = selectedGenre == null && selectedDecade == null,
                onClick = onNew,
                label = { Text("New") },
            )
        }
        items(categories.decades, key = { "d$it" }) { d ->
            FilterChip(
                selected = selectedDecade == d,
                onClick = { onToggleDecade(d) },
                label = { Text("${d}s") },
            )
        }
        items(categories.genres, key = { "g$it" }) { g ->
            FilterChip(
                selected = selectedGenre == g,
                onClick = { onToggleGenre(g) },
                label = { Text(g) },
            )
        }
    }
}
