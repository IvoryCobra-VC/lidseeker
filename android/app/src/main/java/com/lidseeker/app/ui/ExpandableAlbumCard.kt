package com.lidseeker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lidseeker.app.data.Track

/**
 * An album row with an expand chevron that reveals the album's track list.
 * Tracks are fetched lazily the first time the row is expanded (and re-fetched
 * on Retry). The [trailing] slot is the row's existing control (Request button,
 * checkbox, "In library" label, …); the chevron is added after it.
 */
@Composable
fun ExpandableAlbumCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    loadTracks: suspend () -> List<Track>,
    modifier: Modifier = Modifier,
    onRowClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, reloadKey) {
        if (expanded && tracks == null && !loading) {
            loading = true
            failed = false
            try {
                tracks = loadTracks()
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }

    AppCard(modifier) {
        Column {
            MediaRow(
                imageUrl = imageUrl,
                title = title,
                subtitle = subtitle,
                onClick = onRowClick,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        trailing()
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                if (expanded) Icons.Filled.KeyboardArrowUp
                                else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (expanded) "Hide songs" else "Show songs",
                            )
                        }
                    }
                },
            )
            if (expanded) {
                TrackListSection(
                    loading = loading,
                    failed = failed,
                    tracks = tracks,
                    onRetry = { tracks = null; reloadKey++ },
                )
            }
        }
    }
}

@Composable
private fun TrackListSection(
    loading: Boolean,
    failed: Boolean,
    tracks: List<Track>?,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, bottom = 10.dp),
    ) {
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            failed -> Hint("Couldn't load songs.", onRetry)
            tracks.isNullOrEmpty() -> Hint("No song list available.", null)
            else -> {
                val multiDisc = tracks.map { it.mediumNumber }.distinct().size > 1
                tracks.forEachIndexed { i, t ->
                    if (multiDisc && (i == 0 || tracks[i - 1].mediumNumber != t.mediumNumber)) {
                        Text(
                            "Disc ${t.mediumNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    TrackRow(t)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${t.position}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        Text(
            t.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        t.durationMs?.let { ms ->
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(ms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hint(text: String, onRetry: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
