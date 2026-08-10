package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandPoliciesTest {
    @Test
    fun screenTapDimsOnlyWhileHolding() {
        assertEquals(ScreenTapLampAction.DIM, ScreenTapPolicy.action(LampPhase.HOLDING))
        assertEquals(ScreenTapLampAction.BRIGHTEN, ScreenTapPolicy.action(LampPhase.FADING))
        assertEquals(ScreenTapLampAction.BRIGHTEN, ScreenTapPolicy.action(LampPhase.OFF))
    }

    @Test
    fun holdDurationUsesIosFiveSecondStepsAndBounds() {
        assertEquals(5f, HoldDurationAdjustment.value(5f, -1_000f), 0f)
        assertEquals(155f, HoldDurationAdjustment.value(5f, 150f), 0f)
        assertEquals(300f, HoldDurationAdjustment.value(5f, 1_000f), 0f)
    }

    @Test
    fun onlyMateAutomaticallyDimsAndMonitors() {
        assertTrue(StandAutomaticDimmingPolicy.shouldFade(true, EnvironmentDisplayMode.MATE))
        assertFalse(StandAutomaticDimmingPolicy.shouldFade(true, EnvironmentDisplayMode.OBJECT))
        assertFalse(StandAutomaticDimmingPolicy.shouldFade(false, EnvironmentDisplayMode.MATE))
        assertTrue(SleepCareMonitoringPolicy.shouldMonitor(true, EnvironmentDisplayMode.MATE))
        assertFalse(SleepCareMonitoringPolicy.shouldMonitor(true, EnvironmentDisplayMode.OBJECT))
    }

    @Test
    fun torchMatchesIosNormalAndMovementRules() {
        assertEquals(
            1.0,
            LampTorchLightingPolicy.maximumLevel(true, false, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(false, false, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.1,
            LampTorchLightingPolicy.maximumLevel(false, true, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(true, true, EnvironmentDisplayMode.OBJECT),
            0.0,
        )
    }
}
