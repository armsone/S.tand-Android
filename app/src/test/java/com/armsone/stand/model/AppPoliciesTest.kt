package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPoliciesTest {
    @Test
    fun cameraAmbientSensingIsOptIn() {
        assertFalse(AppSettings.Recommended.cameraAmbientSensingEnabled)
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
    }
}
