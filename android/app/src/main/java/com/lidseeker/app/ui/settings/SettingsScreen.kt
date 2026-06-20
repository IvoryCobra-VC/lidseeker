package com.lidseeker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lidseeker.app.data.Repository
import com.lidseeker.app.ui.AppCard
import com.lidseeker.app.ui.repoFactory
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    // null = the backend's Soularr adapter is off, so there's no quality control.
    var quality by mutableStateOf<String?>(null)
        private set
    var ntfyUrl by mutableStateOf<String?>(null)
        private set
    var ntfyTopic by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            try {
                val s = repo.getSettings()
                quality = s.quality
                ntfyUrl = s.ntfyUrl
                ntfyTopic = s.ntfyTopic
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    fun selectQuality(q: String) {
        if (q == quality || saving) return
        val prev = quality
        quality = q
        saving = true
        message = null
        viewModelScope.launch {
            message = try {
                repo.setQuality(q).message ?: "Saved"
            } catch (e: Exception) {
                quality = prev
                "Couldn't change quality"
            } finally {
                saving = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = repoFactory { SettingsViewModel(it) })
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (vm.loading) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(inner), Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Quality (only when the backend's Soularr adapter exposes it) ---
            if (vm.quality != null) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Download quality", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "FLAC prefers lossless and falls back to MP3. Applies to new requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("mp3" to "MP3", "flac" to "FLAC").forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = vm.quality == key,
                                onClick = { vm.selectQuality(key) },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                enabled = !vm.saving,
                            ) { Text(label) }
                        }
                    }
                    vm.message?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            }

            // --- Notifications ---
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (vm.ntfyTopic != null) {
                        Text(
                            "Get pinged when an album is ready. Install the ntfy app and " +
                                "subscribe to this topic:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                        Text("Server:  ${vm.ntfyUrl}", style = MaterialTheme.typography.bodyMedium)
                        Text("Topic:   ${vm.ntfyTopic}", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Push notifications aren't configured on the server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
