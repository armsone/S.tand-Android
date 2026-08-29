package com.armsone.stand.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalStandFocusIndicatorEnabled = staticCompositionLocalOf { true }

/**
 * Modifier that highlights a focusable component with high-contrast border, optional background
 * color tint, and subtle scale when focused by a 5-way TV D-pad or keyboard.
 */
fun Modifier.standFocusable(
    shape: Shape? = null,
    focusedBorderColor: Color? = null,
    focusedBorderWidth: Dp = 2.5.dp,
    focusedContainerColor: Color? = null,
    scaleOnFocus: Boolean = true,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val focusIndicatorEnabled = LocalStandFocusIndicatorEnabled.current
    val borderColor = focusedBorderColor ?: MaterialTheme.colorScheme.primary
    val effectiveShape = shape ?: RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused && focusIndicatorEnabled && scaleOnFocus) 1.04f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv-focus-scale",
    )

    this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isFocused && focusIndicatorEnabled) {
                val containerModifier = if (focusedContainerColor != null && focusedContainerColor != Color.Unspecified) {
                    Modifier.background(focusedContainerColor, effectiveShape)
                } else {
                    Modifier
                }
                containerModifier.border(focusedBorderWidth, borderColor, effectiveShape)
            } else {
                Modifier
            },
        )
}

/**
 * Focus highlighting modifier specifically for Google TV Settings interactive controls/buttons/cards.
 *
 * When [isTelevision] is true, highlights the focused interactive element with a restrained
 * primary-color background tint, high-contrast primary border, and subtle scale on TV remote / D-pad focus.
 * When [isTelevision] is false, leaves the element unmodified to preserve phone/tablet visuals.
 */
fun Modifier.settingsFocusable(
    isTelevision: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedBorderColor: Color? = null,
    focusedBorderWidth: Dp = 2.5.dp,
    focusedContainerColor: Color? = null,
    scaleOnFocus: Boolean = true,
): Modifier = composed {
    if (!isTelevision) {
        this
    } else {
        val containerColor = focusedContainerColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        val borderColor = focusedBorderColor ?: MaterialTheme.colorScheme.primary
        standFocusable(
            shape = shape,
            focusedBorderColor = borderColor,
            focusedBorderWidth = focusedBorderWidth,
            focusedContainerColor = containerColor,
            scaleOnFocus = scaleOnFocus,
        )
    }
}
