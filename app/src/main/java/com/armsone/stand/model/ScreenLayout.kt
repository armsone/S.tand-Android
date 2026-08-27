package com.armsone.stand.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PanelTransform(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
) {
    fun normalized(): PanelTransform = copy(
        x = x.finiteOrZero(),
        y = y.finiteOrZero(),
        scale = PanelEditingPolicy.clampScale(scale),
    )

    companion object {
        val Centered = PanelTransform(x = 0f, y = 0f)
    }
}

enum class StandControlKind(val rawValue: String) {
    FLASHLIGHT("flashlight"),
    BRIGHTNESS("brightness"),
    STOP_DETECTION("stopDetection"),
    ORIENTATION("orientation"),
    RECORDINGS("recordings"),
    AI_SHOT("aiShot"),
    SETTINGS("settings"),
    BOYISO("boyiso"),
    ;

    companion object {
        val DefaultOrder: List<StandControlKind> = listOf(
            RECORDINGS,
            BOYISO,
            SETTINGS,
        )

        fun fromRawValue(rawValue: String): StandControlKind? =
            entries.firstOrNull { it.rawValue == rawValue }

        fun normalizedOrder(order: Iterable<StandControlKind>?): List<StandControlKind> {
            val result = LinkedHashSet<StandControlKind>()
            order?.forEach { kind ->
                if (kind in DefaultOrder) result.add(kind)
            }
            DefaultOrder.forEach { result.add(it) }
            return result.toList()
        }

        fun normalizedRawOrder(rawOrder: Iterable<String>?): List<StandControlKind> =
            normalizedOrder(rawOrder?.mapNotNull { fromRawValue(it) })
    }
}

enum class WeatherPiece(val index: Int) {
    ICON(0),
    TEMPERATURE(1),
    CONDITION(2),
}

data class StandScreenLayout(
    var clock: PanelTransform = PanelTransform.Centered,
    var seconds: PanelTransform = PanelTransform(x = 0.27f, y = 0.036f),
    var weatherIcon: PanelTransform,
    var weatherTemperature: PanelTransform,
    var weatherCondition: PanelTransform,
    var date: PanelTransform,
    var status: PanelTransform,
    var brightnessRule: PanelTransform,
    var battery: PanelTransform = PanelTransform(x = 0f, y = 0.18f),
    var radio: PanelTransform = PanelTransform(x = 0f, y = 0.28f),
    var secondaryRadio: PanelTransform = PanelTransform(x = -0.26f, y = 0.215f, scale = 0.75f),
    var radiosGrouped: Boolean = false,
    var weatherGroupIds: List<Int>,
    var controlOrder: List<StandControlKind> = StandControlKind.DefaultOrder,
) {
    init {
        clock = clock.normalized()
        seconds = seconds.normalized()
        weatherIcon = weatherIcon.normalized()
        weatherTemperature = weatherTemperature.normalized()
        weatherCondition = weatherCondition.normalized()
        date = date.normalized()
        status = status.normalized()
        brightnessRule = brightnessRule.normalized()
        battery = battery.normalized()
        radio = radio.normalized()
        secondaryRadio = secondaryRadio.normalized()
        weatherGroupIds = WeatherGroupPolicy.normalizedIds(weatherGroupIds)
        controlOrder = StandControlKind.normalizedOrder(controlOrder)
    }

    fun weatherTransform(piece: WeatherPiece): PanelTransform = when (piece) {
        WeatherPiece.ICON -> weatherIcon
        WeatherPiece.TEMPERATURE -> weatherTemperature
        WeatherPiece.CONDITION -> weatherCondition
    }

    fun withWeatherTransform(
        piece: WeatherPiece,
        transform: PanelTransform,
    ): StandScreenLayout = when (piece) {
        WeatherPiece.ICON -> copy(weatherIcon = transform)
        WeatherPiece.TEMPERATURE -> copy(weatherTemperature = transform)
        WeatherPiece.CONDITION -> copy(weatherCondition = transform)
    }

    fun weatherGroupId(piece: WeatherPiece): Int = weatherGroupIds[piece.index]

    companion object {
        val Portrait: StandScreenLayout
            get() {
                val weather = PanelTransform(
                    x = 0f,
                    y = -0.2049743f,
                    scale = 0.8692272f,
                )
                return StandScreenLayout(
                    clock = PanelTransform(x = 0f, y = 0f, scale = 1.291905f),
                    seconds = PanelTransform(x = 0.3355058f, y = 0.0578509f),
                    weatherIcon = weather,
                    weatherTemperature = weather,
                    weatherCondition = weather,
                    date = PanelTransform(x = 0f, y = 0.11799486f),
                    status = PanelTransform(x = 0f, y = 0.15f),
                    brightnessRule = PanelTransform(x = 0f, y = 0.21f),
                    battery = PanelTransform(x = 0f, y = 0.20698372f),
                    radio = PanelTransform(x = 0f, y = -0.31070694f, scale = 1.047652f),
                    secondaryRadio = PanelTransform(
                        x = -0.17436153f,
                        y = 0.31097257f,
                        scale = 0.75f,
                    ),
                    radiosGrouped = true,
                    weatherGroupIds = listOf(1, 1, 1),
                    controlOrder = StandControlKind.DefaultOrder,
                )
            }

        val Landscape: StandScreenLayout
            get() {
                val weather = PanelTransform(
                    x = 0f,
                    y = -0.067452006f,
                    scale = 0.55f,
                )
                return StandScreenLayout(
                    clock = PanelTransform(
                        x = 0f,
                        y = 0.21553229f,
                        scale = 1.1122912f,
                    ),
                    seconds = PanelTransform(
                        x = 0.192f,
                        y = 0.29101223f,
                        scale = 0.82054085f,
                    ),
                    weatherIcon = weather,
                    weatherTemperature = weather,
                    weatherCondition = weather,
                    date = PanelTransform(x = 0f, y = 0.43815008f, scale = 0.85f),
                    status = PanelTransform(x = 0f, y = 0.5f),
                    brightnessRule = PanelTransform(x = 0f, y = 0.34f),
                    battery = PanelTransform(x = 0f, y = 0.5245899f),
                    radio = PanelTransform(x = 0.4f, y = -0.3f, scale = 0.75f),
                    secondaryRadio = PanelTransform(
                        x = -0.4f,
                        y = -0.3f,
                        scale = 0.75f,
                    ),
                    radiosGrouped = false,
                    weatherGroupIds = listOf(1, 1, 1),
                    controlOrder = StandControlKind.DefaultOrder,
                )
            }

        val Television: StandScreenLayout
            get() {
                val weather = PanelTransform(
                    x = 0f,
                    y = -0.067452006f,
                    scale = 0.55f,
                )
                val clockTransform = PanelTransform(
                    x = 0f,
                    y = 0.21553229f,
                    scale = 1.1122912f,
                )
                val secondsScale = 0.82054085f
                val defaultCanvasWidth = 960f
                val defaultCanvasHeight = 540f
                val alignedSeconds = TvClockAlignmentPolicy.calculateAlignedSecondsTransform(
                    clockTransform = clockTransform,
                    canvasWidthDp = defaultCanvasWidth,
                    canvasHeightDp = defaultCanvasHeight,
                    isPortrait = false,
                    secondsScale = secondsScale,
                )
                return StandScreenLayout(
                    clock = clockTransform,
                    seconds = alignedSeconds,
                    weatherIcon = weather,
                    weatherTemperature = weather,
                    weatherCondition = weather,
                    date = PanelTransform(x = 0f, y = 0.43815008f, scale = 0.85f),
                    status = PanelTransform(x = 0f, y = 0.5f),
                    brightnessRule = PanelTransform(x = 0f, y = 0.34f),
                    battery = PanelTransform(x = 0f, y = 0.5245899f),
                    radio = PanelTransform(x = 0.4f, y = -0.3f, scale = 0.75f),
                    secondaryRadio = PanelTransform(
                        x = -0.4f,
                        y = -0.3f,
                        scale = 0.75f,
                    ),
                    radiosGrouped = false,
                    weatherGroupIds = listOf(1, 1, 1),
                    controlOrder = StandControlKind.DefaultOrder,
                )
            }
    }
}

data class FloatSize(
    val width: Float,
    val height: Float,
) {
    fun normalized(): FloatSize = FloatSize(
        width = width.finiteNonNegative(),
        height = height.finiteNonNegative(),
    )
}

data class FloatOffset(
    val x: Float,
    val y: Float,
) {
    fun normalized(): FloatOffset = FloatOffset(
        x = x.finiteOrZero(),
        y = y.finiteOrZero(),
    )
}

data class FloatInsets(
    val top: Float = 0f,
    val left: Float = 0f,
    val bottom: Float = 0f,
    val right: Float = 0f,
) {
    fun normalized(): FloatInsets = FloatInsets(
        top = top.finiteNonNegative(),
        left = left.finiteNonNegative(),
        bottom = bottom.finiteNonNegative(),
        right = right.finiteNonNegative(),
    )
}

data class FloatRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val area: Float
        get() = width.finiteNonNegative() * height.finiteNonNegative()
}

object PanelEditingPolicy {
    const val WeatherMergeOverlapThreshold = 0.40f
    const val RadioMergeOverlapThreshold = 0.40f
    const val MinimumPanelScale = 0.30f
    const val MaximumPanelScale = 2.00f
    private const val DefaultPanelScale = 1f
    private const val CenterSnapFraction = 0.05f

    fun clampScale(scale: Float): Float {
        val finiteScale = scale.takeIf { it.isFinite() } ?: DefaultPanelScale
        return finiteScale.coerceIn(MinimumPanelScale, MaximumPanelScale)
    }

    fun shouldSnapToCenter(centerOffset: Float, panelLength: Float): Boolean =
        centerOffset.isFinite() &&
            panelLength.isFinite() &&
            panelLength > 0f &&
            abs(centerOffset) <= panelLength * CenterSnapFraction

    fun snappedTransform(
        transform: PanelTransform,
        panelSize: FloatSize,
        canvasSize: FloatSize,
    ): PanelTransform {
        val normalizedTransform = transform.normalized()
        val panel = panelSize.normalized()
        val canvas = canvasSize.normalized()
        if (canvas.width <= 0f || canvas.height <= 0f) return normalizedTransform

        val snapX = shouldSnapToCenter(
            centerOffset = normalizedTransform.x * canvas.width,
            panelLength = panel.width * normalizedTransform.scale,
        )
        val snapY = shouldSnapToCenter(
            centerOffset = normalizedTransform.y * canvas.height,
            panelLength = panel.height * normalizedTransform.scale,
        )
        return normalizedTransform.copy(
            x = if (snapX) 0f else normalizedTransform.x,
            y = if (snapY) 0f else normalizedTransform.y,
        )
    }

    fun scaleFromTopLeadingDrag(
        startScale: Float,
        panelSize: FloatSize,
        translation: FloatOffset,
    ): Float {
        val normalizedStart = clampScale(startScale)
        val panel = panelSize.normalized()
        if (
            panel.width <= 0f ||
            panel.height <= 0f ||
            !translation.x.isFinite() ||
            !translation.y.isFinite()
        ) {
            return normalizedStart
        }

        val halfWidth = panel.width * normalizedStart / 2f
        val halfHeight = panel.height * normalizedStart / 2f
        val denominator = halfWidth * halfWidth + halfHeight * halfHeight
        if (!denominator.isFinite() || denominator <= 0f) return normalizedStart

        val resizedX = -halfWidth + translation.x
        val resizedY = -halfHeight + translation.y
        val projectedRatio = max(
            0f,
            (resizedX * -halfWidth + resizedY * -halfHeight) / denominator,
        )
        return clampScale(normalizedStart * projectedRatio)
    }

    fun clampedCenter(
        proposed: FloatOffset,
        panelSize: FloatSize,
        canvasSize: FloatSize,
        insets: FloatInsets = FloatInsets(),
    ): FloatOffset {
        return proposed.normalized()
    }

    fun clampedTransform(
        transform: PanelTransform,
        panelSize: FloatSize,
        canvasSize: FloatSize,
        insets: FloatInsets = FloatInsets(),
        screenScale: Float = 1f,
    ): PanelTransform {
        return transform.normalized()
    }

    fun bounds(center: FloatOffset, size: FloatSize): FloatRect {
        val safeCenter = center.normalized()
        val safeSize = size.normalized()
        return FloatRect(
            x = safeCenter.x - safeSize.width / 2f,
            y = safeCenter.y - safeSize.height / 2f,
            width = safeSize.width,
            height = safeSize.height,
        )
    }

    /**
     * Returns the panel's actual screen-space bounds after both its own transform and the
     * dashboard-wide scale have been applied around the canvas centre.
     */
    fun transformedBounds(
        transform: PanelTransform,
        panelSize: FloatSize,
        canvasSize: FloatSize,
        screenScale: Float = 1f,
    ): FloatRect {
        val canvas = canvasSize.normalized()
        val panel = panelSize.normalized()
        if (canvas.width <= 0f || canvas.height <= 0f) {
            return FloatRect(0f, 0f, 0f, 0f)
        }
        val normalizedTransform = transform.normalized()
        val safeScreenScale = screenScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val center = FloatOffset(
            x = canvas.width / 2f +
                normalizedTransform.x * canvas.width * safeScreenScale,
            y = canvas.height / 2f +
                normalizedTransform.y * canvas.height * safeScreenScale,
        )
        return bounds(
            center = center,
            size = FloatSize(
                width = panel.width * normalizedTransform.scale * safeScreenScale,
                height = panel.height * normalizedTransform.scale * safeScreenScale,
            ),
        )
    }

    fun overlapFraction(lhs: FloatRect, rhs: FloatRect): Float {
        val lhsWidth = lhs.width.finiteNonNegative()
        val lhsHeight = lhs.height.finiteNonNegative()
        val rhsWidth = rhs.width.finiteNonNegative()
        val rhsHeight = rhs.height.finiteNonNegative()
        val smallerArea = min(lhsWidth * lhsHeight, rhsWidth * rhsHeight)
        if (!smallerArea.isFinite() || smallerArea <= 0f) return 0f

        val left = max(lhs.x.finiteOrZero(), rhs.x.finiteOrZero())
        val top = max(lhs.y.finiteOrZero(), rhs.y.finiteOrZero())
        val right = min(
            lhs.x.finiteOrZero() + lhsWidth,
            rhs.x.finiteOrZero() + rhsWidth,
        )
        val bottom = min(
            lhs.y.finiteOrZero() + lhsHeight,
            rhs.y.finiteOrZero() + rhsHeight,
        )
        val intersectionWidth = max(0f, right - left)
        val intersectionHeight = max(0f, bottom - top)
        return ((intersectionWidth * intersectionHeight) / smallerArea).coerceIn(0f, 1f)
    }
}

object RadioGroupPolicy {
    private const val SplitHorizontalOffset = 0.11f

    fun mergeIfOverlapping(
        layout: StandScreenLayout,
        firstBounds: FloatRect,
        secondBounds: FloatRect,
    ): StandScreenLayout {
        if (layout.radiosGrouped ||
            PanelEditingPolicy.overlapFraction(firstBounds, secondBounds) <
            PanelEditingPolicy.RadioMergeOverlapThreshold
        ) {
            return layout
        }
        val merged = PanelTransform(
            x = (layout.radio.x + layout.secondaryRadio.x) / 2f,
            y = (layout.radio.y + layout.secondaryRadio.y) / 2f,
            scale = min(layout.radio.scale, layout.secondaryRadio.scale),
        ).normalized()
        return layout.copy(
            radio = merged,
            secondaryRadio = merged,
            radiosGrouped = true,
        )
    }

    fun split(layout: StandScreenLayout): StandScreenLayout {
        if (!layout.radiosGrouped) return layout
        val center = layout.radio.normalized()
        return layout.copy(
            radio = center.copy(x = center.x - SplitHorizontalOffset),
            secondaryRadio = center.copy(x = center.x + SplitHorizontalOffset),
            radiosGrouped = false,
        )
    }
}

object WeatherGroupPolicy {
    val DefaultGroupIds: List<Int> = listOf(1, 1, 1)

    fun normalizedIds(ids: Iterable<Int>?): List<Int> {
        val provided = ids?.toList().orEmpty()
        return WeatherPiece.entries.map { piece ->
            provided.getOrNull(piece.index) ?: DefaultGroupIds[piece.index]
        }
    }

    fun splitGroup(layout: StandScreenLayout, groupId: Int): StandScreenLayout {
        val pieces = WeatherPiece.entries.filter { layout.weatherGroupId(it) == groupId }
        if (pieces.size <= 1) return layout

        val center = layout.weatherTransform(pieces.first()).normalized()
        val ids = layout.weatherGroupIds.toMutableList()
        var result = layout
        pieces.forEachIndexed { position, piece ->
            ids[piece.index] = piece.index
            result = result.withWeatherTransform(
                piece,
                center.copy(
                    x = center.x + position * 0.16f - (pieces.size - 1) * 0.08f,
                ),
            )
        }
        return result.copy(weatherGroupIds = ids)
    }

    fun mergeIfOverlapping(
        layout: StandScreenLayout,
        sourceGroupId: Int,
        targetGroupId: Int,
        sourceBounds: FloatRect,
        targetBounds: FloatRect,
    ): StandScreenLayout {
        if (sourceGroupId == targetGroupId) return layout
        if (
            PanelEditingPolicy.overlapFraction(sourceBounds, targetBounds) <
            PanelEditingPolicy.WeatherMergeOverlapThreshold
        ) {
            return layout
        }

        val sourcePieces = WeatherPiece.entries.filter {
            layout.weatherGroupId(it) == sourceGroupId
        }
        val targetPieces = WeatherPiece.entries.filter {
            layout.weatherGroupId(it) == targetGroupId
        }
        if (sourcePieces.isEmpty() || targetPieces.isEmpty()) return layout

        val source = layout.weatherTransform(sourcePieces.first())
        val target = layout.weatherTransform(targetPieces.first())
        val combinedPieces = sourcePieces + targetPieces
        val leftTransform = combinedPieces
            .map { layout.weatherTransform(it) }
            .minBy { it.x }
        val merged = leftTransform.copy(
            x = (source.x + target.x) / 2f,
            y = (source.y + target.y) / 2f,
        ).normalized()

        val ids = layout.weatherGroupIds.toMutableList()
        var result = layout
        combinedPieces.forEach { piece ->
            ids[piece.index] = targetGroupId
            result = result.withWeatherTransform(piece, merged)
        }
        return result.copy(weatherGroupIds = ids)
    }
}

object HomeEditorResetPolicy {
    fun panels(layout: StandScreenLayout, isPortrait: Boolean): StandScreenLayout {
        val reset = if (isPortrait) StandScreenLayout.Portrait else StandScreenLayout.Landscape
        return reset.copy(controlOrder = layout.controlOrder)
    }

    fun controls(layout: StandScreenLayout): StandScreenLayout =
        layout.copy(controlOrder = StandControlKind.DefaultOrder)
}

object TvClockAlignmentPolicy {
    const val VERTICAL_SPACING_DP = 8f

    fun calculateAlignedSecondsTransform(
        clockTransform: PanelTransform,
        canvasWidthDp: Float,
        canvasHeightDp: Float,
        isPortrait: Boolean = false,
        secondsScale: Float = 0.82054085f,
        verticalSpacingDp: Float = VERTICAL_SPACING_DP,
    ): PanelTransform {
        if (!canvasWidthDp.isFinite() || canvasWidthDp <= 0f ||
            !canvasHeightDp.isFinite() || canvasHeightDp <= 0f
        ) {
            return PanelTransform(x = 0.192f, y = 0.29101223f, scale = secondsScale)
        }

        val widthFraction = if (isPortrait) 0.78f else 0.52f
        val maximumClockWidth = if (isPortrait) 456f else 560f
        val clockWidth = (canvasWidthDp * widthFraction)
            .coerceAtMost(maximumClockWidth)
            .coerceAtMost(canvasWidthDp)
        val gap = if (isPortrait) 8f else 12f
        val colonWidth = if (isPortrait) 18f else 24f
        val cardAspectRatio = if (isPortrait) 126f / 92f else 164f / 116f
        val cardWidth = ((clockWidth - gap * 2f - colonWidth) / 2f).coerceAtLeast(1f)
        val cardHeight = cardWidth / cardAspectRatio

        val panelWidth = if (isPortrait) 48f else 58f
        val panelHeight = if (isPortrait) 36f else 42f

        val safeClockScale = clockTransform.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeSecondsScale = secondsScale.takeIf { it.isFinite() && it > 0f } ?: 1f

        val clockRenderWidth = clockWidth * safeClockScale
        val clockRenderHeight = cardHeight * safeClockScale
        val secondsRenderWidth = panelWidth * safeSecondsScale
        val secondsRenderHeight = panelHeight * safeSecondsScale

        val clockCenterX = clockTransform.x * canvasWidthDp
        val clockCenterY = clockTransform.y * canvasHeightDp

        val minuteCardRight = clockCenterX + clockRenderWidth / 2f
        val secondsCenterX = minuteCardRight - secondsRenderWidth / 2f

        val minuteCardBottom = clockCenterY + clockRenderHeight / 2f
        val secondsCenterY = minuteCardBottom + verticalSpacingDp + secondsRenderHeight / 2f

        val secondsX = (secondsCenterX / canvasWidthDp).coerceIn(-1f, 1f)
        val secondsY = (secondsCenterY / canvasHeightDp).coerceIn(-1f, 1f)

        return PanelTransform(
            x = secondsX,
            y = secondsY,
            scale = safeSecondsScale,
        )
    }
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun Float.finiteNonNegative(): Float =
    if (isFinite()) coerceAtLeast(0f) else 0f

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
