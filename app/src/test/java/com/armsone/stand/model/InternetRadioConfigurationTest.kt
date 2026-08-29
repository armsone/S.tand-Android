package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetRadioConfigurationTest {
    @Test fun acceptsHttpsAndUsesDefaultName() {
        val result = InternetRadioConfiguration("  ", " https://radio.example/live ").normalizedOrNull()
        assertEquals("인터넷 라디오", result?.displayName)
        assertEquals("https://radio.example/live", result?.streamUrl)
    }

    @Test fun acceptsLegacyHttpIncludingIpPortAndRejectsCredentials() {
        val legacy = InternetRadioConfiguration("FM", "http://192.0.2.10:8000/live").normalizedOrNull()
        assertEquals("http://192.0.2.10:8000/live", legacy?.streamUrl)
        assertTrue(legacy?.isUnencrypted == true)
        assertNull(InternetRadioConfiguration("FM", "ftp://radio.example/live").normalizedOrNull())
        assertNull(InternetRadioConfiguration("FM", "https://id:pw@radio.example/live").normalizedOrNull())
        assertNull(InternetRadioConfiguration("FM", "http://id:pw@radio.example/live").normalizedOrNull())
    }

    @Test fun reconnectBackoffStopsAfterFiveAttempts() {
        assertEquals(listOf(2, 4, 8, 15, 30), (0..4).map(InternetRadioReconnectPolicy::delaySeconds))
        assertNull(InternetRadioReconnectPolicy.delaySeconds(5))
    }

    @Test fun settingsKeepAtMostFourChannelsAndPreserveSelection() {
        val first = InternetRadioConfiguration("첫째", "https://one.example/live", "one")
        val second = InternetRadioConfiguration("둘째", "https://two.example/live", "two")
        val third = InternetRadioConfiguration("셋째", "https://three.example/live", "three")
        val fourth = InternetRadioConfiguration("넷째", "https://four.example/live", "four")
        val fifth = InternetRadioConfiguration("다섯째", "https://five.example/live", "five")

        val normalized = AppSettings(
            internetRadio = second,
            internetRadioChannels = listOf(first, second, third, fourth, fifth),
            selectedInternetRadioId = "two",
        ).normalized()

        assertEquals(listOf("one", "two", "three", "four"), normalized.internetRadioChannels.map { it.id })
        assertEquals("two", normalized.internetRadio?.id)
        assertTrue(normalized.internetRadioChannels.size <= 4)
        assertEquals(4, AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT)
    }

    @Test fun radioMutationsOnlyStopPlaybackWhenTheActiveStreamChanges() {
        val first = InternetRadioConfiguration("첫째", "https://one.example/live", "one")
        val renamed = first.copy(displayName = "이름만 변경")
        val changedUrl = first.copy(streamUrl = "https://one.example/alternate")

        assertTrue(!InternetRadioMutationPolicy.shouldStopForSave("one", first, renamed))
        assertTrue(InternetRadioMutationPolicy.shouldStopForSave("one", first, changedUrl))
        assertTrue(!InternetRadioMutationPolicy.shouldStopForDelete("one", "two"))
        assertTrue(InternetRadioMutationPolicy.shouldStopForDelete("one", "one"))
        assertEquals(
            "one",
            InternetRadioMutationPolicy.selectedChannelIDAfterSave("one", "two", "two"),
        )
    }

    @Test fun sharedRadioImportAcceptsOneValidatedHttpOrHttpsUrl() {
        assertEquals(
            "https://radio.example/live",
            RadioShareImportPolicy.validatedUrlOrNull("  https://radio.example/live  "),
        )
        assertEquals(
            "http://192.0.2.10:8000/live",
            RadioShareImportPolicy.validatedUrlOrNull("http://192.0.2.10:8000/live"),
        )
        assertNull(RadioShareImportPolicy.validatedUrlOrNull("https://id:pw@radio.example/live"))
        assertNull(RadioShareImportPolicy.validatedUrlOrNull("설명 https://radio.example/live"))
    }

    @Test fun sanitizesMalformedQueryBackslashesImmediatelyBeforeSeparator() {
        val result1 = InternetRadioConfiguration(
            "KBS 2FM",
            "https://radio.bsod.kr/stream?stn=kbs%5C&ch=2fm",
        ).normalizedOrNull()
        assertEquals("https://radio.bsod.kr/stream?stn=kbs&ch=2fm", result1?.streamUrl)

        val result2 = InternetRadioConfiguration(
            "KBS 2FM",
            "https://radio.bsod.kr/stream?stn=kbs\\&ch=2fm",
        ).normalizedOrNull()
        assertEquals("https://radio.bsod.kr/stream?stn=kbs&ch=2fm", result2?.streamUrl)

        val result3 = InternetRadioConfiguration(
            "KBS 2FM",
            "https://radio.bsod.kr/stream?stn=kbs%5c&ch=2fm#live",
        ).normalizedOrNull()
        assertEquals("https://radio.bsod.kr/stream?stn=kbs&ch=2fm#live", result3?.streamUrl)
    }

    @Test fun preservesLegitimateQueryBackslashesNotImmediatelyBeforeSeparator() {
        val preserved = InternetRadioConfiguration(
            "Radio",
            "https://radio.example/stream?token=abc%5Cdef&ch=2",
        ).normalizedOrNull()
        assertEquals("https://radio.example/stream?token=abc%5Cdef&ch=2", preserved?.streamUrl)

        val trailingBackslash = InternetRadioConfiguration(
            "Radio",
            "https://radio.example/stream?token=abc%5C",
        ).normalizedOrNull()
        assertEquals("https://radio.example/stream?token=abc%5C", trailingBackslash?.streamUrl)

        assertNull(InternetRadioConfiguration.validationMessage(
            "KBS 2FM",
            "https://radio.bsod.kr/stream?stn=kbs%5C&ch=2fm",
        ))
    }
}
