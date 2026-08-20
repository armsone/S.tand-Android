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
}
