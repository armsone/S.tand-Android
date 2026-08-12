package com.armsone.stand.update

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
}
