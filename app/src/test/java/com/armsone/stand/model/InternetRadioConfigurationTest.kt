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

    @Test fun rejectsInsecureOrCredentialUrls() {
        assertNull(InternetRadioConfiguration("FM", "http://radio.example/live").normalizedOrNull())
        assertNull(InternetRadioConfiguration("FM", "https://id:pw@radio.example/live").normalizedOrNull())
    }

    @Test fun reconnectBackoffStopsAfterFiveAttempts() {
        assertEquals(listOf(2, 4, 8, 15, 30), (0..4).map(InternetRadioReconnectPolicy::delaySeconds))
        assertNull(InternetRadioReconnectPolicy.delaySeconds(5))
    }

    @Test fun settingsKeepAtMostTwoChannelsAndPreserveSelection() {
        val first = InternetRadioConfiguration("첫째", "https://one.example/live", "one")
        val second = InternetRadioConfiguration("둘째", "https://two.example/live", "two")
        val third = InternetRadioConfiguration("셋째", "https://three.example/live", "three")

        val normalized = AppSettings(
            internetRadio = second,
            internetRadioChannels = listOf(first, second, third),
            selectedInternetRadioId = "two",
        ).normalized()

        assertEquals(listOf("one", "two"), normalized.internetRadioChannels.map { it.id })
        assertEquals("two", normalized.internetRadio?.id)
        assertTrue(normalized.internetRadioChannels.size <= 2)
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

    @Test fun sharedRadioImportAcceptsOnlyOneValidatedHttpsUrl() {
        assertEquals(
            "https://radio.example/live",
            RadioShareImportPolicy.validatedUrlOrNull("  https://radio.example/live  "),
        )
        assertNull(RadioShareImportPolicy.validatedUrlOrNull("http://radio.example/live"))
        assertNull(RadioShareImportPolicy.validatedUrlOrNull("https://id:pw@radio.example/live"))
        assertNull(RadioShareImportPolicy.validatedUrlOrNull("설명 https://radio.example/live"))
    }
}
