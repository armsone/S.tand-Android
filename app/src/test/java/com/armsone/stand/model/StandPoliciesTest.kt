package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandPoliciesTest {
    @Test
    fun startleWaitsForTwoMinutesAfterMateEntry() {
        val enteredAt = 1_000L

        assertFalse(StartleActivationPolicy.canActivate(null, enteredAt + 120_000L))
        assertFalse(StartleActivationPolicy.canActivate(enteredAt, enteredAt + 119_999L))
        assertTrue(StartleActivationPolicy.canActivate(enteredAt, enteredAt + 120_000L))
    }

    @Test
    fun mateEntryDeadlineDoesNotMoveUntilObjectRoundTrip() {
        val enteredAt = StartleActivationPolicy.entryTimeAfterTransition(
            previous = EnvironmentDisplayMode.OBJECT,
            current = EnvironmentDisplayMode.MATE,
            existingEntryTimeMillis = null,
            nowMillis = 1_000L,
        )
        assertEquals(1_000L, enteredAt)
        assertEquals(
            enteredAt,
            StartleActivationPolicy.entryTimeAfterTransition(
                previous = EnvironmentDisplayMode.MATE,
                current = EnvironmentDisplayMode.MATE,
                existingEntryTimeMillis = enteredAt,
                nowMillis = 30_000L,
            ),
        )
        assertEquals(
            null,
            StartleActivationPolicy.entryTimeAfterTransition(
                previous = EnvironmentDisplayMode.MATE,
                current = EnvironmentDisplayMode.OBJECT,
                existingEntryTimeMillis = enteredAt,
                nowMillis = 31_000L,
            ),
        )
    }

    @Test
    fun homeClockScaleMatchesTheIosPinchRange() {
        assertEquals(0.7f, HomeClockScalePolicy.scaled(1f, 0.1f), 0f)
        assertEquals(1.35f, HomeClockScalePolicy.scaled(1f, 3f), 0f)
        assertEquals(1.2f, HomeClockScalePolicy.scaled(0.8f, 1.5f), 0.0001f)
        assertEquals(1.7f, HomeClockScalePolicy.clamped(3f), 0f)
    }

    @Test
    fun faceDownBlackoutRequiresActiveSessionAndAlwaysBlocksTorch() {
        assertFalse(FaceDownLightingPolicy.shouldBlackout(false, true))
        assertTrue(FaceDownLightingPolicy.shouldBlackout(true, true))
        assertFalse(FaceDownLightingPolicy.shouldBlackout(true, false))
        assertFalse(FaceDownLightingPolicy.allowsTorch(true))
        assertTrue(FaceDownLightingPolicy.allowsTorch(false))
    }

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
    fun torchUsesTheIosTenPercentFallbackWhenDisabledDuringMateStartle() {
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(true, false, true, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(false, false, true, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.1,
            LampTorchLightingPolicy.maximumLevel(false, true, true, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            1.0,
            LampTorchLightingPolicy.maximumLevel(true, true, true, EnvironmentDisplayMode.MATE),
            0.0,
        )
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(true, true, true, EnvironmentDisplayMode.OBJECT),
            0.0,
        )
        assertEquals(
            0.0,
            LampTorchLightingPolicy.maximumLevel(true, true, false, EnvironmentDisplayMode.MATE),
            0.0,
        )
    }
}
