package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPoliciesTest {
    @Test
    fun recommendedSettingsUseTheSimplifiedIosDefaults() {
        val settings = AppSettings.Recommended

        assertEquals(AppSettings.DEFAULT_CLOCK_SCALE, settings.clockScale, 0f)
        assertEquals(0.4f, settings.brightnessModeThreshold, 0f)
        assertFalse(settings.automaticDimmingEnabled)
        assertEquals(ClockHourMode.TWELVE, settings.clockHourMode)
        assertEquals(OrientationPreference.AUTOMATIC, settings.orientationPreference)
        assertEquals(
            listOf(StandControlKind.RECORDINGS, StandControlKind.SETTINGS),
            settings.portraitLayout.controlOrder,
        )
    }

    @Test
    fun currentExperienceMigrationPreservesPersonalChoicesAndResetsOnlyTheContract() {
        val previous = AppSettings(
            clockScale = 1.7f,
            clockFont = ClockFontChoice.POPPINS,
            displayTheme = StandDisplayTheme.GRAYSCALE,
            brightnessModeThreshold = 0.12f,
            holdDurationSeconds = 90f,
            torchEnabled = false,
            recordingEnabled = false,
            orientationPreference = OrientationPreference.LANDSCAPE,
            clockHourMode = ClockHourMode.TWENTY_FOUR,
        )

        val migrated = CurrentExperienceMigration.apply(previous)

        assertEquals(AppSettings.DEFAULT_CLOCK_SCALE, migrated.clockScale, 0f)
        assertEquals(0.4f, migrated.brightnessModeThreshold, 0f)
        assertEquals(5f, migrated.holdDurationSeconds, 0f)
        assertTrue(migrated.torchEnabled)
        assertEquals(OrientationPreference.AUTOMATIC, migrated.orientationPreference)
        assertEquals(ClockHourMode.TWELVE, migrated.clockHourMode)
        assertEquals(ClockFontChoice.POPPINS, migrated.clockFont)
        assertEquals(StandDisplayTheme.GRAYSCALE, migrated.displayTheme)
        assertFalse(migrated.recordingEnabled)
    }

    @Test
    fun cameraAmbientSensingIsOptIn() {
        assertFalse(AppSettings.Recommended.cameraAmbientSensingEnabled)
    }

    @Test
    fun displayThemeCyclesThroughAllFourIosThemes() {
        assertEquals(StandDisplayTheme.GRAYSCALE, StandDisplayTheme.COLOR.next())
        assertEquals(StandDisplayTheme.MIDNIGHT, StandDisplayTheme.GRAYSCALE.next())
        assertEquals(StandDisplayTheme.SAGE, StandDisplayTheme.MIDNIGHT.next())
        assertEquals(StandDisplayTheme.COLOR, StandDisplayTheme.SAGE.next())
    }

    @Test
    fun luxScaleKeepsBedroomThresholdIntuitive() {
        assertEquals(0f, AmbientLightPolicy.normalizedLux(0f), 0.0001f)
        assertTrue(AmbientLightPolicy.normalizedLux(10f) in 0.34f..0.36f)
        assertEquals(1f, AmbientLightPolicy.normalizedLux(1_000f), 0.0001f)
    }

    @Test
    fun automaticModeUsesDarkForMateAndBrightForObject() {
        assertEquals(
            EnvironmentDisplayMode.MATE,
            AmbientLightPolicy.targetMode(StandModePreference.AUTOMATIC, 0.1f, 0.35f),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientLightPolicy.targetMode(StandModePreference.AUTOMATIC, 0.8f, 0.35f),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientLightPolicy.targetMode(StandModePreference.OBJECT, 0f, 0.35f),
        )
    }

    @Test
    fun batteryProtectionOnlyStopsLowUnpluggedDevice() {
        assertTrue(BatteryProtectionPolicy.shouldProtect(0.2f, isCharging = false))
        assertFalse(BatteryProtectionPolicy.shouldProtect(0.2f, isCharging = true))
        assertFalse(BatteryProtectionPolicy.shouldProtect(0.5f, isCharging = false))
        assertFalse(BatteryProtectionPolicy.shouldProtect(null, isCharging = false))
        assertTrue(
            BatteryProtectionPolicy.shouldClearProtection(
                wasProtecting = true,
                shouldProtectNow = false,
            ),
        )
        assertFalse(
            BatteryProtectionPolicy.shouldClearProtection(
                wasProtecting = false,
                shouldProtectNow = false,
            ),
        )
    }
}
