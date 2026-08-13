package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLayoutCodecTest {
    @Test
    fun decodabilityDistinguishesCurrentPayloadsFromFutureOrCorruptPayloads() {
        val encoded = ScreenLayoutCodec.encode(StandScreenLayout.Portrait)

        assertTrue(ScreenLayoutCodec.isDecodable(encoded))
        assertFalse(ScreenLayoutCodec.isDecodable("S.tand-layout-v99|future=true"))
        assertFalse(ScreenLayoutCodec.isDecodable("corrupt"))
        assertFalse(ScreenLayoutCodec.isDecodable(null))
    }

    @Test
    fun customizedLayoutRoundTripsWithoutLosingCoordinatesGroupsOrOrder() {
        val customized = StandScreenLayout.Portrait.copy(
            clock = PanelTransform(x = 0.18f, y = -0.12f, scale = 1.4f),
            seconds = PanelTransform(x = 0.33f, y = 0.08f, scale = 0.7f),
            secondaryRadio = PanelTransform(x = -0.4f, y = 0.3f, scale = 0.6f),
            radiosGrouped = false,
            date = PanelTransform(x = -0.2f, y = 0.28f, scale = 0.8f),
            weatherGroupIds = listOf(4, 4, 9),
            controlOrder = listOf(
                StandControlKind.SETTINGS,
                StandControlKind.RECORDINGS,
                StandControlKind.FLASHLIGHT,
                StandControlKind.BRIGHTNESS,
                StandControlKind.STOP_DETECTION,
            ),
        )

        val decoded = ScreenLayoutCodec.decodeOrDefault(
            encoded = ScreenLayoutCodec.encode(customized),
            fallback = StandScreenLayout.Portrait,
        )

        assertEquals(customized, decoded)
    }

    @Test
    fun legacyV1LayoutWithoutNewOptionalPanelsStillDecodes() {
        val encoded = ScreenLayoutCodec.encode(StandScreenLayout.Portrait)
            .replace(Regex("\\|seconds=[^|]+"), "")
            .replace(Regex("\\|secondaryRadio=[^|]+"), "")
            .replace(Regex("\\|radiosGrouped=[^|]+"), "")

        val decoded = ScreenLayoutCodec.decodeOrDefault(encoded, StandScreenLayout.Landscape)

        assertEquals(PanelTransform(0.27f, 0.036f), decoded.seconds)
        assertEquals(PanelTransform(-0.26f, 0.215f, 0.75f), decoded.secondaryRadio)
        assertFalse(decoded.radiosGrouped)
    }

    @Test
    fun unknownDuplicateAndMissingControlKindsAreNormalized() {
        val encoded = ScreenLayoutCodec.encode(StandScreenLayout.Portrait)
        val altered = encoded.replace(
            oldValue = "controlOrder=recordings,settings,boyiso",
            newValue =
                "controlOrder=settings,futureControl,orientation,aiShot,settings,flashlight",
        )

        val decoded = ScreenLayoutCodec.decodeOrDefault(
            encoded = altered,
            fallback = StandScreenLayout.Portrait,
        )

        assertEquals(
            listOf(
                StandControlKind.SETTINGS,
                StandControlKind.RECORDINGS,
                StandControlKind.BOYISO,
            ),
            decoded.controlOrder,
        )
    }

    @Test
    fun missingCorruptAndPastPayloadsUseTheDirectionSpecificFallback() {
        val portrait = StandScreenLayout.Portrait
        val landscape = StandScreenLayout.Landscape

        assertEquals(
            portrait,
            ScreenLayoutCodec.decodeOrDefault(encoded = null, fallback = portrait),
        )
        assertEquals(
            landscape,
            ScreenLayoutCodec.decodeOrDefault(
                encoded = "S.tand-layout-v0|clock=0.0,0.0,1.0",
                fallback = landscape,
            ),
        )
        assertEquals(
            landscape,
            ScreenLayoutCodec.decodeOrDefault(
                encoded = "S.tand-layout-v1|clock=broken",
                fallback = landscape,
            ),
        )
    }

    @Test
    fun nonFiniteTransformFallsBackButFiniteScaleIsClamped() {
        val fallback = StandScreenLayout.Landscape
        val valid = ScreenLayoutCodec.encode(StandScreenLayout.Portrait)
        val nonFinite = valid.replace(
            oldValue = "clock=0.0,0.0,1.291905",
            newValue = "clock=NaN,0.0,1.291905",
        )
        assertEquals(
            fallback,
            ScreenLayoutCodec.decodeOrDefault(nonFinite, fallback),
        )

        val oversizedScale = valid.replace(
            oldValue = "clock=0.0,0.0,1.291905",
            newValue = "clock=0.0,0.0,9.0",
        )
        val decoded = ScreenLayoutCodec.decodeOrDefault(oversizedScale, fallback)
        assertEquals(PanelEditingPolicy.MaximumPanelScale, decoded.clock.scale, 0f)
    }

    @Test
    fun unknownOrDuplicateStructuralFieldsRejectTheWholePayload() {
        val fallback = StandScreenLayout.Landscape
        val valid = ScreenLayoutCodec.encode(StandScreenLayout.Portrait)

        assertEquals(
            fallback,
            ScreenLayoutCodec.decodeOrDefault("$valid|futureField=1", fallback),
        )
        assertEquals(
            fallback,
            ScreenLayoutCodec.decodeOrDefault("$valid|clock=0.0,0.0,1.0", fallback),
        )
    }

    @Test
    fun appSettingsNormalizationCopiesAndNormalizesBothLayouts() {
        val portrait = StandScreenLayout.Portrait.apply {
            clock = PanelTransform(
                x = Float.NaN,
                y = Float.POSITIVE_INFINITY,
                scale = 9f,
            )
            controlOrder = listOf(StandControlKind.SETTINGS, StandControlKind.SETTINGS)
        }
        val landscape = StandScreenLayout.Landscape.apply {
            weatherGroupIds = listOf(7)
        }

        val normalized = AppSettings(
            portraitLayout = portrait,
            landscapeLayout = landscape,
        ).normalized()

        assertNotSame(portrait, normalized.portraitLayout)
        assertNotSame(landscape, normalized.landscapeLayout)
        assertEquals(PanelTransform(x = 0f, y = 0f, scale = 2f), normalized.portraitLayout.clock)
        assertEquals(
            listOf(
                StandControlKind.SETTINGS,
                StandControlKind.RECORDINGS,
                StandControlKind.BOYISO,
            ),
            normalized.portraitLayout.controlOrder,
        )
        assertEquals(listOf(7, 1, 1), normalized.landscapeLayout.weatherGroupIds)
    }
}
