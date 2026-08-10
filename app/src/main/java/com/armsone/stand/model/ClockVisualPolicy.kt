package com.armsone.stand.model

/** A transparent horizontal interval measured from the top of a flip-clock card. */
data class ClockSplitGapBounds(
    val top: Float,
    val bottom: Float,
)

/** Pure layout values shared by the clock previews and the full-size flip clock. */
object ClockVisualPolicy {
    const val ReferenceFontSize = 64f

    private val referenceVerticalOffsets = mapOf(
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

    /**
     * Returns the visual vertical correction for [fontSize]. Positive values move glyphs down.
     * Invalid or negative sizes collapse to zero instead of propagating an unsafe layout value.
     */
    fun verticalOffset(font: ClockFontChoice, fontSize: Float): Float {
        val safeSize = fontSize.takeIf { it.isFinite() && it > 0f } ?: 0f
        return referenceVerticalOffsets.getValue(font) * (safeSize / ReferenceFontSize)
    }

    /**
     * Centers a transparent split gap in the whole card, independent of glyph bounds or offset.
     */
    fun splitGapBounds(cardHeight: Float, gapHeight: Float): ClockSplitGapBounds {
        val safeCardHeight = cardHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
        val safeGapHeight = gapHeight
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(safeCardHeight)
            ?: 0f
        val top = (safeCardHeight - safeGapHeight) / 2f
        return ClockSplitGapBounds(
            top = top,
            bottom = top + safeGapHeight,
        )
    }
}
