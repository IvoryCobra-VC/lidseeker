package com.lidseeker.app.ui.requests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.MusicRequest
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.ServiceLink
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
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var forcing by mutableStateOf(false)
        private set
    var forceMessage by mutableStateOf<String?>(null)
        private set
    var retryingId by mutableStateOf<Int?>(null)
        private set

    fun retry(id: Int) {
        if (retryingId != null) return
        retryingId = id
        viewModelScope.launch {
            try { repo.retryRequest(id) } catch (_: Exception) {}
            retryingId = null
            refresh()
        }
    }

    fun remove(id: Int) {
        viewModelScope.launch {
            try { repo.deleteRequest(id) } catch (_: Exception) {}
            refresh()
        }
    }

    fun forceSearch() {
        if (forcing) return
        forcing = true
        forceMessage = null
        viewModelScope.launch {
            forceMessage = try {
                repo.forceSoularr().message ?: "Searching now…"
            } catch (e: Exception) {
                "Couldn't start a search right now."
            } finally {
                forcing = false
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

    /** Awaitable fetch — used by pull-to-refresh so the spinner stays until done. */
    suspend fun refreshNow() {
        try {
            items = repo.requests()
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

    // Initial load, then poll so statuses advance pending -> downloading -> available.
    LaunchedEffect(Unit) {
        vm.loadServices()
        vm.refresh(initial = true)
        while (true) {
            delay(8_000)
            vm.refresh()
        }
    }

    when {
        vm.loading -> LoadingState(modifier)
        vm.error != null && vm.items.isEmpty() -> FullScreenState(
            icon = Icons.Filled.CloudOff,
            title = "Couldn't load requests",
            message = vm.error,
            iconTint = MaterialTheme.colorScheme.error,
            actionLabel = "Try again",
            onAction = { vm.refresh(initial = true) },
            modifier = modifier,
        )
        vm.items.isEmpty() -> FullScreenState(
            icon = Icons.Filled.Inbox,
            title = "No requests yet",
            message = "Albums and artists you request will appear here, with live download status.",
            modifier = modifier,
        )
        else -> PullToRefresh(onRefresh = { vm.refreshNow() }, modifier = modifier) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.items, key = { it.id }) { req ->
                    RequestRow(
                        req = req,
                        services = vm.services,
                        forcing = vm.forcing,
                        forceMessage = vm.forceMessage,
                        onForceSearch = vm::forceSearch,
                        retrying = vm.retryingId == req.id,
                        onRetry = { vm.retry(req.id) },
                        onRemove = { vm.remove(req.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    req: MusicRequest,
    services: List<ServiceLink>,
    forcing: Boolean,
    forceMessage: String?,
    onForceSearch: () -> Unit,
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
                            forcing = forcing,
                            forceMessage = forceMessage,
                            onForceSearch = onForceSearch,
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
