package com.armsone.stand.boyiso

import com.armsone.stand.model.EnvironmentDisplayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoyisoStartlePolicyTest {
    private fun device(mode: EnvironmentDisplayMode?, active: Boolean = true) = BoyisoDevice(
        id = "id",
        name = "기기",
        role = BoyisoRole.VIEWER,
        batteryPercent = 80,
        monitoring = true,
        lastSeenMillis = 1L,
        displayMode = mode,
        sessionActive = active,
    )

    @Test
    fun `movement is relayed only when speaker and every connected device is mate`() {
        assertTrue(
            BoyisoStartlePolicy.shouldRelayMovement(
                BoyisoRole.SPEAKER,
                true,
                EnvironmentDisplayMode.MATE,
                listOf(device(EnvironmentDisplayMode.MATE)),
            ),
        )
        assertFalse(
            BoyisoStartlePolicy.shouldRelayMovement(
                BoyisoRole.SPEAKER,
                true,
                EnvironmentDisplayMode.MATE,
                listOf(device(EnvironmentDisplayMode.OBJECT)),
            ),
        )
        assertFalse(
            BoyisoStartlePolicy.shouldRelayMovement(
                BoyisoRole.SPEAKER,
                true,
                EnvironmentDisplayMode.MATE,
                emptyList(),
            ),
        )
    }

    @Test
    fun `speaker sound startles only an active mate viewer`() {
        assertTrue(
            BoyisoStartlePolicy.shouldActivateForSound(
                BoyisoRole.VIEWER,
                true,
                EnvironmentDisplayMode.MATE,
            ),
        )
        assertFalse(
            BoyisoStartlePolicy.shouldActivateForSound(
                BoyisoRole.VIEWER,
                true,
                EnvironmentDisplayMode.OBJECT,
            ),
        )
        assertFalse(
            BoyisoStartlePolicy.shouldActivateForSound(
                BoyisoRole.SPEAKER,
                true,
                EnvironmentDisplayMode.MATE,
            ),
        )
    }

    @Test
    fun `crying child is reserved for big or continuous sound`() {
        fun event(detail: String) = BoyisoEventSummary("말할사람", "sound", detail, "LAN", 1L)
        assertTrue(BoyisoStartlePolicy.shouldShowCryingChild(event("big_sound")))
        assertTrue(BoyisoStartlePolicy.shouldShowCryingChild(event("continuous_sound")))
        assertTrue(BoyisoStartlePolicy.shouldShowCryingChild(event("finger_snap")))
        assertFalse(BoyisoStartlePolicy.shouldShowCryingChild(event("quiet")))
    }
}
