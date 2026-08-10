package com.armsone.stand.recording

import android.content.Context
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

data class PcmWavFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
)

enum class RecordingMergeKind(val fileToken: String) {
    SELECTED("selected"),
    TODAY("today"),
}

sealed class RecordingMergeException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class NotEnoughRecordings(val count: Int) : RecordingMergeException(
        "녹음을 합치려면 서로 다른 원본이 두 개 이상 필요합니다. 현재 ${count}개입니다.",
    )

    class DuplicateSource(val file: File) : RecordingMergeException(
        "같은 녹음이 두 번 이상 포함되어 있습니다: ${file.name}",
    )

    class SourceOutsideDirectory(val file: File) : RecordingMergeException(
        "앱 녹음 폴더 밖의 파일은 합칠 수 없습니다: ${file.name}",
    )

    class MissingSource(val file: File) : RecordingMergeException(
        "원본 녹음 파일을 찾을 수 없습니다: ${file.name}",
    )

    class AlreadyMergedSource(val file: File) : RecordingMergeException(
        "이미 합친 녹음은 다시 합칠 수 없습니다: ${file.name}",
    )

    class InvalidWav(
        val file: File,
        val detail: String,
        cause: Throwable? = null,
    ) : RecordingMergeException(
        "유효한 PCM16 WAV 녹음이 아닙니다: ${file.name} ($detail)",
        cause,
    )

    class IncompatibleFormat(
        val file: File,
        val expected: PcmWavFormat,
        val actual: PcmWavFormat,
    ) : RecordingMergeException(
        "녹음 형식이 서로 다릅니다: ${file.name} (기준 $expected, 실제 $actual)",
    )

    class OutputFailure(cause: Throwable? = null) : RecordingMergeException(
        "합친 녹음 파일을 저장할 수 없습니다.",
        cause,
    )
}

/**
 * Merges app-private PCM16 WAV recordings without modifying their source files.
 * This method performs file I/O and should be called away from the main thread.
 */
class RecordingMergeService internal constructor(
    val directory: File,
    private val zoneId: ZoneId,
    val gapDurationSeconds: Double,
    private val compressedRecordingDecoder: CompressedRecordingDecoder?,
    private val completedRecordingFinalizer: CompletedRecordingFinalizer,
) {
    constructor(
        directory: File,
        zoneId: ZoneId = ZoneId.systemDefault(),
        gapDurationSeconds: Double = DEFAULT_GAP_DURATION_SECONDS,
    ) : this(
        directory = directory,
        zoneId = zoneId,
        gapDurationSeconds = gapDurationSeconds,
        compressedRecordingDecoder = null,
        completedRecordingFinalizer = KeepWavRecordingFinalizer,
    )

    constructor(context: Context) : this(
        directory = File(context.applicationContext.filesDir, RecordingRepository.DIRECTORY_NAME),
        zoneId = ZoneId.systemDefault(),
        gapDurationSeconds = DEFAULT_GAP_DURATION_SECONDS,
        compressedRecordingDecoder = AndroidCompressedRecordingDecoder,
        completedRecordingFinalizer = AacM4aRecordingFinalizer(),
    )

    init {
        require(gapDurationSeconds.isFinite() && gapDurationSeconds >= 0.0) {
            "gapDurationSeconds must be finite and non-negative"
        }
    }

    @Throws(RecordingMergeException::class)
    fun merge(
        clips: Collection<RecordingClip>,
        kind: RecordingMergeKind = RecordingMergeKind.SELECTED,
    ): RecordingClip {
        if (clips.size < 2) {
            throw RecordingMergeException.NotEnoughRecordings(clips.size)
        }

        val canonicalDirectory = try {
            directory.canonicalFile
        } catch (error: IOException) {
            throw RecordingMergeException.OutputFailure(error)
        }
        ensureOutputDirectory(canonicalDirectory)

        val sources = clips
            .sortedWith(
                compareBy<RecordingClip> { it.createdAt }
                    .thenBy { it.file.name },
            )
            .map { clip -> validateSourcePath(clip, canonicalDirectory) }
        if (sources.map { it.canonicalFile.path }.toSet().size != sources.size) {
            val duplicate = sources
                .groupBy { it.canonicalFile.path }
                .values
                .first { group -> group.size > 1 }
                .first()
            throw RecordingMergeException.DuplicateSource(duplicate.clip.file)
        }

        val temporaryInputs = mutableListOf<File>()
        return try {
            val parsedSources = sources.map { source ->
                val wavFile = prepareWavSource(source, canonicalDirectory, temporaryInputs)
                source.copy(
                    workingFile = wavFile,
                    wav = parseWav(wavFile),
                )
            }
            mergePreparedSources(parsedSources, canonicalDirectory, kind)
        } finally {
            temporaryInputs.forEach(File::delete)
        }
    }

    private fun mergePreparedSources(
        parsedSources: List<MergeSource>,
        canonicalDirectory: File,
        kind: RecordingMergeKind,
    ): RecordingClip {
        val outputFormat = parsedSources.first().wav!!.format
        parsedSources.drop(1).forEach { source ->
            val actualFormat = source.wav!!.format
            if (actualFormat != outputFormat) {
                throw RecordingMergeException.IncompatibleFormat(
                    file = source.clip.file,
                    expected = outputFormat,
                    actual = actualFormat,
                )
            }
        }

        val gapFrames = try {
            (outputFormat.sampleRate * gapDurationSeconds).roundToLong()
        } catch (error: ArithmeticException) {
            throw RecordingMergeException.OutputFailure(error)
        }
        val gapByteCount = checkedMultiply(
            gapFrames,
            outputFormat.blockAlign.toLong(),
        )
        val totalDataByteCount = parsedSources.foldIndexed(0L) { index, total, source ->
            val withSource = checkedAdd(total, source.wav!!.dataByteCount)
            if (index == parsedSources.lastIndex) {
                withSource
            } else {
                checkedAdd(withSource, gapByteCount)
            }
        }
        if (totalDataByteCount > MAX_WAV_DATA_BYTES) {
            throw RecordingMergeException.OutputFailure(
                IOException("WAV 파일 크기가 표준 RIFF 한도를 넘었습니다."),
            )
        }

        val firstClip = parsedSources.first().clip
        val outputFile = File(
            canonicalDirectory,
            mergedFileName(
                timestamp = firstClip.createdAt.atZone(zoneId).format(FILE_TIME_FORMATTER),
                kind = kind,
            ),
        )
        val stagingFile = File(
            canonicalDirectory,
            ".${outputFile.name}.${UUID.randomUUID().toString().take(8)}.tmp",
        )

        var moveStarted = false
        try {
            if (outputFile.exists() || stagingFile.exists()) {
                throw RecordingMergeException.OutputFailure(
                    IOException("병합 출력 파일 이름이 이미 존재합니다."),
                )
            }
            writeMergedWav(
                stagingFile = stagingFile,
                sources = parsedSources,
                format = outputFormat,
                gapByteCount = gapByteCount,
                totalDataByteCount = totalDataByteCount,
            )
            moveStarted = true
            moveAtomically(stagingFile, outputFile)
        } catch (error: RecordingMergeException) {
            stagingFile.delete()
            if (moveStarted) outputFile.delete()
            throw error
        } catch (error: Exception) {
            stagingFile.delete()
            if (moveStarted) outputFile.delete()
            throw RecordingMergeException.OutputFailure(error)
        }

        val finalizedOutput = completedRecordingFinalizer.finalize(outputFile)
        return RecordingClip(
            file = finalizedOutput,
            createdAt = firstClip.createdAt,
            durationSeconds = totalDataByteCount.toDouble() /
                (outputFormat.sampleRate.toDouble() * outputFormat.blockAlign.toDouble()),
        )
    }

    private fun prepareWavSource(
        source: MergeSource,
        canonicalDirectory: File,
        temporaryInputs: MutableList<File>,
    ): File = when (RecordingMediaFormat.from(source.canonicalFile)) {
        RecordingMediaFormat.WAV -> source.canonicalFile
        RecordingMediaFormat.M4A -> {
            val decoder = compressedRecordingDecoder ?: throw RecordingMergeException.InvalidWav(
                file = source.clip.file,
                detail = "이 실행 환경에서는 M4A 병합을 지원하지 않습니다.",
            )
            val temporary = File(
                canonicalDirectory,
                ".${source.canonicalFile.name}.${UUID.randomUUID().toString().take(8)}.decode.wav",
            )
            temporaryInputs += temporary
            try {
                decoder.decodeToWav(source.canonicalFile, temporary)
                temporary
            } catch (error: Exception) {
                throw RecordingMergeException.InvalidWav(
                    file = source.clip.file,
                    detail = "M4A 오디오를 해독할 수 없습니다.",
                    cause = error,
                )
            }
        }
        null -> throw RecordingMergeException.InvalidWav(
            file = source.clip.file,
            detail = "지원하는 녹음 형식이 아닙니다.",
        )
    }

    private fun validateSourcePath(
        clip: RecordingClip,
        canonicalDirectory: File,
    ): MergeSource {
        val source = clip.file
        if (clip.isMerged) {
            throw RecordingMergeException.AlreadyMergedSource(source)
        }
        val canonicalSource = try {
            source.canonicalFile
        } catch (error: IOException) {
            throw RecordingMergeException.InvalidWav(
                file = source,
                detail = "파일 경로를 확인할 수 없습니다.",
                cause = error,
            )
        }
        if (
            canonicalSource.parentFile != canonicalDirectory ||
            Files.isSymbolicLink(source.toPath())
        ) {
            throw RecordingMergeException.SourceOutsideDirectory(source)
        }
        if (!canonicalSource.isFile) {
            throw RecordingMergeException.MissingSource(source)
        }
        if (RecordingMediaFormat.from(canonicalSource) == null) {
            throw RecordingMergeException.InvalidWav(source, "지원하는 녹음 확장자가 아닙니다.")
        }
        return MergeSource(
            clip = clip,
            canonicalFile = canonicalSource,
        )
    }

    private fun parseWav(file: File): ParsedWav {
        try {
            RandomAccessFile(file, "r").use { input ->
                if (input.length() < MINIMUM_WAV_HEADER_BYTES) {
                    throw invalid(file, "헤더가 너무 짧습니다.")
                }
                if (input.readFourCc() != RIFF_ID) {
                    throw invalid(file, "RIFF 표식이 없습니다.")
                }
                val riffByteCount = input.readUInt32LittleEndian()
                val riffEnd = checkedAddForSource(file, RIFF_HEADER_BYTES, riffByteCount)
                if (riffEnd < MINIMUM_RIFF_BYTES || riffEnd > input.length()) {
                    throw invalid(file, "RIFF 크기가 실제 파일과 맞지 않습니다.")
                }
                if (input.readFourCc() != WAVE_ID) {
                    throw invalid(file, "WAVE 표식이 없습니다.")
                }

                var format: PcmWavFormat? = null
                var dataOffset: Long? = null
                var dataByteCount: Long? = null
                while (input.filePointer + CHUNK_HEADER_BYTES <= riffEnd) {
                    val chunkId = input.readFourCc()
                    val chunkByteCount = input.readUInt32LittleEndian()
                    val chunkStart = input.filePointer
                    val chunkEnd = checkedAddForSource(file, chunkStart, chunkByteCount)
                    if (chunkEnd > riffEnd) {
                        throw invalid(file, "$chunkId 청크가 RIFF 범위를 벗어납니다.")
                    }

                    when (chunkId) {
                        FORMAT_ID -> {
                            if (format != null) throw invalid(file, "fmt 청크가 중복됩니다.")
                            if (chunkByteCount < PCM_FORMAT_CHUNK_BYTES) {
                                throw invalid(file, "fmt 청크가 너무 짧습니다.")
                            }
                            format = readAndValidateFormat(
                                input = input,
                                file = file,
                                chunkByteCount = chunkByteCount,
                            )
                        }

                        DATA_ID -> {
                            if (dataOffset != null) throw invalid(file, "data 청크가 중복됩니다.")
                            dataOffset = chunkStart
                            dataByteCount = chunkByteCount
                        }
                    }

                    val paddedEnd = checkedAddForSource(file, chunkEnd, chunkByteCount and 1L)
                    if (paddedEnd > riffEnd) {
                        throw invalid(file, "$chunkId 청크 패딩이 손상되었습니다.")
                    }
                    input.seek(paddedEnd)
                }
                if (input.filePointer != riffEnd) {
                    throw invalid(file, "RIFF 끝에 불완전한 청크 데이터가 있습니다.")
                }

                val parsedFormat = format ?: throw invalid(file, "fmt 청크가 없습니다.")
                val parsedDataOffset = dataOffset ?: throw invalid(file, "data 청크가 없습니다.")
                val parsedDataByteCount = dataByteCount ?: throw invalid(file, "data 크기가 없습니다.")
                if (parsedDataByteCount % parsedFormat.blockAlign != 0L) {
                    throw invalid(file, "PCM 데이터가 프레임 경계에 맞지 않습니다.")
                }
                return ParsedWav(
                    format = parsedFormat,
                    dataOffset = parsedDataOffset,
                    dataByteCount = parsedDataByteCount,
                )
            }
        } catch (error: RecordingMergeException) {
            throw error
        } catch (error: IOException) {
            throw RecordingMergeException.InvalidWav(
                file = file,
                detail = "헤더를 읽을 수 없습니다.",
                cause = error,
            )
        }
    }

    private fun readAndValidateFormat(
        input: RandomAccessFile,
        file: File,
        chunkByteCount: Long,
    ): PcmWavFormat {
        val parsed = try {
            PcmWavFormatParser.read(input, chunkByteCount)
        } catch (error: InvalidPcmWavFormatException) {
            throw invalid(file, error.message ?: "PCM fmt 청크가 올바르지 않습니다.")
        }
        return PcmWavFormat(
            sampleRate = parsed.sampleRate,
            channelCount = parsed.channelCount,
            bitsPerSample = parsed.bitsPerSample,
        )
    }

    private fun writeMergedWav(
        stagingFile: File,
        sources: List<MergeSource>,
        format: PcmWavFormat,
        gapByteCount: Long,
        totalDataByteCount: Long,
    ) {
        RandomAccessFile(stagingFile, "rw").use { output ->
            output.setLength(0L)
            WavFiles.writeHeader(
                file = output,
                sampleRate = format.sampleRate,
                channelCount = format.channelCount,
                dataByteCount = 0L,
            )

            var writtenByteCount = 0L
            sources.forEachIndexed { index, source ->
                copyPcmData(source, output)
                writtenByteCount = checkedAdd(writtenByteCount, source.wav!!.dataByteCount)
                if (index != sources.lastIndex) {
                    writeSilence(output, gapByteCount)
                    writtenByteCount = checkedAdd(writtenByteCount, gapByteCount)
                }
            }
            if (writtenByteCount != totalDataByteCount) {
                throw RecordingMergeException.OutputFailure(
                    IOException("예상한 PCM 길이와 실제 출력 길이가 다릅니다."),
                )
            }

            WavFiles.writeHeader(
                file = output,
                sampleRate = format.sampleRate,
                channelCount = format.channelCount,
                dataByteCount = totalDataByteCount,
            )
            output.setLength(MINIMUM_WAV_HEADER_BYTES + totalDataByteCount)
            output.fd.sync()
        }
    }

    private fun copyPcmData(source: MergeSource, output: RandomAccessFile) {
        val wav = source.wav!!
        RandomAccessFile(source.workingFile, "r").use { input ->
            input.seek(wav.dataOffset)
            var remaining = wav.dataByteCount
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (remaining > 0L) {
                val requested = minOf(buffer.size.toLong(), remaining).toInt()
                val readCount = try {
                    input.read(buffer, 0, requested)
                } catch (error: IOException) {
                    throw RecordingMergeException.InvalidWav(
                        file = source.clip.file,
                        detail = "PCM 데이터를 읽을 수 없습니다.",
                        cause = error,
                    )
                }
                if (readCount <= 0) {
                    throw invalid(source.clip.file, "PCM 데이터가 선언된 길이보다 짧습니다.")
                }
                output.write(buffer, 0, readCount)
                remaining -= readCount
            }
        }
    }

    private fun writeSilence(output: RandomAccessFile, byteCount: Long) {
        if (byteCount == 0L) return
        val zeroes = ByteArray(minOf(COPY_BUFFER_BYTES.toLong(), byteCount).toInt())
        var remaining = byteCount
        while (remaining > 0L) {
            val writeCount = minOf(zeroes.size.toLong(), remaining).toInt()
            output.write(zeroes, 0, writeCount)
            remaining -= writeCount
        }
    }

    private fun ensureOutputDirectory(canonicalDirectory: File) {
        if (canonicalDirectory.isDirectory) return
        if (canonicalDirectory.exists() || !canonicalDirectory.mkdirs()) {
            throw RecordingMergeException.OutputFailure(
                IOException("녹음 폴더를 만들 수 없습니다."),
            )
        }
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun mergedFileName(timestamp: String, kind: RecordingMergeKind): String {
        val nonce = UUID.randomUUID().toString().take(8).lowercase(Locale.US)
        return "sleep-sound-$timestamp-$nonce-${kind.fileToken}-merged.wav"
    }

    private fun checkedAdd(first: Long, second: Long): Long = try {
        Math.addExact(first, second)
    } catch (error: ArithmeticException) {
        throw RecordingMergeException.OutputFailure(error)
    }

    private fun checkedMultiply(first: Long, second: Long): Long = try {
        Math.multiplyExact(first, second)
    } catch (error: ArithmeticException) {
        throw RecordingMergeException.OutputFailure(error)
    }

    private fun checkedAddForSource(file: File, first: Long, second: Long): Long = try {
        Math.addExact(first, second)
    } catch (error: ArithmeticException) {
        throw RecordingMergeException.InvalidWav(
            file = file,
            detail = "청크 크기가 올바르지 않습니다.",
            cause = error,
        )
    }

    private fun invalid(file: File, detail: String): RecordingMergeException.InvalidWav =
        RecordingMergeException.InvalidWav(file = file, detail = detail)

    private data class MergeSource(
        val clip: RecordingClip,
        val canonicalFile: File,
        val workingFile: File = canonicalFile,
        val wav: ParsedWav? = null,
    )

    private data class ParsedWav(
        val format: PcmWavFormat,
        val dataOffset: Long,
        val dataByteCount: Long,
    )

    private val PcmWavFormat.blockAlign: Int
        get() = channelCount * (bitsPerSample / Byte.SIZE_BITS)

    companion object {
        const val DEFAULT_GAP_DURATION_SECONDS = 0.5
        private const val COPY_BUFFER_BYTES = 64 * 1_024
        private const val RIFF_ID = "RIFF"
        private const val WAVE_ID = "WAVE"
        private const val FORMAT_ID = "fmt "
        private const val DATA_ID = "data"
        private const val PCM_FORMAT_CHUNK_BYTES = 16L
        private const val RIFF_HEADER_BYTES = 8L
        private const val MINIMUM_RIFF_BYTES = 12L
        private const val CHUNK_HEADER_BYTES = 8L
        private const val MINIMUM_WAV_HEADER_BYTES = 44L
        private const val MAX_WAV_DATA_BYTES = 0xffff_ffffL - 36L
        private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmmss-SSS",
            Locale.US,
        )
    }
}

private fun RandomAccessFile.readFourCc(): String {
    val bytes = ByteArray(4)
    readFully(bytes)
    return String(bytes, StandardCharsets.US_ASCII)
}

private fun RandomAccessFile.readUInt16LittleEndian(): Int {
    val low = read()
    val high = read()
    if (low < 0 || high < 0) throw EOFException()
    return low or (high shl 8)
}

private fun RandomAccessFile.readUInt32LittleEndian(): Long {
    val byte0 = read()
    val byte1 = read()
    val byte2 = read()
    val byte3 = read()
    if (byte0 < 0 || byte1 < 0 || byte2 < 0 || byte3 < 0) throw EOFException()
    return (byte0.toLong() or
        (byte1.toLong() shl 8) or
        (byte2.toLong() shl 16) or
        (byte3.toLong() shl 24)) and 0xffff_ffffL
}
