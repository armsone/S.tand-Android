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

enum class StandDisplayTheme(val title: String) {
    COLOR("오렌지"),
    GRAYSCALE("그레이"),
    MIDNIGHT("미드나이트"),
    SAGE("세이지");

    fun next(): StandDisplayTheme = entries[(ordinal + 1) % entries.size]
}

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
    val clockScale: Float = DEFAULT_CLOCK_SCALE,
    val clockFont: ClockFontChoice = ClockFontChoice.TENADA,
    val clockHourMode: ClockHourMode = ClockHourMode.TWELVE,
    val displayTheme: StandDisplayTheme = StandDisplayTheme.COLOR,
    val portraitLayout: StandScreenLayout = StandScreenLayout.Portrait,
    val landscapeLayout: StandScreenLayout = StandScreenLayout.Landscape,
    val brightnessModeThreshold: Float = 0.4f,
    val holdDurationSeconds: Float = 5f,
    val fadeDurationSeconds: Float = 30f,
    val automaticDimmingEnabled: Boolean = false,
    val soundThresholdDB: Float = -36f,
    val recordingEnabled: Boolean = true,
    val orientationPreference: OrientationPreference = OrientationPreference.AUTOMATIC,
    val torchEnabled: Boolean = true,
    val multiStimulusWakeEnabled: Boolean = true,
    val modePreference: StandModePreference = StandModePreference.AUTOMATIC,
    val ambientSensingEnabled: Boolean = true,
    val cameraAmbientSensingEnabled: Boolean = false,
    val backgroundModeEnabled: Boolean = true,
    val soundSensingEnabled: Boolean = true,
    val weatherLocationEnabled: Boolean = true,
    val internetRadio: InternetRadioConfiguration? = null,
    val internetRadioChannels: List<InternetRadioConfiguration> = emptyList(),
    val selectedInternetRadioId: String? = null,
    val homeMusicChannels: List<HomeMusicChannelSelection> = listOf(
        HomeMusicChannelSelection.Spotify,
        HomeMusicChannelSelection.YouTubeMusic,
    ),
) {
    fun normalized(): AppSettings {
        val normalizedChannels = buildList {
            internetRadioChannels.forEach { channel ->
                channel.normalizedOrNull()?.let(::add)
            }
            internetRadio?.normalizedOrNull()?.let(::add)
        }.distinctBy { it.id }.take(MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT)
        val selected = normalizedChannels.firstOrNull { it.id == selectedInternetRadioId }
            ?: internetRadio?.normalizedOrNull()?.let { legacy ->
                normalizedChannels.firstOrNull { it.id == legacy.id }
            }
            ?: normalizedChannels.firstOrNull()
        return copy(
            lampIntensity = lampIntensity.coerceIn(0f, 1f),
            silhouetteIntensity = silhouetteIntensity.coerceIn(0.005f, 0.2f),
            clockScale = clockScale.coerceIn(
                HomeClockScalePolicy.MINIMUM_SCALE,
                HomeClockScalePolicy.MAXIMUM_SCALE,
            ),
            portraitLayout = portraitLayout.copy(),
            landscapeLayout = landscapeLayout.copy(),
            brightnessModeThreshold = brightnessModeThreshold.coerceIn(0f, 1f),
            holdDurationSeconds = holdDurationSeconds.coerceIn(5f, 300f),
            fadeDurationSeconds = fadeDurationSeconds.coerceIn(0.1f, 120f),
            soundThresholdDB = soundThresholdDB.coerceIn(-55f, -18f),
            clockHourMode = ClockHourMode.TWELVE,
            orientationPreference = OrientationPreference.AUTOMATIC,
            internetRadio = selected,
            internetRadioChannels = normalizedChannels,
            selectedInternetRadioId = selected?.id,
            homeMusicChannels = HomeMusicChannelPolicy.normalized(
                requested = homeMusicChannels,
                radioChannels = normalizedChannels,
            ),
        )
    }

    fun moveHomeMusicChannel(fromIndex: Int, toIndex: Int): AppSettings = copy(
        homeMusicChannels = HomeMusicChannelPolicy.moving(
            current = homeMusicChannels,
            fromIndex = fromIndex,
            toIndex = toIndex,
            radioChannels = internetRadioChannels,
        ),
    )

    companion object {
        const val DEFAULT_CLOCK_SCALE = 1.0059053f
        const val MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT = 4
        val Recommended = AppSettings()
    }
}

/** One-time alignment with the simplified iOS 1.0.0 (0.23.3) experience. */
object CurrentExperienceMigration {
    fun apply(previous: AppSettings): AppSettings = previous.copy(
        clockScale = AppSettings.DEFAULT_CLOCK_SCALE,
        clockHourMode = ClockHourMode.TWELVE,
        portraitLayout = StandScreenLayout.Portrait,
        landscapeLayout = StandScreenLayout.Landscape,
        brightnessModeThreshold = 0.4f,
        holdDurationSeconds = 5f,
        automaticDimmingEnabled = false,
        orientationPreference = OrientationPreference.AUTOMATIC,
        torchEnabled = true,
    ).normalized()
}

/** Moves the former default bottom-control order to the iOS 0.32.5 order without overriding custom orders. */
object LatestControlOrderMigration {
    private val PreviousDefaultOrder = listOf(
        StandControlKind.RECORDINGS,
        StandControlKind.SETTINGS,
        StandControlKind.BOYISO,
    )

    fun apply(previous: AppSettings): AppSettings = previous.copy(
        portraitLayout = migrated(previous.portraitLayout),
        landscapeLayout = migrated(previous.landscapeLayout),
    ).normalized()

    private fun migrated(layout: StandScreenLayout): StandScreenLayout =
        if (layout.controlOrder == PreviousDefaultOrder) {
            layout.copy(controlOrder = StandControlKind.DefaultOrder)
        } else {
            layout
        }
}

/** Applies the representative iPhone landscape composition once while preserving every other choice. */
object LatestLandscapeLayoutMigration {
    fun apply(previous: AppSettings): AppSettings = previous.copy(
        landscapeLayout = StandScreenLayout.Landscape,
    ).normalized()
}

/** Applies the overnight-safe default of background Mate monitoring once for existing installations. */
object BackgroundMateMonitoringMigration {
    fun apply(previous: AppSettings): AppSettings = previous.copy(
        backgroundModeEnabled = true,
    ).normalized()
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
    fun normalizedLevel(level: Int, scale: Int, isPresent: Boolean): Float? =
        if (isPresent && level >= 0 && scale > 0) {
            (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    fun shouldProtect(levelFraction: Float?, isCharging: Boolean): Boolean =
        levelFraction != null && levelFraction <= 0.2f && !isCharging

    fun shouldClearProtection(wasProtecting: Boolean, shouldProtectNow: Boolean): Boolean =
        wasProtecting && !shouldProtectNow
}
