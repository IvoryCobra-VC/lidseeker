package com.lidseeker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Brand greens (tasteful accent, the "seerr indigo" equivalent) ---
val Green = Color(0xFF22C55E)       // primary accent
val GreenBright = Color(0xFF4ADE80) // highlights / secondary
val GreenDeep = Color(0xFF15803D)   // pressed / secondary container
val GreenContainer = Color(0xFF14532D)
val GreenSoft = Color(0xFF86EFAC)   // text on green container

// --- Seerr-style slate surfaces ---
val Slate900 = Color(0xFF111827)    // app background
val Slate850 = Color(0xFF161F2E)
val Slate800 = Color(0xFF1F2937)    // cards, bars
val Slate700 = Color(0xFF374151)    // dividers, placeholders, chips
val TextHigh = Color(0xFFF9FAFB)
val TextMuted = Color(0xFF9CA3AF)
val Danger = Color(0xFFEF4444)

// Status accents used by chips / pipeline.
val StatusAvailable = Green
val StatusDownloading = Color(0xFFF59E0B) // amber
val StatusImporting = Color(0xFF3B82F6)   // blue
val StatusPending = Color(0xFF6B7280)     // slate-gray

private val LidseekerDark = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF04210F),
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenSoft,
    secondary = GreenBright,
    onSecondary = Color(0xFF04210F),
    secondaryContainer = GreenDeep,
    onSecondaryContainer = GreenSoft,
    tertiary = GreenBright,
    onTertiary = Color(0xFF04210F),
    background = Slate900,
    onBackground = TextHigh,
    surface = Slate900,
    onSurface = TextHigh,
    surfaceVariant = Slate700,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Slate900,
    surfaceContainerLow = Slate850,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate700,
    outlineVariant = Slate800,
    error = Danger,
    onError = Color.White,
)

/** Lidseeker is always dark (Overseerr/Jellyseerr style), with a green accent. */
@Composable
fun LidseekerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LidseekerDark,
        typography = LidseekerType,
        shapes = LidseekerShapes,
        content = content,
    )
}
