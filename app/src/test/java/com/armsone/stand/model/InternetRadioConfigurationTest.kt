package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
