package com.lidseeker.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.SearchResult
import com.lidseeker.app.data.Track
import com.lidseeker.app.ui.ExpandableAlbumCard
import com.lidseeker.app.ui.repoFactory
import kotlinx.coroutines.launch

class ArtistDetailViewModel(private val repo: Repository) : ViewModel() {
    private var foreignId: String = ""
    var albums by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var requesting by mutableStateOf(false)
        private set

    /** Per-album request outcome: "loading" | "done" | "error". */
    val requestState = mutableStateMapOf<String, String>()

    /** Which albums are ticked for requesting (default: everything not already in library). */
    val selected = mutableStateMapOf<String, Boolean>()

    fun load(foreignId: String) {
        this.foreignId = foreignId
        viewModelScope.launch {
            loading = true
            error = null
            try {
                albums = repo.artistAlbums(foreignId)
                // Start with everything DESELECTED — the user ticks what they want.
                selected.clear()
            } catch (e: Exception) {
                error = e.message ?: "Failed to load albums"
            } finally {
                loading = false
            }
        }
    }

    suspend fun albumTracks(foreignId: String): List<Track> = repo.albumTracks(foreignId)

    private fun isSingleOrEp(a: SearchResult): Boolean =
        a.albumType?.equals("single", ignoreCase = true) == true ||
            a.albumType?.equals("ep", ignoreCase = true) == true

    /** Full-length releases (everything that isn't a single or EP). */
    fun albumsTab(): List<SearchResult> = albums.filter { !isSingleOrEp(it) }

    /** Singles and EPs. */
    fun singlesEpsTab(): List<SearchResult> = albums.filter { isSingleOrEp(it) }

    private fun selectable(a: SearchResult): Boolean =
        !a.inLibrary && !a.requested && requestState[a.foreignId] != "done"

    fun toggle(a: SearchResult) {
        if (!selectable(a)) return
        selected[a.foreignId] = !(selected[a.foreignId] ?: false)
    }

    /** Number of albums currently ticked and not yet requested/in-library. */
    fun selectedCount(): Int =
        albums.count { selectable(it) && selected[it.foreignId] == true }

    fun setAll(value: Boolean) {
        albums.forEach { if (selectable(it)) selected[it.foreignId] = value }
    }

    /** Request every ticked album, sequentially (so the artist is only created once). */
    fun requestSelected() {
        if (requesting) return
        val toRequest = albums.filter { selectable(it) && selected[it.foreignId] == true }
        if (toRequest.isEmpty()) return
        requesting = true
        viewModelScope.launch {
            try {
                for (a in toRequest) {
                    requestState[a.foreignId] = "loading"
                    try {
                        val res = repo.request("album", a.foreignId)
                        requestState[a.foreignId] = if (res.status == "error") "error" else "done"
                        if (requestState[a.foreignId] == "done") selected.remove(a.foreignId)
                    } catch (e: Exception) {
                        requestState[a.foreignId] = "error"
                    }
                }
            } finally {
                requesting = false
            }
        }
    }
}

@Composable
private fun LabelChip(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumCard(album: SearchResult, vm: ArtistDetailViewModel) {
    val st = vm.requestState[album.foreignId]
    ExpandableAlbumCard(
        imageUrl = album.imageUrl,
        title = album.title,
        subtitle = listOfNotNull(album.albumType, album.year?.toString())
            .joinToString(" · "),
        loadTracks = { vm.albumTracks(album.foreignId) },
        onRowClick = { vm.toggle(album) },
        trailing = {
            when {
                album.inLibrary -> LabelChip("In library")
                album.requested || st == "done" -> LabelChip("Requested")
                st == "loading" -> CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp,
                )
                else -> Checkbox(
                    checked = vm.selected[album.foreignId] == true,
                    onCheckedChange = { vm.toggle(album) },
                )
            }
        },
    )
}

@Composable
private fun AlbumList(items: List<SearchResult>, vm: ArtistDetailViewModel) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Nothing here", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.foreignId }) { album -> AlbumCard(album, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    foreignId: String,
    artistName: String,
    onBack: () -> Unit,
) {
    val vm: ArtistDetailViewModel = viewModel(factory = repoFactory { ArtistDetailViewModel(it) })
    LaunchedEffect(foreignId) { vm.load(foreignId) }

    val count = vm.selectedCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    when {
                        vm.requesting -> CircularProgressIndicator(
                            Modifier.size(20.dp).padding(end = 12.dp), strokeWidth = 2.dp,
                        )
                        else -> {
                            // All / None toggle.
                            TextButton(
                                onClick = { vm.setAll(count == 0) },
                                enabled = vm.albums.isNotEmpty(),
                            ) { Text(if (count == 0) "All" else "None") }
                            TextButton(onClick = { vm.requestSelected() }, enabled = count > 0) {
                                Text(if (count > 0) "Request ($count)" else "Request")
                            }
                        }
                    }
                },
            )
        },
    ) { inner ->
        when {
            vm.loading -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                CircularProgressIndicator()
            }
            vm.error != null -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text(vm.error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Albums", "Singles & EPs")
                Column(Modifier.fillMaxSize().padding(inner)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                text = { Text(title) },
                            )
                        }
                    }
                    val items = if (selectedTab == 0) vm.albumsTab() else vm.singlesEpsTab()
                    AlbumList(items, vm)
                }
            }
        }
    }
}
