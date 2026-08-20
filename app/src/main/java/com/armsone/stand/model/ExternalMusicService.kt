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

/**
 * Android's Spotify/YouTube Music equivalents of iOS's Apple Music/Apple Music
 * Classical channels, plus [AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT]
 * stable internet-radio slots. [radioSlot] identifies a fixed home-card position
 * (0-based) independent of which radio configuration currently fills it, mirroring
 * the iOS `HomeMusicChannelSelection.radioSlot` source token.
 */
data class HomeMusicChannelSelection(
    val kind: HomeMusicChannelKind,
    val radioID: String? = null,
    val radioSlot: Int? = null,
) {
    val stableID: String
        get() = when (kind) {
            HomeMusicChannelKind.SPOTIFY -> "spotify"
            HomeMusicChannelKind.YOUTUBE_MUSIC -> "youtube_music"
            HomeMusicChannelKind.INTERNET_RADIO ->
                "radio:${radioSlot?.toString() ?: "legacy"}:${radioID.orEmpty()}"
        }

    fun encoded(): String = stableID

    companion object {
        val Spotify = HomeMusicChannelSelection(HomeMusicChannelKind.SPOTIFY)
        val YouTubeMusic = HomeMusicChannelSelection(HomeMusicChannelKind.YOUTUBE_MUSIC)

        fun radio(id: String, slot: Int? = null) =
            HomeMusicChannelSelection(HomeMusicChannelKind.INTERNET_RADIO, id, slot)

        fun emptyRadio(slot: Int) =
            HomeMusicChannelSelection(HomeMusicChannelKind.INTERNET_RADIO, null, slot)

        /** Decodes both the current `radio:<slot>:<id>` form and the legacy 2-slot `radio:<id>` form. */
        fun decode(value: String?): HomeMusicChannelSelection? {
            if (value.isNullOrEmpty()) return null
            return when {
                value == "spotify" -> Spotify
                value == "youtube_music" || value == "apple_music" -> YouTubeMusic
                value.startsWith("radio:") -> {
                    val rest = value.removePrefix("radio:")
                    val separatorIndex = rest.indexOf(':')
                    if (separatorIndex < 0) {
                        rest.takeIf { it.isNotBlank() }?.let { radio(it, slot = null) }
                    } else {
                        val slot = rest.substring(0, separatorIndex).toIntOrNull()
                        val id = rest.substring(separatorIndex + 1).takeIf { it.isNotBlank() }
                        if (id == null && slot == null) null else HomeMusicChannelSelection(
                            HomeMusicChannelKind.INTERNET_RADIO,
                            id,
                            slot,
                        )
                    }
                }
                else -> null
            }
        }
    }
}

/**
 * Orders the home music strip as [Spotify, YouTube Music] followed by
 * [AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT] stable radio slots (including
 * empty placeholders), mirroring iOS's `normalizedHomeMusicChannels`.
 */
object HomeMusicChannelPolicy {
    val CARD_COUNT: Int get() = 2 + AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT

    fun normalized(
        requested: List<HomeMusicChannelSelection>,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val slotCount = AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT
        val remainingRadios = radioChannels.toMutableList()
        val usedSlots = mutableSetOf<Int>()
        var hasSpotify = false
        var hasYouTubeMusic = false
        val result = mutableListOf<HomeMusicChannelSelection>()

        for (candidate in requested) {
            when (candidate.kind) {
                HomeMusicChannelKind.SPOTIFY -> if (!hasSpotify) {
                    result += HomeMusicChannelSelection.Spotify
                    hasSpotify = true
                }
                HomeMusicChannelKind.YOUTUBE_MUSIC -> if (!hasYouTubeMusic) {
                    result += HomeMusicChannelSelection.YouTubeMusic
                    hasYouTubeMusic = true
                }
                HomeMusicChannelKind.INTERNET_RADIO -> {
                    val slot = normalizedRadioSlot(candidate.radioSlot, usedSlots, slotCount)
                        ?: continue
                    usedSlots += slot
                    val matchIndex = candidate.radioID
                        ?.let { id -> remainingRadios.indexOfFirst { it.id == id } } ?: -1
                    when {
                        matchIndex >= 0 -> result += HomeMusicChannelSelection.radio(
                            remainingRadios.removeAt(matchIndex).id,
                            slot,
                        )
                        candidate.radioID == null && remainingRadios.isNotEmpty() ->
                            result += HomeMusicChannelSelection.radio(
                                remainingRadios.removeAt(0).id,
                                slot,
                            )
                        else -> result += HomeMusicChannelSelection.emptyRadio(slot)
                    }
                }
            }
        }

        if (!hasSpotify) result += HomeMusicChannelSelection.Spotify
        if (!hasYouTubeMusic) result += HomeMusicChannelSelection.YouTubeMusic

        for (slot in 0 until slotCount) {
            if (slot in usedSlots) continue
            result += if (remainingRadios.isNotEmpty()) {
                HomeMusicChannelSelection.radio(remainingRadios.removeAt(0).id, slot)
            } else {
                HomeMusicChannelSelection.emptyRadio(slot)
            }
        }

        return result
    }

    /** Swaps by (kind, radioID) identity so re-picking an already-placed radio moves it. */
    fun assigning(
        current: List<HomeMusicChannelSelection>,
        slot: Int,
        selection: HomeMusicChannelSelection,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val normalized = normalized(current, radioChannels).toMutableList()
        if (slot !in normalized.indices || !isValid(selection, radioChannels)) return normalized
        val otherIndex = normalized.indexOfFirst { candidate ->
            candidate.kind == selection.kind && candidate.radioID == selection.radioID
        }
        if (otherIndex >= 0 && otherIndex != slot) {
            val previous = normalized[slot]
            normalized[slot] = normalized[otherIndex]
            normalized[otherIndex] = previous
        } else {
            normalized[slot] = selection
        }
        return normalized
    }

    private fun normalizedRadioSlot(requested: Int?, used: Set<Int>, slotCount: Int): Int? {
        if (requested != null && requested in 0 until slotCount && requested !in used) {
            return requested
        }
        return (0 until slotCount).firstOrNull { it !in used }
    }

    private fun isValid(
        selection: HomeMusicChannelSelection,
        radioChannels: List<InternetRadioConfiguration>,
    ): Boolean = when (selection.kind) {
        HomeMusicChannelKind.SPOTIFY, HomeMusicChannelKind.YOUTUBE_MUSIC -> true
        HomeMusicChannelKind.INTERNET_RADIO -> selection.radioID == null ||
            radioChannels.any { it.id == selection.radioID }
    }
}
