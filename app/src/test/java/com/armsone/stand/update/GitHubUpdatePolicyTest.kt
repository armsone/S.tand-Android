package com.armsone.stand.update

import java.io.IOException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdatePolicyTest {
    @Test
    fun releaseTagMustUseTheExactAndroidVersionCodeFormat() {
        assertEquals(28, GitHubUpdatePolicy.versionCode("android-v28"))
        assertNull(GitHubUpdatePolicy.versionCode("v28"))
        assertNull(GitHubUpdatePolicy.versionCode("android-v0"))
        assertNull(GitHubUpdatePolicy.versionCode("android-v28-beta"))
    }

    @Test
    fun apkAssetMustComeFromTheMatchingRepositoryRelease() {
        assertTrue(
            GitHubUpdatePolicy.isApprovedApkAsset(
                assetName = "S.tand-Android-v28.apk",
                urlText = "https://github.com/armsone/S.tand-Android/releases/download/android-v28/S.tand-Android-v28.apk",
                versionCode = 28,
                sizeBytes = 12_345L,
            ),
        )
        assertFalse(
            GitHubUpdatePolicy.isApprovedApkAsset(
                assetName = "S.tand-Android-v28.apk",
                urlText = "https://example.com/S.tand-Android-v28.apk",
                versionCode = 28,
                sizeBytes = 12_345L,
            ),
        )
        assertFalse(
            GitHubUpdatePolicy.isApprovedApkAsset(
                assetName = "other.apk",
                urlText = "https://github.com/armsone/S.tand-Android/releases/download/android-v28/other.apk",
                versionCode = 28,
                sizeBytes = 12_345L,
            ),
        )
    }

    @Test
    fun automaticCheckRemainsQuietWhenAlreadyLatestOrFetchFails() {
        val currentRelease = sampleRelease(versionCode = 340467)

        val sameVersionResult = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(currentRelease),
            isManual = false,
        )
        assertEquals(AppUpdateState.Idle, sameVersionResult)

        val olderVersionResult = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(sampleRelease(versionCode = 340460)),
            isManual = false,
        )
        assertEquals(AppUpdateState.Idle, olderVersionResult)

        val failedResult = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.failure(IOException("Network error")),
            isManual = false,
        )
        assertEquals(AppUpdateState.Idle, failedResult)

        val noReleaseResult = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(null),
            isManual = false,
        )
        assertEquals(AppUpdateState.Idle, noReleaseResult)
    }

    @Test
    fun automaticAndManualChecksBothReportNewerVersionAvailable() {
        val newerRelease = sampleRelease(versionCode = 340468)

        val automaticState = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(newerRelease),
            isManual = false,
        )
        assertEquals(AppUpdateState.Available(newerRelease), automaticState)

        val manualState = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(newerRelease),
            isManual = true,
        )
        assertEquals(AppUpdateState.Available(newerRelease), manualState)
    }

    @Test
    fun manualCheckClearlyReportsAlreadyLatestWhenVersionIsCurrentOrOlder() {
        val currentRelease = sampleRelease(versionCode = 340467)

        val state = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(currentRelease),
            isManual = true,
        )
        assertTrue(state is AppUpdateState.Latest)
        val latest = state as AppUpdateState.Latest
        assertEquals(340467, latest.currentVersionCode)
        assertEquals(currentRelease, latest.release)
    }

    @Test
    fun manualCheckDoesNotConflateNoPublishedReleaseWithLatest() {
        val state = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.success(null),
            isManual = true,
        )
        assertTrue(state is AppUpdateState.Failed)
        val failed = state as AppUpdateState.Failed
        assertTrue(failed.canRetry)
        assertTrue(failed.message.contains("릴리스 정보를 찾을 수 없어"))
    }

    @Test
    fun manualCheckReportsFailureWithNextActionOnNetworkError() {
        val state = GitHubUpdatePolicy.resolveCheckState(
            currentVersionCode = 340467,
            releaseResult = Result.failure(IOException("Socket timeout")),
            isManual = true,
        )
        assertTrue(state is AppUpdateState.Failed)
        val failed = state as AppUpdateState.Failed
        assertTrue(failed.canRetry)
        assertTrue(failed.message.contains("인터넷 연결"))
    }

    private fun sampleRelease(versionCode: Int): GitHubAppRelease = GitHubAppRelease(
        versionCode = versionCode,
        tagName = "android-v$versionCode",
        assetName = "S.tand-Android-v$versionCode.apk",
        apkUrl = URL(
            "https://github.com/armsone/S.tand-Android/releases/download/android-v$versionCode/S.tand-Android-v$versionCode.apk",
        ),
        assetSizeBytes = 12_345_678L,
    )
}
