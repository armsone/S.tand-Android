package com.armsone.stand.model

import kotlin.math.roundToInt

enum class ScreenTapLampAction { BRIGHTEN, DIM }

object ScreenTapPolicy {
    fun action(phase: LampPhase): ScreenTapLampAction =
        if (phase == LampPhase.HOLDING) ScreenTapLampAction.DIM else ScreenTapLampAction.BRIGHTEN
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
        return if (torchEnabled) 1.0 else 0.1
    }
}

object LampTorchLightingPolicy {
    fun maximumLevel(
        torchEnabled: Boolean,
        isMovementTriggered: Boolean,
        environmentMode: EnvironmentDisplayMode,
    ): Double {
        if (environmentMode != EnvironmentDisplayMode.MATE) return 0.0
        if (isMovementTriggered) {
            return SleepMovementLightingPolicy.torchLevel(torchEnabled, environmentMode)
        }
        return if (torchEnabled) 1.0 else 0.0
    }
}
