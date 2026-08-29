package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLayoutTest {
    @Test
    fun portraitAndLandscapeDefaultsMatchIosCoordinatesAndOrders() {
        val portrait = StandScreenLayout.Portrait
        assertEquals(PanelTransform(0f, 0f, 1.291905f), portrait.clock)
        assertEquals(PanelTransform(0.3355058f, 0.0578509f, 1f), portrait.seconds)
        assertEquals(
            PanelTransform(0f, -0.2049743f, 0.8692272f),
            portrait.weatherIcon,
        )
        assertEquals(portrait.weatherIcon, portrait.weatherTemperature)
        assertEquals(portrait.weatherIcon, portrait.weatherCondition)
        assertEquals(PanelTransform(0f, 0.11799486f, 1f), portrait.date)
        assertEquals(PanelTransform(0f, 0.15f, 1f), portrait.status)
        assertEquals(PanelTransform(0f, 0.21f, 1f), portrait.brightnessRule)
        assertEquals(PanelTransform(0f, 0.20698372f, 1f), portrait.battery)
        assertEquals(PanelTransform(0f, -0.31070694f, 1.047652f), portrait.radio)
        assertEquals(
            PanelTransform(-0.17436153f, 0.31097257f, 0.75f),
            portrait.secondaryRadio,
        )
        assertTrue(portrait.radiosGrouped)
        assertEquals(listOf(1, 1, 1), portrait.weatherGroupIds)
        assertEquals(StandControlKind.DefaultOrder, portrait.controlOrder)

        val landscape = StandScreenLayout.Landscape
        assertEquals(PanelTransform(0f, 0.21553229f, 1.1122912f), landscape.clock)
        assertEquals(PanelTransform(0.192f, 0.29101223f, 0.82054085f), landscape.seconds)
        assertEquals(
            PanelTransform(0f, -0.067452006f, 0.55f),
            landscape.weatherIcon,
        )
        assertEquals(landscape.weatherIcon, landscape.weatherTemperature)
        assertEquals(landscape.weatherIcon, landscape.weatherCondition)
        assertEquals(PanelTransform(0f, 0.43815008f, 0.85f), landscape.date)
        assertEquals(PanelTransform(0f, 0.5f, 1f), landscape.status)
        assertEquals(PanelTransform(0f, 0.34f, 1f), landscape.brightnessRule)
        assertEquals(PanelTransform(0f, 0.5245899f, 1f), landscape.battery)
        assertEquals(PanelTransform(0.4f, -0.3f, 0.75f), landscape.radio)
        assertEquals(
            PanelTransform(-0.4f, -0.3f, 0.75f),
            landscape.secondaryRadio,
        )
        assertFalse(landscape.radiosGrouped)
        assertEquals(listOf(1, 1, 1), landscape.weatherGroupIds)
        assertEquals(StandControlKind.DefaultOrder, landscape.controlOrder)

        val television = StandScreenLayout.Television
        assertEquals(PanelTransform(0f, -0.067452006f, 0.55f), television.weatherIcon)
        assertEquals(television.weatherIcon, television.weatherTemperature)
        assertEquals(television.weatherIcon, television.weatherCondition)
        assertEquals(0.21553229f, television.clock.y, 0.0001f)
        assertEquals(0f, television.clock.x, 0.0001f)
        assertEquals(1.1122912f, television.clock.scale, 0.0001f)
        assertFalse(television.radiosGrouped)
        assertEquals(listOf(1, 1, 1), television.weatherGroupIds)
        assertEquals(StandControlKind.DefaultOrder, television.controlOrder)
    }

    @Test
    fun controlOrderNormalizationRemovesDuplicatesAndCompletesMissingKinds() {
        assertEquals(
            listOf(
                StandControlKind.SETTINGS,
                StandControlKind.RECORDINGS,
                StandControlKind.BOYISO,
            ),
            StandControlKind.normalizedOrder(
                listOf(
                    StandControlKind.SETTINGS,
                    StandControlKind.SETTINGS,
                    StandControlKind.FLASHLIGHT,
                ),
            ),
        )
        assertEquals(
            StandControlKind.DefaultOrder,
            StandControlKind.normalizedOrder(null),
        )
        assertEquals(
            listOf(
                StandControlKind.SETTINGS,
                StandControlKind.RECORDINGS,
                StandControlKind.BOYISO,
            ),
            StandControlKind.normalizedRawOrder(
                listOf(
                    "settings",
                    "futureControl",
                    "orientation",
                    "aiShot",
                    "settings",
                    "flashlight",
                ),
            ),
        )
    }

    @Test
    fun panelScaleAndNonFiniteValuesAreDefensivelyNormalized() {
        assertEquals(0.30f, PanelEditingPolicy.clampScale(0.1f), 0f)
        assertEquals(2f, PanelEditingPolicy.clampScale(2.5f), 0f)
        assertEquals(1f, PanelEditingPolicy.clampScale(Float.NaN), 0f)

        val normalized = PanelTransform(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            scale = Float.POSITIVE_INFINITY,
        )
            .normalized()
        assertEquals(PanelTransform(x = 0f, y = 0f, scale = 1f), normalized)
    }

    @Test
    fun centerSnapUsesMiddleTenPercentOfRenderedPanel() {
        assertTrue(PanelEditingPolicy.shouldSnapToCenter(centerOffset = 6f, panelLength = 120f))
        assertFalse(
            PanelEditingPolicy.shouldSnapToCenter(centerOffset = 6.1f, panelLength = 120f),
        )
        assertTrue(PanelEditingPolicy.shouldSnapToCenter(centerOffset = -2f, panelLength = 40f))
        assertFalse(
            PanelEditingPolicy.shouldSnapToCenter(centerOffset = -2.1f, panelLength = 40f),
        )

        val snapped = PanelEditingPolicy.snappedTransform(
            transform = PanelTransform(x = 0.01f, y = 0.2f),
            panelSize = FloatSize(width = 120f, height = 40f),
            canvasSize = FloatSize(width = 600f, height = 800f),
        )
        assertEquals(0f, snapped.x, 0f)
        assertEquals(0.2f, snapped.y, 0f)
    }

    @Test
    fun panelEditingAllowsMovingBeyondTheCanvasInsets() {
        val center = PanelEditingPolicy.clampedCenter(
            proposed = FloatOffset(x = 400f, y = 800f),
            panelSize = FloatSize(width = 100f, height = 80f),
            canvasSize = FloatSize(width = 320f, height = 700f),
            insets = FloatInsets(top = 100f, left = 20f, bottom = 120f, right = 20f),
        )

        assertEquals(FloatOffset(x = 400f, y = 800f), center)
    }

    @Test
    fun panelResetRestoresOrientationDefaultsAndPreservesControlOrder() {
        val customOrder = StandControlKind.DefaultOrder.reversed()
        val customized = StandScreenLayout.Portrait.copy(
            clock = PanelTransform(x = 0.18f, y = -0.12f, scale = 1.4f),
            weatherGroupIds = listOf(4, 4, 9),
            controlOrder = customOrder,
        )

        val reset = HomeEditorResetPolicy.panels(customized, isPortrait = true)

        assertEquals(StandScreenLayout.Portrait.clock, reset.clock)
        assertEquals(StandScreenLayout.Portrait.weatherGroupIds, reset.weatherGroupIds)
        assertEquals(customOrder, reset.controlOrder)
    }

    @Test
    fun controlResetPreservesPanelsAndWeatherGroups() {
        val customized = StandScreenLayout.Landscape.copy(
            clock = PanelTransform(x = -0.16f, y = 0.08f, scale = 1.25f),
            date = PanelTransform(x = 0.22f, y = 0.18f, scale = 0.8f),
            weatherGroupIds = listOf(4, 4, 9),
            controlOrder = StandControlKind.DefaultOrder.reversed(),
        )

        val reset = HomeEditorResetPolicy.controls(customized)

        assertEquals(customized.clock, reset.clock)
        assertEquals(customized.date, reset.date)
        assertEquals(customized.weatherGroupIds, reset.weatherGroupIds)
        assertEquals(StandControlKind.DefaultOrder, reset.controlOrder)
    }

    @Test
    fun weatherGroupSplitAndMergeMatchIosGroupIdPolicy() {
        val split = WeatherGroupPolicy.splitGroup(StandScreenLayout.Portrait, groupId = 1)
        assertEquals(listOf(0, 1, 2), split.weatherGroupIds)
        assertEquals(-0.16f, split.weatherIcon.x, 0.0001f)
        assertEquals(0f, split.weatherTemperature.x, 0.0001f)
        assertEquals(0.16f, split.weatherCondition.x, 0.0001f)

        val merged = WeatherGroupPolicy.mergeIfOverlapping(
            layout = split,
            sourceGroupId = 0,
            targetGroupId = 1,
            sourceBounds = FloatRect(x = 0f, y = 0f, width = 100f, height = 100f),
            targetBounds = FloatRect(x = 60f, y = 0f, width = 100f, height = 100f),
        )
        assertEquals(listOf(1, 1, 2), merged.weatherGroupIds)
        assertEquals(merged.weatherIcon, merged.weatherTemperature)
    }

    @Test
    fun weatherMergeUsesSmallerPanelAreaAtExactFortyPercentBoundary() {
        val source = FloatRect(x = 0f, y = 0f, width = 100f, height = 100f)
        val fortyPercent = FloatRect(x = 60f, y = 0f, width = 200f, height = 100f)
        val thirtyNinePercent = FloatRect(x = 61f, y = 0f, width = 200f, height = 100f)

        assertEquals(
            PanelEditingPolicy.WeatherMergeOverlapThreshold,
            PanelEditingPolicy.overlapFraction(source, fortyPercent),
            0.0001f,
        )
        assertEquals(0.39f, PanelEditingPolicy.overlapFraction(source, thirtyNinePercent), 0.0001f)

        val split = WeatherGroupPolicy.splitGroup(StandScreenLayout.Portrait, groupId = 1)
        val retained = WeatherGroupPolicy.mergeIfOverlapping(
            layout = split,
            sourceGroupId = 0,
            targetGroupId = 1,
            sourceBounds = source,
            targetBounds = thirtyNinePercent,
        )
        val merged = WeatherGroupPolicy.mergeIfOverlapping(
            layout = split,
            sourceGroupId = 0,
            targetGroupId = 1,
            sourceBounds = source,
            targetBounds = fortyPercent,
        )

        assertEquals(listOf(0, 1, 2), retained.weatherGroupIds)
        assertEquals(listOf(1, 1, 2), merged.weatherGroupIds)
    }

    @Test
    fun radioOverlapBoundaryKeepsThirtyNinePercentMergesFortyAndSplits() {
        val separated = StandScreenLayout.Landscape.copy(
            radio = PanelTransform(x = -0.2f, y = 0.1f, scale = 1f),
            secondaryRadio = PanelTransform(x = 0.2f, y = 0.1f, scale = 0.8f),
            radiosGrouped = false,
        )
        val first = FloatRect(x = 0f, y = 0f, width = 100f, height = 100f)
        val thirtyNinePercent = FloatRect(x = 61f, y = 0f, width = 100f, height = 100f)
        val fortyPercent = FloatRect(x = 60f, y = 0f, width = 100f, height = 100f)

        assertEquals(
            separated,
            RadioGroupPolicy.mergeIfOverlapping(separated, first, thirtyNinePercent),
        )
        val grouped = RadioGroupPolicy.mergeIfOverlapping(separated, first, fortyPercent)
        assertTrue(grouped.radiosGrouped)
        assertEquals(PanelTransform(x = 0f, y = 0.1f, scale = 0.8f), grouped.radio)
        assertEquals(grouped.radio, grouped.secondaryRadio)

        val split = RadioGroupPolicy.split(grouped)
        assertFalse(split.radiosGrouped)
        assertEquals(-0.11f, split.radio.x, 0.0001f)
        assertEquals(0.11f, split.secondaryRadio.x, 0.0001f)
    }

    @Test
    fun transformedBoundsIncludePanelAndWholeDashboardScale() {
        val bounds = PanelEditingPolicy.transformedBounds(
            transform = PanelTransform(x = 0.2f, y = -0.1f, scale = 0.8f),
            panelSize = FloatSize(width = 100f, height = 80f),
            canvasSize = FloatSize(width = 1_000f, height = 600f),
            screenScale = 1.25f,
        )

        assertEquals(700f, bounds.x, 0.0001f)
        assertEquals(185f, bounds.y, 0.0001f)
        assertEquals(100f, bounds.width, 0.0001f)
        assertEquals(80f, bounds.height, 0.0001f)
    }
}
