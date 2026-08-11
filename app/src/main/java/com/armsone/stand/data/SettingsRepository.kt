package com.armsone.stand.data

import android.content.Context
import androidx.core.content.edit
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.CurrentExperienceMigration
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.InternetRadioConfiguration
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
            transform(current).normalized().also(::persist)
        }
    }

    fun restoreRecommendedValues() {
        val recommended = AppSettings.Recommended
        persist(recommended)
        mutableSettings.value = recommended
    }

    private fun loadAndMigrate(): AppSettings {
        val loaded = load()
        if (preferences.getBoolean(CURRENT_EXPERIENCE_MIGRATION_KEY, false)) return loaded

        return CurrentExperienceMigration.apply(loaded).also { migrated ->
            persist(migrated)
            preferences.edit { putBoolean(CURRENT_EXPERIENCE_MIGRATION_KEY, true) }
        }
    }

    private fun load(): AppSettings {
        val radioSnapshot = loadInternetRadios()
        return AppSettings(
        lampIntensity = preferences.getFloat("lampIntensity", 0.72f),
        silhouetteIntensity = preferences.getFloat("silhouetteIntensity", 0.05f),
        clockScale = preferences.getFloat("clockScale", AppSettings.DEFAULT_CLOCK_SCALE),
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
        brightnessModeThreshold = preferences.getFloat("brightnessModeThreshold", 0.4f),
        holdDurationSeconds = preferences.getFloat("holdDurationSeconds", 5f),
        fadeDurationSeconds = preferences.getFloat("fadeDurationSeconds", 30f),
        automaticDimmingEnabled = preferences.getBoolean("automaticDimmingEnabled", false),
        soundThresholdDB = preferences.getFloat("soundThresholdDB", -36f),
        recordingEnabled = preferences.getBoolean("recordingEnabled", true),
        orientationPreference = enumValue(
            "orientationPreference",
            OrientationPreference.AUTOMATIC,
        ),
        torchEnabled = preferences.getBoolean("torchEnabled", true),
        multiStimulusWakeEnabled = preferences.getBoolean("multiStimulusWakeEnabled", true),
        modePreference = enumValue("modePreference", StandModePreference.AUTOMATIC),
        ambientSensingEnabled = preferences.getBoolean("ambientSensingEnabled", true),
        cameraAmbientSensingEnabled = preferences.getBoolean(
            "cameraAmbientSensingEnabled",
            false,
        ),
        soundSensingEnabled = preferences.getBoolean("soundSensingEnabled", true),
        weatherLocationEnabled = preferences.getBoolean("weatherLocationEnabled", true),
            internetRadio = radioSnapshot.selected,
            internetRadioChannels = radioSnapshot.channels,
            selectedInternetRadioId = radioSnapshot.selected?.id,
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        preferences.getString(key, null)
            ?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } }
            ?: fallback

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

    private fun persist(value: AppSettings) {
        preferences.edit {
            putFloat("lampIntensity", value.lampIntensity)
            putFloat("silhouetteIntensity", value.silhouetteIntensity)
            putFloat("clockScale", value.clockScale)
            putString("clockFont", value.clockFont.name)
            putString("clockHourMode", value.clockHourMode.name)
            putString("displayTheme", value.displayTheme.name)
            putString(PORTRAIT_LAYOUT_KEY, ScreenLayoutCodec.encode(value.portraitLayout))
            putString(LANDSCAPE_LAYOUT_KEY, ScreenLayoutCodec.encode(value.landscapeLayout))
            putFloat("brightnessModeThreshold", value.brightnessModeThreshold)
            putFloat("holdDurationSeconds", value.holdDurationSeconds)
            putFloat("fadeDurationSeconds", value.fadeDurationSeconds)
            putBoolean("automaticDimmingEnabled", value.automaticDimmingEnabled)
            putFloat("soundThresholdDB", value.soundThresholdDB)
            putBoolean("recordingEnabled", value.recordingEnabled)
            putString("orientationPreference", value.orientationPreference.name)
            putBoolean("torchEnabled", value.torchEnabled)
            putBoolean("multiStimulusWakeEnabled", value.multiStimulusWakeEnabled)
            putString("modePreference", value.modePreference.name)
            putBoolean("ambientSensingEnabled", value.ambientSensingEnabled)
            putBoolean("cameraAmbientSensingEnabled", value.cameraAmbientSensingEnabled)
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
        const val RADIO_NAME_KEY = "internetRadioName"
        const val RADIO_URL_KEY = "internetRadioUrl"
        const val RADIO_ID_PREFIX = "internetRadioId."
        const val RADIO_NAME_PREFIX = "internetRadioName."
        const val RADIO_URL_PREFIX = "internetRadioUrl."
        const val SELECTED_RADIO_ID_KEY = "selectedInternetRadioId"
        const val LEGACY_RADIO_ID = "legacy-primary-radio"
        const val CURRENT_EXPERIENCE_MIGRATION_KEY = "currentExperienceDefaults.v1"
    }
}
