package com.armsone.stand.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AudioDetectorConfiguration(
    var soundThresholdDB: Float,
    var clapPeakThresholdDB: Float = -18f,
    var clapRiseDB: Float = 6f,
    var clapPeakRiseDB: Float = 8f,
    var clapRefractoryInterval: Double = 1.5,
    var soundAttackDuration: Double = 0.06,
)

data class AudioDetection(
    val clapDetected: Boolean,
    val soundBegan: Boolean,
    val isAboveSoundThreshold: Boolean,
)

data class AdaptiveNoiseState(
    val noiseFloorDB: Float?,
    val effectiveSoundThresholdDB: Float,
    val effectiveClapPeakThresholdDB: Float,
    val calibrationProgress: Double,
) {
    val isCalibrated: Boolean get() = calibrationProgress >= 1.0
}

object AdaptiveSoundThresholdPolicy {
    const val CalibrationDurationSeconds = 60.0
    const val QuietestThresholdDB = -58f
    const val LoudestThresholdDB = -18f

    fun soundThreshold(noiseFloorDB: Float?): Float {
        val floor = noiseFloorDB ?: return -50f
        val margin = when {
            floor < -50f -> 3f
            floor < -35f -> 4f
            else -> 6f
        }
        return (floor + margin).coerceIn(QuietestThresholdDB, LoudestThresholdDB)
    }

    fun clapPeakThreshold(noiseFloorDB: Float?): Float =
        (soundThreshold(noiseFloorDB) + 9f).coerceIn(-45f, -8f)
}

class AdaptiveNoiseFloorTracker {
    private var totalObservedDuration = 0.0
    private var currentBucketDuration = 0.0
    private val currentBucketSamples = mutableListOf<Float>()
    private val calibrationBuckets = mutableListOf<Float>()
    private val adaptationBuckets = ArrayDeque<Float>()
    private var noiseFloorDB: Float? = null

    val state: AdaptiveNoiseState
        get() = AdaptiveNoiseState(
            noiseFloorDB = noiseFloorDB,
            effectiveSoundThresholdDB = AdaptiveSoundThresholdPolicy.soundThreshold(noiseFloorDB),
            effectiveClapPeakThresholdDB =
                AdaptiveSoundThresholdPolicy.clapPeakThreshold(noiseFloorDB),
            calibrationProgress =
                (totalObservedDuration / AdaptiveSoundThresholdPolicy.CalibrationDurationSeconds)
                    .coerceIn(0.0, 1.0),
        )

    fun observe(rmsDB: Float, duration: Double): AdaptiveNoiseState {
        val safeDuration = duration.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        totalObservedDuration += safeDuration
        currentBucketDuration += safeDuration
        currentBucketSamples += rmsDB.takeIf { it.isFinite() }?.coerceIn(-90f, 0f) ?: -90f
        if (currentBucketDuration >= BucketDurationSeconds) finishCurrentBucket()
        return state
    }

    fun reset() {
        totalObservedDuration = 0.0
        currentBucketDuration = 0.0
        currentBucketSamples.clear()
        calibrationBuckets.clear()
        adaptationBuckets.clear()
        noiseFloorDB = null
    }

    private fun finishCurrentBucket() {
        if (currentBucketSamples.isEmpty()) return
        val bucketFloor = percentile(currentBucketSamples, 0.35)
        currentBucketDuration = 0.0
        currentBucketSamples.clear()
        if (totalObservedDuration <= AdaptiveSoundThresholdPolicy.CalibrationDurationSeconds) {
            calibrationBuckets += bucketFloor
            noiseFloorDB = percentile(calibrationBuckets, 0.5)
            return
        }
        adaptationBuckets.addLast(bucketFloor)
        while (adaptationBuckets.size > AdaptationWindowCount) adaptationBuckets.removeFirst()
        val candidate = percentile(adaptationBuckets.toList(), 0.5)
        val current = noiseFloorDB
        if (current == null) {
            noiseFloorDB = candidate
        } else {
            val rate = if (candidate > current) 0.12f else 0.22f
            noiseFloorDB = current + (candidate - current) * rate
        }
    }

    private fun percentile(values: List<Float>, fraction: Double): Float {
        if (values.isEmpty()) return -90f
        val sorted = values.sorted()
        val position = ((sorted.lastIndex * fraction).roundToInt()).coerceIn(0, sorted.lastIndex)
        return sorted[position]
    }

    private companion object {
        const val BucketDurationSeconds = 1.0
        const val AdaptationWindowCount = 8
    }
}

enum class SleepSoundKind(val rawValue: String) {
    SNORE("snore"),
    SLEEP_TALK("sleepTalk"),
    MOVEMENT("movement"),
    OTHER("other"),
}

data class SleepSoundFeatures(
    val rmsDB: Float,
    val peakDB: Float,
    val zeroCrossingRate: Double,
    val lowFrequencyRatio: Double,
    val duration: Double,
)

data class SleepSoundClassification(
    val kind: SleepSoundKind,
    val confidence: Double,
    val duration: Double,
)

object SleepSoundRecordingPolicy {
    fun shouldKeep(classification: SleepSoundClassification): Boolean =
        when (classification.kind) {
            SleepSoundKind.SNORE -> classification.confidence >= 0.58
            SleepSoundKind.SLEEP_TALK -> classification.confidence >= 0.60
            SleepSoundKind.MOVEMENT,
            SleepSoundKind.OTHER -> false
        }
}

object SleepSoundWakePolicy {
    fun shouldWake(classification: SleepSoundClassification): Boolean =
        classification.kind == SleepSoundKind.MOVEMENT && classification.confidence >= 0.55
}

class SleepSoundClassifier(
    private val releaseDuration: Double = 0.18,
) {
    private var isCollecting = false
    private var soundDuration = 0.0
    private var silenceDuration = 0.0
    private var weightedCrestDB = 0.0
    private var weightedZeroCrossingRate = 0.0
    private var weightedLowFrequencyRatio = 0.0
    private var minimumRMSDB = Float.POSITIVE_INFINITY
    private var maximumRMSDB = Float.NEGATIVE_INFINITY

    fun analyze(
        features: SleepSoundFeatures,
        detection: AudioDetection,
    ): SleepSoundClassification? {
        if (detection.soundBegan && !isCollecting) {
            isCollecting = true
        }
        if (!isCollecting) return null

        if (detection.isAboveSoundThreshold) {
            silenceDuration = 0.0
            soundDuration += features.duration
            val crestDB = (features.peakDB - features.rmsDB).toDouble()
            weightedCrestDB += crestDB * features.duration
            weightedZeroCrossingRate += features.zeroCrossingRate * features.duration
            weightedLowFrequencyRatio += features.lowFrequencyRatio * features.duration
            minimumRMSDB = min(minimumRMSDB, features.rmsDB)
            maximumRMSDB = max(maximumRMSDB, features.rmsDB)
            return null
        }

        silenceDuration += features.duration
        if (silenceDuration < releaseDuration) return null

        val classification = classifyCurrentSound()
        reset()
        return classification
    }

    fun reset() {
        isCollecting = false
        soundDuration = 0.0
        silenceDuration = 0.0
        weightedCrestDB = 0.0
        weightedZeroCrossingRate = 0.0
        weightedLowFrequencyRatio = 0.0
        minimumRMSDB = Float.POSITIVE_INFINITY
        maximumRMSDB = Float.NEGATIVE_INFINITY
    }

    private fun classifyCurrentSound(): SleepSoundClassification {
        val duration = max(soundDuration, 0.001)
        val crestDB = weightedCrestDB / duration
        val zeroCrossingRate = weightedZeroCrossingRate / duration
        val lowFrequencyRatio = weightedLowFrequencyRatio / duration

        val movementScore = min(
            1.0,
            max(
                0.0,
                (1.4 - duration) / 1.4 * 0.35 +
                    max(0.0, crestDB - 7.0) / 14.0 * 0.25 +
                    zeroCrossingRate / 0.28 * 0.2 +
                    max(0.0, 0.5 - lowFrequencyRatio) / 0.5 * 0.2,
            ),
        )
        val snoreScore = min(
            1.0,
            max(
                0.0,
                min(1.0, duration / 1.2) * 0.35 +
                    lowFrequencyRatio * 0.4 +
                    max(0.0, 0.2 - zeroCrossingRate) / 0.2 * 0.15 +
                    max(0.0, 14.0 - crestDB) / 14.0 * 0.1,
            ),
        )

        if (duration <= 1.5 && movementScore >= 0.55 && movementScore > snoreScore) {
            return SleepSoundClassification(
                kind = SleepSoundKind.MOVEMENT,
                confidence = movementScore,
                duration = soundDuration,
            )
        }
        if (duration >= 0.45 && lowFrequencyRatio >= 0.45 && snoreScore >= 0.58) {
            return SleepSoundClassification(
                kind = SleepSoundKind.SNORE,
                confidence = snoreScore,
                duration = soundDuration,
            )
        }

        val rmsRange = (maximumRMSDB - minimumRMSDB).toDouble()
        val sleepTalkScore = min(
            1.0,
            max(
                0.0,
                min(1.0, max(0.0, (duration - 0.55) / 1.45)) * 0.35 +
                    min(1.0, max(0.0, 1.0 - abs(zeroCrossingRate - 0.11) / 0.11)) * 0.25 +
                    min(1.0, max(0.0, 1.0 - abs(lowFrequencyRatio - 0.43) / 0.32)) * 0.25 +
                    min(1.0, max(0.0, (18.0 - crestDB) / 10.0)) * 0.15,
            ),
        )
        val isSpeechLike = duration >= 0.70 &&
            duration <= 30.0 &&
            crestDB <= 18.0 &&
            zeroCrossingRate in 0.025..0.24 &&
            lowFrequencyRatio in 0.18..0.72 &&
            rmsRange >= 3.5

        if (isSpeechLike && sleepTalkScore >= 0.60) {
            return SleepSoundClassification(
                kind = SleepSoundKind.SLEEP_TALK,
                confidence = sleepTalkScore,
                duration = soundDuration,
            )
        }
        return SleepSoundClassification(
            kind = SleepSoundKind.OTHER,
            confidence = max(movementScore, snoreScore),
            duration = soundDuration,
        )
    }
}

class AudioEventDetector(
    var configuration: AudioDetectorConfiguration,
) {
    var previousRMSDB: Float = -90f
        private set
    var previousPeakDB: Float = -90f
        private set

    private var loudDuration = 0.0
    private var lastClapTime = Double.NEGATIVE_INFINITY
    private var soundIsActive = false

    fun analyze(
        rmsDB: Float,
        peakDB: Float,
        bufferDuration: Double,
        now: Double,
    ): AudioDetection {
        val isAboveThreshold = rmsDB >= configuration.soundThresholdDB
        loudDuration = if (isAboveThreshold) loudDuration + bufferDuration else 0.0

        if (!isAboveThreshold) {
            soundIsActive = false
        }
        val soundBegan = !soundIsActive && loudDuration >= configuration.soundAttackDuration
        if (soundBegan) {
            soundIsActive = true
        }

        val roseQuickly = rmsDB - previousRMSDB >= configuration.clapRiseDB ||
            peakDB - previousPeakDB >= configuration.clapPeakRiseDB
        val isSharpTransient = peakDB >= configuration.clapPeakThresholdDB
        val isOutsideRefractoryWindow =
            now - lastClapTime >= configuration.clapRefractoryInterval
        val clapDetected = roseQuickly && isSharpTransient && isOutsideRefractoryWindow

        if (clapDetected) {
            lastClapTime = now
        }
        previousRMSDB = rmsDB
        previousPeakDB = peakDB

        return AudioDetection(
            clapDetected = clapDetected,
            soundBegan = soundBegan,
            isAboveSoundThreshold = isAboveThreshold,
        )
    }

    fun reset() {
        previousRMSDB = -90f
        previousPeakDB = -90f
        loudDuration = 0.0
        lastClapTime = Double.NEGATIVE_INFINITY
        soundIsActive = false
    }
}

data class LampEnvelope(
    val activatedAt: Double,
    val holdDuration: Double,
    val fadeDuration: Double,
    val maximumIntensity: Double,
) {
    fun intensity(at: Double): Double {
        val elapsed = max(0.0, at - activatedAt)

        if (elapsed <= holdDuration) {
            return maximumIntensity
        }

        if (fadeDuration <= 0.0) return 0.0
        val fadeProgress = min(1.0, (elapsed - holdDuration) / fadeDuration)
        val easedProgress = fadeProgress * fadeProgress * (3.0 - 2.0 * fadeProgress)
        return maximumIntensity * (1.0 - easedProgress)
    }

    fun isFinished(at: Double): Boolean =
        at >= activatedAt + holdDuration + max(0.0, fadeDuration)
}
