package com.armsone.stand.recording

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class RecordingMediaFormat(
    val extension: String,
    val mimeType: String,
) {
    WAV("wav", "audio/wav"),
    M4A("m4a", "audio/mp4"),
    ;

    companion object {
        fun from(file: File): RecordingMediaFormat? = entries.firstOrNull { format ->
            file.extension.equals(format.extension, ignoreCase = true)
        }
    }
}

internal fun interface RecordingDurationResolver {
    fun durationSeconds(file: File): Double?
}

internal object WavRecordingDurationResolver : RecordingDurationResolver {
    override fun durationSeconds(file: File): Double? = WavFiles.durationSeconds(file)
}

internal object AndroidRecordingDurationResolver : RecordingDurationResolver {
    override fun durationSeconds(file: File): Double? = when (RecordingMediaFormat.from(file)) {
        RecordingMediaFormat.WAV -> WavFiles.durationSeconds(file)
        RecordingMediaFormat.M4A -> compressedDurationSeconds(file)
        null -> null
    }

    private fun compressedDurationSeconds(file: File): Double? = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?.div(1_000.0)
        } finally {
            retriever.release()
        }
    } catch (_: RuntimeException) {
        null
    }
}

internal fun interface CompletedRecordingFinalizer {
    /** Returns the visible recording. Implementations must preserve [wavFile] on failure. */
    fun finalize(wavFile: File): File
}

internal object KeepWavRecordingFinalizer : CompletedRecordingFinalizer {
    override fun finalize(wavFile: File): File = wavFile
}

internal fun interface CompressedRecordingDecoder {
    fun decodeToWav(inputFile: File, outputFile: File)
}

/** Decodes an app-owned compressed recording to a temporary PCM16 WAV for merging. */
internal object AndroidCompressedRecordingDecoder : CompressedRecordingDecoder {
    override fun decodeToWav(inputFile: File, outputFile: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IOException("M4A 오디오 트랙을 찾을 수 없습니다.")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("M4A 오디오 형식을 확인할 수 없습니다.")
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            RandomAccessFile(outputFile, "rw").use { output ->
                output.setLength(0L)
                WavFiles.writeHeader(output, sampleRate = 1, channelCount = 1, dataByteCount = 0L)
                var inputEnded = false
                var outputEnded = false
                var sampleRate = 0
                var channelCount = 0
                var dataByteCount = 0L
                var lastProgressAt = System.nanoTime()
                val bufferInfo = MediaCodec.BufferInfo()

                while (!outputEnded) {
                    var progressed = false
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: throw IOException("M4A 해독 입력 버퍼를 사용할 수 없습니다.")
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime.coerceAtLeast(0L),
                                    extractor.sampleFlags,
                                )
                                extractor.advance()
                            }
                            progressed = true
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val decodedFormat = codec.outputFormat
                            sampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val pcmEncoding = if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                                throw IOException("지원하지 않는 PCM 해독 형식입니다.")
                            }
                            progressed = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                                ?: throw IOException("M4A 해독 출력 버퍼를 사용할 수 없습니다.")
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                if (sampleRate <= 0 || channelCount <= 0) {
                                    throw IOException("M4A 해독 형식이 준비되지 않았습니다.")
                                }
                                val bytes = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.get(bytes)
                                output.seek(WAV_HEADER_BYTES + dataByteCount)
                                output.write(bytes)
                                dataByteCount += bytes.size
                            }
                            outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                            progressed = true
                        }
                    }

                    if (progressed) {
                        lastProgressAt = System.nanoTime()
                    } else if (System.nanoTime() - lastProgressAt > STALL_TIMEOUT_NANOS) {
                        throw IOException("M4A 해독기가 응답하지 않습니다.")
                    }
                }

                if (sampleRate <= 0 || channelCount <= 0 || dataByteCount <= 0L) {
                    throw IOException("M4A에서 PCM 오디오를 해독하지 못했습니다.")
                }
                val blockAlign = channelCount * Short.SIZE_BYTES
                if (dataByteCount % blockAlign != 0L) {
                    throw IOException("해독한 PCM 데이터 정렬이 올바르지 않습니다.")
                }
                WavFiles.writeHeader(output, sampleRate, channelCount, dataByteCount)
                output.fd.sync()
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            if (WavFiles.durationSeconds(outputFile) == null) outputFile.delete()
        }
    }

    private const val WAV_HEADER_BYTES = 44L
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val STALL_TIMEOUT_NANOS = 10_000_000_000L
}

/** Converts an approved PCM16 WAV candidate to AAC-LC in an MPEG-4 audio container. */
internal class AacM4aRecordingFinalizer(
    private val bitRate: Int = IOS_AAC_BIT_RATE,
) : CompletedRecordingFinalizer {
    init {
        require(bitRate > 0)
    }

    override fun finalize(wavFile: File): File {
        val outputFile = File(wavFile.parentFile, "${wavFile.nameWithoutExtension}.m4a")
        val temporaryFile = File(
            wavFile.parentFile,
            ".${outputFile.name}.${UUID.randomUUID().toString().take(8)}.tmp",
        )
        var outputCreated = false
        return try {
            if (outputFile.exists()) {
                throw IOException("같은 이름의 M4A 녹음이 이미 있습니다.")
            }
            encode(wavFile, temporaryFile)
            if (!temporaryFile.isFile || temporaryFile.length() <= 0L) {
                throw IOException("AAC 녹음 결과가 비어 있습니다.")
            }
            moveAtomically(temporaryFile, outputFile)
            outputCreated = true
            if (!wavFile.delete() && wavFile.exists()) {
                throw IOException("원본 WAV를 안전하게 교체할 수 없습니다.")
            }
            outputFile
        } catch (_: Exception) {
            temporaryFile.delete()
            if (outputCreated) outputFile.delete()
            wavFile
        }
    }

    private fun encode(inputFile: File, outputFile: File) {
        val descriptor = WavFiles.dataDescriptor(inputFile)
            ?: throw IOException("유효한 PCM16 WAV 녹음이 아닙니다.")
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            descriptor.format.sampleRate,
            descriptor.format.channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_CHUNK_BYTES)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            RandomAccessFile(inputFile, "r").use { input ->
                input.seek(descriptor.dataOffset)
                var remainingBytes = descriptor.dataByteCount
                var queuedBytes = 0L
                var inputEnded = false
                var outputEnded = false
                var trackIndex = -1
                var lastProgressAt = System.nanoTime()
                val bufferInfo = MediaCodec.BufferInfo()

                while (!outputEnded) {
                    var progressed = false
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: throw IOException("AAC 입력 버퍼를 사용할 수 없습니다.")
                            inputBuffer.clear()
                            val byteCount = minOf(
                                remainingBytes,
                                inputBuffer.remaining().toLong(),
                            ).toInt().let { count ->
                                count - count % descriptor.format.blockAlign
                            }
                            val presentationTimeUs = framesToMicroseconds(
                                frameCount = queuedBytes / descriptor.format.blockAlign,
                                sampleRate = descriptor.format.sampleRate,
                            )
                            if (byteCount > 0) {
                                val bytes = ByteArray(byteCount)
                                input.readFully(bytes)
                                inputBuffer.put(bytes)
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    byteCount,
                                    presentationTimeUs,
                                    0,
                                )
                                queuedBytes += byteCount
                                remainingBytes -= byteCount
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            }
                            progressed = true
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IOException("AAC 출력 형식이 두 번 변경됐습니다.")
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                            progressed = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                                ?: throw IOException("AAC 출력 버퍼를 사용할 수 없습니다.")
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                if (!muxerStarted || trackIndex < 0) {
                                    throw IOException("AAC 트랙이 준비되기 전에 데이터가 생성됐습니다.")
                                }
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            }
                            outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                            progressed = true
                        }
                    }

                    if (progressed) {
                        lastProgressAt = System.nanoTime()
                    } else if (System.nanoTime() - lastProgressAt > STALL_TIMEOUT_NANOS) {
                        throw IOException("AAC 인코더가 응답하지 않습니다.")
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
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

    companion object {
        const val IOS_AAC_BIT_RATE = 48_000
        private const val INPUT_CHUNK_BYTES = 32 * 1_024
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val STALL_TIMEOUT_NANOS = 10_000_000_000L

        internal fun framesToMicroseconds(frameCount: Long, sampleRate: Int): Long {
            require(frameCount >= 0L)
            require(sampleRate > 0)
            return frameCount * 1_000_000L / sampleRate
        }
    }
}
