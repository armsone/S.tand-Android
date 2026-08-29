package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeMusicChannelPolicyTest {
    private val radios = listOf(
        InternetRadioConfiguration("재즈", "https://example.com/jazz", "jazz"),
        InternetRadioConfiguration("클래식", "https://example.com/classic", "classic"),
    )

    @Test
    fun sixCardCountMatchesTwoExternalServicesPlusFourRadioSlots() {
        assertEquals(6, HomeMusicChannelPolicy.CARD_COUNT)
        assertEquals(4, AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT)
    }

    @Test
    fun defaultOrderIsSpotifyYouTubeMusicThenFourStableEmptyRadioSlots() {
        assertEquals(
            listOf("spotify", "youtube_music", "radio:0:", "radio:1:", "radio:2:", "radio:3:"),
            AppSettings.Recommended.normalized().homeMusicChannels.map { it.stableID },
        )
    }

    @Test
    fun assigningAnExistingChoiceSwapsTheTwoSlotsAndKeepsRadioCardsStable() {
        val result = HomeMusicChannelPolicy.assigning(
            current = listOf(
                HomeMusicChannelSelection.Spotify,
                HomeMusicChannelSelection.YouTubeMusic,
            ),
            slot = 0,
            selection = HomeMusicChannelSelection.YouTubeMusic,
            radioChannels = radios,
        )

        assertEquals(
            listOf(
                "youtube_music",
                "spotify",
                "radio:0:jazz",
                "radio:1:classic",
                "radio:2:",
                "radio:3:",
            ),
            result.map { it.stableID },
        )
    }

    @Test
    fun assigningARadioIntoAnEmptySlotFillsThatCardOnly() {
        val normalized = HomeMusicChannelPolicy.normalized(emptyList(), radios)
        val result = HomeMusicChannelPolicy.assigning(
            current = normalized,
            slot = 3,
            selection = HomeMusicChannelSelection.radio("jazz"),
            radioChannels = radios,
        )

        assertEquals(HomeMusicChannelKind.INTERNET_RADIO, result[3].kind)
        assertEquals("jazz", result[3].radioID)
    }

    @Test
    fun deletedRadioSelectionIsReplacedWithAnEmptyStableCard() {
        val result = HomeMusicChannelPolicy.normalized(
            requested = listOf(
                HomeMusicChannelSelection.radio("deleted"),
                HomeMusicChannelSelection.radio("jazz"),
            ),
            radioChannels = radios.take(1),
        )

        assertEquals(
            listOf("radio:0:", "radio:1:jazz", "spotify", "youtube_music", "radio:2:", "radio:3:"),
            result.map { it.stableID },
        )
    }

    @Test
    fun raisedCapacityKeepsExistingTwoSlotRadioPreferencesAndAddsTwoMoreEmptySlots() {
        val fourRadios = radios + listOf(
            InternetRadioConfiguration("뉴스", "https://example.com/news", "news"),
            InternetRadioConfiguration("팝", "https://example.com/pop", "pop"),
        )
        val legacyTwoSlotRequest = listOf(
            HomeMusicChannelSelection.radio("classic"),
            HomeMusicChannelSelection.radio("jazz"),
        )
        val result = HomeMusicChannelPolicy.normalized(legacyTwoSlotRequest, fourRadios)

        assertEquals("classic", result[0].radioID)
        assertEquals("jazz", result[1].radioID)
        assertEquals(6, result.size)
        assertEquals(setOf("news", "pop"), result.drop(2).mapNotNull { it.radioID }.toSet())
    }

    @Test
    fun legacyTwoSlotEncodedStringDecodesWithoutAFixedSlot() {
        val decoded = HomeMusicChannelSelection.decode("radio:jazz")
        assertEquals(HomeMusicChannelKind.INTERNET_RADIO, decoded?.kind)
        assertEquals("jazz", decoded?.radioID)
        assertNull(decoded?.radioSlot)
    }

    @Test
    fun currentSlotEncodedStringRoundTrips() {
        val selection = HomeMusicChannelSelection.radio("jazz", slot = 2)
        val decoded = HomeMusicChannelSelection.decode(selection.encoded())
        assertEquals(selection, decoded)
    }

    @Test
    fun reorderingRadioBeforeExternalMusicServicesPreservesExactOrderAcrossNormalization() {
        val standard = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )
        val moved = HomeMusicChannelPolicy.assigning(
            current = standard,
            slot = 0,
            selection = standard[2],
            radioChannels = radios,
        )
        assertEquals(
            listOf("radio:0:jazz", "youtube_music", "spotify", "radio:1:classic", "radio:2:", "radio:3:"),
            moved.map { it.stableID },
        )
        val normalizedAgain = HomeMusicChannelPolicy.normalized(moved, radios)
        assertEquals(
            listOf("radio:0:jazz", "youtube_music", "spotify", "radio:1:classic", "radio:2:", "radio:3:"),
            normalizedAgain.map { it.stableID },
        )
    }

    @Test
    fun reorderingEmptyRadioSlotBeforeConfiguredRadioPreservesBothPositionsWithoutStealing() {
        val standard = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )
        val moved = HomeMusicChannelPolicy.assigning(
            current = standard,
            slot = 2,
            selection = standard[4],
            radioChannels = radios,
        )
        assertEquals(
            listOf("spotify", "youtube_music", "radio:2:", "radio:1:classic", "radio:0:jazz", "radio:3:"),
            moved.map { it.stableID },
        )
        val normalized = HomeMusicChannelPolicy.normalized(moved, radios)
        assertEquals(
            listOf("spotify", "youtube_music", "radio:2:", "radio:1:classic", "radio:0:jazz", "radio:3:"),
            normalized.map { it.stableID },
        )
    }

    @Test
    fun reorderingEmptyRadioSlotsAmongThemselvesPreservesUniqueSlotIdentities() {
        val standard = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.emptyRadio(1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )
        val moved = HomeMusicChannelPolicy.assigning(
            current = standard,
            slot = 4,
            selection = standard[5],
            radioChannels = radios.take(1),
        )
        assertEquals(
            listOf("spotify", "youtube_music", "radio:0:jazz", "radio:1:", "radio:3:", "radio:2:"),
            moved.map { it.stableID },
        )
        val normalized = HomeMusicChannelPolicy.normalized(moved, radios.take(1))
        assertEquals(
            listOf("spotify", "youtube_music", "radio:0:jazz", "radio:1:", "radio:3:", "radio:2:"),
            normalized.map { it.stableID },
        )
    }

    @Test
    fun reorderedChannelsSurvivePersistenceRoundTrip() {
        val custom = listOf(
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(3),
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.emptyRadio(2),
        )
        val encodedStrings = custom.map { it.encoded() }
        val decoded = encodedStrings.mapNotNull { HomeMusicChannelSelection.decode(it) }
        val normalized = HomeMusicChannelPolicy.normalized(decoded, radios)
        assertEquals(custom.map { it.stableID }, normalized.map { it.stableID })
    }

    @Test
    fun assigningDistinguishesFilledRadioFromEmptyRadioWithSameSlotIndex() {
        val channels = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.emptyRadio(1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )
        // Moving emptyRadio(1) to slot 2 should swap empty slot 1 with jazz at slot 2
        val moved = HomeMusicChannelPolicy.assigning(
            current = channels,
            slot = 2,
            selection = HomeMusicChannelSelection.emptyRadio(1),
            radioChannels = radios.take(1),
        )
        assertEquals(
            listOf("spotify", "youtube_music", "radio:1:", "radio:0:jazz", "radio:2:", "radio:3:"),
            moved.map { it.stableID },
        )
        val normalized = HomeMusicChannelPolicy.normalized(moved, radios.take(1))
        assertEquals(
            listOf("spotify", "youtube_music", "radio:1:", "radio:0:jazz", "radio:2:", "radio:3:"),
            normalized.map { it.stableID },
        )
    }

    @Test
    fun movingRadioAcrossMultipleStepsPreservesOrderAndIdentity() {
        var current = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )
        // Step 1: Move classic (index 3) to index 2 (swap with jazz)
        current = HomeMusicChannelPolicy.assigning(current, 2, current[3], radios)
        assertEquals(
            listOf("spotify", "youtube_music", "radio:1:classic", "radio:0:jazz", "radio:2:", "radio:3:"),
            current.map { it.stableID },
        )
        // Step 2: Move classic (now index 2) to index 1 (swap with YouTube Music)
        current = HomeMusicChannelPolicy.assigning(current, 1, current[2], radios)
        assertEquals(
            listOf("spotify", "radio:1:classic", "youtube_music", "radio:0:jazz", "radio:2:", "radio:3:"),
            current.map { it.stableID },
        )
        // Step 3: Move classic (now index 1) to index 0 (swap with Spotify)
        current = HomeMusicChannelPolicy.assigning(current, 0, current[1], radios)
        assertEquals(
            listOf("radio:1:classic", "spotify", "youtube_music", "radio:0:jazz", "radio:2:", "radio:3:"),
            current.map { it.stableID },
        )
        // Verify normalized preserves the exact multi-step result
        val normalized = HomeMusicChannelPolicy.normalized(current, radios)
        assertEquals(
            listOf("radio:1:classic", "spotify", "youtube_music", "radio:0:jazz", "radio:2:", "radio:3:"),
            normalized.map { it.stableID },
        )
    }

    @Test
    fun directDragMovingDownwardsReordersItemsContiguously() {
        val initial = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )

        // Drag Spotify (index 0) to index 3
        val moved = HomeMusicChannelPolicy.moving(initial, fromIndex = 0, toIndex = 3, radioChannels = radios)
        assertEquals(
            listOf("youtube_music", "radio:0:jazz", "radio:1:classic", "spotify", "radio:2:", "radio:3:"),
            moved.map { it.stableID },
        )

        // Drag again from index 2 ("radio:1:classic") to index 5
        val movedAgain = HomeMusicChannelPolicy.moving(moved, fromIndex = 2, toIndex = 5, radioChannels = radios)
        assertEquals(
            listOf("youtube_music", "radio:0:jazz", "spotify", "radio:2:", "radio:3:", "radio:1:classic"),
            movedAgain.map { it.stableID },
        )
    }

    @Test
    fun directDragMovingUpwardsReordersItemsContiguously() {
        val initial = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )

        // Drag emptyRadio(3) (index 5) to index 0
        val moved = HomeMusicChannelPolicy.moving(initial, fromIndex = 5, toIndex = 0, radioChannels = radios)
        assertEquals(
            listOf("radio:3:", "spotify", "youtube_music", "radio:0:jazz", "radio:1:classic", "radio:2:"),
            moved.map { it.stableID },
        )

        // Drag radio:0:jazz (now index 3) to index 1
        val movedAgain = HomeMusicChannelPolicy.moving(moved, fromIndex = 3, toIndex = 1, radioChannels = radios)
        assertEquals(
            listOf("radio:3:", "radio:0:jazz", "spotify", "youtube_music", "radio:1:classic", "radio:2:"),
            movedAgain.map { it.stableID },
        )
    }

    @Test
    fun directDragMovingWithSameIndexOrOutOfBoundsReturnsNormalized() {
        val initial = listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
            HomeMusicChannelSelection.radio("jazz", slot = 0),
            HomeMusicChannelSelection.radio("classic", slot = 1),
            HomeMusicChannelSelection.emptyRadio(2),
            HomeMusicChannelSelection.emptyRadio(3),
        )

        assertEquals(initial.map { it.stableID }, HomeMusicChannelPolicy.moving(initial, 2, 2, radios).map { it.stableID })
        assertEquals(initial.map { it.stableID }, HomeMusicChannelPolicy.moving(initial, -1, 3, radios).map { it.stableID })
        assertEquals(initial.map { it.stableID }, HomeMusicChannelPolicy.moving(initial, 2, 99, radios).map { it.stableID })
    }

    @Test
    fun appSettingsMoveHomeMusicChannelHelperUpdatesChannels() {
        val baseSettings = AppSettings(
            internetRadioChannels = radios,
            homeMusicChannels = listOf(
                HomeMusicChannelSelection.Spotify,
                HomeMusicChannelSelection.YouTubeMusic,
                HomeMusicChannelSelection.radio("jazz", slot = 0),
                HomeMusicChannelSelection.radio("classic", slot = 1),
                HomeMusicChannelSelection.emptyRadio(2),
                HomeMusicChannelSelection.emptyRadio(3),
            ),
        )

        val updated = baseSettings.moveHomeMusicChannel(fromIndex = 0, toIndex = 2)
        assertEquals(
            listOf("youtube_music", "radio:0:jazz", "spotify", "radio:1:classic", "radio:2:", "radio:3:"),
            updated.homeMusicChannels.map { it.stableID },
        )
    }

    @Test
    fun calculateSlotCentersCalculatesExactMidpoints() {
        val heights = listOf(100f, 100f, 100f)
        val spacing = 20f
        val centers = HomeMusicChannelPolicy.calculateSlotCenters(heights, spacing)

        assertEquals(3, centers.size)
        assertEquals(50f, centers[0], 0.001f)
        assertEquals(170f, centers[1], 0.001f)
        assertEquals(290f, centers[2], 0.001f)
    }

    @Test
    fun calculateTargetIndexFindsClosestSlotIndex() {
        val slotCenters = listOf(50f, 170f, 290f, 410f)

        // Dragging slot 0
        assertEquals(0, HomeMusicChannelPolicy.calculateTargetIndex(0, 0f, slotCenters))
        assertEquals(0, HomeMusicChannelPolicy.calculateTargetIndex(0, 50f, slotCenters)) // center 100 -> closer to 50 than 170
        assertEquals(1, HomeMusicChannelPolicy.calculateTargetIndex(0, 70f, slotCenters)) // center 120 -> closer to 170 (diff 50) than 50 (diff 70)
        assertEquals(2, HomeMusicChannelPolicy.calculateTargetIndex(0, 200f, slotCenters)) // center 250 -> closer to 290 (diff 40)

        // Dragging slot 3 upwards
        assertEquals(3, HomeMusicChannelPolicy.calculateTargetIndex(3, 0f, slotCenters))
        assertEquals(2, HomeMusicChannelPolicy.calculateTargetIndex(3, -110f, slotCenters)) // center 300 -> closer to 290 (diff 10)
        assertEquals(0, HomeMusicChannelPolicy.calculateTargetIndex(3, -330f, slotCenters)) // center 80 -> closer to 50
    }

    @Test
    fun calculateItemDisplacementCalculatesSmoothShifts() {
        val draggedHeight = 100f
        val spacing = 20f
        val shiftAmount = draggedHeight + spacing // 120f

        // Downward drag: slot 0 dragged to target slot 2
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(0, 0, 2, draggedHeight, spacing), 0.001f)
        assertEquals(-shiftAmount, HomeMusicChannelPolicy.calculateItemDisplacement(1, 0, 2, draggedHeight, spacing), 0.001f)
        assertEquals(-shiftAmount, HomeMusicChannelPolicy.calculateItemDisplacement(2, 0, 2, draggedHeight, spacing), 0.001f)
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(3, 0, 2, draggedHeight, spacing), 0.001f)

        // Upward drag: slot 3 dragged to target slot 1
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(3, 3, 1, draggedHeight, spacing), 0.001f)
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(0, 3, 1, draggedHeight, spacing), 0.001f)
        assertEquals(shiftAmount, HomeMusicChannelPolicy.calculateItemDisplacement(1, 3, 1, draggedHeight, spacing), 0.001f)
        assertEquals(shiftAmount, HomeMusicChannelPolicy.calculateItemDisplacement(2, 3, 1, draggedHeight, spacing), 0.001f)
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(4, 3, 1, draggedHeight, spacing), 0.001f)

        // Dragged to same slot
        assertEquals(0f, HomeMusicChannelPolicy.calculateItemDisplacement(1, 2, 2, draggedHeight, spacing), 0.001f)
    }
}
