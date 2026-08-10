package com.armsone.stand.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Draws the shared S.tand panel treatment behind this modifier's content.
 *
 * [splitGap] removes only the surface fill at the panel's vertical midpoint. The
 * content is intentionally left untouched so weather icons and labels remain
 * readable. Apply a separate content mask when a flip-clock glyph should share
 * the same transparent gap.
 */
fun Modifier.standPanelSurface(
    isDimmed: Boolean,
    cornerRadius: Dp,
    splitGap: Dp = 0.dp,
): Modifier = drawWithCache {
    val panelWidth = size.width
    val panelHeight = size.height
    val hasValidSize = panelWidth.isFinite() && panelHeight.isFinite() &&
        panelWidth > 0f && panelHeight > 0f

    if (!hasValidSize) {
        onDrawWithContent { drawContent() }
    } else {
        val rawCornerRadius = cornerRadius.toPx()
        val maximumCornerRadius = min(panelWidth, panelHeight) / 2f
        val cornerRadiusPx = if (rawCornerRadius.isFinite()) {
            rawCornerRadius.coerceIn(0f, maximumCornerRadius)
        } else {
            0f
        }
        val rawSplitGap = splitGap.toPx()
        val splitGapPx = if (rawSplitGap.isFinite()) {
            rawSplitGap.coerceIn(0f, panelHeight)
        } else {
            0f
        }
        val splitTop = (panelHeight - splitGapPx) / 2f
        val splitBottom = splitTop + splitGapPx
        val topOpacity = if (isDimmed) 0.014f else 0.095f
        val bottomOpacity = if (isDimmed) 0.008f else 0.052f
        val borderOpacity = if (isDimmed) 0.018f else 0.08f
        val fill = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topOpacity),
                Color.White.copy(alpha = bottomOpacity),
            ),
            startY = 0f,
            endY = panelHeight,
        )
        val outlinePath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset.Zero, size),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                ),
            )
        }
        val borderWidth = 1.dp.toPx().coerceAtMost(min(panelWidth, panelHeight))
        val borderInset = borderWidth / 2f
        val borderSize = Size(
            width = (panelWidth - borderWidth).coerceAtLeast(0f),
            height = (panelHeight - borderWidth).coerceAtLeast(0f),
        )
        val borderCornerRadius = (cornerRadiusPx - borderInset).coerceAtLeast(0f)

        onDrawWithContent {
            clipPath(outlinePath) {
                if (splitGapPx <= 0f) {
                    drawRect(brush = fill)
                } else {
                    if (splitTop > 0f) {
                        drawRect(
                            brush = fill,
                            size = Size(panelWidth, splitTop),
                        )
                    }
                    if (splitBottom < panelHeight) {
                        drawRect(
                            brush = fill,
                            topLeft = Offset(0f, splitBottom),
                            size = Size(panelWidth, panelHeight - splitBottom),
                        )
                    }
                }
            }

            drawContent()

            if (borderWidth > 0f && borderSize.width > 0f && borderSize.height > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = borderOpacity),
                    topLeft = Offset(borderInset, borderInset),
                    size = borderSize,
                    cornerRadius = CornerRadius(borderCornerRadius),
                    style = Stroke(width = borderWidth),
                )
            }
        }
    }
}
