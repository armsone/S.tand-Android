package com.armsone.stand.model

import android.content.res.Configuration

/**
 * Pure policy for Android TV / Google TV platform detection, capability gating,
 * overscan margin boundaries, and D-pad remote alternative controls.
 */
object TvUiModePolicy {
    const val SAFE_MARGIN_HORIZONTAL_DP = 48f
    const val SAFE_MARGIN_VERTICAL_DP = 24f
    const val TV_HOME_TOP_PADDING_DP = 11.4f
    const val MINIMUM_BRIGHTNESS_STEP = 0.1f
    const val BRIGHTNESS_STEP_COUNT = 10
    const val CLOCK_SCALE_STEP_COUNT = 6

    private val clockScaleLevels = floatArrayOf(0.7f, 0.9f, 1.1f, 1.3f, 1.5f, 1.7f)

    fun isTelevision(uiMode: Int): Boolean =
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    fun isTelevision(configuration: Configuration?): Boolean =
        configuration?.let { isTelevision(it.uiMode) } ?: false

    fun supportsTorch(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsCamera(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsOrientationLock(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsAiShot(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsCameraScanning(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsBoyiso(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsSleepSounds(isTelevision: Boolean): Boolean = !isTelevision

    fun supportsModeCycling(isTelevision: Boolean): Boolean = !isTelevision

    fun allowedControls(
        isTelevision: Boolean,
        requested: List<StandControlKind>,
    ): List<StandControlKind> {
        if (!isTelevision) return requested
        return requested.filter { kind ->
            when (kind) {
                StandControlKind.FLASHLIGHT -> false
                StandControlKind.ORIENTATION -> false
                StandControlKind.AI_SHOT -> false
                StandControlKind.BRIGHTNESS -> false
                StandControlKind.STOP_DETECTION -> false
                StandControlKind.RECORDINGS -> false
                StandControlKind.BOYISO -> false
                StandControlKind.SETTINGS -> true
            }
        }
    }

    fun allowedSettingsSections(
        isTelevision: Boolean,
        requested: List<SettingsSectionKind> = SettingsInformationArchitecture.CardOrder,
    ): List<SettingsSectionKind> {
        if (!isTelevision) return requested
        return requested.filter { section ->
            when (section) {
                SettingsSectionKind.BOYISO -> false
                SettingsSectionKind.SLEEP_SOUNDS -> false
                else -> true
            }
        }
    }

    fun homeTopPaddingDp(isTelevision: Boolean): Float =
        if (isTelevision) TV_HOME_TOP_PADDING_DP else 14f

    fun filterLaunchPermissions(
        isTelevision: Boolean,
        permissions: List<String>,
    ): List<String> {
        if (!isTelevision) return permissions
        return permissions.filterNot { permission ->
            permission == "android.permission.CAMERA" ||
                permission == "android.permission.RECORD_AUDIO"
        }
    }

    fun stepBrightness(current: Float, increase: Boolean = true): Float =
        if (increase) {
            if (current >= 1.0f - 0.01f) {
                0.1f
            } else {
                (current + MINIMUM_BRIGHTNESS_STEP).coerceAtMost(1f)
            }
        } else {
            if (current <= 0.1f + 0.01f) {
                1.0f
            } else {
                (current - MINIMUM_BRIGHTNESS_STEP).coerceAtLeast(0.05f)
            }
        }

    fun brightnessStep(current: Float): Int =
        (current.coerceIn(0.1f, 1f) * BRIGHTNESS_STEP_COUNT + 0.5f)
            .toInt()
            .coerceIn(1, BRIGHTNESS_STEP_COUNT)

    fun stepClockScale(current: Float, increase: Boolean = true): Float = if (increase) {
        clockScaleLevels.firstOrNull { it > current + 0.01f } ?: clockScaleLevels.first()
    } else {
        clockScaleLevels.lastOrNull { it < current - 0.01f } ?: clockScaleLevels.last()
    }

    fun clockScaleStep(current: Float): Int =
        clockScaleLevels.indices.minByOrNull { index ->
            kotlin.math.abs(clockScaleLevels[index] - current)
        }?.plus(1) ?: 1

    fun stepVolume(current: Float, increase: Boolean = true): Float =
        if (increase) {
            (current + 0.1f).coerceAtMost(1f)
        } else {
            (current - 0.1f).coerceAtLeast(0f)
        }
}
