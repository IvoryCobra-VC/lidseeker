package com.lidseeker.app.ui.requests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.MusicRequest
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.ServiceLink
import com.lidseeker.app.data.StatsOut
import com.lidseeker.app.ui.AppCard
import com.lidseeker.app.ui.FullScreenState
import com.lidseeker.app.ui.LoadingState
import com.lidseeker.app.ui.MediaRow
import com.lidseeker.app.ui.PullToRefresh
import com.lidseeker.app.ui.StatusChip
import com.lidseeker.app.ui.repoFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RequestsViewModel(private val repo: Repository) : ViewModel() {
    var items by mutableStateOf<List<MusicRequest>>(emptyList())
        private set
    var services by mutableStateOf<List<ServiceLink>>(emptyList())
        private set
    var stats by mutableStateOf<StatsOut?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    // Per-request search: tracks which request id is currently being searched.
    var searchingId by mutableStateOf<Int?>(null)
        private set
    var retryingId by mutableStateOf<Int?>(null)
        private set
    var actionMessage by mutableStateOf<String?>(null)
        private set

    fun consumeActionMessage() { actionMessage = null }

    fun retry(id: Int) {
        if (retryingId != null) return
        retryingId = id
        viewModelScope.launch {
            actionMessage = try {
                repo.retryRequest(id).message ?: "Retrying…"
            } catch (e: Exception) {
                "Couldn't retry: ${e.message ?: "try again"}"
            }
            retryingId = null
            refresh()
        }
    }

    fun remove(id: Int) {
        viewModelScope.launch {
            actionMessage = try {
                repo.deleteRequest(id).message ?: "Removed."
            } catch (e: Exception) {
                "Couldn't remove: ${e.message ?: "try again"}"
            }
            refresh()
        }
    }

    fun searchNow(id: Int) {
        if (searchingId != null) return
        searchingId = id
        viewModelScope.launch {
            actionMessage = try {
                repo.searchRequestNow(id).message ?: "Searching now…"
            } catch (e: Exception) {
                "Couldn't start search: ${e.message ?: "try again"}"
            } finally {
                searchingId = null
            }
            refresh()
        }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            if (initial) loading = true
            refreshNow()
            loading = false
        }
    }

    /** Awaitable fetch used by pull-to-refresh so the spinner stays until done. */
    suspend fun refreshNow() {
        try {
            items = repo.requests()
            stats = runCatching { repo.stats() }.getOrNull()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Failed to load requests"
        }
    }

    fun loadServices() {
        viewModelScope.launch {
            runCatching { repo.services() }.onSuccess { services = it }
        }
    }
}

@Composable
fun RequestsScreen(modifier: Modifier = Modifier) {
    val vm: RequestsViewModel = viewModel(factory = repoFactory { RequestsViewModel(it) })
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.loadServices()
        vm.refresh(initial = true)
    }
    // Poll only while the screen is visible; 15 s is enough given the SSE push on web.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                vm.refresh()
            }
        }
    }
    LaunchedEffect(vm.actionMessage) {
        vm.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeActionMessage()
        }
    }

    Box(modifier) {
        when {
            vm.loading -> LoadingState(Modifier.fillMaxSize())
            vm.error != null && vm.items.isEmpty() -> FullScreenState(
                icon = Icons.Filled.CloudOff,
                title = "Couldn't load requests",
                message = vm.error,
                iconTint = MaterialTheme.colorScheme.error,
                actionLabel = "Try again",
                onAction = { vm.refresh(initial = true) },
                modifier = Modifier.fillMaxSize(),
            )
            vm.items.isEmpty() -> FullScreenState(
                icon = Icons.Filled.Inbox,
                title = "No requests yet",
                message = "Albums and artists you request will appear here, with live download status.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(Modifier.fillMaxSize()) {
                vm.stats?.let { StatsBar(it) }
                PullToRefresh(
                    onRefresh = { vm.refreshNow() },
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.items, key = { it.id }) { req ->
                            RequestRow(
                                req = req,
                                services = vm.services,
                                searchingId = vm.searchingId,
                                onSearchNow = { vm.searchNow(req.id) },
                                retrying = vm.retryingId == req.id,
                                onRetry = { vm.retry(req.id) },
                                onRemove = { vm.remove(req.id) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatsBar(stats: StatsOut) {
    if (stats.total == 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (stats.available > 0) StatPill("${stats.available} ready", MaterialTheme.colorScheme.primary)
        if (stats.downloading > 0) StatPill("${stats.downloading} downloading", MaterialTheme.colorScheme.tertiary)
        if (stats.pending > 0) StatPill("${stats.pending} pending", MaterialTheme.colorScheme.onSurfaceVariant)
        if (stats.failed > 0) StatPill("${stats.failed} failed", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun RequestRow(
    req: MusicRequest,
    services: List<ServiceLink>,
    searchingId: Int?,
    onSearchNow: () -> Unit,
    retrying: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove request?") },
            text = {
                Text(
                    "“${req.title.ifBlank { req.foreignId }}” will be removed from your requests" +
                        " and unmonitored in Lidarr. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            MediaRow(
                imageUrl = req.imageUrl,
                title = req.title.ifBlank { req.foreignId },
                subtitle = listOfNotNull(
                    req.artist,
                    if (req.type == "artist") "Full discography" else null,
                ).joinToString(" · "),
                onClick = { expanded = !expanded },
                trailing = { StatusChip(req.status) },
            )
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    req.pipeline?.let {
                        PipelineView(
                            pipeline = it,
                            services = services,
                            forcing = searchingId == req.id,
                            onForceSearch = onSearchNow,
                            retrying = retrying,
                            onRetry = onRetry,
                        )
                    }
                    TextButton(
                        onClick = { confirmRemove = true },
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Remove request", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
