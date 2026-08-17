package com.armsone.stand.data

import android.content.Context
import androidx.core.content.edit
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.CurrentExperienceMigration
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.HomeMusicChannelSelection
import com.armsone.stand.model.ScreenLayoutCodec
import com.armsone.stand.model.StandScreenLayout
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandModePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "stand_settings",
        Context.MODE_PRIVATE,
    )
    private val mutableSettings = MutableStateFlow(loadAndMigrate())

    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        mutableSettings.update { current ->
            transform(current).normalized().also { updated ->
                persist(
                    updated,
                    preservedUnreadableStrings = unreadableStringPayloads(current, updated),
                )
            }
        }
    }

    fun restoreRecommendedValues() {
        val recommended = AppSettings.Recommended
        persist(recommended)
        mutableSettings.value = recommended
    }

    private fun loadAndMigrate(): AppSettings {
        val loaded = load()
        if (booleanValue(CURRENT_EXPERIENCE_MIGRATION_KEY, false)) return loaded

        return CurrentExperienceMigration.apply(loaded).also { migrated ->
            persist(
                migrated,
                preservedUnreadableStrings = unreadableStringPayloads(loaded, migrated),
            )
            preferences.edit { putBoolean(CURRENT_EXPERIENCE_MIGRATION_KEY, true) }
        }
    }

    private fun unreadableStringPayloads(
        before: AppSettings,
        after: AppSettings,
    ): Map<String, String> = buildMap {
        preserveUnreadableLayout(PORTRAIT_LAYOUT_KEY, before.portraitLayout == after.portraitLayout)
        preserveUnreadableLayout(LANDSCAPE_LAYOUT_KEY, before.landscapeLayout == after.landscapeLayout)
        preserveUnknownEnum<ClockFontChoice>(CLOCK_FONT_KEY, before.clockFont == after.clockFont)
        preserveUnknownEnum<ClockHourMode>(CLOCK_HOUR_MODE_KEY, before.clockHourMode == after.clockHourMode)
        preserveUnknownEnum<StandDisplayTheme>(DISPLAY_THEME_KEY, before.displayTheme == after.displayTheme)
        preserveUnknownEnum<OrientationPreference>(ORIENTATION_KEY,
            before.orientationPreference == after.orientationPreference,
        )
        preserveUnknownEnum<StandModePreference>(MODE_PREFERENCE_KEY,
            before.modePreference == after.modePreference,
        )
    }

    private fun MutableMap<String, String>.preserveUnreadableLayout(key: String, unchanged: Boolean) {
        if (!unchanged) return
        val raw = stringValue(key)
        if (!raw.isNullOrBlank() && !ScreenLayoutCodec.isDecodable(raw)) put(key, raw)
    }

    private inline fun <reified T : Enum<T>> MutableMap<String, String>.preserveUnknownEnum(
        key: String,
        unchanged: Boolean,
    ) {
        if (!unchanged) return
        val raw = stringValue(key) ?: return
        if (enumValues<T>().none { it.name == raw }) put(key, raw)
    }

    private fun load(): AppSettings {
        val radioSnapshot = loadInternetRadios()
        return AppSettings(
        lampIntensity = floatValue("lampIntensity", 0.72f),
        silhouetteIntensity = floatValue("silhouetteIntensity", 0.05f),
        clockScale = floatValue("clockScale", AppSettings.DEFAULT_CLOCK_SCALE),
        clockFont = enumValue("clockFont", ClockFontChoice.TENADA),
        clockHourMode = enumValue("clockHourMode", ClockHourMode.TWELVE),
        displayTheme = enumValue("displayTheme", StandDisplayTheme.COLOR),
        portraitLayout = ScreenLayoutCodec.decodeOrDefault(
            encoded = stringValue(PORTRAIT_LAYOUT_KEY),
            fallback = StandScreenLayout.Portrait,
        ),
        landscapeLayout = ScreenLayoutCodec.decodeOrDefault(
            encoded = stringValue(LANDSCAPE_LAYOUT_KEY),
            fallback = StandScreenLayout.Landscape,
        ),
        brightnessModeThreshold = floatValue("brightnessModeThreshold", 0.4f),
        holdDurationSeconds = floatValue("holdDurationSeconds", 5f),
        fadeDurationSeconds = floatValue("fadeDurationSeconds", 30f),
        automaticDimmingEnabled = booleanValue("automaticDimmingEnabled", false),
        soundThresholdDB = floatValue("soundThresholdDB", -36f),
        recordingEnabled = booleanValue("recordingEnabled", true),
        orientationPreference = enumValue(
            "orientationPreference",
            OrientationPreference.AUTOMATIC,
        ),
        torchEnabled = booleanValue("torchEnabled", true),
        multiStimulusWakeEnabled = booleanValue("multiStimulusWakeEnabled", true),
        modePreference = enumValue("modePreference", StandModePreference.AUTOMATIC),
        ambientSensingEnabled = booleanValue("ambientSensingEnabled", true),
        cameraAmbientSensingEnabled = booleanValue(
            "cameraAmbientSensingEnabled",
            false,
        ),
        backgroundModeEnabled = booleanValue("backgroundModeEnabled", false),
        soundSensingEnabled = booleanValue("soundSensingEnabled", true),
        weatherLocationEnabled = booleanValue("weatherLocationEnabled", true),
            internetRadio = radioSnapshot.selected,
            internetRadioChannels = radioSnapshot.channels,
            selectedInternetRadioId = radioSnapshot.selected?.id,
            homeMusicChannels = listOfNotNull(
                HomeMusicChannelSelection.decode(stringValue(HOME_MUSIC_CHANNEL_0_KEY)),
                HomeMusicChannelSelection.decode(stringValue(HOME_MUSIC_CHANNEL_1_KEY)),
            ),
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        stringValue(key)
            ?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } }
            ?: fallback

    private fun floatValue(key: String, fallback: Float): Float = try {
        preferences.getFloat(key, fallback).takeIf(Float::isFinite) ?: fallback
    } catch (_: ClassCastException) {
        fallback
    }

    private fun booleanValue(key: String, fallback: Boolean): Boolean = try {
        preferences.getBoolean(key, fallback)
    } catch (_: ClassCastException) {
        fallback
    }

    private fun stringValue(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun loadInternetRadios(): RadioSnapshot {
        val channels = (0 until AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT).mapNotNull { index ->
            val url = stringValue("$RADIO_URL_PREFIX$index") ?: return@mapNotNull null
            InternetRadioConfiguration(
                displayName = stringValue("$RADIO_NAME_PREFIX$index").orEmpty(),
                streamUrl = url,
                id = stringValue("$RADIO_ID_PREFIX$index").orEmpty(),
            ).normalizedOrNull()
        }.ifEmpty {
            val legacyUrl = stringValue(RADIO_URL_KEY)
            listOfNotNull(
                legacyUrl?.let { url ->
                    InternetRadioConfiguration(
                        displayName = stringValue(RADIO_NAME_KEY).orEmpty(),
                        streamUrl = url,
                        id = LEGACY_RADIO_ID,
                    ).normalizedOrNull()
                },
            )
        }
        val selectedID = stringValue(SELECTED_RADIO_ID_KEY)
        return RadioSnapshot(
            channels = channels,
            selected = channels.firstOrNull { it.id == selectedID } ?: channels.firstOrNull(),
        )
    }

    private fun persist(
        value: AppSettings,
        preservedUnreadableStrings: Map<String, String> = emptyMap(),
    ) {
        preferences.edit {
            putFloat("lampIntensity", value.lampIntensity)
            putFloat("silhouetteIntensity", value.silhouetteIntensity)
            putFloat("clockScale", value.clockScale)
            putString(CLOCK_FONT_KEY, value.clockFont.name)
            putString(CLOCK_HOUR_MODE_KEY, value.clockHourMode.name)
            putString(DISPLAY_THEME_KEY, value.displayTheme.name)
            putString(PORTRAIT_LAYOUT_KEY, ScreenLayoutCodec.encode(value.portraitLayout))
            putString(LANDSCAPE_LAYOUT_KEY, ScreenLayoutCodec.encode(value.landscapeLayout))
            preservedUnreadableStrings.forEach(::putString)
            putFloat("brightnessModeThreshold", value.brightnessModeThreshold)
            putFloat("holdDurationSeconds", value.holdDurationSeconds)
            putFloat("fadeDurationSeconds", value.fadeDurationSeconds)
            putBoolean("automaticDimmingEnabled", value.automaticDimmingEnabled)
            putFloat("soundThresholdDB", value.soundThresholdDB)
            putBoolean("recordingEnabled", value.recordingEnabled)
            putString(ORIENTATION_KEY, value.orientationPreference.name)
            putBoolean("torchEnabled", value.torchEnabled)
            putBoolean("multiStimulusWakeEnabled", value.multiStimulusWakeEnabled)
            putString(MODE_PREFERENCE_KEY, value.modePreference.name)
            putBoolean("ambientSensingEnabled", value.ambientSensingEnabled)
            putBoolean("cameraAmbientSensingEnabled", value.cameraAmbientSensingEnabled)
            putBoolean("backgroundModeEnabled", value.backgroundModeEnabled)
            putBoolean("soundSensingEnabled", value.soundSensingEnabled)
            putBoolean("weatherLocationEnabled", value.weatherLocationEnabled)
            value.internetRadio?.let { radio ->
                putString(RADIO_NAME_KEY, radio.displayName)
                putString(RADIO_URL_KEY, radio.streamUrl)
            } ?: run {
                remove(RADIO_NAME_KEY)
                remove(RADIO_URL_KEY)
            }
            putString(SELECTED_RADIO_ID_KEY, value.selectedInternetRadioId)
            putString(HOME_MUSIC_CHANNEL_0_KEY, value.homeMusicChannels.getOrNull(0)?.encoded())
            putString(HOME_MUSIC_CHANNEL_1_KEY, value.homeMusicChannels.getOrNull(1)?.encoded())
            repeat(AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) { index ->
                val channel = value.internetRadioChannels.getOrNull(index)
                if (channel == null) {
                    remove("$RADIO_ID_PREFIX$index")
                    remove("$RADIO_NAME_PREFIX$index")
                    remove("$RADIO_URL_PREFIX$index")
                } else {
                    putString("$RADIO_ID_PREFIX$index", channel.id)
                    putString("$RADIO_NAME_PREFIX$index", channel.displayName)
                    putString("$RADIO_URL_PREFIX$index", channel.streamUrl)
                }
            }
        }
    }

    private data class RadioSnapshot(
        val channels: List<InternetRadioConfiguration>,
        val selected: InternetRadioConfiguration?,
    )

    private companion object {
        const val PORTRAIT_LAYOUT_KEY = "portraitLayout"
        const val LANDSCAPE_LAYOUT_KEY = "landscapeLayout"
        const val CLOCK_FONT_KEY = "clockFont"
        const val CLOCK_HOUR_MODE_KEY = "clockHourMode"
        const val DISPLAY_THEME_KEY = "displayTheme"
        const val ORIENTATION_KEY = "orientationPreference"
        const val MODE_PREFERENCE_KEY = "modePreference"
        const val RADIO_NAME_KEY = "internetRadioName"
        const val RADIO_URL_KEY = "internetRadioUrl"
        const val RADIO_ID_PREFIX = "internetRadioId."
        const val RADIO_NAME_PREFIX = "internetRadioName."
        const val RADIO_URL_PREFIX = "internetRadioUrl."
        const val SELECTED_RADIO_ID_KEY = "selectedInternetRadioId"
        const val HOME_MUSIC_CHANNEL_0_KEY = "homeMusicChannel.0"
        const val HOME_MUSIC_CHANNEL_1_KEY = "homeMusicChannel.1"
        const val LEGACY_RADIO_ID = "legacy-primary-radio"
        const val CURRENT_EXPERIENCE_MIGRATION_KEY = "currentExperienceDefaults.v1"
    }
}
