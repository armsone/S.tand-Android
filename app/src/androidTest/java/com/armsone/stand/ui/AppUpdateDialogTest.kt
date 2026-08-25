package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.armsone.stand.ui.theme.STandTheme
import com.armsone.stand.update.AppUpdateState
import com.armsone.stand.update.GitHubAppRelease
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppUpdateDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun availableReleaseExplainsDataPreservationAndStartsDownload() {
        var downloadCount = 0
        val release = GitHubAppRelease(
            productVersion = "2.1.1",
            versionCode = 29,
            tagName = "android-v2.1.1",
            assetName = "S.tand-Android-2.1.1.apk",
            apkUrl = URL(
                "https://github.com/armsone/S.tand-Android/releases/download/android-v2.1.1/S.tand-Android-2.1.1.apk",
            ),
            assetSizeBytes = 12_345L,
        )

        composeRule.setContent {
            STandTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Available(release),
                    onDownload = { downloadCount += 1 },
                    onInstall = {},
                    onLater = {},
                )
            }
        }

        composeRule.onNodeWithText("새 버전이 있습니다").assertIsDisplayed()
        composeRule.onNodeWithText("기존 설정과 녹음은 유지됩니다.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("2.1.1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("업데이트").performClick()
        composeRule.waitForIdle()

        assertEquals(1, downloadCount)
    }

    @Test
    fun latestReleaseShowsUpToDateDialogAndDismisses() {
        var laterCount = 0
        composeRule.setContent {
            STandTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Latest(currentVersionCode = 29),
                    onDownload = {},
                    onInstall = {},
                    onLater = { laterCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("최신 버전입니다").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        composeRule.waitForIdle()

        assertEquals(1, laterCount)
    }

    @Test
    fun failedCheckShowsNextActionAndAllowsRetry() {
        var retryCount = 0
        var dismissCount = 0
        composeRule.setContent {
            STandTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Failed(
                        message = "최신 버전을 확인하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.",
                        canRetry = true,
                    ),
                    onDownload = {},
                    onInstall = {},
                    onRetry = { retryCount += 1 },
                    onLater = { dismissCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("최신 버전을 확인할 수 없습니다").assertIsDisplayed()
        composeRule.onNodeWithText("인터넷 연결을 확인한 뒤 다시 시도해 주세요.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        composeRule.waitForIdle()

        assertEquals(1, retryCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun manualCheckingStateDisplaysProgress() {
        composeRule.setContent {
            STandTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Checking(isManual = true),
                    onDownload = {},
                    onInstall = {},
                    onLater = {},
                )
            }
        }

        composeRule.onNodeWithText("최신 버전 확인 중").assertIsDisplayed()
        composeRule.onNodeWithText("취소").assertDoesNotExist()
    }
}
