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
            versionCode = 29,
            tagName = "android-v29",
            assetName = "S.tand-Android-v29.apk",
            apkUrl = URL(
                "https://github.com/armsone/S.tand-Android/releases/download/android-v29/S.tand-Android-v29.apk",
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
        composeRule.onNodeWithText("업데이트").performClick()
        composeRule.waitForIdle()

        assertEquals(1, downloadCount)
    }
}
