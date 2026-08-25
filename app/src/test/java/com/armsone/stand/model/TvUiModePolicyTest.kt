package com.armsone.stand.model

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvUiModePolicyTest {

    @Test
    fun testIsTelevisionDetection() {
        val tvUiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        val phoneUiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
        val undefinedUiMode = Configuration.UI_MODE_TYPE_UNDEFINED

        assertTrue(TvUiModePolicy.isTelevision(tvUiMode))
        assertFalse(TvUiModePolicy.isTelevision(phoneUiMode))
        assertFalse(TvUiModePolicy.isTelevision(undefinedUiMode))
        assertFalse(TvUiModePolicy.isTelevision(null as Configuration?))
    }

    @Test
    fun testCapabilityGatingOnTelevision() {
        assertFalse(TvUiModePolicy.supportsTorch(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsCamera(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsOrientationLock(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsAiShot(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsCameraScanning(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsBoyiso(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsSleepSounds(isTelevision = true))
        assertFalse(TvUiModePolicy.supportsModeCycling(isTelevision = true))

        assertTrue(TvUiModePolicy.supportsTorch(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsCamera(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsOrientationLock(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsAiShot(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsCameraScanning(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsBoyiso(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsSleepSounds(isTelevision = false))
        assertTrue(TvUiModePolicy.supportsModeCycling(isTelevision = false))
    }

    @Test
    fun testAllowedControlsOnTelevisionFiltersUnavailableHardware() {
        val fullControls = listOf(
            StandControlKind.FLASHLIGHT,
            StandControlKind.BRIGHTNESS,
            StandControlKind.STOP_DETECTION,
            StandControlKind.ORIENTATION,
            StandControlKind.RECORDINGS,
            StandControlKind.AI_SHOT,
            StandControlKind.SETTINGS,
            StandControlKind.BOYISO,
        )

        val tvControls = TvUiModePolicy.allowedControls(isTelevision = true, requested = fullControls)
        assertEquals(
            listOf(
                StandControlKind.SETTINGS,
            ),
            tvControls,
        )
        assertFalse(tvControls.contains(StandControlKind.FLASHLIGHT))
        assertFalse(tvControls.contains(StandControlKind.ORIENTATION))
        assertFalse(tvControls.contains(StandControlKind.AI_SHOT))
        assertFalse(tvControls.contains(StandControlKind.BRIGHTNESS))
        assertFalse(tvControls.contains(StandControlKind.STOP_DETECTION))
        assertFalse(tvControls.contains(StandControlKind.RECORDINGS))
        assertFalse(tvControls.contains(StandControlKind.BOYISO))
        assertTrue(tvControls.contains(StandControlKind.SETTINGS))

        val mobileControls = TvUiModePolicy.allowedControls(isTelevision = false, requested = fullControls)
        assertEquals(fullControls, mobileControls)
    }

    @Test
    fun testAllowedSettingsSectionsOnTelevision() {
        val fullSections = listOf(
            SettingsSectionKind.SCREEN_AND_CLOCK,
            SettingsSectionKind.PERMISSIONS,
            SettingsSectionKind.BOYISO,
            SettingsSectionKind.SLEEP_SOUNDS,
            SettingsSectionKind.INFORMATION,
            SettingsSectionKind.MUSIC,
        )

        val tvSections = TvUiModePolicy.allowedSettingsSections(isTelevision = true, requested = fullSections)
        assertEquals(
            listOf(
                SettingsSectionKind.SCREEN_AND_CLOCK,
                SettingsSectionKind.PERMISSIONS,
                SettingsSectionKind.INFORMATION,
                SettingsSectionKind.MUSIC,
            ),
            tvSections,
        )
        assertFalse(tvSections.contains(SettingsSectionKind.BOYISO))
        assertFalse(tvSections.contains(SettingsSectionKind.SLEEP_SOUNDS))

        val mobileSections = TvUiModePolicy.allowedSettingsSections(isTelevision = false, requested = fullSections)
        assertEquals(fullSections, mobileSections)
    }

    @Test
    fun testHomeTopPaddingOnTelevision() {
        assertEquals(11.4f, TvUiModePolicy.homeTopPaddingDp(isTelevision = true), 0.001f)
        assertEquals(14f, TvUiModePolicy.homeTopPaddingDp(isTelevision = false), 0.001f)
    }

    @Test
    fun testFilterLaunchPermissionsOnTelevision() {
        val permissions = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_COARSE_LOCATION",
        )

        val tvFiltered = TvUiModePolicy.filterLaunchPermissions(isTelevision = true, permissions = permissions)
        assertEquals(
            listOf("android.permission.ACCESS_COARSE_LOCATION"),
            tvFiltered,
        )
        assertFalse(tvFiltered.contains("android.permission.CAMERA"))
        assertFalse(tvFiltered.contains("android.permission.RECORD_AUDIO"))

        val mobileFiltered = TvUiModePolicy.filterLaunchPermissions(isTelevision = false, permissions = permissions)
        assertEquals(permissions, mobileFiltered)
    }

    @Test
    fun testStepBrightnessCycling() {
        // Stepping up from 0.5
        val next = TvUiModePolicy.stepBrightness(0.5f, increase = true)
        assertEquals(0.6f, next, 0.001f)

        // Stepping up at max (1.0) cycles back to 0.1
        val wrappedFromMax = TvUiModePolicy.stepBrightness(1.0f, increase = true)
        assertEquals(0.1f, wrappedFromMax, 0.001f)

        // Stepping down from 0.5
        val prev = TvUiModePolicy.stepBrightness(0.5f, increase = false)
        assertEquals(0.4f, prev, 0.001f)

        // Stepping down at min (0.1) cycles to 1.0
        val wrappedFromMin = TvUiModePolicy.stepBrightness(0.1f, increase = false)
        assertEquals(1.0f, wrappedFromMin, 0.001f)
    }

    @Test
    fun testStepClockScaleCycling() {
        // Stepping up from 1.0
        val next = TvUiModePolicy.stepClockScale(1.0f, increase = true)
        assertEquals(1.1f, next, 0.001f)
        assertEquals(3, TvUiModePolicy.clockScaleStep(next))

        // Stepping up at the enlarged TV maximum cycles back to 0.7.
        val wrappedFromMax = TvUiModePolicy.stepClockScale(HomeClockScalePolicy.MAXIMUM_SCALE, increase = true)
        assertEquals(HomeClockScalePolicy.MINIMUM_SCALE, wrappedFromMax, 0.001f)

        // Stepping down at min cycles to the enlarged TV maximum (1.7).
        val wrappedFromMin = TvUiModePolicy.stepClockScale(HomeClockScalePolicy.MINIMUM_SCALE, increase = false)
        assertEquals(HomeClockScalePolicy.MAXIMUM_SCALE, wrappedFromMin, 0.001f)
        assertEquals(6, TvUiModePolicy.clockScaleStep(wrappedFromMin))
    }

    @Test
    fun testBrightnessStepLabels() {
        assertEquals(1, TvUiModePolicy.brightnessStep(0.1f))
        assertEquals(4, TvUiModePolicy.brightnessStep(0.4f))
        assertEquals(10, TvUiModePolicy.brightnessStep(1f))
    }

    @Test
    fun testStepVolumeBounds() {
        val up = TvUiModePolicy.stepVolume(0.95f, increase = true)
        assertEquals(1.0f, up, 0.001f)

        val down = TvUiModePolicy.stepVolume(0.05f, increase = false)
        assertEquals(0.0f, down, 0.001f)
    }

    @Test
    fun testSafeMarginsMatchTvGuidelines() {
        assertEquals(48f, TvUiModePolicy.SAFE_MARGIN_HORIZONTAL_DP, 0.001f)
        assertEquals(24f, TvUiModePolicy.SAFE_MARGIN_VERTICAL_DP, 0.001f)
    }
}
