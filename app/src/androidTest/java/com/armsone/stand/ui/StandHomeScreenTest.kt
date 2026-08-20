package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.ui.theme.STandTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StandHomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startScreenExplainsAllFourPermissionUsesBeforeStarting() {
        var startCount = 0

        composeRule.setContent {
            STandTheme {
                StandHomeScreen(
                    state = StandUiState(),
                    showPermissionReview = true,
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
                    onToggleSession = { startCount += 1 },
                    onToggleOrientation = {},
                    onOpenRecordings = {},
                    onOpenAiShot = {},
                    onOpenSettings = {},
                    onOpenBoyiso = {},
                    boyisoStatus = "설정 필요",
                    boyisoCanSendTokTok = false,
                    onSendBoyisoTokTok = {},
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        listOf("카메라와 플래시", "마이크", "위치 정보").forEach { title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
        composeRule.onNodeWithText("사진·영상은 저장하거나 전송하지 않습니다.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("정확한 위치는 요청하지 않습니다.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("권한 확인하고 시작")
            .performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(1, startCount)
    }

    @Test
    fun doubleTapOnWeatherPanelTogglesThemeOnce() {
        var themeToggleCount = 0
        val state = StandUiState(
            isSessionActive = true,
            lampIntensity = 0.7f,
            lampPhase = LampPhase.HOLDING,
            weather = WeatherUiState(
                temperatureCelsius = 22.0,
                apparentTemperatureCelsius = 25.0,
                precipitationMillimeters = 0.0,
                weatherCode = 1,
                isDay = true,
                locationName = "현재 위치",
            ),
        )

        composeRule.setContent {
            STandTheme(displayTheme = state.settings.displayTheme) {
                StandHomeScreen(
                    state = state,
                    onScreenTap = {},
                    onToggleTheme = { themeToggleCount += 1 },
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
                    boyisoStatus = "설정 필요",
                    boyisoCanSendTokTok = false,
                    onSendBoyisoTokTok = {},
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        composeRule.onNodeWithText("22°").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(1, themeToggleCount)
    }

    @Test
    fun connectedBoyisoTapSendsTokTokAndLongPressOpensSettings() {
        var openCount = 0
        var tokTokCount = 0

        composeRule.setContent {
            STandTheme {
                StandHomeScreen(
                    state = StandUiState(),
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
                    onOpenBoyiso = { openCount += 1 },
                    boyisoStatus = "말할 사람",
                    boyisoCanSendTokTok = true,
                    onSendBoyisoTokTok = { tokTokCount += 1 },
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        composeRule.onNodeWithText("말할 사람").assertIsDisplayed()
        composeRule.onNodeWithText("보이소").performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(0, openCount)
        assertEquals(1, tokTokCount)

        composeRule.onNodeWithText("보이소").performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(1, openCount)
        assertEquals(1, tokTokCount)
    }

    @Test
    fun disconnectedBoyisoTapAndLongPressBothOpenSettings() {
        var openCount = 0
        var tokTokCount = 0

        composeRule.setContent {
            STandTheme {
                StandHomeScreen(
                    state = StandUiState(),
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
                    onOpenBoyiso = { openCount += 1 },
                    boyisoStatus = "연결 안 됨",
                    boyisoCanSendTokTok = false,
                    onSendBoyisoTokTok = { tokTokCount += 1 },
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        composeRule.onNodeWithText("보이소").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("보이소").performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(2, openCount)
        assertEquals(0, tokTokCount)
    }

    @Test
    fun mateModeLockShowsCentralLockOnClock() {
        composeRule.setContent {
            STandTheme {
                StandHomeScreen(
                    state = StandUiState(
                        settings = AppSettings.Recommended.copy(
                            modePreference = StandModePreference.MATE,
                        ),
                        isSessionActive = true,
                        environmentMode = EnvironmentDisplayMode.MATE,
                        experienceMode = StandExperienceMode.MATE,
                        lampPhase = LampPhase.OFF,
                        controlsVisible = false,
                    ),
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
                )
            }
        }

        composeRule.onNodeWithContentDescription("매이트 모드 잠금").assertIsDisplayed()
        composeRule.onNodeWithText("매이트 모드 잠금").assertIsDisplayed()
    }

    @Test
    fun startledModeTemporarilyHidesMateLockFromClock() {
        composeRule.setContent {
            STandTheme {
                StandHomeScreen(
                    state = StandUiState(
                        settings = AppSettings.Recommended.copy(
                            modePreference = StandModePreference.MATE,
                        ),
                        isSessionActive = true,
                        environmentMode = EnvironmentDisplayMode.MATE,
                        experienceMode = StandExperienceMode.STARTLED,
                        lampPhase = LampPhase.HOLDING,
                    ),
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
                    boyisoStatus = "연결됨",
                    boyisoCanSendTokTok = true,
                    onSendBoyisoTokTok = {},
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("매이트 모드 잠금").assertCountEquals(0)
        composeRule.onNodeWithText("화들짝 모드").assertIsDisplayed()
    }
}
