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
        val validRadioIDsInRequested = mutableSetOf<String>()
        for (candidate in requested) {
            if (candidate.kind == HomeMusicChannelKind.INTERNET_RADIO && candidate.radioID != null) {
                if (candidate.radioID !in validRadioIDsInRequested && radioChannels.any { it.id == candidate.radioID }) {
                    validRadioIDsInRequested.add(candidate.radioID)
                }
            }
        }
        val unplacedRadios = radioChannels.filter { it.id !in validRadioIDsInRequested }.toMutableList()
        val remainingRequestedRadioIDs = validRadioIDsInRequested.toMutableSet()
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
                    val requestedId = candidate.radioID
                    when {
                        requestedId != null && remainingRequestedRadioIDs.remove(requestedId) -> {
                            result += HomeMusicChannelSelection.radio(requestedId, slot)
                        }
                        unplacedRadios.isNotEmpty() && (candidate.radioID == null || candidate.radioSlot == null) -> {
                            result += HomeMusicChannelSelection.radio(
                                unplacedRadios.removeAt(0).id,
                                slot,
                            )
                        }
                        else -> {
                            result += HomeMusicChannelSelection.emptyRadio(slot)
                        }
                    }
                }
            }
        }

        if (!hasSpotify) result += HomeMusicChannelSelection.Spotify
        if (!hasYouTubeMusic) result += HomeMusicChannelSelection.YouTubeMusic

        for (slot in 0 until slotCount) {
            if (slot in usedSlots) continue
            result += if (unplacedRadios.isNotEmpty()) {
                HomeMusicChannelSelection.radio(unplacedRadios.removeAt(0).id, slot)
            } else {
                HomeMusicChannelSelection.emptyRadio(slot)
            }
        }

        return result
    }

    /**
     * Moves a channel selection directly from [fromIndex] to [toIndex] (like iOS's drag-and-drop reordering).
     */
    fun moving(
        current: List<HomeMusicChannelSelection>,
        fromIndex: Int,
        toIndex: Int,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val normalized = normalized(current, radioChannels).toMutableList()
        if (fromIndex !in normalized.indices || toIndex !in normalized.indices || fromIndex == toIndex) {
            return normalized
        }
        val item = normalized.removeAt(fromIndex)
        val boundedDestination = toIndex.coerceIn(0, normalized.size)
        normalized.add(boundedDestination, item)
        return normalized
    }

    /** Swaps by (kind, radioSlot, radioID) identity so re-picking an already-placed radio or slot moves it. */
    fun assigning(
        current: List<HomeMusicChannelSelection>,
        slot: Int,
        selection: HomeMusicChannelSelection,
        radioChannels: List<InternetRadioConfiguration>,
    ): List<HomeMusicChannelSelection> {
        val normalized = normalized(current, radioChannels).toMutableList()
        if (slot !in normalized.indices || !isValid(selection, radioChannels)) return normalized
        val otherIndex = normalized.indexOfFirst { candidate ->
            when (selection.kind) {
                HomeMusicChannelKind.SPOTIFY, HomeMusicChannelKind.YOUTUBE_MUSIC ->
                    candidate.kind == selection.kind
                HomeMusicChannelKind.INTERNET_RADIO ->
                    candidate.kind == HomeMusicChannelKind.INTERNET_RADIO && if (selection.radioID != null) {
                        candidate.radioID == selection.radioID
                    } else {
                        candidate.radioID == null && selection.radioSlot != null && candidate.radioSlot == selection.radioSlot
                    }
            }
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

    fun calculateSlotCenters(
        itemHeightsPx: List<Float>,
        spacingPx: Float,
    ): List<Float> {
        if (itemHeightsPx.isEmpty()) return emptyList()
        val centers = ArrayList<Float>(itemHeightsPx.size)
        var currentTop = 0f
        for (height in itemHeightsPx) {
            centers.add(currentTop + height / 2f)
            currentTop += height + spacingPx
        }
        return centers
    }

    fun calculateTargetIndex(
        draggedIndex: Int,
        dragOffsetY: Float,
        slotCenters: List<Float>,
    ): Int {
        if (draggedIndex !in slotCenters.indices) return draggedIndex
        val currentCenter = slotCenters[draggedIndex] + dragOffsetY
        var closestIndex = draggedIndex
        var minDistance = Float.MAX_VALUE
        for (i in slotCenters.indices) {
            val distance = kotlin.math.abs(slotCenters[i] - currentCenter)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }
        return closestIndex
    }

    fun calculateItemDisplacement(
        itemIndex: Int,
        draggedIndex: Int?,
        targetIndex: Int?,
        draggedItemHeightPx: Float,
        spacingPx: Float,
    ): Float {
        if (draggedIndex == null || targetIndex == null || draggedIndex == targetIndex) return 0f
        if (itemIndex == draggedIndex) return 0f
        val shiftAmount = draggedItemHeightPx + spacingPx
        return when {
            draggedIndex < targetIndex && itemIndex in (draggedIndex + 1)..targetIndex -> -shiftAmount
            draggedIndex > targetIndex && itemIndex in targetIndex until draggedIndex -> shiftAmount
            else -> 0f
        }
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
