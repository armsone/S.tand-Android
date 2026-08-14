package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoyisoStartleLightingPolicyTest {
    @Test
    fun `movement and finger snap gently rise then finish in ten seconds`() {
        val movement = BoyisoStartleLightingPolicy.profile("movement", null)
        val snap = BoyisoStartleLightingPolicy.profile("sound", "finger_snap")

        assertEquals(BoyisoStartleLightingProfile.GENTLE, movement)
        assertEquals(BoyisoStartleLightingProfile.GENTLE, snap)
        assertEquals(10_000L, movement.totalMillis)
        assertEquals(0.08f, BoyisoStartleLightingPolicy.intensityAt(movement, 0L, 0f), 0.001f)
        assertEquals(0.40f, BoyisoStartleLightingPolicy.intensityAt(movement, 2_000L, 0f), 0.001f)
        assertTrue(BoyisoStartleLightingPolicy.intensityAt(movement, 2_000L, 0f) < 1f)
        assertEquals(0f, BoyisoStartleLightingPolicy.intensityAt(movement, 10_000L, 0f), 0.001f)
    }

    @Test
    fun `large sound reaches full brightness in one second and still ends at ten seconds`() {
        val profile = BoyisoStartleLightingPolicy.profile("sound", "big_sound")

        assertEquals(BoyisoStartleLightingProfile.STRONG, profile)
        assertEquals(1_000L, profile.rampMillis)
        assertEquals(1f, BoyisoStartleLightingPolicy.intensityAt(profile, 1_000L, 0f), 0.001f)
        assertEquals(0f, BoyisoStartleLightingPolicy.intensityAt(profile, 10_000L, 0f), 0.001f)
    }

    @Test
    fun `gentle torch needs strength support while strong torch stays available`() {
        assertEquals(0.0, BoyisoStartleLightingPolicy.torchLevel(
            BoyisoStartleLightingProfile.GENTLE, true, true, false), 0.0)
        assertEquals(0.25, BoyisoStartleLightingPolicy.torchLevel(
            BoyisoStartleLightingProfile.GENTLE, true, true, true), 0.0)
        assertEquals(1.0, BoyisoStartleLightingPolicy.torchLevel(
            BoyisoStartleLightingProfile.STRONG, true, true, false), 0.0)
    }
}
