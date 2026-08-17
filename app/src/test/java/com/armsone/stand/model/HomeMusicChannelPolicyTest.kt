package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMusicChannelPolicyTest {
    private val radios = listOf(
        InternetRadioConfiguration("재즈", "https://example.com/jazz", "jazz"),
        InternetRadioConfiguration("클래식", "https://example.com/classic", "classic"),
    )

    @Test
    fun defaultAndroidMusicButtonsAreSpotifyAndYouTubeMusic() {
        assertEquals(
            listOf("spotify", "youtube_music"),
            AppSettings.Recommended.normalized().homeMusicChannels.map { it.stableID },
        )
    }

    @Test
    fun assigningAnExistingChoiceSwapsTheTwoSlots() {
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
            listOf("youtube_music", "spotify"),
            result.map { it.stableID },
        )
    }

    @Test
    fun deletedRadioSelectionIsReplacedWithAnAvailableService() {
        val result = HomeMusicChannelPolicy.normalized(
            requested = listOf(
                HomeMusicChannelSelection.radio("deleted"),
                HomeMusicChannelSelection.radio("jazz"),
            ),
            radioChannels = radios.take(1),
        )

        assertEquals(listOf("radio:jazz", "spotify"), result.map { it.stableID })
    }
}
