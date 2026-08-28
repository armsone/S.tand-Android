package com.armsone.stand.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.armsone.stand.model.InternetRadioCodec

class RadioTransferServerTest {
    @Test fun oneTimeTokensAreStrongAndDifferent() {
        val first = RadioTransferServer.generateToken()
        val second = RadioTransferServer.generateToken()

        assertEquals(32, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)
    }

    @Test fun requestPolicyRequiresExactTokenAndBoundedNonEmptyBody() {
        assertTrue(RadioTransferRequestPolicy.tokenMatches("secret", "secret"))
        assertTrue(!RadioTransferRequestPolicy.tokenMatches("secret", null))
        assertTrue(!RadioTransferRequestPolicy.tokenMatches("secret", "other"))
        assertEquals(
            RadioTransferPayloadDecision.LENGTH_REQUIRED,
            RadioTransferRequestPolicy.payloadDecision(0),
        )
        assertEquals(
            RadioTransferPayloadDecision.ACCEPT,
            RadioTransferRequestPolicy.payloadDecision(InternetRadioCodec.MAX_PAYLOAD_BYTES),
        )
        assertEquals(
            RadioTransferPayloadDecision.TOO_LARGE,
            RadioTransferRequestPolicy.payloadDecision(InternetRadioCodec.MAX_PAYLOAD_BYTES + 1),
        )
    }
}
