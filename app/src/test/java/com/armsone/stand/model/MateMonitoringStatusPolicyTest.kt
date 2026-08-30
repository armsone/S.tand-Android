package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MateMonitoringStatusPolicyTest {

    @Test
    fun returnsNullWhenSessionInactiveOrObjectMode() {
        // Session inactive
        assertNull(
            MateMonitoringStatusPolicy.evaluate(
                isSessionActive = false,
                environmentMode = EnvironmentDisplayMode.MATE,
                isTelevision = false,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                audioRunning = true,
                audioErrorMessage = null,
                isStarting = false,
                isWritingClip = false,
                noiseCalibrationProgress = 1.0f,
                isSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                batteryProtectionActive = false,
            ),
        )

        // Object mode
        assertNull(
            MateMonitoringStatusPolicy.evaluate(
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.OBJECT,
                isTelevision = false,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                audioRunning = true,
                audioErrorMessage = null,
                isStarting = false,
                isWritingClip = false,
                noiseCalibrationProgress = 1.0f,
                isSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                batteryProtectionActive = false,
            ),
        )

        // Television
        assertNull(
            MateMonitoringStatusPolicy.evaluate(
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                isTelevision = true,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                audioRunning = true,
                audioErrorMessage = null,
                isStarting = false,
                isWritingClip = false,
                noiseCalibrationProgress = 1.0f,
                isSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                batteryProtectionActive = false,
            ),
        )
    }

    @Test
    fun returnsMicrophonePermissionRequiredWhenPermissionMissing() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = false,
            soundSensingEnabled = true,
            audioRunning = false,
            audioErrorMessage = null,
            isStarting = false,
            isWritingClip = false,
            noiseCalibrationProgress = 0f,
            isSuspendedForPlayback = false,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.PERMISSION_REQUIRED, status)
        assertEquals("마이크 권한 필요", status?.displayText)
    }

    @Test
    fun returnsFailedToStartWhenAudioErrorMessageIsPresent() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = true,
            soundSensingEnabled = true,
            audioRunning = false,
            audioErrorMessage = "AudioRecord initialization failed",
            isStarting = false,
            isWritingClip = false,
            noiseCalibrationProgress = 0f,
            isSuspendedForPlayback = false,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.INITIALIZATION_FAILED, status)
        assertEquals("감시를 시작하지 못했어요", status?.displayText)
    }

    @Test
    fun returnsSuspendedWhenSuspensionConditionsMet() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = true,
            soundSensingEnabled = true,
            audioRunning = false,
            audioErrorMessage = null,
            isStarting = false,
            isWritingClip = false,
            noiseCalibrationProgress = 0f,
            isSuspendedForPlayback = true,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.SUSPENDED, status)
        assertEquals("감시 일시 중지", status?.displayText)
    }

    @Test
    fun returnsRecordingClipWhenWritingClip() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = true,
            soundSensingEnabled = true,
            audioRunning = true,
            audioErrorMessage = null,
            isStarting = false,
            isWritingClip = true,
            noiseCalibrationProgress = 1.0f,
            isSuspendedForPlayback = false,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.RECORDING, status)
        assertEquals("소리 저장 중", status?.displayText)
    }

    @Test
    fun returnsCalibratingWhenCalibrationProgressUnderOne() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = true,
            soundSensingEnabled = true,
            audioRunning = true,
            audioErrorMessage = null,
            isStarting = false,
            isWritingClip = false,
            noiseCalibrationProgress = 0.5f,
            isSuspendedForPlayback = false,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.CALIBRATING, status)
        assertEquals("방 소리 익히는 중", status?.displayText)
    }

    @Test
    fun returnsActiveWhenMonitoringNormally() {
        val status = MateMonitoringStatusPolicy.evaluate(
            isSessionActive = true,
            environmentMode = EnvironmentDisplayMode.MATE,
            isTelevision = false,
            hasMicrophonePermission = true,
            soundSensingEnabled = true,
            audioRunning = true,
            audioErrorMessage = null,
            isStarting = false,
            isWritingClip = false,
            noiseCalibrationProgress = 1.0f,
            isSuspendedForPlayback = false,
            isRadioActive = false,
            isExternalMusicActive = false,
            isBoyisoSpeakerActive = false,
            batteryProtectionActive = false,
        )
        assertEquals(MateMonitoringStatus.MONITORING, status)
        assertEquals("소리 감시 중", status?.displayText)
    }
}
