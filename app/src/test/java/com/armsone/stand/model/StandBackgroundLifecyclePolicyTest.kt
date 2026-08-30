package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandBackgroundLifecyclePolicyTest {

    @Test
    fun targetModeOnBackgroundTransitionsAutomaticModeToMateDuringActiveSession() {
        val target = StandBackgroundLifecyclePolicy.targetModeOnBackground(
            preference = StandModePreference.AUTOMATIC,
            current = EnvironmentDisplayMode.OBJECT,
            isSessionActive = true,
            isTelevision = false,
        )
        assertEquals(EnvironmentDisplayMode.MATE, target)
    }

    @Test
    fun targetModeOnBackgroundPreservesFixedPreferencesAndInactiveSession() {
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            StandBackgroundLifecyclePolicy.targetModeOnBackground(
                preference = StandModePreference.OBJECT,
                current = EnvironmentDisplayMode.OBJECT,
                isSessionActive = true,
                isTelevision = false,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.MATE,
            StandBackgroundLifecyclePolicy.targetModeOnBackground(
                preference = StandModePreference.MATE,
                current = EnvironmentDisplayMode.MATE,
                isSessionActive = true,
                isTelevision = false,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            StandBackgroundLifecyclePolicy.targetModeOnBackground(
                preference = StandModePreference.AUTOMATIC,
                current = EnvironmentDisplayMode.OBJECT,
                isSessionActive = false,
                isTelevision = false,
            ),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            StandBackgroundLifecyclePolicy.targetModeOnBackground(
                preference = StandModePreference.AUTOMATIC,
                current = EnvironmentDisplayMode.OBJECT,
                isSessionActive = true,
                isTelevision = true,
            ),
        )
    }

    @Test
    fun isBackgroundMonitoringEligibleReturnsTrueOnlyWhenAllPreconditionsAreMet() {
        val safeSettings = AppSettings(
            backgroundModeEnabled = true,
            soundSensingEnabled = true,
        )

        assertTrue(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )
    }

    @Test
    fun isBackgroundMonitoringEligibleReturnsFalseForIneligibleConditions() {
        val safeSettings = AppSettings(
            backgroundModeEnabled = true,
            soundSensingEnabled = true,
        )

        // Background mode disabled by user
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings.copy(backgroundModeEnabled = false),
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // Object mode (not Mate mode)
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.OBJECT,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // Missing microphone permission
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = false,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // Sound sensing disabled
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = false,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // Radio active
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = true,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // External music active
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = true,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = false,
            ),
        )

        // Battery protection active
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = true,
                isTelevision = false,
            ),
        )

        // Television
        assertFalse(
            StandBackgroundLifecyclePolicy.isBackgroundMonitoringEligible(
                settings = safeSettings,
                isSessionActive = true,
                environmentMode = EnvironmentDisplayMode.MATE,
                hasMicrophonePermission = true,
                soundSensingEnabled = true,
                isMonitoringSuspendedForPlayback = false,
                isRadioActive = false,
                isExternalMusicActive = false,
                isBoyisoSpeakerActive = false,
                isBatteryProtectionActive = false,
                isTelevision = true,
            ),
        )
    }
}
