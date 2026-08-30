package com.armsone.stand.ui

import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.model.ExternalMusicPlaybackState
import com.armsone.stand.model.ExternalMusicService
import com.armsone.stand.platform.AmbientCameraState
import com.armsone.stand.model.MateMonitoringStatus
import com.armsone.stand.platform.InternetRadioState

data class WeatherUiState(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val precipitationMillimeters: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val locationName: String?,
)

data class StandUiState(
    val settings: AppSettings = AppSettings.Recommended,
    val isSessionActive: Boolean = false,
    /** Base app illumination, separate from temporary startle/fade intensity. */
    val displayBrightness: Float = settings.lampIntensity,
    val lampIntensity: Float = 0f,
    val lampPhase: LampPhase = LampPhase.OFF,
    val environmentMode: EnvironmentDisplayMode = EnvironmentDisplayMode.OBJECT,
    val experienceMode: StandExperienceMode = StandExperienceMode.OBJECT,
    val controlsVisible: Boolean = true,
    val normalizedAmbientLight: Float? = null,
    val rawAmbientLux: Float? = null,
    val ambientCameraState: AmbientCameraState = AmbientCameraState.DISABLED,
    val ambientCameraBrightness: Float? = null,
    val audioLevel: Float = 0f,
    val effectiveSoundThresholdDB: Float = -50f,
    val noiseCalibrationProgress: Float = 0f,
    val audioRunning: Boolean = false,
    val isWritingClip: Boolean = false,
    val audioMessage: String? = null,
    val hasMicrophonePermission: Boolean = false,
    val hasApproximateLocationPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val torchAvailable: Boolean = false,
    val weather: WeatherUiState? = null,
    val weatherMessage: String? = null,
    val batteryLevel: Float? = null,
    val isCharging: Boolean = false,
    val batteryProtectionActive: Boolean = false,
    val recordingCount: Int = 0,
    val recordingOperationInProgress: Boolean = false,
    val recordingOperationMessage: String? = null,
    val isFaceDown: Boolean = false,
    val internetRadioState: InternetRadioState = InternetRadioState.Idle,
    val internetRadioVolume: Float = 1f,
    val externalMusicService: ExternalMusicService? = null,
    val externalMusicPlaybackState: ExternalMusicPlaybackState = ExternalMusicPlaybackState.IDLE,
    val externalMusicMessage: String? = null,
    val monitoringStatus: MateMonitoringStatus? = null,
) {
    val monitoringStatusText: String?
        get() = monitoringStatus?.displayText
    val isExternalMusicModeActive: Boolean
        get() = externalMusicService != null
    val isDisplayDark: Boolean
        get() = isSessionActive && lampPhase == LampPhase.OFF && !controlsVisible

    val batteryText: String
        get() = batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "--%"
}
