package com.armsone.stand.platform

import androidx.media3.common.MimeTypes
import com.armsone.stand.model.EnvironmentDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformPoliciesTest {
    @Test
    fun horizontalDragCoversFullRadioVolumeRangeAndClamps() {
        assertEquals(0.5f, VolumeAdjustmentPolicy.HORIZONTAL_DRAG_TRAVEL_RATIO, 0f)
        assertEquals(1f, VolumeAdjustmentPolicy.level(0.5f, 100f, 400f), 0f)
        assertEquals(0f, VolumeAdjustmentPolicy.level(0.5f, -100f, 400f), 0f)
        assertEquals(1f, VolumeAdjustmentPolicy.level(0.8f, 1_000f, 400f), 0f)
        assertEquals(0f, VolumeAdjustmentPolicy.level(0.2f, -1_000f, 400f), 0f)
    }

    @Test
    fun sensorCallbackRequiresTheCurrentRunningGeneration() {
        assertTrue(DeviceSensorCallbackPolicy.shouldDeliver(4L, 4L, true))
        assertFalse(DeviceSensorCallbackPolicy.shouldDeliver(3L, 4L, true))
        assertFalse(DeviceSensorCallbackPolicy.shouldDeliver(4L, 4L, false))
    }

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
    fun cameraReadingFreshnessUsesSixtySecondMonotonicWindow() {
        assertEquals(60_000_000_000L, AmbientCameraPolicy.MaximumReadingAgeNanos)
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
    fun cameraRequiresOneSecondOfObservationBeforeCompleting() {
        val startedAt = 4_000_000_000L
        assertFalse(
            AmbientCameraPolicy.hasMinimumObservationDuration(
                startedAt,
                startedAt + AmbientCameraPolicy.MinimumObservationNanos - 1L,
            ),
        )
        assertTrue(
            AmbientCameraPolicy.hasMinimumObservationDuration(
                startedAt,
                startedAt + AmbientCameraPolicy.MinimumObservationNanos,
            ),
        )
        assertFalse(AmbientCameraPolicy.hasMinimumObservationDuration(startedAt, startedAt - 1L))
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
        assertTrue(AmbientCameraModePolicy.isRecentlyDark(reading(0.16f), now))
        assertFalse(AmbientCameraModePolicy.isRecentlyDark(reading(0.17f), now))
        assertFalse(
            AmbientCameraModePolicy.isRecentlyDark(
                reading(0.10f, AmbientCameraPolicy.MaximumReadingAgeNanos),
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

    @Test
    fun streamResolutionMapsHlsMimeTypesToApplicationM3u8() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.bsod.kr/stream?stn=mbc&ch=fm4u",
                contentTypeHeader = "application/vnd.apple.mpegurl",
            ),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.bsod.kr/stream?stn=kbs&ch=2fm",
                contentTypeHeader = "application/x-mpegURL; charset=utf-8",
            ),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.bsod.kr/stream?stn=kbs&ch=2fm",
                contentTypeHeader = "audio/x-mpegurl",
            ),
        )
    }

    @Test
    fun streamResolutionInfersM3u8ExtensionFromUrlWhenContentTypeMissingOrGeneric() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://stream.example/live/playlist.m3u8?token=xyz",
                contentTypeHeader = null,
            ),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://stream.example/live/playlist.m3u8",
                contentTypeHeader = "application/octet-stream",
            ),
        )
    }

    @Test
    fun streamResolutionMapsAudioMpegAndMp3Extension() {
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://ic.radiomonster.fm/tophits.ultra",
                contentTypeHeader = "audio/mpeg",
            ),
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://rocafmadrid.radioca.st/stream",
                contentTypeHeader = "audio/mpeg; charset=utf-8",
            ),
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.example/stream.mp3",
                contentTypeHeader = null,
            ),
        )
    }

    @Test
    fun streamResolutionFallsBackToNullForGenericOrUnknownUrls() {
        assertNull(
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.example/stream",
                contentTypeHeader = null,
            ),
        )
        assertNull(
            RadioStreamResolutionPolicy.inferMimeType(
                url = "https://radio.example/stream",
                contentTypeHeader = "application/octet-stream",
            ),
        )
    }

}
