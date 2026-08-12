package com.armsone.stand.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMonitorTest {
    @Test
    fun continuousRecordingNeverOpensAContinuationAfterFourPendingSegments() {
        assertTrue(AudioPendingSegmentPolicy.canOpenContinuation(0))
        assertTrue(AudioPendingSegmentPolicy.canOpenContinuation(3))
        assertTrue(!AudioPendingSegmentPolicy.canOpenContinuation(4))
        assertTrue(!AudioPendingSegmentPolicy.canOpenContinuation(5))
    }

    @Test
    fun candidateRollsAtNinetySecondsButNotBefore() {
        val startedAt = 12_000_000_000L

        assertTrue(
            !AudioClipDurationPolicy.shouldRoll(
                startedAt,
                startedAt + AudioClipDurationPolicy.MaximumClipDurationNanos - 1L,
            ),
        )
        assertTrue(
            AudioClipDurationPolicy.shouldRoll(
                startedAt,
                startedAt + AudioClipDurationPolicy.MaximumClipDurationNanos,
            ),
        )
        assertTrue(!AudioClipDurationPolicy.shouldRoll(startedAt, startedAt - 1L))
    }

    @Test
    fun maximumLengthSegmentWaitsForClassificationAndPostRollAfterSignalDrops() {
        val startedAt = 12_000_000_000L
        val maximumBoundary = startedAt + AudioClipDurationPolicy.MaximumClipDurationNanos
        val postRollDeadline = maximumBoundary + 1_400_000_000L

        assertEquals(
            AudioCandidateFrameAction.SEAL_AND_WAIT,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = true,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = postRollDeadline,
                nowNanos = maximumBoundary,
                isAboveSoundThreshold = false,
                isApproved = false,
            ),
        )
        assertEquals(
            AudioCandidateFrameAction.RETAIN,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = false,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = postRollDeadline,
                nowNanos = maximumBoundary + 180_000_000L,
                isAboveSoundThreshold = false,
                isApproved = true,
            ),
        )
        assertEquals(
            AudioCandidateFrameAction.RETAIN,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = false,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = postRollDeadline,
                nowNanos = postRollDeadline,
                isAboveSoundThreshold = false,
                isApproved = false,
            ),
        )
        assertEquals(
            AudioCandidateFrameAction.FINALIZE,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = false,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = postRollDeadline,
                nowNanos = postRollDeadline,
                isAboveSoundThreshold = false,
                isApproved = true,
            ),
        )
    }

    @Test
    fun continuousSignalKeepsRollingAndCanResumeFromASealedOnlyCandidate() {
        val startedAt = 12_000_000_000L
        val maximumBoundary = startedAt + AudioClipDurationPolicy.MaximumClipDurationNanos

        assertEquals(
            AudioCandidateFrameAction.SEAL_AND_CONTINUE,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = true,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = maximumBoundary + 1_400_000_000L,
                nowNanos = maximumBoundary,
                isAboveSoundThreshold = true,
                isApproved = false,
            ),
        )
        assertEquals(
            AudioCandidateFrameAction.OPEN_CONTINUATION,
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = false,
                segmentStartedAtNanos = startedAt,
                silenceDeadlineNanos = maximumBoundary + 1_400_000_000L,
                nowNanos = maximumBoundary + 20_000_000L,
                isAboveSoundThreshold = true,
                isApproved = false,
            ),
        )
    }

    @Test
    fun pcmAnalyzerReportsSilenceAtTheNoiseFloor() {
        val analysis = Pcm16AudioAnalyzer.analyze(
            samples = ShortArray(320),
            sampleRate = 16_000,
        )

        assertEquals(-90.0, analysis.rmsDB.toDouble(), 0.001)
        assertEquals(-90.0, analysis.peakDB.toDouble(), 0.001)
        assertEquals(0.0, analysis.normalizedLevel, 0.000_001)
        assertEquals(0.0, analysis.features.zeroCrossingRate, 0.000_001)
        assertEquals(0.0, analysis.features.lowFrequencyRatio, 0.000_001)
        assertEquals(0.02, analysis.features.duration, 0.000_001)
    }

    @Test
    fun pcmAnalyzerRecognizesAFullScaleSharpSignal() {
        val samples = ShortArray(320) { index ->
            if (index % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
        }

        val analysis = Pcm16AudioAnalyzer.analyze(
            samples = samples,
            sampleRate = 16_000,
        )

        assertTrue(analysis.rmsDB > -0.01f)
        assertEquals(0.0, analysis.peakDB.toDouble(), 0.001)
        assertEquals(1.0, analysis.normalizedLevel, 0.000_001)
        assertEquals(1.0, analysis.features.zeroCrossingRate, 0.000_001)
        assertTrue(analysis.features.lowFrequencyRatio < 0.05)
    }

    @Test
    fun preRollKeepsOnlyTheNewestConfiguredSamples() {
        val preRoll = Pcm16PreRollBuffer(maximumSamples = 5)

        preRoll.add(shortArrayOf(1, 2, 3))
        preRoll.add(shortArrayOf(4, 5, 6))

        assertEquals(5, preRoll.sampleCount)
        assertArrayEquals(shortArrayOf(2, 3, 4, 5, 6), preRoll.snapshot())
    }
}
