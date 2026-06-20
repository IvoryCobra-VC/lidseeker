package com.lidseeker.app.ui.requests

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lidseeker.app.data.Pipeline
import com.lidseeker.app.data.ServiceLink

private val ACCENT = Color(0xFF22C55E)

private fun stageLabel(key: String): String = when (key) {
    "requested" -> "Requested"
    "searching" -> "Searching"
    "downloading" -> "Downloading"
    "importing" -> "Importing"
    "available" -> "Available"
    else -> key.replaceFirstChar { it.uppercase() }
}

/** Expanded pipeline detail shown under a request when its row is tapped. */
@Composable
fun PipelineView(
    pipeline: Pipeline,
    services: List<ServiceLink>,
    forcing: Boolean = false,
    forceMessage: String? = null,
    onForceSearch: (() -> Unit)? = null,
    retrying: Boolean = false,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        StageStepper(pipeline)

        Spacer(Modifier.height(12.dp))

        if (pipeline.detail.isNotBlank()) {
            Text(
                pipeline.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (pipeline.failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Progress bar only while there's meaningful in-flight progress.
        val showBar = pipeline.stage == "downloading" || pipeline.stage == "importing" ||
            (pipeline.percent > 0f && pipeline.percent < 100f)
        if (showBar) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (pipeline.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = ACCENT,
            )
        }

        // Retry — for failed/stuck requests (clears denylist, re-monitors, re-searches).
        if (onRetry != null && (pipeline.failed || pipeline.stuck)) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, enabled = !retrying) {
                if (retrying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (retrying) "Retrying…" else "Retry")
            }
        }

        // Force-search button — kick Soularr now (when not failed/stuck/available).
        if (onForceSearch != null && pipeline.stage != "available" &&
            !pipeline.failed && !pipeline.stuck) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onForceSearch,
                enabled = !forcing,
            ) {
                if (forcing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (forcing) "Starting…" else "Search now")
            }
            forceMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (services.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Open in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.forEach { svc ->
                    AssistChip(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(svc.url))
                                )
                            }
                        },
                        label = { Text(svc.name) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StageStepper(pipeline: Pipeline) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        pipeline.stages.forEachIndexed { index, key ->
            val done = index < pipeline.stageIndex
            val current = index == pipeline.stageIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // left connector
                    Connector(filled = index <= pipeline.stageIndex && index > 0, visible = index > 0)
                    StageDot(done = done, current = current, number = index + 1)
                    Connector(
                        filled = index < pipeline.stageIndex,
                        visible = index < pipeline.stages.lastIndex,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stageLabel(key),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (done || current) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun StageDot(done: Boolean, current: Boolean, number: Int) {
    val color = when {
        done -> ACCENT
        current -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Connector(filled: Boolean, visible: Boolean) {
    Box(
        Modifier
            .weight(1f)
            .height(2.dp)
            .clip(CircleShape)
            .then(
                if (!visible) Modifier
                else Modifier.background(
                    if (filled) ACCENT else MaterialTheme.colorScheme.surfaceVariant
                )
            )
    )
}
