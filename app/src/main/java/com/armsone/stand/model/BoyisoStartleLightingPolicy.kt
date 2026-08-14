package com.armsone.stand.model

/** Light-only BOISO alert timing, independent from the user's ordinary lamp hold/fade setting. */
enum class BoyisoStartleLightingProfile(
    val rampMillis: Long,
    val totalMillis: Long,
    val startingIntensity: Float,
    val peakIntensity: Float,
) {
    GENTLE(2_000L, 10_000L, 0.08f, 0.40f),
    STRONG(1_000L, 10_000L, 0.20f, 1.00f),
}

object BoyisoStartleLightingPolicy {
    fun profile(kind: String, detail: String?): BoyisoStartleLightingProfile =
        if (kind == "sound" && detail in setOf("big_sound", "continuous_sound")) {
            BoyisoStartleLightingProfile.STRONG
        } else {
            BoyisoStartleLightingProfile.GENTLE
        }

    fun intensityAt(
        profile: BoyisoStartleLightingProfile,
        elapsedMillis: Long,
        restingIntensity: Float,
    ): Float {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val resting = restingIntensity.coerceIn(0f, 1f)
        if (elapsed >= profile.totalMillis) return resting
        if (elapsed < profile.rampMillis) {
            return lerp(
                profile.startingIntensity,
                profile.peakIntensity,
                elapsed.toFloat() / profile.rampMillis.toFloat(),
            )
        }
        val fadeDuration = (profile.totalMillis - profile.rampMillis).coerceAtLeast(1L)
        return lerp(
            profile.peakIntensity,
            resting,
            (elapsed - profile.rampMillis).toFloat() / fadeDuration.toFloat(),
        )
    }

    /** Binary-only flash hardware remains off for gentle alerts. */
    fun torchLevel(
        profile: BoyisoStartleLightingProfile,
        torchEnabled: Boolean,
        roomIsDark: Boolean,
        supportsStrengthControl: Boolean,
    ): Double {
        if (!torchEnabled || !roomIsDark) return 0.0
        return when (profile) {
            BoyisoStartleLightingProfile.GENTLE -> if (supportsStrengthControl) 0.25 else 0.0
            BoyisoStartleLightingProfile.STRONG -> 1.0
        }
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * progress.coerceIn(0f, 1f)
}
