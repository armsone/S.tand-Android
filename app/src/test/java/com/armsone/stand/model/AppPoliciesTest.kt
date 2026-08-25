package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPoliciesTest {
    @Test
    fun recommendedSettingsUseTheSimplifiedIosDefaults() {
        val settings = AppSettings.Recommended

        assertEquals(AppSettings.DEFAULT_CLOCK_SCALE, settings.clockScale, 0f)
        assertEquals(0.4f, settings.brightnessModeThreshold, 0f)
        assertFalse(settings.automaticDimmingEnabled)
        assertEquals(ClockHourMode.TWELVE, settings.clockHourMode)
        assertEquals(OrientationPreference.AUTOMATIC, settings.orientationPreference)
        assertEquals(
            listOf(
                StandControlKind.RECORDINGS,
                StandControlKind.BOYISO,
                StandControlKind.SETTINGS,
            ),
            settings.portraitLayout.controlOrder,
        )
    }

    @Test
    fun currentExperienceMigrationPreservesPersonalChoicesAndResetsOnlyTheContract() {
        val previous = AppSettings(
            clockScale = 1.7f,
            clockFont = ClockFontChoice.POPPINS,
            displayTheme = StandDisplayTheme.GRAYSCALE,
            brightnessModeThreshold = 0.12f,
            holdDurationSeconds = 90f,
            torchEnabled = false,
            recordingEnabled = false,
            orientationPreference = OrientationPreference.LANDSCAPE,
            clockHourMode = ClockHourMode.TWENTY_FOUR,
        )

        val migrated = CurrentExperienceMigration.apply(previous)

        assertEquals(AppSettings.DEFAULT_CLOCK_SCALE, migrated.clockScale, 0f)
        assertEquals(0.4f, migrated.brightnessModeThreshold, 0f)
        assertEquals(5f, migrated.holdDurationSeconds, 0f)
        assertTrue(migrated.torchEnabled)
        assertEquals(OrientationPreference.AUTOMATIC, migrated.orientationPreference)
        assertEquals(ClockHourMode.TWELVE, migrated.clockHourMode)
        assertEquals(ClockFontChoice.POPPINS, migrated.clockFont)
        assertEquals(StandDisplayTheme.GRAYSCALE, migrated.displayTheme)
        assertFalse(migrated.recordingEnabled)
    }

    @Test
    fun latestControlOrderMigrationUpdatesOnlyTheFormerDefaultOrder() {
        val previousDefault = listOf(
            StandControlKind.RECORDINGS,
            StandControlKind.SETTINGS,
            StandControlKind.BOYISO,
        )
        val migrated = LatestControlOrderMigration.apply(
            AppSettings.Recommended.copy(
                portraitLayout = StandScreenLayout.Portrait.copy(controlOrder = previousDefault),
                landscapeLayout = StandScreenLayout.Landscape.copy(controlOrder = previousDefault),
            ),
        )
        assertEquals(StandControlKind.DefaultOrder, migrated.portraitLayout.controlOrder)
        assertEquals(StandControlKind.DefaultOrder, migrated.landscapeLayout.controlOrder)

        val customOrder = StandControlKind.DefaultOrder.reversed()
        val custom = LatestControlOrderMigration.apply(
            AppSettings.Recommended.copy(
                portraitLayout = StandScreenLayout.Portrait.copy(controlOrder = customOrder),
            ),
        )
        assertEquals(customOrder, custom.portraitLayout.controlOrder)
    }

    @Test
    fun latestLandscapeLayoutMigrationResetsOnlyLandscapeAndPreservesLaterEditsWhenNotReapplied() {
        val portrait = StandScreenLayout.Portrait.copy(
            clock = PanelTransform(x = 0.12f, y = -0.08f, scale = 0.9f),
        )
        val previous = AppSettings.Recommended.copy(
            portraitLayout = portrait,
            landscapeLayout = StandScreenLayout.Landscape.copy(
                clock = PanelTransform(x = -0.3f, y = 0.25f, scale = 0.6f),
            ),
            clockFont = ClockFontChoice.KAKAO_BIG_SANS,
            holdDurationSeconds = 45f,
        )

        val migrated = LatestLandscapeLayoutMigration.apply(previous)

        assertEquals(StandScreenLayout.Landscape, migrated.landscapeLayout)
        assertEquals(portrait, migrated.portraitLayout)
        assertEquals(ClockFontChoice.KAKAO_BIG_SANS, migrated.clockFont)
        assertEquals(45f, migrated.holdDurationSeconds, 0f)
    }

    @Test
    fun cameraAmbientSensingIsOptIn() {
        assertFalse(AppSettings.Recommended.cameraAmbientSensingEnabled)
    }

    @Test
    fun displayThemeCyclesThroughAllFourIosThemes() {
        assertEquals(StandDisplayTheme.GRAYSCALE, StandDisplayTheme.COLOR.next())
        assertEquals(StandDisplayTheme.MIDNIGHT, StandDisplayTheme.GRAYSCALE.next())
        assertEquals(StandDisplayTheme.SAGE, StandDisplayTheme.MIDNIGHT.next())
        assertEquals(StandDisplayTheme.COLOR, StandDisplayTheme.SAGE.next())
    }

    @Test
    fun luxScaleKeepsBedroomThresholdIntuitive() {
        assertEquals(0f, AmbientLightPolicy.normalizedLux(0f), 0.0001f)
        assertTrue(AmbientLightPolicy.normalizedLux(10f) in 0.34f..0.36f)
        assertEquals(1f, AmbientLightPolicy.normalizedLux(1_000f), 0.0001f)
    }

    @Test
    fun automaticModeUsesDarkForMateAndBrightForObject() {
        assertEquals(
            EnvironmentDisplayMode.MATE,
            AmbientLightPolicy.targetMode(StandModePreference.AUTOMATIC, 0.1f, 0.35f),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientLightPolicy.targetMode(StandModePreference.AUTOMATIC, 0.8f, 0.35f),
        )
        assertEquals(
            EnvironmentDisplayMode.OBJECT,
            AmbientLightPolicy.targetMode(StandModePreference.OBJECT, 0f, 0.35f),
        )
    }

    @Test
    fun automaticModeAdoptsSystemBrightnessUnlessTheUserIsAdjustingOrFaceDown() {
        assertTrue(
            AppBrightnessSystemSyncPolicy.shouldAdoptSystemBrightness(
                isAdjustingBrightness = false,
                modePreference = StandModePreference.AUTOMATIC,
                isFaceDown = false,
            ),
        )
        assertFalse(
            AppBrightnessSystemSyncPolicy.shouldAdoptSystemBrightness(
                isAdjustingBrightness = true,
                modePreference = StandModePreference.AUTOMATIC,
                isFaceDown = false,
            ),
        )
        assertFalse(
            AppBrightnessSystemSyncPolicy.shouldAdoptSystemBrightness(
                isAdjustingBrightness = false,
                modePreference = StandModePreference.OBJECT,
                isFaceDown = false,
            ),
        )
        assertFalse(
            AppBrightnessSystemSyncPolicy.shouldAdoptSystemBrightness(
                isAdjustingBrightness = false,
                modePreference = StandModePreference.AUTOMATIC,
                isFaceDown = true,
            ),
        )
    }

    @Test
    fun batteryProtectionOnlyStopsLowUnpluggedDevice() {
        assertEquals(0.2f, BatteryProtectionPolicy.normalizedLevel(20, 100, true)!!, 0f)
        assertEquals(null, BatteryProtectionPolicy.normalizedLevel(0, 100, false))
        assertEquals(null, BatteryProtectionPolicy.normalizedLevel(-1, 100, true))
        assertEquals(null, BatteryProtectionPolicy.normalizedLevel(20, 0, true))
        assertTrue(BatteryProtectionPolicy.shouldProtect(0.2f, isCharging = false))
        assertFalse(BatteryProtectionPolicy.shouldProtect(0.2f, isCharging = true))
        assertFalse(BatteryProtectionPolicy.shouldProtect(0.5f, isCharging = false))
        assertFalse(BatteryProtectionPolicy.shouldProtect(null, isCharging = false))
        assertTrue(
            BatteryProtectionPolicy.shouldClearProtection(
                wasProtecting = true,
                shouldProtectNow = false,
            ),
        )
        assertFalse(
            BatteryProtectionPolicy.shouldClearProtection(
                wasProtecting = false,
                shouldProtectNow = false,
            ),
        )
    }

    @Test
    fun musicChannelStripCardWidthMatchesIosThresholdAndPhoneLandscapeScale() {
        assertEquals(168f, MusicChannelStripLayoutPolicy.cardWidth(760f), 0f)
        assertEquals(168f, MusicChannelStripLayoutPolicy.cardWidth(700f), 0f)
        assertEquals(177f, MusicChannelStripLayoutPolicy.cardWidth(400f), 0f)
        assertEquals(148f, MusicChannelStripLayoutPolicy.cardWidth(300f), 0f)
        assertEquals(
            168f * MusicChannelStripLayoutPolicy.PHONE_LANDSCAPE_CARD_WIDTH_SCALE,
            MusicChannelStripLayoutPolicy.cardWidth(760f, isPhoneLandscape = true),
            0f,
        )
    }

    @Test
    fun musicChannelStripScrollClampsWithinContentBounds() {
        val cardWidth = 148f
        val maximumScroll = MusicChannelStripLayoutPolicy.maximumScroll(
            viewportWidth = 360f,
            cardCount = 6,
            cardWidth = cardWidth,
        )
        assertTrue(maximumScroll > 0f)
        assertEquals(
            0f,
            MusicChannelStripLayoutPolicy.clampedOffset(120f, maximumScroll),
            0f,
        )
        assertEquals(
            -maximumScroll,
            MusicChannelStripLayoutPolicy.clampedOffset(-maximumScroll - 500f, maximumScroll),
            0f,
        )
        assertEquals(
            0f,
            MusicChannelStripLayoutPolicy.maximumScroll(viewportWidth = 2000f, cardCount = 6, cardWidth = cardWidth),
            0f,
        )
    }

    @Test
    fun phoneLandscapeSideControlsUseShortEdgeToKeepWideFoldablesInPhoneLayout() {
        assertEquals(68f, PhoneLandscapeSideControlsPolicy.CONTROL_WIDTH, 0.001f)
        assertTrue(
            PhoneLandscapeSideControlsPolicy.isEnabled(
                isPortrait = false,
                viewportWidth = 730f,
                viewportHeight = 313f,
            ),
        )
        assertFalse(
            PhoneLandscapeSideControlsPolicy.isEnabled(
                isPortrait = true,
                viewportWidth = 393f,
                viewportHeight = 852f,
            ),
        )
        assertFalse(
            PhoneLandscapeSideControlsPolicy.isEnabled(
                isPortrait = false,
                viewportWidth = 960f,
                viewportHeight = 600f,
            ),
        )
    }

    @Test
    fun musicChannelStripDragMovesOnlyWithinItsScrollableRange() {
        assertEquals(
            -120f,
            MusicChannelStripLayoutPolicy.draggedOffset(0f, -120f, 420f),
            0f,
        )
        assertEquals(
            -420f,
            MusicChannelStripLayoutPolicy.draggedOffset(-120f, -500f, 420f),
            0f,
        )
        assertEquals(
            0f,
            MusicChannelStripLayoutPolicy.draggedOffset(-120f, 500f, 420f),
            0f,
        )
    }

    @Test
    fun internetRadioTitleTapPlaysTappedChannelOrAdvancesWhilePlaying() {
        val ordered = listOf("first", "second", "third")

        assertEquals(
            "second",
            InternetRadioTitleTapPolicy.targetChannelID(
                tappedChannelID = "second",
                activeChannelID = null,
                isPlaying = false,
                orderedChannelIDs = ordered,
            ),
        )
        assertEquals(
            "third",
            InternetRadioTitleTapPolicy.targetChannelID(
                tappedChannelID = "first",
                activeChannelID = "second",
                isPlaying = true,
                orderedChannelIDs = ordered,
            ),
        )
        assertEquals(
            "first",
            InternetRadioTitleTapPolicy.targetChannelID(
                tappedChannelID = "second",
                activeChannelID = "third",
                isPlaying = true,
                orderedChannelIDs = ordered,
            ),
        )
        assertEquals(
            null,
            InternetRadioTitleTapPolicy.targetChannelID(
                tappedChannelID = "first",
                activeChannelID = "first",
                isPlaying = true,
                orderedChannelIDs = listOf("first"),
            ),
        )
    }

    @Test
    fun recordingSwipeDeletesOnAFullDragOrQuickLeftFlickOnly() {
        assertTrue(
            RecordingSwipeDeletePolicy.isDeleteGesture(
                translationX = -60f,
                translationY = 4f,
                predictedEndTranslationX = -72f,
            ),
        )
        assertTrue(
            RecordingSwipeDeletePolicy.isDeleteGesture(
                translationX = -34f,
                translationY = 3f,
                predictedEndTranslationX = -90f,
            ),
        )
        assertFalse(
            RecordingSwipeDeletePolicy.isDeleteGesture(
                translationX = -48f,
                translationY = 3f,
                predictedEndTranslationX = -52f,
            ),
        )
        assertFalse(
            RecordingSwipeDeletePolicy.isDeleteGesture(
                translationX = -80f,
                translationY = 95f,
                predictedEndTranslationX = -110f,
            ),
        )
    }

    @Test
    fun recordingSwipeRevealIsClampedToMaximumReveal() {
        assertEquals(0f, RecordingSwipeDeletePolicy.clampedReveal(40f), 0f)
        assertEquals(-40f, RecordingSwipeDeletePolicy.clampedReveal(-40f), 0f)
        assertEquals(
            -RecordingSwipeDeletePolicy.MAXIMUM_REVEAL,
            RecordingSwipeDeletePolicy.clampedReveal(-500f),
            0f,
        )
    }
}
