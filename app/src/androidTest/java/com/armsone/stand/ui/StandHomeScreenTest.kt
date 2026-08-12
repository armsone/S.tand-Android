package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.armsone.stand.model.LampPhase
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
                    onScreenTap = {},
                    onToggleTheme = {},
                    onOpenEditor = {},
                    onBrightnessAdjustmentStarted = {},
                    onBrightnessLevelChanged = {},
                    onBrightnessAdjustmentFinished = {},
                    onInternetRadioVolumeChanged = {},
                    onClockScaleChanged = {},
                    onToggleTorch = {},
                    onCycleMode = {},
                    onToggleSession = { startCount += 1 },
                    onToggleOrientation = {},
                    onOpenRecordings = {},
                    onOpenAiShot = {},
                    onOpenSettings = {},
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        listOf("플래시", "카메라", "마이크", "위치 정보").forEach { title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
        composeRule.onNodeWithText("사진·영상은 저장하거나 전송하지 않습니다.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("정확한 위치는 요청하지 않습니다.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("권한 확인하고 S.tand 시작")
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
                    onInternetRadioVolumeChanged = {},
                    onClockScaleChanged = {},
                    onToggleTorch = {},
                    onCycleMode = {},
                    onToggleSession = {},
                    onToggleOrientation = {},
                    onOpenRecordings = {},
                    onOpenAiShot = {},
                    onOpenSettings = {},
                    onToggleRadio = {},
                    onEditRadio = {},
                )
            }
        }

        composeRule.onNodeWithText("22°").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(1, themeToggleCount)
    }
}
