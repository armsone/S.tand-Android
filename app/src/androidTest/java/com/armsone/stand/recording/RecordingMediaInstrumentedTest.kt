package com.armsone.stand.recording

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingMediaInstrumentedTest {
    @Test
    fun pcmWavRoundTripsThroughAacM4a() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "recording-codec-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val wavFile = File(directory, "round-trip.wav")
        val decodedFile = File(directory, "decoded.wav")
        try {
            writeToneWav(wavFile)

            val m4aFile = AacM4aRecordingFinalizer().finalize(wavFile)

            assertEquals(RecordingMediaFormat.M4A, RecordingMediaFormat.from(m4aFile))
            assertTrue(m4aFile.isFile)
            assertFalse(wavFile.exists())
            val compressedDuration = AndroidRecordingDurationResolver.durationSeconds(m4aFile)
            assertNotNull(compressedDuration)
            assertTrue(compressedDuration!! in 0.8..1.2)
            val temporarySample = File(directory, "embedded-sample.tmp.m4a")
            m4aFile.copyTo(temporarySample)
            assertTrue(
                AndroidRecordingDurationResolver.durationSeconds(temporarySample)!! in 0.8..1.2,
            )

            AndroidCompressedRecordingDecoder.decodeToWav(m4aFile, decodedFile)
            val decodedDuration = WavFiles.durationSeconds(decodedFile)
            assertNotNull(decodedDuration)
            assertTrue(decodedDuration!! in 0.8..1.2)

            val firstSource = File(directory, "first.m4a").also { m4aFile.copyTo(it) }
            val secondSource = File(directory, "second.m4a").also { m4aFile.copyTo(it) }
            val merged = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
                gapDurationSeconds = 0.5,
                compressedRecordingDecoder = AndroidCompressedRecordingDecoder,
                completedRecordingFinalizer = AacM4aRecordingFinalizer(),
            ).merge(
                listOf(
                    RecordingClip(firstSource, Instant.parse("2026-08-10T01:00:00Z"), 1.0),
                    RecordingClip(secondSource, Instant.parse("2026-08-10T01:01:00Z"), 1.0),
                ),
            )
            assertEquals(RecordingMediaFormat.M4A, merged.mediaFormat)
            assertTrue(merged.isMerged)
            val mergedDuration = AndroidRecordingDurationResolver.durationSeconds(merged.file)
            assertNotNull(mergedDuration)
            // Hardware AAC encoders can report up to roughly 450 ms of codec padding in
            // the M4A container even though the merged PCM content remains 2.5 seconds.
            assertTrue(
                "Expected merged duration in 2.3..3.1 seconds, actual=$mergedDuration",
                mergedDuration!! in 2.3..3.1,
            )

            val sampleDirectory = File(directory, "embedded-recordings")
            val sampleRepository = RecordingRepository(
                directory = sampleDirectory,
                zoneId = ZoneOffset.UTC,
                durationResolver = AndroidRecordingDurationResolver,
                completedRecordingFinalizer = AacM4aRecordingFinalizer(),
            )
            sampleRepository.installEmbeddedSamplesIfNeeded(context)
            val samples = sampleRepository.reload().sortedBy { it.durationSeconds }
            assertEquals(3, samples.size)
            assertTrue(samples.all { it.mediaFormat == RecordingMediaFormat.M4A })
            assertTrue(samples[0].durationSeconds in 4.5..5.5)
            assertTrue(samples[1].durationSeconds in 9.5..10.5)
            assertTrue(samples[2].durationSeconds in 14.5..15.5)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeToneWav(file: File) {
        val samples = ShortArray(SAMPLE_RATE) { index ->
            (sin(2.0 * PI * FREQUENCY_HZ * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.2)
                .toInt()
                .toShort()
        }
        RandomAccessFile(file, "rw").use { output ->
            WavFiles.writeHeader(output, SAMPLE_RATE, 1, 0L)
            samples.forEach { sample ->
                output.write(sample.toInt() and 0xff)
                output.write((sample.toInt() ushr 8) and 0xff)
            }
            WavFiles.writeHeader(
                output,
                SAMPLE_RATE,
                1,
                samples.size.toLong() * Short.SIZE_BYTES,
            )
            output.setLength(44L + samples.size.toLong() * Short.SIZE_BYTES)
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FREQUENCY_HZ = 440.0
    }
}
