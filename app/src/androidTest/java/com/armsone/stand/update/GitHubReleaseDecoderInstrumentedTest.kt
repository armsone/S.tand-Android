package com.armsone.stand.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseDecoderInstrumentedTest {
    @Test
    fun acceptsOnlyTheExactStableReleaseAsset() {
        val release = GitHubReleaseDecoder.decode(
            """
            {
              "tag_name": "android-v28",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "S.tand-Android-v28.apk",
                  "size": 12345,
                  "browser_download_url": "https://github.com/armsone/S.tand-Android/releases/download/android-v28/S.tand-Android-v28.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(28, release?.versionCode)
        assertEquals("S.tand-Android-v28.apk", release?.assetName)
    }

    @Test
    fun rejectsDraftReleases() {
        assertNull(
            GitHubReleaseDecoder.decode(
                """
                {
                  "tag_name": "android-v28",
                  "draft": true,
                  "prerelease": false,
                  "assets": []
                }
                """.trimIndent(),
            ),
        )
    }
}
