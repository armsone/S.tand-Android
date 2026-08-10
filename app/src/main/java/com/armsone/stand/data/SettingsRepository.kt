package com.armsone.stand.data

import android.content.Context
import androidx.core.content.edit
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
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
    private val mutableSettings = MutableStateFlow(load())

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

    private fun load(): AppSettings = AppSettings(
        lampIntensity = preferences.getFloat("lampIntensity", 0.72f),
        silhouetteIntensity = preferences.getFloat("silhouetteIntensity", 0.05f),
        clockScale = preferences.getFloat("clockScale", 1f),
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
        brightnessModeThreshold = preferences.getFloat("brightnessModeThreshold", 0.35f),
        holdDurationSeconds = preferences.getFloat("holdDurationSeconds", 5f),
        fadeDurationSeconds = preferences.getFloat("fadeDurationSeconds", 30f),
        automaticDimmingEnabled = preferences.getBoolean("automaticDimmingEnabled", true),
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
        internetRadio = loadInternetRadio(),
    ).normalized()

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        preferences.getString(key, null)
            ?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } }
            ?: fallback

    private fun stringValue(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun loadInternetRadio(): InternetRadioConfiguration? {
        val url = stringValue(RADIO_URL_KEY) ?: return null
        return InternetRadioConfiguration(
            displayName = stringValue(RADIO_NAME_KEY).orEmpty(),
            streamUrl = url,
        ).normalizedOrNull()
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
            value.internetRadio?.let { radio ->
                putString(RADIO_NAME_KEY, radio.displayName)
                putString(RADIO_URL_KEY, radio.streamUrl)
            } ?: run {
                remove(RADIO_NAME_KEY)
                remove(RADIO_URL_KEY)
            }
        }
    }

    private companion object {
        const val PORTRAIT_LAYOUT_KEY = "portraitLayout"
        const val LANDSCAPE_LAYOUT_KEY = "landscapeLayout"
        const val RADIO_NAME_KEY = "internetRadioName"
        const val RADIO_URL_KEY = "internetRadioUrl"
    }
}
