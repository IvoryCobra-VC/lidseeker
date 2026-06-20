package com.lidseeker.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii from tokens/spacing.css. Drives default Material component shapes
 * (buttons/inputs use `small`, menus/cards use `medium`); list cards and album art
 * set their own radii in Components.kt.
 */
val LidseekerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // radius-sm — tags, small chips
    small = RoundedCornerShape(6.dp),        // radius-md — buttons, inputs
    medium = RoundedCornerShape(12.dp),      // menus, mid surfaces
    large = RoundedCornerShape(16.dp),       // radius-card — list cards
    extraLarge = RoundedCornerShape(20.dp),  // radius-xl — dialogs, sheets
)
