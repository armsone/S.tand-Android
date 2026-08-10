package com.armsone.stand.ui

import com.armsone.stand.ui.components.BurnInProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurnInProtectionTest {
    @Test
    fun pathMovesOncePerMinuteWithinFiveDp() {
        val start = 978_307_200_000L
        val offsets = (0 until 8).map { index ->
            BurnInProtection.offsetAt(start + index * 60_000L)
        }

        assertEquals(8, offsets.distinct().size)
        assertTrue(offsets.all { it.xDp in -5..5 && it.yDp in -5..5 })
        assertEquals(offsets.first(), BurnInProtection.offsetAt(start + 8 * 60_000L))
    }

    @Test
    fun schedulesOnlyAtTheNextMinuteBoundary() {
        assertEquals(60_000L, BurnInProtection.millisUntilNextMinute(120_000L))
        assertEquals(1L, BurnInProtection.millisUntilNextMinute(179_999L))
        assertEquals(1L, BurnInProtection.millisUntilNextMinute(-1L))
    }
}
