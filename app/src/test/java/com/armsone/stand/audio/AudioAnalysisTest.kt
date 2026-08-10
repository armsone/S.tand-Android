package com.armsone.stand.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalysisTest {
    @Test
    fun lampEnvelopeHoldsThenFadesSmoothly() {
        val envelope = LampEnvelope(
            activatedAt = 10.0,
            holdDuration = 5.0,
            fadeDuration = 10.0,
            maximumIntensity = 0.8,
        )

        assertEquals(0.8, envelope.intensity(at = 10.0), 0.0001)
        assertEquals(0.8, envelope.intensity(at = 15.0), 0.0001)
        assertEquals(0.4, envelope.intensity(at = 20.0), 0.0001)
        assertEquals(0.0, envelope.intensity(at = 25.0), 0.0001)
        assertTrue(envelope.isFinished(at = 25.0))
    }

    @Test
    fun clapRequiresSharpRiseAndHonorsRefractoryWindow() {
        val detector = AudioEventDetector(
            configuration = AudioDetectorConfiguration(soundThresholdDB = -36f),
        )

        detector.analyze(rmsDB = -52f, peakDB = -30f, bufferDuration = 0.02, now = 1.0)
        val clap = detector.analyze(
            rmsDB = -12f,
            peakDB = -2f,
            bufferDuration = 0.02,
            now = 1.02,
        )
        detector.analyze(rmsDB = -52f, peakDB = -30f, bufferDuration = 0.02, now = 1.1)
        val repeatedClap = detector.analyze(
            rmsDB = -10f,
            peakDB = -1f,
            bufferDuration = 0.02,
            now = 1.12,
        )
        detector.analyze(rmsDB = -52f, peakDB = -30f, bufferDuration = 0.02, now = 3.0)
        val laterClap = detector.analyze(
            rmsDB = -10f,
            peakDB = -1f,
            bufferDuration = 0.02,
            now = 3.02,
        )

        assertTrue(clap.clapDetected)
        assertFalse(repeatedClap.clapDetected)
        assertTrue(laterClap.clapDetected)
    }

    @Test
    fun quietFingerSnapUsesPeakRiseToWakeScreen() {
        val detector = AudioEventDetector(
            configuration = AudioDetectorConfiguration(soundThresholdDB = -36f),
        )
        detector.analyze(rmsDB = -58f, peakDB = -42f, bufferDuration = 0.02, now = 1.0)
        val fingerSnap = detector.analyze(
            rmsDB = -53f,
            peakDB = -17f,
            bufferDuration = 0.02,
            now = 1.02,
        )

        assertTrue(fingerSnap.clapDetected)
        assertFalse(fingerSnap.isAboveSoundThreshold)
    }

    @Test
    fun sustainedSoundStartsOnlyAfterAttackDuration() {
        val detector = AudioEventDetector(
            configuration = AudioDetectorConfiguration(
                soundThresholdDB = -36f,
                soundAttackDuration = 0.1,
            ),
        )

        val detections = (0 until 5).map { index ->
            detector.analyze(
                rmsDB = -25f,
                peakDB = -12f,
                bufferDuration = 0.02,
                now = index * 0.02,
            )
        }

        assertFalse(detections[3].soundBegan)
        assertTrue(detections[4].soundBegan)
        assertTrue(detections[4].isAboveSoundThreshold)
    }

    @Test
    fun briefSoundDoesNotOpenRecordingGate() {
        val detector = AudioEventDetector(
            configuration = AudioDetectorConfiguration(
                soundThresholdDB = -36f,
                soundAttackDuration = 0.1,
            ),
        )

        repeat(3) { index ->
            val detection = detector.analyze(
                rmsDB = -24f,
                peakDB = -10f,
                bufferDuration = 0.02,
                now = index * 0.02,
            )
            assertFalse(detection.soundBegan)
        }

        val silence = detector.analyze(
            rmsDB = -60f,
            peakDB = -55f,
            bufferDuration = 0.02,
            now = 0.08,
        )
        assertFalse(silence.soundBegan)
        assertFalse(silence.isAboveSoundThreshold)
    }

    @Test
    fun sleepSoundClassifierRecognizesSnoreLikeSound() {
        val classifier = SleepSoundClassifier(releaseDuration = 0.2)
        var result: SleepSoundClassification? = null

        repeat(10) { index ->
            result = classifier.analyze(
                features = SleepSoundFeatures(
                    rmsDB = -24f,
                    peakDB = -17f,
                    zeroCrossingRate = 0.05,
                    lowFrequencyRatio = 0.75,
                    duration = 0.1,
                ),
                detection = AudioDetection(
                    clapDetected = false,
                    soundBegan = index == 0,
                    isAboveSoundThreshold = true,
                ),
            )
        }
        repeat(2) {
            result = classifier.analyze(
                features = silentSleepSoundFeatures,
                detection = silentAudioDetection,
            ) ?: result
        }

        assertEquals(SleepSoundKind.SNORE, result?.kind)
        assertTrue((result?.confidence ?: 0.0) > 0.58)
    }

    @Test
    fun sleepSoundClassifierRecognizesMovementLikeSound() {
        val classifier = SleepSoundClassifier(releaseDuration = 0.2)
        var result: SleepSoundClassification? = null

        repeat(3) { index ->
            result = classifier.analyze(
                features = SleepSoundFeatures(
                    rmsDB = -28f,
                    peakDB = -10f,
                    zeroCrossingRate = 0.3,
                    lowFrequencyRatio = 0.15,
                    duration = 0.1,
                ),
                detection = AudioDetection(
                    clapDetected = false,
                    soundBegan = index == 0,
                    isAboveSoundThreshold = true,
                ),
            )
        }
        repeat(2) {
            result = classifier.analyze(
                features = silentSleepSoundFeatures,
                detection = silentAudioDetection,
            ) ?: result
        }

        assertEquals(SleepSoundKind.MOVEMENT, result?.kind)
        assertTrue((result?.confidence ?: 0.0) > 0.55)
    }

    @Test
    fun sleepSoundClassifierRecognizesSpeechLikeVariationAsSleepTalk() {
        val classifier = SleepSoundClassifier(releaseDuration = 0.2)
        var result: SleepSoundClassification? = null

        repeat(12) { index ->
            val rmsDB = if (index % 2 == 0) -30f else -24f
            result = classifier.analyze(
                features = SleepSoundFeatures(
                    rmsDB = rmsDB,
                    peakDB = rmsDB + 9f,
                    zeroCrossingRate = 0.11,
                    lowFrequencyRatio = 0.42,
                    duration = 0.1,
                ),
                detection = AudioDetection(
                    clapDetected = false,
                    soundBegan = index == 0,
                    isAboveSoundThreshold = true,
                ),
            )
        }
        repeat(2) {
            result = classifier.analyze(
                features = silentSleepSoundFeatures,
                detection = silentAudioDetection,
            ) ?: result
        }

        assertEquals(SleepSoundKind.SLEEP_TALK, result?.kind)
        assertTrue((result?.confidence ?: 0.0) >= 0.60)
        assertTrue(result?.let(SleepSoundRecordingPolicy::shouldKeep) ?: false)
        assertFalse(result?.let(SleepSoundWakePolicy::shouldWake) ?: true)
    }

    @Test
    fun steadyBackgroundSoundIsNotSavedAsSleepTalk() {
        val classifier = SleepSoundClassifier(releaseDuration = 0.2)
        var result: SleepSoundClassification? = null

        repeat(12) { index ->
            result = classifier.analyze(
                features = SleepSoundFeatures(
                    rmsDB = -27f,
                    peakDB = -18f,
                    zeroCrossingRate = 0.11,
                    lowFrequencyRatio = 0.42,
                    duration = 0.1,
                ),
                detection = AudioDetection(
                    clapDetected = false,
                    soundBegan = index == 0,
                    isAboveSoundThreshold = true,
                ),
            )
        }
        repeat(2) {
            result = classifier.analyze(
                features = silentSleepSoundFeatures,
                detection = silentAudioDetection,
            ) ?: result
        }

        assertEquals(SleepSoundKind.OTHER, result?.kind)
        assertFalse(result?.let(SleepSoundRecordingPolicy::shouldKeep) ?: true)
    }

    @Test
    fun onlySnoreAndSleepTalkCandidatesAreKeptWhileMovementWakes() {
        val snore = SleepSoundClassification(
            kind = SleepSoundKind.SNORE,
            confidence = 0.7,
            duration = 1.0,
        )
        val sleepTalk = SleepSoundClassification(
            kind = SleepSoundKind.SLEEP_TALK,
            confidence = 0.7,
            duration = 1.0,
        )
        val movement = SleepSoundClassification(
            kind = SleepSoundKind.MOVEMENT,
            confidence = 0.7,
            duration = 0.4,
        )
        val other = SleepSoundClassification(
            kind = SleepSoundKind.OTHER,
            confidence = 0.99,
            duration = 2.0,
        )

        assertTrue(SleepSoundRecordingPolicy.shouldKeep(snore))
        assertTrue(SleepSoundRecordingPolicy.shouldKeep(sleepTalk))
        assertFalse(SleepSoundRecordingPolicy.shouldKeep(movement))
        assertFalse(SleepSoundRecordingPolicy.shouldKeep(other))
        assertFalse(SleepSoundWakePolicy.shouldWake(snore))
        assertFalse(SleepSoundWakePolicy.shouldWake(sleepTalk))
        assertTrue(SleepSoundWakePolicy.shouldWake(movement))
        assertFalse(SleepSoundWakePolicy.shouldWake(other))
    }

    private val silentSleepSoundFeatures: SleepSoundFeatures
        get() = SleepSoundFeatures(
            rmsDB = -70f,
            peakDB = -65f,
            zeroCrossingRate = 0.0,
            lowFrequencyRatio = 0.0,
            duration = 0.1,
        )

    private val silentAudioDetection: AudioDetection
        get() = AudioDetection(
            clapDetected = false,
            soundBegan = false,
            isAboveSoundThreshold = false,
        )
}
