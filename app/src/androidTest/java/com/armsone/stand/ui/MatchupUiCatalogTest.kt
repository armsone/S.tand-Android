package com.armsone.stand.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import com.armsone.stand.boyiso.BoyisoConfiguration
import com.armsone.stand.boyiso.BoyisoState
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.ui.theme.STandTheme
import java.io.File
import java.time.LocalDateTime
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MatchupUiCatalogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setCatalogProfile() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.KOREA)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        // A previous catalog pass ends after a landscape capture. Settle the real
        // window back to portrait before each test so the first composition never
        // inherits landscape constraints in a portrait screenshot.
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    @After
    fun restoreOrientation() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun captureFirstLaunchPermissions() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        catalogContent {
            CatalogHome(state = StandUiState(), showPermissionReview = true)
        }
        capture("first_launch_permissions")
    }

    @Test
    fun capture00HomePortraitAndLandscape() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        catalogContent { CatalogHome(state = homeState()) }
        capture("home_portrait")

        setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        capture("home_landscape")
    }

    @Test
    fun captureHomeEditor() {
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        catalogContent {
            ScreenEditorScreen(
                state = homeState(),
                layout = AppSettings.Recommended.portraitLayout,
                clockFont = ClockFontChoice.TENADA,
                clockHourMode = ClockHourMode.TWELVE,
                isPortrait = true,
                onLayoutChange = {},
                onClockFontChange = {},
                onClockHourModeChange = {},
                onManageRadios = {},
                onSave = { _, _, _ -> },
                onCancel = {},
                catalogNow = CATALOG_NOW,
            )
        }
        capture("home_editor")
    }

    @Test
    fun captureBoyisoSetup() {
        catalogContent {
            BoyisoScreen(
                state = BoyisoState(configuration = BoyisoConfiguration()),
                invitationUri = null,
                onUpdateConfiguration = {},
                onCreateRoom = {},
                onScanInvitation = {},
                onShareInvitation = {},
                onStart = {},
                onLeaveRoom = {},
                onTokTok = {},
                onBack = {},
            )
        }
        capture("boyiso_setup")
    }

    @Test
    fun captureSettingsStates() {
        var state by mutableStateOf(settingsState())
        catalogContent(displayTheme = state.settings.displayTheme) {
            CatalogSettings(
                state = state,
                onUpdate = { transform ->
                    state = state.copy(settings = transform(state.settings))
                },
            )
        }
        capture("settings_top")

        composeRule.onNodeWithText("미드나이트").performClick()
        composeRule.waitForIdle()
        capture("settings_midnight_theme")

        scrollUntilText("인터넷 라디오")
        capture("settings_lower_sections")
    }

    @Test
    fun captureClockFontOptions() {
        catalogContent(displayTheme = StandDisplayTheme.MIDNIGHT) {
            ClockFontOptionsScreen(
                selectedFont = ClockFontChoice.TENADA,
                onFontSelected = {},
                onBack = {},
            )
        }
        capture("clock_font_options")
    }

    @Test
    fun captureFontLicenses() {
        catalogContent {
            FontLicensesScreen(onOpenLicense = {}, onBack = {})
        }
        capture("font_licenses")
    }

    @Test
    fun captureRadioEditorAndDeleteConfirmation() {
        catalogContent(displayTheme = StandDisplayTheme.MIDNIGHT) {
            CatalogSettings(state = settingsState(), onUpdate = {})
        }
        scrollUntilText("인터넷 라디오")
        clickDisplayedContentDescription("수정")
        composeRule.waitForIdle()
        scrollUntilText("채널 수정")
        capture("radio_channel_editor")
        scrollUntilText("삭제")
        clickDisplayedText("삭제")
        composeRule.waitForIdle()
        capture("radio_delete_confirmation")
    }

    @Test
    fun captureRestoreConfirmation() {
        catalogContent {
            CatalogSettings(state = settingsState(), onUpdate = {})
        }
        scrollUntilText("추천 설정 복원")
        composeRule.onNodeWithText("추천 설정 복원", substring = true).performClick()
        composeRule.waitForIdle()
        capture("restore_confirmation")
    }

    private fun catalogContent(
        displayTheme: StandDisplayTheme = StandDisplayTheme.COLOR,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CatalogProfile {
                STandTheme(displayTheme = displayTheme, content = content)
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(id: String) {
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("matchup-ui-catalog"))
        captureCatalogScreenshot(composeRule.activity, directory, id)
    }

    private fun scrollUntilText(text: String) {
        repeat(10) {
            val nodes = composeRule.onAllNodesWithText(text, substring = true)
            if (nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }) return
            composeRule.onRoot().performTouchInput {
                swipe(start = bottomCenter, end = topCenter, durationMillis = 1_000)
            }
            composeRule.waitForIdle()
        }
        error("No displayed node contains after scrolling: $text")
    }

    private fun clickDisplayedText(text: String) {
        val nodes = composeRule.onAllNodesWithText(text, substring = true)
        nodes.fetchSemanticsNodes().indices
            .firstOrNull { nodes[it].isDisplayed() }
            ?.let { nodes[it].performClick() }
            ?: error("No displayed node contains: $text")
    }

    private fun clickDisplayedContentDescription(description: String) {
        val nodes = composeRule.onAllNodesWithContentDescription(description)
        nodes.fetchSemanticsNodes().indices
            .firstOrNull { nodes[it].isDisplayed() }
            ?.let { nodes[it].performClick() }
            ?: error("No displayed node has content description: $description")
    }

    private fun setOrientation(orientation: Int) {
        composeRule.activity.requestedOrientation = orientation
        val expected = when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            else -> Configuration.ORIENTATION_PORTRAIT
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val decor = composeRule.activity.window.decorView
            val windowMatches = if (expected == Configuration.ORIENTATION_LANDSCAPE) {
                decor.width > decor.height
            } else {
                decor.height > decor.width
            }
            composeRule.activity.resources.configuration.orientation == expected && windowMatches
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun CatalogHome(
        state: StandUiState,
        showPermissionReview: Boolean = false,
    ) {
        StandHomeScreen(
            state = state,
            showPermissionReview = showPermissionReview,
            onScreenTap = {},
            onToggleTheme = {},
            onOpenEditor = {},
            onBrightnessAdjustmentStarted = {},
            onBrightnessLevelChanged = {},
            onBrightnessAdjustmentFinished = {},
            readSystemVolume = { 0.5f },
            onSystemVolumeChanged = {},
            onClockScaleChanged = {},
            onToggleTorch = {},
            onCycleMode = {},
            onToggleSession = {},
            onToggleOrientation = {},
            onOpenRecordings = {},
            onOpenAiShot = {},
            onOpenSettings = {},
            onOpenBoyiso = {},
            boyisoStatus = "연결 안 됨",
            boyisoCanSendTokTok = false,
            onSendBoyisoTokTok = {},
            onToggleRadio = {},
            onEditRadio = {},
            catalogNow = CATALOG_NOW,
        )
    }

    @Composable
    private fun CatalogSettings(
        state: StandUiState,
        onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    ) {
        SettingsScreen(
            state = state,
            onUpdate = onUpdate,
            onModePreferenceSelected = {},
            onRestoreRecommended = {},
            onToggleInternetRadio = {},
            onSaveInternetRadio = { _, _, _ -> null },
            onDeleteInternetRadio = {},
            onManageInternetRadios = {},
            onOpenInternetRadioBrowser = {},
            onOpenRecordings = {},
            onOpenBoyiso = {},
            onOpenClockFonts = {},
            onOpenFontLicenses = {},
            boyisoStatus = "설정 필요",
            onRequestMicrophonePermission = {},
            onRequestApproximateLocationPermission = {},
            onRequestCameraPermission = {},
            onCameraAmbientSensingChanged = {},
            onOpenAppSettings = {},
            onBack = {},
        )
    }

    private fun homeState(): StandUiState = settingsState().copy(
        isSessionActive = true,
        lampIntensity = 0.72f,
        lampPhase = LampPhase.HOLDING,
        environmentMode = EnvironmentDisplayMode.OBJECT,
        experienceMode = StandExperienceMode.OBJECT,
        controlsVisible = true,
        weather = WeatherUiState(
            temperatureCelsius = 22.0,
            apparentTemperatureCelsius = 24.0,
            precipitationMillimeters = 0.0,
            weatherCode = 1,
            isDay = true,
            locationName = "현재 위치",
        ),
        batteryLevel = 0.82f,
        recordingCount = 3,
        internetRadioState = InternetRadioState.Idle,
    )

    private fun settingsState(): StandUiState {
        val channels = catalogChannels()
        return StandUiState(
            settings = AppSettings.Recommended.copy(
                lampIntensity = 0.72f,
                internetRadioChannels = channels,
                internetRadio = channels.first(),
                selectedInternetRadioId = channels.first().id,
            ),
            isSessionActive = true,
            lampIntensity = 0.72f,
            lampPhase = LampPhase.HOLDING,
            hasCameraPermission = false,
            hasMicrophonePermission = false,
            hasApproximateLocationPermission = false,
            torchAvailable = true,
            recordingCount = 3,
        )
    }

    private fun catalogChannels(): List<InternetRadioConfiguration> = listOf(
        InternetRadioConfiguration(
            displayName = "편안한 재즈",
            streamUrl = "https://example.com/jazz.m3u8",
            id = "11111111-1111-1111-1111-111111111111",
        ),
        InternetRadioConfiguration(
            displayName = "밤의 클래식",
            streamUrl = "https://example.com/classic.m3u8",
            id = "22222222-2222-2222-2222-222222222222",
        ),
    )

    companion object {
        private val CATALOG_NOW = LocalDateTime.of(2026, 8, 15, 7, 42, 5)
    }
}
