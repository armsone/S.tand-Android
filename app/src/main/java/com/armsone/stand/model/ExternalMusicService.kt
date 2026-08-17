package com.armsone.stand.model

enum class ExternalMusicService(
    val displayName: String,
    val packageName: String,
) {
    SPOTIFY("Spotify", "com.spotify.music"),
    YOUTUBE_MUSIC("YouTube Music", "com.google.android.apps.youtube.music"),
}

enum class ExternalMusicPlaybackState {
    IDLE,
    APP_OPENED,
    UNAVAILABLE,
}

enum class HomeMusicChannelKind {
    SPOTIFY,
    YOUTUBE_MUSIC,
    INTERNET_RADIO,
}

data class HomeMusicChannelSelection(
    val kind: HomeMusicChannelKind,
    val radioID: String? = null,
) {
    val stableID: String
        get() = when (kind) {
            HomeMusicChannelKind.SPOTIFY -> "spotify"
            HomeMusicChannelKind.YOUTUBE_MUSIC -> "youtube_music"
            HomeMusicChannelKind.INTERNET_RADIO -> "radio:${radioID.orEmpty()}"
        }

    fun encoded(): String = stableID

    companion object {
        val Spotify = HomeMusicChannelSelection(HomeMusicChannelKind.SPOTIFY)
        val YouTubeMusic = HomeMusicChannelSelection(HomeMusicChannelKind.YOUTUBE_MUSIC)

        fun radio(id: String) = HomeMusicChannelSelection(HomeMusicChannelKind.INTERNET_RADIO, id)

        fun decode(value: String?): HomeMusicChannelSelection? = when (value) {
            "spotify" -> Spotify
            "youtube_music", "apple_music" -> YouTubeMusic
            else -> value?.removePrefix("radio:")
                ?.takeIf { value.startsWith("radio:") && it.isNotBlank() }
                ?.let(::radio)
        }
    }
}

object HomeMusicChannelPolicy {
    fun normalized(
        requested: List<HomeMusicChannelSelection>,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val availableRadioIDs = radioChannels.mapTo(mutableSetOf()) { it.id }
        val candidates = buildList {
            addAll(requested)
            addAll(radioChannels.map { HomeMusicChannelSelection.radio(it.id) })
            add(HomeMusicChannelSelection.Spotify)
            add(HomeMusicChannelSelection.YouTubeMusic)
        }
        return candidates
            .filter { selection ->
                selection.kind != HomeMusicChannelKind.INTERNET_RADIO ||
                    selection.radioID in availableRadioIDs
            }
            .distinctBy(HomeMusicChannelSelection::stableID)
            .take(2)
    }

    fun assigning(
        current: List<HomeMusicChannelSelection>,
        slot: Int,
        selection: HomeMusicChannelSelection,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val normalized = normalized(current, radioChannels).toMutableList()
        if (slot !in 0..1) return normalized
        while (normalized.size < 2) normalized += listOf(
            HomeMusicChannelSelection.Spotify,
            HomeMusicChannelSelection.YouTubeMusic,
        ).first { fallback -> fallback !in normalized }
        val duplicateIndex = normalized.indexOf(selection)
        if (duplicateIndex >= 0 && duplicateIndex != slot) {
            val previous = normalized[slot]
            normalized[slot] = selection
            normalized[duplicateIndex] = previous
        } else {
            normalized[slot] = selection
        }
        return normalized(normalized, radioChannels)
    }
}
