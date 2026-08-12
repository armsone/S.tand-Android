package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifiedBrightnessModePolicyTest {
    @Test
    fun `half viewport drag spans the full brightness range`() {
        assertEquals(0.5f, SimplifiedBrightnessModePolicy.VERTICAL_DRAG_TRAVEL_RATIO, 0f)
        assertEquals(1f, SimplifiedBrightnessModePolicy.level(0.5f, -250f, 1_000f), 0.0001f)
        assertEquals(0f, SimplifiedBrightnessModePolicy.level(0.5f, 250f, 1_000f), 0.0001f)
    }

    @Test
    fun `tap levels cross the forty percent mode boundary`() {
        assertEquals(
            SimplifiedBrightnessModePolicy.MATE_TAP_LEVEL,
            SimplifiedBrightnessModePolicy.tapLevel(EnvironmentDisplayMode.OBJECT),
            0f,
        )
        assertEquals(
            SimplifiedBrightnessModePolicy.OBJECT_TAP_LEVEL,
            SimplifiedBrightnessModePolicy.tapLevel(EnvironmentDisplayMode.MATE),
            0f,
        )
        assertEquals(
            EnvironmentDisplayMode.MATE,
            SimplifiedBrightnessModePolicy.mode(0.4f, StandModePreference.AUTOMATIC),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            SimplifiedBrightnessModePolicy.mode(0.401f, StandModePreference.AUTOMATIC),
        )
    }

    @Test
    fun `endpoints remain automatic until the delayed lock commits`() {
        assertEquals(
            BrightnessAdjustment(1f, StandModePreference.AUTOMATIC),
            SimplifiedBrightnessModePolicy.stabilizedAdjustment(1f, StandModePreference.AUTOMATIC),
        )
        assertEquals(
            BrightnessAdjustment(0f, StandModePreference.MATE),
            SimplifiedBrightnessModePolicy.stabilizedAdjustment(0f, StandModePreference.AUTOMATIC),
        )
    }

    @Test
    fun `small reverse movement does not release an endpoint lock`() {
        assertEquals(
            BrightnessAdjustment(1f, StandModePreference.OBJECT),
            SimplifiedBrightnessModePolicy.stabilizedAdjustment(0.96f, StandModePreference.OBJECT),
        )
        assertEquals(
            BrightnessAdjustment(0f, StandModePreference.MATE),
            SimplifiedBrightnessModePolicy.stabilizedAdjustment(0.04f, StandModePreference.MATE),
        )
        assertEquals(
            BrightnessAdjustment(0.94f, StandModePreference.AUTOMATIC),
            SimplifiedBrightnessModePolicy.stabilizedAdjustment(0.94f, StandModePreference.OBJECT),
        )
    }
}
