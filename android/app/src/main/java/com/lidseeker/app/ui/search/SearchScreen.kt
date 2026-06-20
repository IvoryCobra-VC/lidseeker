package com.lidseeker.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.MusicRequest
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.SearchResult
import com.lidseeker.app.data.Track
import com.lidseeker.app.ui.AppCard
import com.lidseeker.app.ui.ExpandableAlbumCard
import com.lidseeker.app.ui.FullScreenState
import com.lidseeker.app.ui.LoadingState
import com.lidseeker.app.ui.MediaRow
import com.lidseeker.app.ui.RequestButton
import com.lidseeker.app.ui.repoFactory
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: Repository) : ViewModel() {
    var query by mutableStateOf("")
    var type by mutableStateOf("album")    // "album" | "artist" | "track"
    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searched by mutableStateOf(false)   // a search has run at least once for the current term
        private set

    // foreignId -> "loading" | "done" | "error"
    val requestState = mutableStateMapOf<String, String>()

    fun selectType(t: String) {
        if (t == type) return
        type = t
        if (query.isNotBlank()) search()
    }

    fun search() {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                results = repo.search(query.trim(), type)
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
                results = emptyList()
            } finally {
                loading = false
                searched = true
            }
        }
    }

    suspend fun albumTracks(foreignId: String): List<Track> = repo.albumTracks(foreignId)

    fun request(r: SearchResult) {
        requestState[r.foreignId] = "loading"
        viewModelScope.launch {
            try {
                // A song request adds its parent album (the proven pipeline).
                val res: MusicRequest = repo.request(
                    r.type, r.foreignId, albumForeignId = r.albumForeignId,
                )
                requestState[r.foreignId] = if (res.status == "error") "error" else "done"
            } catch (e: Exception) {
                requestState[r.foreignId] = "error"
            }
        }
    }
}

/** Plural noun for the active search type, used in labels and empty states. */
private fun typeNoun(type: String): String = when (type) {
    "artist" -> "artists"
    "track" -> "songs"
    else -> "albums"
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onArtistClick: (SearchResult) -> Unit,
) {
    val vm: SearchViewModel = viewModel(factory = repoFactory { SearchViewModel(it) })

    Column(modifier.fillMaxSize()) {
        val tabIndex = when (vm.type) { "album" -> 0; "artist" -> 1; else -> 2 }
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = vm.type == "album", onClick = { vm.selectType("album") },
                text = { Text("Albums") })
            Tab(selected = vm.type == "artist", onClick = { vm.selectType("artist") },
                text = { Text("Artists") })
            Tab(selected = vm.type == "track", onClick = { vm.selectType("track") },
                text = { Text("Songs") })
        }

        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            label = { Text("Search ${typeNoun(vm.type)}") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        when {
            vm.loading -> LoadingState()
            vm.error != null -> FullScreenState(
                icon = Icons.Filled.CloudOff,
                title = "Search failed",
                message = vm.error,
                iconTint = MaterialTheme.colorScheme.error,
                actionLabel = "Try again",
                onAction = vm::search,
            )
            vm.results.isEmpty() && vm.searched -> FullScreenState(
                icon = Icons.Filled.SearchOff,
                title = "No results",
                message = "Nothing matched “${vm.query.trim()}”. Try a different spelling or term.",
            )
            vm.results.isEmpty() -> FullScreenState(
                icon = Icons.Filled.Search,
                title = "Search for music",
                message = "Find ${typeNoun(vm.type)} to add to your library.",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.results, key = { it.type + it.foreignId }) { r ->
                    val requestControl: @Composable () -> Unit = {
                        RequestButton(
                            inLibrary = r.inLibrary,
                            requested = r.requested,
                            state = vm.requestState[r.foreignId],
                            onClick = { vm.request(r) },
                        )
                    }
                    when (r.type) {
                        "artist" -> AppCard {
                            MediaRow(
                                imageUrl = r.imageUrl,
                                title = r.title,
                                subtitle = "Artist",
                                onClick = { onArtistClick(r) },
                            )
                        }
                        "track" -> AppCard {
                            // Songs show artist · album, since requesting one adds
                            // the album it's on.
                            MediaRow(
                                imageUrl = r.imageUrl,
                                title = r.title,
                                subtitle = listOfNotNull(
                                    r.artist,
                                    r.albumTitle?.let { "from $it" },
                                ).joinToString(" · "),
                                trailing = requestControl,
                            )
                        }
                        else -> ExpandableAlbumCard(
                            imageUrl = r.imageUrl,
                            title = r.title,
                            subtitle = listOfNotNull(r.artist, r.year?.toString())
                                .joinToString(" · "),
                            loadTracks = { vm.albumTracks(r.foreignId) },
                            trailing = requestControl,
                        )
                    }
                }
            }
        }
    }
}
