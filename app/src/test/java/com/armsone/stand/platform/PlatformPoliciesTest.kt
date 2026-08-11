package com.armsone.stand.platform

import com.armsone.stand.model.EnvironmentDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformPoliciesTest {
    @Test
    fun objectModeKeepsOnlyAmbientMonitoringWhileMateEnablesSleepCare() {
        assertEquals(
            DeviceSensorMonitoringMode.STOPPED,
            DeviceSensorMonitoringPolicy.mode(false, true, EnvironmentDisplayMode.MATE),
        )
        assertEquals(
            DeviceSensorMonitoringMode.STOPPED,
            DeviceSensorMonitoringPolicy.mode(true, false, EnvironmentDisplayMode.MATE),
        )
        assertEquals(
            DeviceSensorMonitoringMode.AMBIENT_ONLY,
            DeviceSensorMonitoringPolicy.mode(true, true, EnvironmentDisplayMode.OBJECT),
        )
        assertEquals(
            DeviceSensorMonitoringMode.SLEEP_CARE,
            DeviceSensorMonitoringPolicy.mode(true, true, EnvironmentDisplayMode.MATE),
        )
    }

    @Test
    fun cameraBrightnessUsesMedianAndExposureCompensation() {
        assertEquals(0.3f, AmbientCameraPolicy.median(listOf(0.9f, 0.1f, 0.3f))!!, 0f)
        assertEquals(null, AmbientCameraPolicy.median(emptyList()))
        assertEquals(
            0.5f,
            AmbientCameraPolicy.adjustedBrightness(
                averageLuma = 0.5f,
                iso = 100,
                exposureTimeNanos = 16_666_667L,
            ),
            0.001f,
        )
        assertTrue(
            AmbientCameraPolicy.adjustedBrightness(
                averageLuma = 0.5f,
                iso = 400,
                exposureTimeNanos = 33_333_334L,
            ) < 0.25f,
        )
    }

    @Test
    fun cameraReadingFreshnessUsesTwentySecondMonotonicWindow() {
        val measuredAt = 1_000_000_000L
        val reading = AmbientCameraReading(
            value = 0.15f,
            measuredAtElapsedRealtimeNanos = measuredAt,
            lensFacing = 0,
        )
        assertTrue(reading.isDark)
        assertTrue(AmbientCameraPolicy.isFresh(reading, measuredAt))
        assertTrue(
            AmbientCameraPolicy.isFresh(
                reading,
                measuredAt + AmbientCameraPolicy.MaximumReadingAgeNanos - 1L,
            ),
        )
        assertFalse(
            AmbientCameraPolicy.isFresh(
                reading,
                measuredAt + AmbientCameraPolicy.MaximumReadingAgeNanos,
            ),
        )
        assertFalse(AmbientCameraPolicy.isFresh(reading, measuredAt - 1L))
    }

    @Test
    fun cameraBrightnessUsesDarkBrightHysteresisAndFallsBackWhenStale() {
        val now = 30_000_000_000L
        fun reading(value: Float, ageNanos: Long = 0L) = AmbientCameraReading(
            value = value,
            measuredAtElapsedRealtimeNanos = now - ageNanos,
            lensFacing = 0,
        )

        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientCameraModePolicy.targetMode(
                EnvironmentDisplayMode.MATE,
                EnvironmentDisplayMode.MATE,
                reading(AmbientCameraPolicy.BrightThreshold),
                now,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.MATE,
            AmbientCameraModePolicy.targetMode(
                EnvironmentDisplayMode.MATE,
                EnvironmentDisplayMode.OBJECT,
                reading(0.22f),
                now,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.MATE,
            AmbientCameraModePolicy.targetMode(
                EnvironmentDisplayMode.OBJECT,
                EnvironmentDisplayMode.OBJECT,
                reading(AmbientCameraPolicy.DarkThreshold),
                now,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientCameraModePolicy.targetMode(
                EnvironmentDisplayMode.MATE,
                EnvironmentDisplayMode.OBJECT,
                reading(0f, AmbientCameraPolicy.MaximumReadingAgeNanos),
                now,
            ),
        )
    }

    @Test
    fun displayBrightnessFallbackNormalizesAndRejectsUnavailableValues() {
        assertEquals(0f, DisplayBrightnessPolicy.normalized(0)!!, 0f)
        assertEquals(128f / 255f, DisplayBrightnessPolicy.normalized(128)!!, 0.0001f)
        assertEquals(1f, DisplayBrightnessPolicy.normalized(400)!!, 0f)
        assertEquals(null, DisplayBrightnessPolicy.normalized(-1))
        assertEquals(null, DisplayBrightnessPolicy.normalized(100, maximumBrightness = 0))
    }

    @Test
    fun movementUsesIosThresholds() {
        assertFalse(
            DeviceMovementPolicy.detectsMovement(
                accelerationMagnitudeG = 0.159f,
                rotationMagnitudeRadiansPerSecond = 1.399f,
            ),
        )
        assertTrue(
            DeviceMovementPolicy.detectsMovement(
                accelerationMagnitudeG = 0.16f,
                rotationMagnitudeRadiansPerSecond = 0f,
            ),
        )
        assertTrue(
            DeviceMovementPolicy.detectsMovement(
                accelerationMagnitudeG = 0f,
                rotationMagnitudeRadiansPerSecond = 1.4f,
            ),
        )
        assertEquals(5f, DeviceMovementPolicy.vectorMagnitude(3f, 4f, 0f), 0.0001f)
    }

    @Test
    fun movementRefractoryWindowLastsTwoSeconds() {
        val lastMovement = 10_000_000_000L

        assertTrue(
            DeviceMovementPolicy.isOutsideRefractoryWindow(
                nowElapsedRealtimeNanos = lastMovement,
                lastMovementElapsedRealtimeNanos = null,
            ),
        )
        assertFalse(
            DeviceMovementPolicy.isOutsideRefractoryWindow(
                nowElapsedRealtimeNanos = lastMovement +
                    DeviceMovementPolicy.REFRACTORY_INTERVAL_NANOS - 1L,
                lastMovementElapsedRealtimeNanos = lastMovement,
            ),
        )
        assertTrue(
            DeviceMovementPolicy.isOutsideRefractoryWindow(
                nowElapsedRealtimeNanos = lastMovement +
                    DeviceMovementPolicy.REFRACTORY_INTERVAL_NANOS,
                lastMovementElapsedRealtimeNanos = lastMovement,
            ),
        )
    }

    @Test
    fun faceDownUsesNegativeAndroidZAndHysteresis() {
        assertFalse(DevicePosturePolicy.isFaceDown(1f, currentlyFaceDown = false))
        assertFalse(DevicePosturePolicy.isFaceDown(-0.81f, currentlyFaceDown = false))
        assertTrue(DevicePosturePolicy.isFaceDown(-0.82f, currentlyFaceDown = false))
        assertTrue(DevicePosturePolicy.isFaceDown(-0.7f, currentlyFaceDown = true))
        assertFalse(DevicePosturePolicy.isFaceDown(-0.62f, currentlyFaceDown = true))
    }

    @Test
    fun torchStrengthClampsAndQuantizesNormalizedLevels() {
        assertEquals(0, TorchStrengthPolicy.strengthLevel(0.0, 4))
        assertEquals(0, TorchStrengthPolicy.strengthLevel(Double.NaN, 4))
        assertEquals(1, TorchStrengthPolicy.strengthLevel(0.01, 4))
        assertEquals(2, TorchStrengthPolicy.strengthLevel(0.5, 4))
        assertEquals(4, TorchStrengthPolicy.strengthLevel(1.5, 4))
        assertEquals(1, TorchStrengthPolicy.strengthLevel(0.5, 1))

        assertEquals(0f, TorchStrengthPolicy.normalizedLevel(-1, 4), 0.0001f)
        assertEquals(0.5f, TorchStrengthPolicy.normalizedLevel(2, 4), 0.0001f)
        assertEquals(1f, TorchStrengthPolicy.normalizedLevel(8, 4), 0.0001f)
    }
}
