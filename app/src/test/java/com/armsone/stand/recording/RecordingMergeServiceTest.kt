package com.armsone.stand.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMergeServiceTest {
    @Test
    fun compressedSourcesAreDecodedForMergeAndFinalOutputCanBeM4a() {
        withTemporaryDirectory { directory ->
            val decodedTemplate = writeClip(
                directory = directory,
                fileName = "decoded-template.wav",
                createdAt = Instant.EPOCH,
                sampleRate = 8,
                samples = shortArrayOf(1, 2, 3, 4),
            ).file
            val firstFile = File(directory, "first.m4a").apply { writeBytes(byteArrayOf(1)) }
            val secondFile = File(directory, "second.m4a").apply { writeBytes(byteArrayOf(2)) }
            val first = RecordingClip(
                firstFile,
                Instant.parse("2026-08-10T01:00:00Z"),
                0.5,
            )
            val second = RecordingClip(
                secondFile,
                Instant.parse("2026-08-10T02:00:00Z"),
                0.5,
            )
            var decodeCount = 0
            val service = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
                gapDurationSeconds = 0.5,
                compressedRecordingDecoder = CompressedRecordingDecoder { _, output ->
                    decodeCount += 1
                    Files.copy(decodedTemplate.toPath(), output.toPath())
                },
                completedRecordingFinalizer = CompletedRecordingFinalizer { wav ->
                    val m4a = File(wav.parentFile, "${wav.nameWithoutExtension}.m4a")
                    assertTrue(wav.renameTo(m4a))
                    m4a
                },
            )

            val merged = service.merge(listOf(second, first))

            assertEquals(2, decodeCount)
            assertEquals(RecordingMediaFormat.M4A, merged.mediaFormat)
            assertTrue(merged.isMerged)
            assertTrue(firstFile.exists())
            assertTrue(secondFile.exists())
            assertTrue(
                directory.listFiles().orEmpty().none { file ->
                    file.name.endsWith(".decode.wav")
                },
            )
        }
    }

    @Test
    fun mergeSortsChronologicallyAddsHalfSecondGapAndPreservesSources() {
        withTemporaryDirectory { directory ->
            val olderInstant = Instant.parse("2026-08-10T01:00:00.000Z")
            val newerInstant = Instant.parse("2026-08-10T02:00:00.000Z")
            val olderSamples = shortArrayOf(100, 200, 300, 400)
            val newerSamples = shortArrayOf(-100, -200, -300, -400)
            val older = writeClip(
                directory = directory,
                fileName = "older.wav",
                createdAt = olderInstant,
                sampleRate = 8,
                samples = olderSamples,
            )
            val newer = writeClip(
                directory = directory,
                fileName = "newer.wav",
                createdAt = newerInstant,
                sampleRate = 8,
                samples = newerSamples,
            )
            val olderBytes = older.file.readBytes()
            val newerBytes = newer.file.readBytes()
            val service = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )

            val merged = service.merge(listOf(newer, older))

            assertEquals(olderInstant, merged.createdAt)
            assertTrue(
                merged.file.name.startsWith(
                    "sleep-sound-20260810-010000-000-",
                ),
            )
            assertTrue(merged.file.name.endsWith("-selected-merged.wav"))
            assertTrue(merged.isMerged)
            assertEquals(1.5, merged.durationSeconds, 0.000_001)
            assertEquals(1.5, WavFiles.durationSeconds(merged.file) ?: -1.0, 0.000_001)
            assertStandardHeader(
                file = merged.file,
                sampleRate = 8,
                channelCount = 1,
                dataByteCount = 24,
            )
            assertArrayEquals(
                olderSamples + ShortArray(4) + newerSamples,
                readPcm16Samples(merged.file),
            )
            assertTrue(older.file.exists())
            assertTrue(newer.file.exists())
            assertArrayEquals(olderBytes, older.file.readBytes())
            assertArrayEquals(newerBytes, newer.file.readBytes())

            val reloaded = RecordingRepository(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            ).reload().first { clip ->
                clip.file.canonicalFile == merged.file.canonicalFile
            }
            assertEquals(olderInstant, reloaded.createdAt)
            assertEquals(merged.durationSeconds, reloaded.durationSeconds, 0.000_001)
        }
    }

    @Test
    fun todayMergeUsesAnUnambiguousMergedSuffix() {
        withTemporaryDirectory { directory ->
            val first = writeClip(
                directory = directory,
                fileName = "first.wav",
                createdAt = Instant.parse("2026-08-10T01:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(1, 2),
            )
            val second = writeClip(
                directory = directory,
                fileName = "second.wav",
                createdAt = Instant.parse("2026-08-10T01:01:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(3, 4),
            )

            val merged = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            ).merge(listOf(first, second), RecordingMergeKind.TODAY)

            assertTrue(merged.file.name.endsWith("-today-merged.wav"))
            assertTrue(merged.isMerged)
            assertThrows(RecordingMergeException.AlreadyMergedSource::class.java) {
                RecordingMergeService(directory).merge(listOf(merged, first))
            }
        }
    }

    @Test
    fun mergeAcceptsPcm16WaveFormatExtensibleAlongsideClassicPcm() {
        withTemporaryDirectory { directory ->
            val classicSamples = shortArrayOf(1, 2, 3, 4)
            val extensibleSamples = shortArrayOf(5, 6, 7, 8)
            val classic = writeClip(
                directory = directory,
                fileName = "classic.wav",
                createdAt = Instant.parse("2026-08-10T01:00:00Z"),
                sampleRate = 8,
                samples = classicSamples,
            )
            val extensible = writeExtensibleClip(
                directory = directory,
                fileName = "extensible.wav",
                createdAt = Instant.parse("2026-08-10T02:00:00Z"),
                sampleRate = 8,
                samples = extensibleSamples,
            )

            assertEquals(0.5, WavFiles.durationSeconds(extensible.file) ?: -1.0, 0.000_001)
            val merged = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
                gapDurationSeconds = 0.0,
            ).merge(listOf(extensible, classic))

            assertEquals(1.0, merged.durationSeconds, 0.000_001)
            assertArrayEquals(classicSamples + extensibleSamples, readPcm16Samples(merged.file))
            assertStandardHeader(
                file = merged.file,
                sampleRate = 8,
                channelCount = 1,
                dataByteCount = 16,
            )
        }
    }

    @Test
    fun mergeRejectsExtensiblePcmWithWrongGuidOrValidBits() {
        withTemporaryDirectory { directory ->
            val valid = writeClip(
                directory = directory,
                fileName = "valid.wav",
                createdAt = Instant.parse("2026-08-10T01:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(1, 2, 3, 4),
            )
            val wrongGuid = pcmSubformatGuid.copyOf().apply { this[0] = 0x03.toByte() }
            val invalidGuid = writeExtensibleClip(
                directory = directory,
                fileName = "invalid-guid.wav",
                createdAt = Instant.parse("2026-08-10T02:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(5, 6, 7, 8),
                subformatGuid = wrongGuid,
            )
            val invalidValidBits = writeExtensibleClip(
                directory = directory,
                fileName = "invalid-valid-bits.wav",
                createdAt = Instant.parse("2026-08-10T03:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(9, 10, 11, 12),
                validBitsPerSample = 15,
            )
            val service = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )

            assertThrows(RecordingMergeException.InvalidWav::class.java) {
                service.merge(listOf(valid, invalidGuid))
            }
            assertThrows(RecordingMergeException.InvalidWav::class.java) {
                service.merge(listOf(valid, invalidValidBits))
            }
            assertTrue(valid.file.exists())
            assertTrue(invalidGuid.file.exists())
            assertTrue(invalidValidBits.file.exists())
        }
    }

    @Test
    fun mergeRejectsFewerThanTwoAndIncompatibleFormatsWithoutOutput() {
        withTemporaryDirectory { directory ->
            val first = writeClip(
                directory = directory,
                fileName = "first.wav",
                createdAt = Instant.parse("2026-08-10T01:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(1, 2, 3, 4),
            )
            val second = writeClip(
                directory = directory,
                fileName = "second.wav",
                createdAt = Instant.parse("2026-08-10T02:00:00Z"),
                sampleRate = 16,
                samples = shortArrayOf(5, 6, 7, 8),
            )
            val service = RecordingMergeService(
                directory = directory,
                zoneId = ZoneOffset.UTC,
            )

            assertThrows(RecordingMergeException.NotEnoughRecordings::class.java) {
                service.merge(listOf(first))
            }
            assertThrows(RecordingMergeException.IncompatibleFormat::class.java) {
                service.merge(listOf(first, second))
            }

            assertTrue(first.file.exists())
            assertTrue(second.file.exists())
            assertFalse(
                directory.listFiles().orEmpty().any { file ->
                    file.nameWithoutExtension.endsWith("-merged")
                },
            )
            assertFalse(directory.listFiles().orEmpty().any { file -> file.extension == "tmp" })
        }
    }

    @Test
    fun mergeRejectsExternalAndCorruptedSources() {
        withTemporaryDirectory { directory ->
            val valid = writeClip(
                directory = directory,
                fileName = "valid.wav",
                createdAt = Instant.parse("2026-08-10T01:00:00Z"),
                sampleRate = 8,
                samples = shortArrayOf(1, 2, 3, 4),
            )
            val outsideDirectory = Files.createTempDirectory("stand-merge-outside-").toFile()
            try {
                val external = writeClip(
                    directory = outsideDirectory,
                    fileName = "external.wav",
                    createdAt = Instant.parse("2026-08-10T02:00:00Z"),
                    sampleRate = 8,
                    samples = shortArrayOf(5, 6, 7, 8),
                )
                val corruptedFile = File(directory, "corrupted.wav").apply {
                    writeBytes("not-a-wave".toByteArray())
                }
                val corrupted = RecordingClip(
                    file = corruptedFile,
                    createdAt = Instant.parse("2026-08-10T03:00:00Z"),
                    durationSeconds = 1.0,
                )
                val service = RecordingMergeService(
                    directory = directory,
                    zoneId = ZoneOffset.UTC,
                )

                assertThrows(RecordingMergeException.SourceOutsideDirectory::class.java) {
                    service.merge(listOf(valid, external))
                }
                assertThrows(RecordingMergeException.InvalidWav::class.java) {
                    service.merge(listOf(valid, corrupted))
                }
                assertTrue(valid.file.exists())
                assertTrue(external.file.exists())
                assertTrue(corrupted.file.exists())
            } finally {
                outsideDirectory.deleteRecursively()
            }
        }
    }

    private fun writeClip(
        directory: File,
        fileName: String,
        createdAt: Instant,
        sampleRate: Int,
        samples: ShortArray,
    ): RecordingClip {
        val file = File(directory, fileName)
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(0L)
            WavFiles.writeHeader(
                file = output,
                sampleRate = sampleRate,
                channelCount = 1,
                dataByteCount = 0L,
            )
            samples.forEach { sample ->
                output.write(sample.toInt() and 0xff)
                output.write((sample.toInt() ushr 8) and 0xff)
            }
            WavFiles.writeHeader(
                file = output,
                sampleRate = sampleRate,
                channelCount = 1,
                dataByteCount = samples.size.toLong() * Short.SIZE_BYTES,
            )
            output.setLength(44L + samples.size.toLong() * Short.SIZE_BYTES)
        }
        return RecordingClip(
            file = file,
            createdAt = createdAt,
            durationSeconds = samples.size.toDouble() / sampleRate.toDouble(),
        )
    }

    private fun writeExtensibleClip(
        directory: File,
        fileName: String,
        createdAt: Instant,
        sampleRate: Int,
        samples: ShortArray,
        validBitsPerSample: Int = 16,
        subformatGuid: ByteArray = pcmSubformatGuid,
    ): RecordingClip {
        require(subformatGuid.size == 16)
        val file = File(directory, fileName)
        val channelCount = 1
        val dataByteCount = samples.size.toLong() * Short.SIZE_BYTES
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(0L)
            output.writeAscii("RIFF")
            output.writeUInt32LittleEndian(60L + dataByteCount)
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
            output.writeAscii("data")
            output.writeUInt32LittleEndian(dataByteCount)
            samples.forEach { sample ->
                output.writeUInt16LittleEndian(sample.toInt())
            }
        }
        return RecordingClip(
            file = file,
            createdAt = createdAt,
            durationSeconds = samples.size.toDouble() / sampleRate.toDouble(),
        )
    }

    private fun assertStandardHeader(
        file: File,
        sampleRate: Int,
        channelCount: Int,
        dataByteCount: Int,
    ) {
        RandomAccessFile(file, "r").use { input ->
            assertEquals("RIFF", input.readAscii(4))
            assertEquals(file.length() - 8L, input.readUInt32LittleEndian())
            assertEquals("WAVE", input.readAscii(4))
            assertEquals("fmt ", input.readAscii(4))
            assertEquals(16L, input.readUInt32LittleEndian())
            assertEquals(1, input.readUInt16LittleEndian())
            assertEquals(channelCount, input.readUInt16LittleEndian())
            assertEquals(sampleRate.toLong(), input.readUInt32LittleEndian())
            assertEquals(
                sampleRate.toLong() * channelCount * Short.SIZE_BYTES,
                input.readUInt32LittleEndian(),
            )
            assertEquals(channelCount * Short.SIZE_BYTES, input.readUInt16LittleEndian())
            assertEquals(16, input.readUInt16LittleEndian())
            assertEquals("data", input.readAscii(4))
            assertEquals(dataByteCount.toLong(), input.readUInt32LittleEndian())
        }
    }

    private fun readPcm16Samples(file: File): ShortArray {
        RandomAccessFile(file, "r").use { input ->
            input.seek(40L)
            val dataByteCount = input.readUInt32LittleEndian().toInt()
            return ShortArray(dataByteCount / Short.SIZE_BYTES) {
                input.readUInt16LittleEndian().toShort()
            }
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt16LittleEndian(): Int {
        val low = read()
        val high = read()
        return low or (high shl 8)
    }

    private fun RandomAccessFile.readUInt32LittleEndian(): Long {
        val byte0 = read()
        val byte1 = read()
        val byte2 = read()
        val byte3 = read()
        return (byte0.toLong() or
            (byte1.toLong() shl 8) or
            (byte2.toLong() shl 16) or
            (byte3.toLong() shl 24)) and 0xffff_ffffL
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
        val directory = Files.createTempDirectory("stand-merge-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
