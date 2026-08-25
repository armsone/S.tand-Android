package com.armsone.stand.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modifier that highlights a focusable component with high-contrast border and subtle scale
 * when focused by a 5-way TV D-pad or keyboard.
 */
fun Modifier.standFocusable(
    shape: Shape? = null,
    focusedBorderColor: Color? = null,
    focusedBorderWidth: Dp = 2.5.dp,
    scaleOnFocus: Boolean = true,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = focusedBorderColor ?: MaterialTheme.colorScheme.primary
    val effectiveShape = shape ?: androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused && scaleOnFocus) 1.04f else 1.0f,
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
            if (isFocused) {
                Modifier.border(focusedBorderWidth, borderColor, effectiveShape)
            } else {
                Modifier
            },
        )
}
