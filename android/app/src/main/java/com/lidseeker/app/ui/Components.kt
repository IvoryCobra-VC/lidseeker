package com.lidseeker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.lidseeker.app.ui.theme.StatusAvailable
import com.lidseeker.app.ui.theme.StatusDownloading
import com.lidseeker.app.ui.theme.StatusPending
import com.lidseeker.app.ui.theme.TextHigh

/** A rounded, slightly-elevated container — the building block of every list. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        content = content,
    )
}

/** Square cover art with a music-note placeholder when no image is available. */
@Composable
fun CoverArt(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription?.let { "Cover art for $it" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Placeholder() },
                error = { Placeholder() },
            )
        }
    }
}

/** Centered spinner for a screen that's loading its first batch of content. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Full-screen placeholder for empty/error states: an illustrative icon, a title, an
 * optional supporting line, and an optional recovery action. (Material empty-state +
 * "help users recover from errors" guidance.)
 */
@Composable
fun FullScreenState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onAction) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp),
    )
}

private val OnBright = Color(0xFF0B1220)  // dark text for bright pills

@Composable
fun StatusChip(status: String) {
    val (label, bg, fg) = when (status) {
        "available" -> Triple("Available", StatusAvailable, OnBright)
        "downloading" -> Triple("Downloading", StatusDownloading, OnBright)
        "failed" -> Triple("Failed", MaterialTheme.colorScheme.error, TextHigh)
        "error" -> Triple("Error", MaterialTheme.colorScheme.error, TextHigh)
        else -> Triple("Pending", StatusPending, TextHigh)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** Trailing request control for search/discover album rows. */
@Composable
fun RequestButton(
    inLibrary: Boolean,
    requested: Boolean,
    state: String?,                 // null | "loading" | "done" | "error"
    onClick: () -> Unit,
) {
    when {
        inLibrary -> InlineLabel("In library")
        requested || state == "done" -> InlineLabel("Requested")
        state == "loading" -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
        )
        else -> FilledTonalButton(onClick = onClick) {
            Text(if (state == "error") "Retry" else "Request")
        }
    }
}

@Composable
private fun InlineLabel(text: String) {
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
