package com.lidseeker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lidseeker.app.R

/**
 * Space Grotesk — the design system's primary typeface (tokens/typography.css).
 * Loaded from the single variable font; each weight asks the font for that axis value.
 */
@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: FontWeight) = Font(
    R.font.space_grotesk_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val SpaceGrotesk = FontFamily(
    spaceGrotesk(FontWeight.Normal),    // 400
    spaceGrotesk(FontWeight.Medium),    // 500
    spaceGrotesk(FontWeight.SemiBold),  // 600
    spaceGrotesk(FontWeight.Bold),      // 700
)

/**
 * Type scale from tokens/typography.css mapped onto Material 3's roles.
 * Sizes in sp; line-heights follow the token leading ratios (tight 1.15 / snug 1.3 /
 * normal 1.5); the uppercase section labels carry the tracking-wide/wider spacing.
 */
val LidseekerType = Typography(
    // Display — big numerals / hero text (text-4xl / 3xl, bold, tight)
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 41.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 35.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 28.sp,
    ),
    // Headline (2xl / xl, semibold, snug)
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 31.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 23.sp,
    ),
    // Titles (screen/section headers, lg / base, medium-semibold)
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Body (base / sm / xs, normal, leading-normal 1.5)
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    // Labels — buttons + the uppercase section labels (tracking-wide/wider)
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.1.em,
    ),
)
