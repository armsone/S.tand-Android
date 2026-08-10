package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockVisualPolicyTest {
    @Test
    fun referenceSizeUsesEveryIosVerticalCorrection() {
        val expected = mapOf(
            ClockFontChoice.SYSTEM_ROUNDED to -1.5f,
            ClockFontChoice.PRETENDARD to 0f,
            ClockFontChoice.KAKAO_BIG_SANS to -3f,
            ClockFontChoice.NANUM_GOTHIC to 1.5f,
            ClockFontChoice.TENADA to 8f,
            ClockFontChoice.BLACK_HAN_SANS to 1.5f,
            ClockFontChoice.DO_HYEON to 2.5f,
            ClockFontChoice.PAPERLOGY_BOLD to 0f,
            ClockFontChoice.NEXON_LV1_GOTHIC to 3.5f,
            ClockFontChoice.POPPINS to 0.5f,
        )

        assertEquals(ClockFontChoice.entries.toSet(), expected.keys)
        expected.forEach { (font, offset) ->
            assertEquals(
                font.displayName,
                offset,
                ClockVisualPolicy.verticalOffset(font, ClockVisualPolicy.ReferenceFontSize),
                0.0001f,
            )
        }
    }

    @Test
    fun verticalCorrectionScalesProportionally() {
        assertEquals(
            4f,
            ClockVisualPolicy.verticalOffset(ClockFontChoice.TENADA, 32f),
            0.0001f,
        )
        assertEquals(
            -6f,
            ClockVisualPolicy.verticalOffset(ClockFontChoice.KAKAO_BIG_SANS, 128f),
            0.0001f,
        )
    }

    @Test
    fun invalidFontSizesReturnSafeZero() {
        listOf(0f, -64f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { size ->
            assertEquals(
                0f,
                ClockVisualPolicy.verticalOffset(ClockFontChoice.TENADA, size),
                0f,
            )
        }
    }

    @Test
    fun splitGapStaysAtCardCenter() {
        assertEquals(
            ClockSplitGapBounds(top = 44f, bottom = 48f),
            ClockVisualPolicy.splitGapBounds(cardHeight = 92f, gapHeight = 4f),
        )
        assertEquals(
            ClockSplitGapBounds(top = 57f, bottom = 59f),
            ClockVisualPolicy.splitGapBounds(cardHeight = 116f, gapHeight = 2f),
        )
    }

    @Test
    fun splitGapClampsToSafeCardBounds() {
        assertEquals(
            ClockSplitGapBounds(top = 0f, bottom = 50f),
            ClockVisualPolicy.splitGapBounds(cardHeight = 50f, gapHeight = 80f),
        )
        assertEquals(
            ClockSplitGapBounds(top = 25f, bottom = 25f),
            ClockVisualPolicy.splitGapBounds(cardHeight = 50f, gapHeight = -2f),
        )
        assertEquals(
            ClockSplitGapBounds(top = 0f, bottom = 0f),
            ClockVisualPolicy.splitGapBounds(cardHeight = -50f, gapHeight = 4f),
        )
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(
                ClockSplitGapBounds(top = 0f, bottom = 0f),
                ClockVisualPolicy.splitGapBounds(cardHeight = invalid, gapHeight = 4f),
            )
            assertEquals(
                ClockSplitGapBounds(top = 25f, bottom = 25f),
                ClockVisualPolicy.splitGapBounds(cardHeight = 50f, gapHeight = invalid),
            )
        }
    }
}
