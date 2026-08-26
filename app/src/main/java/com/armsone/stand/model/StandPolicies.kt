package com.armsone.stand.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScreenTapLampAction { BRIGHTEN, DIM }

object ScreenTapPolicy {
    fun action(phase: LampPhase): ScreenTapLampAction =
        if (phase == LampPhase.HOLDING) ScreenTapLampAction.DIM else ScreenTapLampAction.BRIGHTEN
}

data class BrightnessAdjustment(
    val level: Float,
    val preference: StandModePreference,
)

object SimplifiedBrightnessModePolicy {
    const val MATE_UPPER_BOUND = 0.4f
    const val MATE_TAP_LEVEL = 0.35f
    const val OBJECT_TAP_LEVEL = 0.8f
    const val VERTICAL_DRAG_TRAVEL_RATIO = 0.5f
    const val ENDPOINT_LOCK_DELAY_MILLIS = 1_000L
    const val OBJECT_LOCK_RELEASE_LEVEL = 0.95f
    const val MATE_LOCK_RELEASE_LEVEL = 0.05f

    fun clamped(level: Float): Float = when {
        !level.isFinite() -> 0f
        else -> level.coerceIn(0f, 1f)
    }

    fun level(
        startingAt: Float,
        verticalTranslationPx: Float,
        viewportHeightPx: Float,
    ): Float {
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val travel = (height * VERTICAL_DRAG_TRAVEL_RATIO).coerceAtLeast(1f)
        val translation = verticalTranslationPx.takeIf(Float::isFinite) ?: 0f
        return clamped(clamped(startingAt) - translation / travel)
    }

    fun stabilizedAdjustment(
        requestedLevel: Float,
        currentPreference: StandModePreference,
    ): BrightnessAdjustment {
        val requested = clamped(requestedLevel)
        return when {
            currentPreference == StandModePreference.OBJECT &&
                requested >= OBJECT_LOCK_RELEASE_LEVEL ->
                BrightnessAdjustment(1f, StandModePreference.OBJECT)
            currentPreference == StandModePreference.MATE &&
                requested <= MATE_LOCK_RELEASE_LEVEL ->
                BrightnessAdjustment(0f, StandModePreference.MATE)
            requested == 0f -> BrightnessAdjustment(0f, StandModePreference.AUTOMATIC)
            requested == 1f -> BrightnessAdjustment(1f, StandModePreference.AUTOMATIC)
            else -> BrightnessAdjustment(requested, StandModePreference.AUTOMATIC)
        }
    }

    fun mode(level: Float, preference: StandModePreference): EnvironmentDisplayMode =
        when (preference) {
            StandModePreference.OBJECT -> EnvironmentDisplayMode.OBJECT
            StandModePreference.MATE -> EnvironmentDisplayMode.MATE
            StandModePreference.AUTOMATIC -> if (clamped(level) <= MATE_UPPER_BOUND) {
                EnvironmentDisplayMode.MATE
            } else {
                EnvironmentDisplayMode.OBJECT
            }
        }

    fun tapLevel(from: EnvironmentDisplayMode): Float =
        if (from == EnvironmentDisplayMode.OBJECT) MATE_TAP_LEVEL else OBJECT_TAP_LEVEL
}

object AppBrightnessSystemSyncPolicy {
    fun shouldAdoptSystemBrightness(
        isAdjustingBrightness: Boolean,
        modePreference: StandModePreference,
        isFaceDown: Boolean,
    ): Boolean = !isAdjustingBrightness &&
        modePreference == StandModePreference.AUTOMATIC &&
        !isFaceDown
}

object HoldDurationAdjustment {
    fun value(startingAt: Float, horizontalTranslationPx: Float): Float {
        val rawValue = startingAt + horizontalTranslationPx / 300f * 295f
        val steppedValue = (rawValue / 5f).roundToInt() * 5f
        return steppedValue.coerceIn(5f, 300f)
    }
}

object HomeEditGesturePolicy {
    const val HOLD_DURATION_MILLIS = 2_000L

    fun shouldCancelHold(
        movementDistancePx: Float,
        touchSlopPx: Float,
        pointerCount: Int,
    ): Boolean = movementDistancePx >= touchSlopPx || pointerCount > 1

    fun canEnterEditMode(
        holdDurationMillis: Long,
        hasMovedBeyondSlop: Boolean,
        pointerCount: Int,
    ): Boolean = holdDurationMillis >= HOLD_DURATION_MILLIS &&
        !hasMovedBeyondSlop &&
        pointerCount == 1
}

object HomeClockScalePolicy {
    const val MINIMUM_SCALE = 0.7f
    const val MAXIMUM_TOUCH_SCALE = 1.35f
    const val MAXIMUM_SCALE = 1.7f

    fun clamped(scale: Float): Float = when {
        !scale.isFinite() -> 1f
        else -> scale.coerceIn(MINIMUM_SCALE, MAXIMUM_SCALE)
    }

    fun scaled(startingAt: Float, magnification: Float): Float {
        val safeMagnification = magnification.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeStartingScale = startingAt.coerceIn(MINIMUM_SCALE, MAXIMUM_TOUCH_SCALE)
        return (safeStartingScale * safeMagnification)
            .coerceIn(MINIMUM_SCALE, MAXIMUM_TOUCH_SCALE)
    }
}

object StandAutomaticDimmingPolicy {
    fun shouldFade(
        automaticDimmingEnabled: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Boolean = automaticDimmingEnabled && environmentMode == EnvironmentDisplayMode.MATE
}

object SleepCareMonitoringPolicy {
    fun shouldMonitor(
        isSessionActive: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Boolean = isSessionActive && environmentMode == EnvironmentDisplayMode.MATE
}

object StartleActivationPolicy {
    const val DELAY_MILLIS = 120_000L

    fun canActivate(
        mateModeEnteredAtMillis: Long?,
        nowMillis: Long,
    ): Boolean = mateModeEnteredAtMillis != null &&
        nowMillis - mateModeEnteredAtMillis >= DELAY_MILLIS

    fun entryTimeAfterTransition(
        previous: EnvironmentDisplayMode,
        current: EnvironmentDisplayMode,
        existingEntryTimeMillis: Long?,
        nowMillis: Long,
    ): Long? = when {
        previous == current -> existingEntryTimeMillis
        current == EnvironmentDisplayMode.MATE -> nowMillis
        else -> null
    }
}

object SleepMovementLightingPolicy {
    fun torchLevel(
        torchEnabled: Boolean,
        roomIsDark: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Double {
        if (environmentMode != EnvironmentDisplayMode.MATE || !roomIsDark) return 0.0
        return if (torchEnabled) 1.0 else 0.1
    }
}

object LampTorchLightingPolicy {
    fun maximumLevel(
        torchEnabled: Boolean,
        isMovementTriggered: Boolean,
        roomIsDark: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Double {
        if (environmentMode != EnvironmentDisplayMode.MATE) return 0.0
        if (!isMovementTriggered) return 0.0
        return SleepMovementLightingPolicy.torchLevel(torchEnabled, roomIsDark, environmentMode)
    }
}

object FaceDownLightingPolicy {
    fun shouldBlackout(isSessionActive: Boolean, isFaceDown: Boolean): Boolean =
        isSessionActive && isFaceDown

    fun allowsTorch(isFaceDown: Boolean): Boolean = !isFaceDown
}

/** Fixed horizontal music strip below the top header, matching the iOS source tokens. */
object MusicChannelStripLayoutPolicy {
    const val SPACING = 8f
    const val SIDE_INSET = 12f
    const val PHONE_LANDSCAPE_CARD_WIDTH_SCALE = 0.8f
    const val CARD_HEIGHT = 60f

    fun cardWidth(viewportWidth: Float, isPhoneLandscape: Boolean = false): Float {
        val baseWidth = if (viewportWidth < 700f) {
            max(148f, (viewportWidth - 46f) / 2f)
        } else {
            168f
        }
        return if (isPhoneLandscape) baseWidth * PHONE_LANDSCAPE_CARD_WIDTH_SCALE else baseWidth
    }

    fun contentWidth(cardCount: Int, cardWidth: Float): Float {
        if (cardCount <= 0) return 0f
        return cardCount * cardWidth + (cardCount - 1) * SPACING
    }

    fun maximumScroll(viewportWidth: Float, cardCount: Int, cardWidth: Float): Float = max(
        0f,
        contentWidth(cardCount, cardWidth) - max(0f, viewportWidth - SIDE_INSET * 2),
    )

    fun clampedOffset(offset: Float, maximumScroll: Float): Float =
        min(0f, max(-maximumScroll, offset))

    fun draggedOffset(offset: Float, dragAmount: Float, maximumScroll: Float): Float =
        clampedOffset(offset + dragAmount, maximumScroll)

    fun leadingAlignedOffset(cardIndex: Int, cardWidth: Float, maximumScroll: Float): Float =
        clampedOffset(-max(0, cardIndex) * (cardWidth + SPACING), maximumScroll)
}

object InternetRadioTitleTapPolicy {
    fun targetChannelID(
        tappedChannelID: String,
        activeChannelID: String?,
        isPlaying: Boolean,
        orderedChannelIDs: List<String>,
    ): String? {
        if (orderedChannelIDs.isEmpty()) return null
        if (!isPlaying) return tappedChannelID.takeIf(orderedChannelIDs::contains)
        if (orderedChannelIDs.size == 1) return null

        val currentChannelID = activeChannelID ?: tappedChannelID
        val currentIndex = orderedChannelIDs.indexOf(currentChannelID)
        return if (currentIndex >= 0) {
            orderedChannelIDs[(currentIndex + 1) % orderedChannelIDs.size]
        } else {
            orderedChannelIDs.first()
        }
    }
}

/** Fixed control column beside the music strip on phone-shaped landscape canvases. */
object PhoneLandscapeSideControlsPolicy {
    const val CONTROL_WIDTH = 68f
    const val TABLET_MINIMUM_SHORT_EDGE = 600f

    fun isEnabled(
        isPortrait: Boolean,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Boolean = !isPortrait &&
        min(viewportWidth, viewportHeight) < TABLET_MINIMUM_SHORT_EDGE
}

/** Matches iOS RecordingSwipeDeletePolicy: left-swipe-only immediate delete. */
object RecordingSwipeDeletePolicy {
    const val DELETE_THRESHOLD = 56f
    const val MAXIMUM_REVEAL = 112f

    fun isDeleteGesture(
        translationX: Float,
        translationY: Float,
        predictedEndTranslationX: Float,
    ): Boolean {
        val horizontalDistance = min(translationX, predictedEndTranslationX)
        return horizontalDistance <= -DELETE_THRESHOLD && abs(translationX) > abs(translationY)
    }

    fun clampedReveal(translationX: Float): Float = translationX.coerceIn(-MAXIMUM_REVEAL, 0f)
}
