package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetRadioBrowserPolicyTest {
    @Test
    fun addressAcceptsHttpsAddsSchemeAndSearchesPlainText() {
        assertEquals(
            InternetRadioBrowserAddressResult.Valid("https://radio.example/live"),
            InternetRadioBrowserPolicy.browsingAddress(" https://radio.example/live "),
        )
        assertEquals(
            InternetRadioBrowserAddressResult.Valid("https://radio.example"),
            InternetRadioBrowserPolicy.browsingAddress("radio.example"),
        )
        assertEquals(
            InternetRadioBrowserAddressResult.Valid("https://www.google.com/search?q=internet+radio"),
            InternetRadioBrowserPolicy.browsingAddress("internet radio"),
        )
    }

    @Test
    fun addressRejectsNonHttpsCredentialsEmptyAndOversizedInput() {
        assertFalse(InternetRadioBrowserPolicy.isSecureWebAddress("http://radio.example/live"))
        assertFalse(InternetRadioBrowserPolicy.isSecureWebAddress("https://id:pw@radio.example/live"))
        assertTrue(
            InternetRadioBrowserPolicy.browsingAddress("") is
                InternetRadioBrowserAddressResult.Invalid,
        )
        assertTrue(
            InternetRadioBrowserPolicy.browsingAddress(
                "a".repeat(InternetRadioBrowserPolicy.MAXIMUM_ADDRESS_LENGTH + 1),
            ) is InternetRadioBrowserAddressResult.Invalid,
        )
    }

    @Test
    fun defaultsMatchTheIosBrowser() {
        assertEquals("https://www.google.com/", InternetRadioBrowserPolicy.homepage)
        assertEquals(
            listOf("Google", "한국 라디오", "FMSTREAM", "Radio Browser"),
            InternetRadioBrowserPolicy.favorites.map { it.title },
        )
    }
}
