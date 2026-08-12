package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.StandScreenLayout
import com.armsone.stand.ui.theme.STandTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ScreenEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clockSingleTapTogglesPaletteAndDoubleTapOnlyTogglesHourMode() {
        var layout by mutableStateOf(StandScreenLayout.Portrait)
        var font by mutableStateOf(ClockFontChoice.TENADA)
        var hourMode by mutableStateOf(ClockHourMode.TWELVE)
        var hourModeChangeCount = 0
        var savedValues: Triple<StandScreenLayout, ClockFontChoice, ClockHourMode>? = null
        var cancelCount = 0

        composeRule.setContent {
            STandTheme {
                ScreenEditorScreen(
                    state = StandUiState(),
                    layout = layout,
                    clockFont = font,
                    clockHourMode = hourMode,
                    isPortrait = true,
                    onLayoutChange = { layout = it },
                    onClockFontChange = { font = it },
                    onClockHourModeChange = {
                        hourModeChangeCount += 1
                        hourMode = it
                    },
                    onManageRadios = {},
                    onSave = { savedLayout, savedFont, savedHourMode ->
                        savedValues = Triple(savedLayout, savedFont, savedHourMode)
                    },
                    onCancel = { cancelCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("editor_clock_font_palette").assertDoesNotExist()

        composeRule.onNodeWithTag("editor_panel_clock").assertIsDisplayed()
            .performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithTag("editor_clock_font_palette")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("editor_clock_font_palette").assertExists()
        ClockFontChoice.entries.forEach { choice ->
            composeRule.onAllNodesWithContentDescription(
                "${choice.displayName} 글꼴, 12시 34분 미리보기",
            ).assertCountEquals(1)
        }

        composeRule.onNodeWithTag("editor_panel_clock").performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithTag("editor_clock_font_palette")
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("editor_clock_font_palette").assertDoesNotExist()

        composeRule.onNodeWithTag("editor_panel_clock").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(ClockHourMode.TWENTY_FOUR, hourMode)
        assertEquals(1, hourModeChangeCount)
        composeRule.onNodeWithTag("editor_clock_font_palette").assertDoesNotExist()

        composeRule.onNodeWithText("저장").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(Triple(layout, font, hourMode), savedValues)

        composeRule.onNodeWithContentDescription("화면 편집 취소")
            .performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(1, cancelCount)
    }

    @Test
    fun weatherDoubleTapSplitsCombinedPanelIntoThreeLivePanels() {
        var layout by mutableStateOf(StandScreenLayout.Landscape)

        composeRule.setContent {
            STandTheme {
                ScreenEditorScreen(
                    state = StandUiState(),
                    layout = layout,
                    clockFont = ClockFontChoice.TENADA,
                    clockHourMode = ClockHourMode.TWENTY_FOUR,
                    isPortrait = false,
                    onLayoutChange = { layout = it },
                    onClockFontChange = {},
                    onClockHourModeChange = {},
                    onManageRadios = {},
                    onSave = { _, _, _ -> },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag("editor_panel_weather_1").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_panel_weather_1").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(listOf(0, 1, 2), layout.weatherGroupIds)
        composeRule.onNodeWithTag("editor_panel_weather_0").assertExists()
        composeRule.onNodeWithTag("editor_panel_weather_1").assertExists()
        composeRule.onNodeWithTag("editor_panel_weather_2").assertExists()
    }

    @Test
    fun groupedRadioSingleTapSplitsTheTwoPanels() {
        var layout by mutableStateOf(
            StandScreenLayout.Portrait.copy(radiosGrouped = true),
        )
        val channels = listOf(
            InternetRadioConfiguration("첫 채널", "https://example.com/one", "one"),
            InternetRadioConfiguration("둘째 채널", "https://example.com/two", "two"),
        )

        composeRule.setContent {
            STandTheme {
                ScreenEditorScreen(
                    state = StandUiState(
                        settings = AppSettings.Recommended.copy(
                            internetRadioChannels = channels,
                            internetRadio = channels.first(),
                            selectedInternetRadioId = channels.first().id,
                        ),
                    ),
                    layout = layout,
                    clockFont = ClockFontChoice.TENADA,
                    clockHourMode = ClockHourMode.TWELVE,
                    isPortrait = true,
                    onLayoutChange = { layout = it },
                    onClockFontChange = {},
                    onClockHourModeChange = {},
                    onManageRadios = {},
                    onSave = { _, _, _ -> },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag("editor_panel_radio").performTouchInput { click() }
        composeRule.waitForIdle()

        assertFalse(layout.radiosGrouped)
    }

}
