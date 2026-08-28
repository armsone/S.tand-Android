package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetRadioCodecTest {
    private val https = InternetRadioConfiguration(
        displayName = "안전한 라디오",
        streamUrl = "https://radio.example/live",
        id = "secure",
    )
    private val http = InternetRadioConfiguration(
        displayName = "오래된 라디오",
        streamUrl = "http://192.0.2.10:8000/live",
        id = "legacy",
    )

    @Test fun versionedExportRoundTripsHttpAndHttps() {
        val encoded = InternetRadioCodec.encode(listOf(https, http), exportedAtMillis = 123L)
        val decoded = InternetRadioCodec.decode(encoded) as RadioDecodeResult.Success

        assertTrue(encoded.contains("\"format\": \"s.tand-radio\""))
        assertEquals(listOf(https, http), decoded.channels)
        assertTrue(decoded.channels[1].isUnencrypted)
    }

    @Test fun rejectsMalformedWrongVersionOversizeAndCredentialUrls() {
        assertTrue(InternetRadioCodec.decode("not-json") is RadioDecodeResult.Failure)
        assertTrue(
            InternetRadioCodec.decode(
                """{"format":"s.tand-radio","version":2,"channels":[]}""",
            ) is RadioDecodeResult.Failure,
        )
        assertTrue(
            InternetRadioCodec.decode(ByteArray(InternetRadioCodec.MAX_PAYLOAD_BYTES + 1))
                is RadioDecodeResult.Failure,
        )
        assertTrue(
            InternetRadioCodec.decode(
                """{"format":"s.tand-radio","version":1,"channels":[{"id":"bad","displayName":"bad","streamUrl":"http://id:pw@radio.example/live"}]}""",
            ) is RadioDecodeResult.Failure,
        )
    }

    @Test fun decodeDeduplicatesAndKeepsAtMostFourChannels() {
        val channels = (1..6).joinToString(",") { index ->
            val url = if (index == 2) "https://RADIO.EXAMPLE/1" else "https://radio.example/$index"
            """{"id":"$index","displayName":"채널 $index","streamUrl":"$url"}"""
        }
        val decoded = InternetRadioCodec.decode(
            """{"format":"s.tand-radio","version":1,"channels":[$channels]}""",
        ) as RadioDecodeResult.Success

        assertEquals(4, decoded.channels.size)
        assertEquals(4, decoded.channels.map { it.streamUrl }.distinct().size)
    }

    @Test fun addPolicyPreservesExistingChannelsAndRepairsIdCollisions() {
        val current = listOf(https)
        val importedWithSameId = http.copy(id = https.id)
        val preview = InternetRadioImportPolicy.evaluate(current, listOf(importedWithSameId))
        val merged = InternetRadioImportPolicy.applyAdd(current, preview.addableChannels)

        assertEquals(2, merged.size)
        assertEquals(https, merged.first())
        assertNotEquals(https.id, merged.last().id)
        assertEquals(http.streamUrl, merged.last().streamUrl)
    }

    @Test fun fullListNeverChangesUntilExplicitReplacePolicyIsApplied() {
        val current = (1..4).map { index ->
            InternetRadioConfiguration("기존 $index", "https://old.example/$index", "old-$index")
        }
        val preview = InternetRadioImportPolicy.evaluate(current, listOf(http))

        assertTrue(preview.isFull)
        assertTrue(preview.addableChannels.isEmpty())
        assertEquals(current, InternetRadioImportPolicy.applyAdd(current, preview.newChannels))
        assertEquals(listOf(http), InternetRadioImportPolicy.applyReplace(listOf(http)))
    }

    @Test fun duplicateComparisonNormalizesSchemeAndHostButPreservesPathCase() {
        val current = listOf(
            InternetRadioConfiguration("A", "HTTPS://RADIO.EXAMPLE/Live", "a"),
        )
        val imported = listOf(
            InternetRadioConfiguration("중복", "https://radio.example/Live", "b"),
            InternetRadioConfiguration("다른 경로", "https://radio.example/live", "c"),
        )
        val preview = InternetRadioImportPolicy.evaluate(current, imported)

        assertEquals(1, preview.duplicateChannels.size)
        assertEquals(listOf("다른 경로"), preview.newChannels.map { it.displayName })
    }
}
