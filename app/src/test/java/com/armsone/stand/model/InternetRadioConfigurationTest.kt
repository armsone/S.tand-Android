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
        assertEquals(listOf(1, 2, 4, 8, 15), (0..4).map(InternetRadioReconnectPolicy::delaySeconds))
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
}
