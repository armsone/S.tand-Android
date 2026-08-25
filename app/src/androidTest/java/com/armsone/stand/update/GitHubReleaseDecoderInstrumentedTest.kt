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
              "tag_name": "android-v2.1.1",
              "draft": false,
              "prerelease": false,
              "body": "Android-Version-Code: 340540\\n\\n변경 사항",
              "assets": [
                {
                  "name": "S.tand-Android-2.1.1.apk",
                  "size": 12345,
                  "digest": "sha256:${"ab".repeat(32)}",
                  "browser_download_url": "https://github.com/armsone/S.tand-Android/releases/download/android-v2.1.1/S.tand-Android-2.1.1.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("2.1.1", release?.productVersion)
        assertEquals(340540, release?.versionCode)
        assertEquals("S.tand-Android-2.1.1.apk", release?.assetName)
    }

    @Test
    fun rejectsDraftReleases() {
        assertNull(
            GitHubReleaseDecoder.decode(
                """
                {
                  "tag_name": "android-v2.1.1",
                  "draft": true,
                  "prerelease": false,
                  "assets": []
                }
                """.trimIndent(),
            ),
        )
    }
}
