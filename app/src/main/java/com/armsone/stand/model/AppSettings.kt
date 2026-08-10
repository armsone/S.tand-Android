package com.armsone.stand.model

import kotlin.math.ln

enum class ClockFontChoice(val displayName: String) {
    SYSTEM_ROUNDED("시스템 둥근체"),
    PRETENDARD("프리텐다드"),
    KAKAO_BIG_SANS("카카오 Big Sans"),
    NANUM_GOTHIC("나눔고딕"),
    TENADA("태나다"),
    BLACK_HAN_SANS("검은고딕"),
    DO_HYEON("도현"),
    PAPERLOGY_BOLD("페이퍼로지 Bold"),
    NEXON_LV1_GOTHIC("넥슨 Lv.1 고딕"),
    POPPINS("Poppins"),
}

enum class OrientationPreference(val title: String) {
    AUTOMATIC("기기 설정 따르기"),
    PORTRAIT("세로 고정"),
    LANDSCAPE("가로 고정"),
}

enum class ClockHourMode { TWELVE, TWENTY_FOUR }

enum class StandDisplayTheme { COLOR, GRAYSCALE }

enum class StandModePreference(val title: String) {
    AUTOMATIC("자동"),
    OBJECT("오브제 유지"),
    MATE("매이트 유지"),
}

enum class LampPhase { OFF, HOLDING, FADING }

enum class EnvironmentDisplayMode { MATE, OBJECT }

enum class StandExperienceMode(val title: String) {
    OBJECT("오브제 모드"),
    MATE("매이트 모드"),
    STARTLED("화들짝 모드"),
}

data class AppSettings(
    val lampIntensity: Float = 0.72f,
    val silhouetteIntensity: Float = 0.05f,
    val clockScale: Float = 1f,
    val clockFont: ClockFontChoice = ClockFontChoice.TENADA,
    val clockHourMode: ClockHourMode = ClockHourMode.TWELVE,
    val displayTheme: StandDisplayTheme = StandDisplayTheme.COLOR,
    val portraitLayout: StandScreenLayout = StandScreenLayout.Portrait,
    val landscapeLayout: StandScreenLayout = StandScreenLayout.Landscape,
    val brightnessModeThreshold: Float = 0.35f,
    val holdDurationSeconds: Float = 5f,
    val fadeDurationSeconds: Float = 30f,
    val automaticDimmingEnabled: Boolean = true,
    val soundThresholdDB: Float = -36f,
    val recordingEnabled: Boolean = true,
    val orientationPreference: OrientationPreference = OrientationPreference.AUTOMATIC,
    val torchEnabled: Boolean = true,
    val multiStimulusWakeEnabled: Boolean = true,
    val modePreference: StandModePreference = StandModePreference.AUTOMATIC,
    val ambientSensingEnabled: Boolean = true,
    val cameraAmbientSensingEnabled: Boolean = false,
    val internetRadio: InternetRadioConfiguration? = null,
) {
    fun normalized(): AppSettings = copy(
        lampIntensity = lampIntensity.coerceIn(0.15f, 1f),
        silhouetteIntensity = silhouetteIntensity.coerceIn(0.005f, 0.2f),
        clockScale = clockScale.coerceIn(0.7f, 1.35f),
        portraitLayout = portraitLayout.copy(),
        landscapeLayout = landscapeLayout.copy(),
        brightnessModeThreshold = brightnessModeThreshold.coerceIn(0f, 1f),
        holdDurationSeconds = holdDurationSeconds.coerceIn(5f, 300f),
        fadeDurationSeconds = fadeDurationSeconds.coerceIn(0.1f, 120f),
        soundThresholdDB = soundThresholdDB.coerceIn(-55f, -18f),
        internetRadio = internetRadio?.normalizedOrNull(),
    )

    companion object {
        val Recommended = AppSettings()
    }
}

/** Maps an Android light sensor's very wide lux range onto the 0...1 UI rail. */
object AmbientLightPolicy {
    private const val REFERENCE_LUX = 1_000.0

    fun normalizedLux(lux: Float): Float =
        (ln(1.0 + lux.coerceAtLeast(0f)) / ln(1.0 + REFERENCE_LUX))
            .toFloat()
            .coerceIn(0f, 1f)

    fun targetMode(
        preference: StandModePreference,
        normalizedBrightness: Float,
        threshold: Float,
    ): EnvironmentDisplayMode = when (preference) {
        StandModePreference.OBJECT -> EnvironmentDisplayMode.OBJECT
        StandModePreference.MATE -> EnvironmentDisplayMode.MATE
        StandModePreference.AUTOMATIC -> {
            if (normalizedBrightness < threshold) {
                EnvironmentDisplayMode.MATE
            } else {
                EnvironmentDisplayMode.OBJECT
            }
        }
    }

    fun confirmationDelayMillis(
        current: EnvironmentDisplayMode,
        target: EnvironmentDisplayMode,
    ): Long = when {
        current == target -> 0L
        current == EnvironmentDisplayMode.OBJECT -> 20_000L
        else -> 35_000L
    }
}

object BatteryProtectionPolicy {
    fun shouldProtect(levelFraction: Float?, isCharging: Boolean): Boolean =
        levelFraction != null && levelFraction <= 0.2f && !isCharging
}
