package com.armsone.stand.model

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
    const val VERTICAL_DRAG_TRAVEL_RATIO = 0.25f
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
            requested == 0f || requested == 1f ->
                BrightnessAdjustment(requested, StandModePreference.AUTOMATIC)
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

object HoldDurationAdjustment {
    fun value(startingAt: Float, horizontalTranslationPx: Float): Float {
        val rawValue = startingAt + horizontalTranslationPx / 300f * 295f
        val steppedValue = (rawValue / 5f).roundToInt() * 5f
        return steppedValue.coerceIn(5f, 300f)
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

object SleepMovementLightingPolicy {
    fun torchLevel(
        torchEnabled: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Double {
        if (environmentMode != EnvironmentDisplayMode.MATE) return 0.0
        return if (torchEnabled) 1.0 else 0.0
    }
}

object LampTorchLightingPolicy {
    fun maximumLevel(
        torchEnabled: Boolean,
        isMovementTriggered: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Double {
        if (environmentMode != EnvironmentDisplayMode.MATE) return 0.0
        if (!isMovementTriggered) return 0.0
        return SleepMovementLightingPolicy.torchLevel(torchEnabled, environmentMode)
    }
}

object FaceDownLightingPolicy {
    fun shouldBlackout(isSessionActive: Boolean, isFaceDown: Boolean): Boolean =
        isSessionActive && isFaceDown

    fun allowsTorch(isFaceDown: Boolean): Boolean = !isFaceDown
}
