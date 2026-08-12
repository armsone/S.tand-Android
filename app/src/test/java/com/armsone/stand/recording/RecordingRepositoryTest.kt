package com.armsone.stand.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordingRepositoryTest {
    @Test
    fun recordingMediaFormatRecognizesSupportedExtensionsAndMimeTypes() {
        assertEquals(RecordingMediaFormat.WAV, RecordingMediaFormat.from(File("clip.WAV")))
        assertEquals("audio/wav", RecordingMediaFormat.WAV.mimeType)
        assertEquals(RecordingMediaFormat.M4A, RecordingMediaFormat.from(File("clip.m4a")))
        assertEquals("audio/mp4", RecordingMediaFormat.M4A.mimeType)
        assertNull(RecordingMediaFormat.from(File("clip.mp3")))
    }

    @Test
    fun completedRecordingFinalizerCanReplaceWavWithM4aWithoutChangingRepositoryFlow() {
        withTemporaryDirectory { directory ->
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
                durationResolver = RecordingDurationResolver { file ->
                    when (RecordingMediaFormat.from(file)) {
                        RecordingMediaFormat.WAV -> WavFiles.durationSeconds(file)
                        RecordingMediaFormat.M4A -> 1.25
                        null -> null
                    }
                },
                completedRecordingFinalizer = CompletedRecordingFinalizer { wavFile ->
                    val m4aFile = File(wavFile.parentFile, "${wavFile.nameWithoutExtension}.m4a")
                    assertTrue(wavFile.renameTo(m4aFile))
                    m4aFile
                },
            )

            val saved = repository.beginWavCandidate(
                createdAt = Instant.parse("2026-08-10T05:00:00.000Z"),
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(16_000))
            }.commit()

            assertEquals("m4a", saved.extension)
            assertFalse(File(directory, "${saved.nameWithoutExtension}.wav").exists())
            val clip = repository.reload().single()
            assertEquals(RecordingMediaFormat.M4A, clip.mediaFormat)
            assertEquals(1.25, clip.durationSeconds, 0.000_001)
            assertTrue(repository.delete(clip))
            assertFalse(saved.exists())
        }
    }

    @Test
    fun wavIsDurablyCommittedBeforeADeferredFinalizerRuns() {
        withTemporaryDirectory { directory ->
            var finalizerCalled = false
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
                durationResolver = WavRecordingDurationResolver,
                completedRecordingFinalizer = CompletedRecordingFinalizer {
                    finalizerCalled = true
                    throw IllegalStateException("simulated encoder failure")
                },
            )
            val pending = repository.beginWavCandidate(
                createdAt = Instant.parse("2026-08-10T06:00:00Z"),
                sampleRate = 16_000,
            ).apply { append(ShortArray(16_000)) }

            val wav = pending.commitWav()

            assertTrue(wav.isFile)
            assertFalse(finalizerCalled)
            assertEquals(1.0, repository.reload().single().durationSeconds, 0.000_001)
            assertThrows(IllegalStateException::class.java) { pending.finalizeCommitted() }
            assertTrue(finalizerCalled)
            assertTrue(wav.isFile)
        }
    }

    @Test
    fun fileNameRoundTripsItsMillisecondTimestamp() {
        val instant = Instant.parse("2026-08-10T01:02:03.456Z")
        val fileName = RecordingFileNames.create(
            instant = instant,
            zoneId = ZoneOffset.UTC,
            nonce = "abcdef12",
        )

        assertEquals("sleep-sound-20260810-010203-456-abcdef12.wav", fileName)
        assertEquals(instant, RecordingFileNames.parse(fileName, ZoneOffset.UTC))
        assertNull(RecordingFileNames.parse("unrelated.wav", ZoneOffset.UTC))
    }

    @Test
    fun recordingTimestampPrefersAppFileNameOverLocalFileMetadata() {
        val fileNameInstant = Instant.parse("2026-08-10T01:02:03.456Z")
        val fileName = RecordingFileNames.create(
            instant = fileNameInstant,
            zoneId = ZoneOffset.UTC,
            nonce = "abcdef12",
        )

        assertEquals(
            fileNameInstant,
            RecordingTimestampPolicy.createdAt(
                fileName = fileName,
                lastModifiedMillis = Instant.parse("2026-08-11T00:00:00Z").toEpochMilli(),
                zoneId = ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun recordingTimestampFallsBackOnlyToPositiveLocalFileMetadata() {
        val fallback = Instant.parse("2026-08-11T00:00:00Z")

        assertEquals(
            fallback,
            RecordingTimestampPolicy.createdAt(
                fileName = "legacy-recording.wav",
                lastModifiedMillis = fallback.toEpochMilli(),
                zoneId = ZoneOffset.UTC,
            ),
        )
        assertEquals(
            Instant.EPOCH,
            RecordingTimestampPolicy.createdAt(
                fileName = "legacy-recording.wav",
                lastModifiedMillis = 0L,
                zoneId = ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun repositoryWritesStandardWavCalculatesDurationAndSortsNewestFirst() {
        withTemporaryDirectory { directory ->
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )
            val olderInstant = Instant.parse("2026-08-10T01:00:00.000Z")
            val newerInstant = Instant.parse("2026-08-10T02:00:00.000Z")

            val olderFile = repository.beginWavCandidate(
                createdAt = olderInstant,
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(16_000))
            }.commit()
            val newerFile = repository.beginWavCandidate(
                createdAt = newerInstant,
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(8_000))
            }.commit()

            val clips = repository.reload()

            assertEquals(listOf(newerFile, olderFile), clips.map(RecordingClip::file))
            assertEquals(newerInstant, clips[0].createdAt)
            assertEquals(0.5, clips[0].durationSeconds, 0.000_001)
            assertEquals(olderInstant, clips[1].createdAt)
            assertEquals(1.0, clips[1].durationSeconds, 0.000_001)
            assertEquals(44L + 16_000L * Short.SIZE_BYTES, olderFile.length())
            assertEquals(clips, repository.recordings.value)
        }
    }

    @Test
    fun repositoryReadsPcm16WaveFormatExtensibleDuration() {
        withTemporaryDirectory { directory ->
            val file = File(directory, "extensible.wav")
            writeExtensiblePcm16Wav(
                file = file,
                sampleRate = 16_000,
                samples = ShortArray(8_000),
            )

            assertEquals(0.5, WavFiles.durationSeconds(file) ?: -1.0, 0.000_001)
            val clip = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            ).reload().single()
            assertEquals(file.canonicalFile, clip.file.canonicalFile)
            assertEquals(0.5, clip.durationSeconds, 0.000_001)
        }
    }

    @Test
    fun waveFormatExtensibleRejectsWrongPcmGuidAndValidBits() {
        withTemporaryDirectory { directory ->
            val wrongGuid = pcmSubformatGuid.copyOf().apply { this[0] = 0x03.toByte() }
            val wrongGuidFile = File(directory, "wrong-guid.wav")
            val wrongValidBitsFile = File(directory, "wrong-valid-bits.wav")
            writeExtensiblePcm16Wav(
                file = wrongGuidFile,
                sampleRate = 16_000,
                samples = ShortArray(160),
                subformatGuid = wrongGuid,
            )
            writeExtensiblePcm16Wav(
                file = wrongValidBitsFile,
                sampleRate = 16_000,
                samples = ShortArray(160),
                validBitsPerSample = 15,
            )

            assertNull(WavFiles.durationSeconds(wrongGuidFile))
            assertNull(WavFiles.durationSeconds(wrongValidBitsFile))
            assertTrue(
                RecordingRepository(
                    directory = directory,
                    zoneId = ZoneOffset.UTC,
                ).reload().isEmpty(),
            )
        }
    }

    @Test
    fun sealedRolloverSegmentStaysHiddenUntilTheWholeCandidateIsApproved() {
        withTemporaryDirectory { directory ->
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )
            val segment = repository.beginWavCandidate(
                createdAt = Instant.parse("2026-08-10T03:00:00.000Z"),
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(16_000))
                seal()
            }

            assertTrue(repository.reload().isEmpty())

            val committed = segment.commit()
            val clip = repository.reload().single()
            assertEquals(committed.canonicalFile, clip.file.canonicalFile)
            assertEquals(1.0, clip.durationSeconds, 0.000_001)
        }
    }

    @Test
    fun sealedRolloverSegmentCanStillBeDiscarded() {
        withTemporaryDirectory { directory ->
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )
            val segment = repository.beginWavCandidate(
                createdAt = Instant.parse("2026-08-10T04:00:00.000Z"),
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(160))
                seal()
                discard()
            }

            assertTrue(repository.reload().isEmpty())
            assertTrue(directory.walkTopDown().none { it.isFile })
        }
    }

    @Test
    fun deleteIsScopedAndIdempotent() {
        withTemporaryDirectory { directory ->
            val repository = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )
            val file = repository.beginWavCandidate(
                createdAt = Instant.parse("2026-08-10T01:00:00.000Z"),
                sampleRate = 16_000,
            ).apply {
                append(ShortArray(160))
            }.commit()
            val clip = repository.reload().single()
            val outsideFile = File.createTempFile(
                "stand-outside-",
                ".wav",
                directory.parentFile,
            ).apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }

            try {
                assertTrue(repository.delete(clip))
                assertFalse(file.exists())
                assertTrue(repository.delete(clip))
                assertFalse(repository.delete(outsideFile))
                assertTrue(outsideFile.exists())
                assertTrue(repository.recordings.value.isEmpty())
            } finally {
                outsideFile.delete()
            }
        }
    }

    private fun writeExtensiblePcm16Wav(
        file: File,
        sampleRate: Int,
        samples: ShortArray,
        validBitsPerSample: Int = 16,
        subformatGuid: ByteArray = pcmSubformatGuid,
    ) {
        require(subformatGuid.size == 16)
        val channelCount = 1
        val dataByteCount = samples.size.toLong() * Short.SIZE_BYTES
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(0L)
            output.writeAscii("RIFF")
            output.writeUInt32LittleEndian(72L + dataByteCount)
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeUInt32LittleEndian(40L)
            output.writeUInt16LittleEndian(0xfffe)
            output.writeUInt16LittleEndian(channelCount)
            output.writeUInt32LittleEndian(sampleRate.toLong())
            output.writeUInt32LittleEndian(
                sampleRate.toLong() * channelCount * Short.SIZE_BYTES,
            )
            output.writeUInt16LittleEndian(channelCount * Short.SIZE_BYTES)
            output.writeUInt16LittleEndian(16)
            output.writeUInt16LittleEndian(22)
            output.writeUInt16LittleEndian(validBitsPerSample)
            output.writeUInt32LittleEndian(0x04L)
            output.write(subformatGuid)
            output.writeAscii("FLLR")
            output.writeUInt32LittleEndian(4L)
            output.writeUInt32LittleEndian(0L)
            output.writeAscii("data")
            output.writeUInt32LittleEndian(dataByteCount)
            samples.forEach { sample ->
                output.writeUInt16LittleEndian(sample.toInt())
            }
        }
    }

    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun RandomAccessFile.writeUInt16LittleEndian(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun RandomAccessFile.writeUInt32LittleEndian(value: Long) {
        write((value and 0xff).toInt())
        write(((value ushr 8) and 0xff).toInt())
        write(((value ushr 16) and 0xff).toInt())
        write(((value ushr 24) and 0xff).toInt())
    }

    private val pcmSubformatGuid = byteArrayOf(
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x10,
        0x00,
        0x80.toByte(),
        0x00,
        0x00,
        0xaa.toByte(),
        0x00,
        0x38,
        0x9b.toByte(),
        0x71,
    )

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("stand-recordings-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
