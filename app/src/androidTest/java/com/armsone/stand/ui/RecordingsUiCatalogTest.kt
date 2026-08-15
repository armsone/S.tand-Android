package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.armsone.stand.recording.RecordingClip
import com.armsone.stand.recording.RecordingSessionGroup
import com.armsone.stand.recording.SleepStartleEvent
import com.armsone.stand.ui.theme.STandTheme
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecordingsUiCatalogTest {
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
    }

    @After
    fun restoreCatalogProfile() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun capturePopulatedReportAndManagement() {
        val session = populatedSession()
        composeRule.setContent {
            CatalogProfile {
                STandTheme {
                    RecordingsScreen(
                        recordings = session.clips,
                        sessionGroups = listOf(session),
                        isBusy = false,
                        message = null,
                        onMessageDismiss = {},
                        onBack = {},
                        onDelete = {},
                        onShare = {},
                        onMergeSelected = { _, _ -> },
                        onMergeToday = {},
                        onDeleteSelected = {},
                        onDeleteAll = {},
                    )
                }
            }
        }

        capture("recordings_report_populated")
        composeRule.onNodeWithText("잠소리 관리").performClick()
        composeRule.waitForIdle()
        capture("recordings_management")
    }

    @Test
    fun captureEmptyReport() {
        composeRule.setContent {
            CatalogProfile {
                STandTheme {
                    RecordingsScreen(
                        recordings = emptyList(),
                        sessionGroups = emptyList(),
                        isBusy = false,
                        message = null,
                        onMessageDismiss = {},
                        onBack = {},
                        onDelete = {},
                        onShare = {},
                        onMergeSelected = { _, _ -> },
                        onMergeToday = {},
                        onDeleteSelected = {},
                        onDeleteAll = {},
                    )
                }
            }
        }

        capture("recordings_report_empty")
    }

    private fun capture(id: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("matchup-ui-catalog"))
        captureCatalogScreenshot(composeRule.activity, directory, id)
    }

    private fun populatedSession(): RecordingSessionGroup {
        val start = Instant.parse("2026-08-14T22:30:00Z")
        val clips = listOf(
            RecordingClip(File("/catalog/sleep-1.wav"), start.plusSeconds(3_600), 12.0),
            RecordingClip(File("/catalog/sleep-2.wav"), start.plusSeconds(7_200), 18.0),
            RecordingClip(File("/catalog/sleep-3.wav"), start.plusSeconds(18_000), 9.0),
        )
        return RecordingSessionGroup(
            id = "catalog-session",
            startedAt = start,
            endedAt = start.plusSeconds(8 * 3_600L),
            clips = clips,
            isInferred = false,
            startleEvents = listOf(
                SleepStartleEvent(UUID.fromString("11111111-1111-1111-1111-111111111111"), start.plusSeconds(14_400), start.plusSeconds(14_405)),
            ),
        )
    }
}
