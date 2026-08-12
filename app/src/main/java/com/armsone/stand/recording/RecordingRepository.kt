package com.armsone.stand.recording

import android.content.Context
import com.armsone.stand.R
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingClip(
    val file: File,
    val createdAt: Instant,
    val durationSeconds: Double,
) {
    val mediaFormat: RecordingMediaFormat?
        get() = RecordingMediaFormat.from(file)

    val isMerged: Boolean
        get() = file.nameWithoutExtension.endsWith("-merged", ignoreCase = true)
}

/**
 * Owns the app-private `files/recordings` directory and its completed audio files.
 * Files under `.pending` are deliberately excluded until classification approves them.
 */
class RecordingRepository internal constructor(
    val directory: File,
    private val zoneId: ZoneId,
    private val durationResolver: RecordingDurationResolver = WavRecordingDurationResolver,
    private val completedRecordingFinalizer: CompletedRecordingFinalizer =
        KeepWavRecordingFinalizer,
) {
    constructor(
        directory: File,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) : this(
        directory = directory,
        zoneId = zoneId,
        durationResolver = WavRecordingDurationResolver,
        completedRecordingFinalizer = KeepWavRecordingFinalizer,
    )

    constructor(context: Context) : this(
        directory = File(context.applicationContext.filesDir, DIRECTORY_NAME),
        zoneId = ZoneId.systemDefault(),
        durationResolver = AndroidRecordingDurationResolver,
        completedRecordingFinalizer = AacM4aRecordingFinalizer(),
    ) {
        installEmbeddedSamplesIfNeeded(context.applicationContext)
        reload()
    }

    private val ioLock = Any()
    private val stagingDirectory = File(directory, PENDING_DIRECTORY_NAME)

    private val _recordings = MutableStateFlow<List<RecordingClip>>(emptyList())
    val recordings: StateFlow<List<RecordingClip>> = _recordings.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        synchronized(ioLock) {
            removeStalePendingFilesLocked()
        }
        reload()
    }

    /** Reloads valid supported audio files newest first. Malformed files are ignored. */
    fun reload(): List<RecordingClip> = synchronized(ioLock) {
        if (!ensureDirectoryLocked()) {
            _recordings.value = emptyList()
            return@synchronized emptyList()
        }

        val clips = directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file -> file.isFile && RecordingMediaFormat.from(file) != null }
            .mapNotNull(::makeClip)
            .sortedWith(
                compareByDescending<RecordingClip> { it.createdAt }
                    .thenByDescending { it.file.name },
            )
            .toList()

        _lastError.value = null
        _recordings.value = clips
        clips
    }

    /** Deletes one managed recording. Repeating the same delete is safe. */
    fun delete(clip: RecordingClip): Boolean = delete(clip.file)

    /** Deletes one managed recording without allowing paths outside this repository. */
    fun delete(file: File): Boolean = synchronized(ioLock) {
        if (!isManagedRecording(file)) return@synchronized false

        val deleted = !file.exists() || file.delete()
        if (!deleted) {
            _lastError.value = "녹음 파일을 삭제할 수 없습니다."
            return@synchronized false
        }
        _lastError.value = null
        reloadLocked()
        true
    }

    /** Deletes all completed recordings and any abandoned candidate data. */
    fun deleteAll(): Boolean = synchronized(ioLock) {
        var succeeded = true
        directory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && RecordingMediaFormat.from(file) != null }
            .forEach { file ->
                if (!file.delete() && file.exists()) succeeded = false
            }
        removeStalePendingFilesLocked()

        if (!succeeded) {
            _lastError.value = "일부 녹음 파일을 삭제할 수 없습니다."
        } else {
            _lastError.value = null
        }
        reloadLocked()
        succeeded
    }

    @Throws(IOException::class)
    internal fun beginWavCandidate(
        createdAt: Instant,
        sampleRate: Int,
        channelCount: Int = 1,
    ): PendingWavRecording = synchronized(ioLock) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
        if (!ensureDirectoryLocked()) {
            throw IOException(_lastError.value ?: "녹음 폴더를 만들 수 없습니다.")
        }
        if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
            throw IOException("임시 녹음 폴더를 만들 수 없습니다.")
        }

        val fileName = RecordingFileNames.create(
            instant = createdAt,
            zoneId = zoneId,
        )
        PendingWavRecording(
            stagingFile = File(stagingDirectory, "$fileName.part"),
            completedFile = File(directory, fileName),
            sampleRate = sampleRate,
            channelCount = channelCount,
            completedRecordingFinalizer = completedRecordingFinalizer,
        )
    }

    private fun reloadLocked() {
        if (!ensureDirectoryLocked()) {
            _recordings.value = emptyList()
            return
        }
        _recordings.value = directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file -> file.isFile && RecordingMediaFormat.from(file) != null }
            .mapNotNull(::makeClip)
            .sortedWith(
                compareByDescending<RecordingClip> { it.createdAt }
                    .thenByDescending { it.file.name },
            )
            .toList()
    }

    private fun makeClip(file: File): RecordingClip? {
        val duration = durationResolver.durationSeconds(file) ?: return null
        val createdAt = RecordingTimestampPolicy.createdAt(
            fileName = file.name,
            lastModifiedMillis = file.lastModified(),
            zoneId = zoneId,
        )
        return RecordingClip(
            file = file,
            createdAt = createdAt,
            durationSeconds = duration,
        )
    }

    private fun ensureDirectoryLocked(): Boolean {
        if (directory.isDirectory) return true
        if (directory.exists() || !directory.mkdirs()) {
            _lastError.value = "녹음 폴더를 만들 수 없습니다."
            return false
        }
        return true
    }

    private fun isManagedRecording(file: File): Boolean = try {
        file.canonicalFile.parentFile == directory.canonicalFile &&
            RecordingMediaFormat.from(file) != null
    } catch (_: IOException) {
        false
    }

    private fun removeStalePendingFilesLocked() {
        stagingDirectory.listFiles().orEmpty().forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
        if (stagingDirectory.isDirectory && stagingDirectory.list().isNullOrEmpty()) {
            stagingDirectory.delete()
        }
    }

    internal fun installEmbeddedSamplesIfNeeded(context: Context) = synchronized(ioLock) {
        val marker = File(directory, EMBEDDED_SAMPLE_MARKER_NAME)
        if (marker.isFile || !ensureDirectoryLocked()) return@synchronized

        val firstSampleAt = LocalDate.now(zoneId)
            .minusDays(1)
            .atTime(1, 0)
            .atZone(zoneId)
            .toInstant()
        val samples = listOf(
            EmbeddedSample(R.raw.sample_snore_5s, minuteOffset = 0),
            EmbeddedSample(R.raw.sample_snore_10s, minuteOffset = 20),
            EmbeddedSample(R.raw.sample_snore_15s, minuteOffset = 40),
        )

        val installed = samples.all { sample ->
            val createdAt = firstSampleAt.plusSeconds(sample.minuteOffset * 60L)
            val destination = File(
                directory,
                RecordingFileNames.create(
                    instant = createdAt,
                    zoneId = zoneId,
                    nonce = EMBEDDED_SAMPLE_NONCE,
                    mediaFormat = RecordingMediaFormat.M4A,
                ),
            )
            if (destination.isFile && durationResolver.durationSeconds(destination) != null) {
                return@all true
            }

            val temporary = File(
                directory,
                ".${destination.name}.${UUID.randomUUID()}.tmp.${destination.extension}",
            )
            try {
                context.resources.openRawResource(sample.rawResource).use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (durationResolver.durationSeconds(temporary) == null) {
                    throw IOException("내장 M4A 샘플이 손상되었습니다.")
                }
                moveReplacing(temporary, destination)
                destination.setLastModified(createdAt.toEpochMilli())
                true
            } catch (_: Exception) {
                temporary.delete()
                false
            }
        }

        if (installed) {
            val temporaryMarker = File(directory, ".${marker.name}.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporaryMarker).use { output ->
                    output.write(byteArrayOf())
                    output.fd.sync()
                }
                moveReplacing(temporaryMarker, marker)
            } catch (_: Exception) {
                temporaryMarker.delete()
            }
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        const val DIRECTORY_NAME = "recordings"
        private const val PENDING_DIRECTORY_NAME = ".pending"
        private const val EMBEDDED_SAMPLE_MARKER_NAME = ".embedded-snore-samples-v1"
        private const val EMBEDDED_SAMPLE_NONCE = "embedded-snore"
    }

    private data class EmbeddedSample(
        val rawResource: Int,
        val minuteOffset: Int,
    )
}

internal object RecordingTimestampPolicy {
    /**
     * Uses the timestamp embedded by S.tand first. The fallback reads only the local metadata of
     * the recording file already managed inside the app's private recordings directory.
     */
    fun createdAt(
        fileName: String,
        lastModifiedMillis: Long,
        zoneId: ZoneId,
    ): Instant = RecordingFileNames.parse(fileName, zoneId)
        ?: lastModifiedMillis
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
        ?: Instant.EPOCH
}

internal object RecordingFileNames {
    private const val PREFIX = "sleep-sound-"
    private val formatter = DateTimeFormatter.ofPattern(
        "yyyyMMdd-HHmmss-SSS",
        Locale.US,
    )

    fun create(
        instant: Instant,
        zoneId: ZoneId,
        nonce: String = UUID.randomUUID().toString().take(8).lowercase(Locale.US),
        mediaFormat: RecordingMediaFormat = RecordingMediaFormat.WAV,
    ): String {
        val timestamp = formatter.format(instant.atZone(zoneId))
        return "$PREFIX$timestamp-$nonce.${mediaFormat.extension}"
    }

    fun parse(fileName: String, zoneId: ZoneId): Instant? {
        if (!fileName.startsWith(PREFIX)) return null
        val withoutExtension = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val timestamp = withoutExtension
            .removePrefix(PREFIX)
            .take(TIMESTAMP_LENGTH)
        if (timestamp.length != TIMESTAMP_LENGTH) return null

        return try {
            LocalDateTime.parse(timestamp, formatter)
                .atZone(zoneId)
                .toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private const val TIMESTAMP_LENGTH = 19
}

/** A streaming WAV candidate that becomes visible only after [commit]. */
internal class PendingWavRecording(
    private val stagingFile: File,
    private val completedFile: File,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val completedRecordingFinalizer: CompletedRecordingFinalizer,
) {
    private enum class State {
        OPEN,
        SEALED,
        WAV_COMMITTED,
        COMMITTED,
        DISCARDED,
    }

    private val file = RandomAccessFile(stagingFile, "rw")
    private var state = State.OPEN
    private var dataByteCount = 0L

    init {
        require(sampleRate > 0)
        require(channelCount > 0)
        file.setLength(0L)
        WavFiles.writeHeader(
            file = file,
            sampleRate = sampleRate,
            channelCount = channelCount,
            dataByteCount = 0L,
        )
    }

    @Synchronized
    @Throws(IOException::class)
    fun append(samples: ShortArray, count: Int = samples.size) {
        check(state == State.OPEN) { "The WAV candidate is no longer open" }
        require(count in 0..samples.size)
        if (count == 0) return

        val bytes = ByteArray(count * Short.SIZE_BYTES)
        var byteIndex = 0
        repeat(count) { sampleIndex ->
            val sample = samples[sampleIndex].toInt()
            bytes[byteIndex++] = (sample and 0xff).toByte()
            bytes[byteIndex++] = ((sample ushr 8) and 0xff).toByte()
        }
        file.write(bytes)
        dataByteCount += bytes.size
    }

    @Synchronized
    @Throws(IOException::class)
    fun seal() {
        when (state) {
            State.SEALED, State.WAV_COMMITTED, State.COMMITTED -> return
            State.DISCARDED -> throw IOException("폐기한 녹음은 마무리할 수 없습니다.")
            State.OPEN -> Unit
        }

        try {
            WavFiles.writeHeader(
                file = file,
                sampleRate = sampleRate,
                channelCount = channelCount,
                dataByteCount = dataByteCount,
            )
            file.fd.sync()
            file.close()
            state = State.SEALED
        } catch (error: Exception) {
            runCatching { file.close() }
            stagingFile.delete()
            state = State.DISCARDED
            if (error is IOException) throw error
            throw IOException("녹음 조각을 마무리할 수 없습니다.", error)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun commitWav(): File {
        when (state) {
            State.WAV_COMMITTED, State.COMMITTED -> return completedFile
            State.DISCARDED -> throw IOException("폐기한 녹음은 저장할 수 없습니다.")
            State.OPEN -> seal()
            State.SEALED -> Unit
        }

        try {
            moveAtomically(stagingFile, completedFile)
            state = State.WAV_COMMITTED
            return completedFile
        } catch (error: Exception) {
            stagingFile.delete()
            state = State.DISCARDED
            if (error is IOException) throw error
            throw IOException("녹음 파일을 확정할 수 없습니다.", error)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun finalizeCommitted(): File {
        if (state == State.COMMITTED) return finalizedFile ?: completedFile
        val wavFile = commitWav()
        val output = completedRecordingFinalizer.finalize(wavFile)
        finalizedFile = output
        state = State.COMMITTED
        return output
    }

    @Synchronized
    @Throws(IOException::class)
    fun commit(): File {
        commitWav()
        return finalizeCommitted()
    }

    @Synchronized
    fun discard() {
        if (state == State.WAV_COMMITTED || state == State.COMMITTED || state == State.DISCARDED) {
            return
        }
        if (state == State.OPEN) runCatching { file.close() }
        stagingFile.delete()
        state = State.DISCARDED
    }

    private var finalizedFile: File? = null

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
}

internal data class ValidatedPcmWavFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val byteRate: Long,
    val blockAlign: Int,
)

internal class InvalidPcmWavFormatException(message: String) : IOException(message)

/** Reads the common PCM fields from classic PCM and WAVE_FORMAT_EXTENSIBLE fmt chunks. */
internal object PcmWavFormatParser {
    private const val CLASSIC_PCM_FORMAT_CODE = 0x0001
    private const val EXTENSIBLE_FORMAT_CODE = 0xfffe
    private const val PCM_BITS_PER_SAMPLE = 16
    private const val CLASSIC_FORMAT_BYTES = 16L
    private const val WAVE_FORMAT_EX_BYTES = 18L
    private const val MINIMUM_EXTENSIBLE_FORMAT_BYTES = 40L
    private const val MINIMUM_EXTENSIBLE_EXTRA_BYTES = 22
    private const val UINT32_MAX = 0xffff_ffffL
    private val PCM_SUBFORMAT_GUID = byteArrayOf(
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

    @Throws(IOException::class)
    fun read(input: RandomAccessFile, chunkByteCount: Long): ValidatedPcmWavFormat {
        if (chunkByteCount < CLASSIC_FORMAT_BYTES) {
            throw InvalidPcmWavFormatException("fmt 청크가 너무 짧습니다.")
        }

        val audioFormat = input.readUInt16LittleEndian()
        val channelCount = input.readUInt16LittleEndian()
        val sampleRate = input.readUInt32LittleEndian()
        val byteRate = input.readUInt32LittleEndian()
        val blockAlign = input.readUInt16LittleEndian()
        val bitsPerSample = input.readUInt16LittleEndian()

        when (audioFormat) {
            CLASSIC_PCM_FORMAT_CODE -> Unit
            EXTENSIBLE_FORMAT_CODE -> validateExtensibleFields(
                input = input,
                chunkByteCount = chunkByteCount,
                bitsPerSample = bitsPerSample,
            )
            else -> throw InvalidPcmWavFormatException(
                "지원하는 PCM 형식 코드가 아닙니다.",
            )
        }

        if (channelCount <= 0 || sampleRate !in 1L..Int.MAX_VALUE.toLong()) {
            throw InvalidPcmWavFormatException(
                "채널 수 또는 샘플레이트가 올바르지 않습니다.",
            )
        }
        if (bitsPerSample != PCM_BITS_PER_SAMPLE) {
            throw InvalidPcmWavFormatException("16비트 PCM 형식이 아닙니다.")
        }

        val expectedBlockAlign = channelCount * Short.SIZE_BYTES
        val expectedByteRate = sampleRate * expectedBlockAlign.toLong()
        if (blockAlign != expectedBlockAlign || byteRate != expectedByteRate) {
            throw InvalidPcmWavFormatException(
                "PCM byte rate 또는 block align 값이 올바르지 않습니다.",
            )
        }

        return ValidatedPcmWavFormat(
            sampleRate = sampleRate.toInt(),
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            byteRate = byteRate,
            blockAlign = blockAlign,
        )
    }

    private fun validateExtensibleFields(
        input: RandomAccessFile,
        chunkByteCount: Long,
        bitsPerSample: Int,
    ) {
        if (chunkByteCount < MINIMUM_EXTENSIBLE_FORMAT_BYTES) {
            throw InvalidPcmWavFormatException(
                "WAVE_FORMAT_EXTENSIBLE fmt 크기가 올바르지 않습니다.",
            )
        }
        val extensionByteCount = input.readUInt16LittleEndian()
        if (
            extensionByteCount < MINIMUM_EXTENSIBLE_EXTRA_BYTES ||
            WAVE_FORMAT_EX_BYTES + extensionByteCount != chunkByteCount
        ) {
            throw InvalidPcmWavFormatException(
                "WAVE_FORMAT_EXTENSIBLE 확장 크기가 올바르지 않습니다.",
            )
        }
        val validBitsPerSample = input.readUInt16LittleEndian()
        if (validBitsPerSample != bitsPerSample || validBitsPerSample != PCM_BITS_PER_SAMPLE) {
            throw InvalidPcmWavFormatException(
                "WAVE_FORMAT_EXTENSIBLE valid bits가 16비트 PCM과 맞지 않습니다.",
            )
        }

        input.readUInt32LittleEndian() // Channel mask may validly be zero (unspecified).
        val subFormatGuid = ByteArray(PCM_SUBFORMAT_GUID.size)
        input.readFully(subFormatGuid)
        if (!subFormatGuid.contentEquals(PCM_SUBFORMAT_GUID)) {
            throw InvalidPcmWavFormatException(
                "WAVE_FORMAT_EXTENSIBLE subtype이 PCM GUID가 아닙니다.",
            )
        }
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
            (byte3.toLong() shl 24)) and UINT32_MAX
    }
}

internal object WavFiles {
    private const val HEADER_SIZE = 44L
    private const val PCM_FORMAT = 1
    private const val BITS_PER_SAMPLE = 16
    private const val UINT32_MAX = 0xffff_ffffL

    fun durationSeconds(file: File): Double? {
        val descriptor = dataDescriptor(file) ?: return null
        return descriptor.dataByteCount.toDouble() / descriptor.format.byteRate.toDouble()
    }

    fun dataDescriptor(file: File): PcmWavDataDescriptor? {
        return try {
            RandomAccessFile(file, "r").use { input ->
                if (input.length() < HEADER_SIZE) return@use null
                if (input.readAscii(4) != "RIFF") return@use null
                val riffByteCount = input.readUInt32LittleEndian()
                val riffEnd = 8L + riffByteCount
                if (riffEnd < 12L || riffEnd > input.length()) return@use null
                if (input.readAscii(4) != "WAVE") return@use null

                var format: ValidatedPcmWavFormat? = null
                var dataByteCount: Long? = null
                var dataOffset: Long? = null
                while (input.filePointer + 8L <= riffEnd) {
                    val chunkId = input.readAscii(4)
                    val chunkSize = input.readUInt32LittleEndian()
                    val chunkDataStart = input.filePointer
                    val chunkDataEnd = chunkDataStart + chunkSize
                    if (chunkDataEnd < chunkDataStart || chunkDataEnd > riffEnd) {
                        return@use null
                    }

                    when (chunkId) {
                        "fmt " -> {
                            if (format != null) return@use null
                            format = PcmWavFormatParser.read(input, chunkSize)
                        }

                        "data" -> {
                            if (dataByteCount != null) return@use null
                            dataByteCount = chunkSize
                            dataOffset = chunkDataStart
                        }
                    }

                    val paddedEnd = chunkDataEnd + (chunkSize and 1L)
                    if (paddedEnd < chunkDataEnd || paddedEnd > riffEnd) {
                        return@use null
                    }
                    input.seek(paddedEnd)
                }
                if (input.filePointer != riffEnd) return@use null

                val parsedFormat = format ?: return@use null
                val bytes = dataByteCount ?: return@use null
                val offset = dataOffset ?: return@use null
                if (bytes % parsedFormat.blockAlign != 0L) return@use null
                PcmWavDataDescriptor(
                    format = parsedFormat,
                    dataOffset = offset,
                    dataByteCount = bytes,
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    @Throws(IOException::class)
    fun writeHeader(
        file: RandomAccessFile,
        sampleRate: Int,
        channelCount: Int,
        dataByteCount: Long,
    ) {
        val blockAlign = channelCount * Short.SIZE_BYTES
        val byteRate = sampleRate.toLong() * blockAlign
        val riffSize = 36L + dataByteCount
        if (dataByteCount !in 0L..UINT32_MAX || riffSize > UINT32_MAX) {
            throw IOException("WAV 파일 크기가 표준 RIFF 한도를 넘었습니다.")
        }

        val originalPosition = file.filePointer
        file.seek(0L)
        file.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        file.writeUInt32LittleEndian(riffSize)
        file.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        file.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        file.writeUInt32LittleEndian(16L)
        file.writeUInt16LittleEndian(PCM_FORMAT)
        file.writeUInt16LittleEndian(channelCount)
        file.writeUInt32LittleEndian(sampleRate.toLong())
        file.writeUInt32LittleEndian(byteRate)
        file.writeUInt16LittleEndian(blockAlign)
        file.writeUInt16LittleEndian(BITS_PER_SAMPLE)
        file.write("data".toByteArray(StandardCharsets.US_ASCII))
        file.writeUInt32LittleEndian(dataByteCount)
        file.seek(maxOf(originalPosition, HEADER_SIZE + dataByteCount))
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.US_ASCII)
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
            (byte3.toLong() shl 24)) and UINT32_MAX
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
}

internal data class PcmWavDataDescriptor(
    val format: ValidatedPcmWavFormat,
    val dataOffset: Long,
    val dataByteCount: Long,
)
