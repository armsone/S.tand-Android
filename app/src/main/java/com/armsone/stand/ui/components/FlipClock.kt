package com.armsone.stand.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun FlipClock(
    hourMode: ClockHourMode,
    clockFont: ClockFontChoice,
    isPortrait: Boolean,
    scale: Float,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
    fixedNow: LocalDateTime? = null,
) {
    val now by produceState(initialValue = fixedNow ?: LocalDateTime.now(), fixedNow) {
        if (fixedNow == null) {
            while (true) {
                value = LocalDateTime.now()
                delay(250)
            }
        }
    }
    val formatter = if (hourMode == ClockHourMode.TWENTY_FOUR) {
        DateTimeFormatter.ofPattern("HHmm")
    } else {
        DateTimeFormatter.ofPattern("hhmm")
    }
    val digits = now.format(formatter)

    BoxWithConstraints(modifier = modifier) {
        // Keep the clock proportional to the available canvas, while stopping a tablet's
        // extra width from stretching the cards without also growing their contents.
        val widthFraction = if (isPortrait) 0.78f else 0.52f
        val maximumClockWidth = if (isPortrait) 456.dp else 560.dp
        val safeScale = scale.takeIf { it.isFinite() }?.coerceIn(0.7f, 1.35f) ?: 1f
        val clockWidth = (maxWidth * widthFraction * safeScale)
            .coerceAtMost(maximumClockWidth)
            .coerceAtMost(maxWidth)
        val gap = (if (isPortrait) 8.dp else 12.dp) * safeScale
        val colonWidth = (if (isPortrait) 18.dp else 24.dp) * safeScale
        val cardAspectRatio = if (isPortrait) 126f / 92f else 164f / 116f
        val cardWidth = ((clockWidth - gap * 2f - colonWidth) / 2f).coerceAtLeast(1.dp)
        val cardHeight = cardWidth / cardAspectRatio
        val systemFontScale = LocalDensity.current.fontScale.coerceAtLeast(0.1f)
        val digitTextSize = (cardHeight.value * 0.70f / systemFontScale).sp
        val colonTextSize = (cardHeight.value * 0.52f / systemFontScale).sp
        val cardSplitGap = (if (isPortrait) 4.dp else 3.dp) * safeScale

        Row(
            modifier = Modifier
                .width(clockWidth)
                .height(cardHeight),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlipCard(
                value = digits.take(2),
                clockFont = clockFont,
                textSize = digitTextSize,
                contentAlpha = contentAlpha,
                width = cardWidth,
                height = cardHeight,
                splitGap = cardSplitGap,
            )
            Box(
                modifier = Modifier.size(width = colonWidth, height = cardHeight),
                contentAlignment = Alignment.Center,
            ) {
                val colonVisualSize = colonTextSize.value * systemFontScale
                val colonVerticalOffset = ClockVisualPolicy.verticalOffset(
                    font = clockFont,
                    fontSize = colonVisualSize,
                ).dp
                Text(
                    text = ":",
                    color = Color.White.copy(alpha = contentAlpha * 0.82f),
                    fontFamily = clockFont.fontFamily(),
                    fontSize = colonTextSize,
                    fontWeight = clockFont.fontWeight(),
                    modifier = Modifier.offset(y = colonVerticalOffset),
                )
            }
            FlipCard(
                value = digits.takeLast(2),
                clockFont = clockFont,
                textSize = digitTextSize,
                contentAlpha = contentAlpha,
                width = cardWidth,
                height = cardHeight,
                splitGap = cardSplitGap,
            )
        }
    }
}

@Composable
private fun FlipCard(
    value: String,
    clockFont: ClockFontChoice,
    textSize: TextUnit,
    contentAlpha: Float,
    width: Dp,
    height: Dp,
    splitGap: Dp,
) {
    val fontFamily = clockFont.fontFamily()
    val density = LocalDensity.current
    val visualFontSize = with(density) { textSize.toPx() / this.density }
    val verticalOffset = ClockVisualPolicy.verticalOffset(
        font = clockFont,
        fontSize = visualFontSize,
    ).dp
    val cornerRadius = if (height < 100.dp) 18.dp else 22.dp
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .standPanelSurface(
                isDimmed = contentAlpha <= 0.2f,
                cornerRadius = cornerRadius,
                splitGap = splitGap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .flipTextSplitMask(splitGap),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = value,
                modifier = Modifier.offset(y = verticalOffset),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "flip card",
            ) { displayedValue ->
                Text(
                    text = displayedValue,
                    color = Color.White.copy(alpha = contentAlpha),
                    fontFamily = fontFamily,
                    fontSize = textSize,
                    fontWeight = clockFont.fontWeight(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun ClockSeconds(
    clockFont: ClockFontChoice,
    isPortrait: Boolean,
    contentAlpha: Float,
    showsBackground: Boolean,
    modifier: Modifier = Modifier,
    fixedNow: LocalDateTime? = null,
) {
    val now by produceState(initialValue = fixedNow ?: LocalDateTime.now(), fixedNow) {
        if (fixedNow == null) {
            while (true) {
                value = LocalDateTime.now()
                delay(250)
            }
        }
    }
    val fontSize = if (showsBackground) {
        if (isPortrait) 18.sp else 22.sp
    } else {
        if (isPortrait) 13.sp else 16.sp
    }
    val systemFontScale = LocalDensity.current.fontScale.coerceAtLeast(0.1f)
    val verticalOffset = ClockVisualPolicy.verticalOffset(
        font = clockFont,
        fontSize = fontSize.value * systemFontScale,
    ).dp
    val panelWidth = if (isPortrait) 48.dp else 58.dp
    val panelHeight = if (isPortrait) 36.dp else 42.dp
    val cornerRadius = if (isPortrait) 11.dp else 13.dp
    val splitGap = if (isPortrait) 2.dp else 2.5.dp

    Box(
        modifier = modifier
            .size(width = panelWidth, height = panelHeight)
            .then(
                if (showsBackground) {
                    Modifier.standPanelSurface(
                        isDimmed = contentAlpha <= 0.2f,
                        cornerRadius = cornerRadius,
                        splitGap = splitGap,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = now.format(DateTimeFormatter.ofPattern("ss")),
            modifier = Modifier.offset(y = verticalOffset),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "clock seconds",
        ) { seconds ->
            Text(
                text = seconds,
                color = Color.White.copy(alpha = contentAlpha * 0.40f),
                fontFamily = clockFont.fontFamily(),
                fontSize = fontSize,
                fontWeight = clockFont.fontWeight(),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Clears the same card-centered interval used by [standPanelSurface] from clock glyphs only. */
internal fun Modifier.flipTextSplitMask(splitGap: Dp): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val gap = ClockVisualPolicy.splitGapBounds(
            cardHeight = size.height,
            gapHeight = splitGap.toPx(),
        )
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(0f, gap.top),
            size = Size(size.width, gap.bottom - gap.top),
            blendMode = BlendMode.Clear,
        )
    }

@Suppress("UNUSED_PARAMETER")
@Composable
fun ClockDateAndSeconds(
    hourMode: ClockHourMode,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
    fixedNow: LocalDateTime? = null,
) {
    val now by produceState(initialValue = fixedNow ?: LocalDateTime.now(), fixedNow) {
        if (fixedNow == null) {
            while (true) {
                value = LocalDateTime.now()
                delay(250)
            }
        }
    }
    val dateText = now.format(
        DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN),
    )
    Text(
        text = dateText,
        color = Color.White.copy(alpha = contentAlpha * 0.7f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
