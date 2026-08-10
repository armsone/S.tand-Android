package com.armsone.stand.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.armsone.stand.recording.PendingWavRecording
import com.armsone.stand.recording.RecordingRepository
import java.io.File
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

sealed interface AudioMonitorState {
    data object Stopped : AudioMonitorState
    data object Starting : AudioMonitorState
    data object Monitoring : AudioMonitorState
    data object Stopping : AudioMonitorState
    data class Error(val message: String) : AudioMonitorState
}

/**
 * Foreground PCM16 microphone monitor. Permission requests remain the responsibility of the UI.
 * Callbacks are delivered through [callbackExecutor]; observable state is also exposed as flows.
 */
class AudioMonitor(
    context: Context,
    private val recordingRepository: RecordingRepository = RecordingRepository(context),
    detectorConfiguration: AudioDetectorConfiguration = AudioDetectorConfiguration(
        soundThresholdDB = DEFAULT_SOUND_THRESHOLD_DB,
    ),
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val lifecycleLock = Any()
    private val recordingFinalizationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, RECORDING_FINALIZATION_THREAD_NAME).apply { isDaemon = true }
    }

    @Volatile
    private var requestedConfiguration = detectorConfiguration.copy()

    @Volatile
    private var requestedRecordingEnabled = true

    private var recordingEnabled = true

    @Volatile
    private var stopRequested = true

    private var workerThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    private val detector = AudioEventDetector(detectorConfiguration.copy())
    private val classifier = SleepSoundClassifier()
    private val preRoll = Pcm16PreRollBuffer(
        maximumSamples = (sampleRate * PRE_ROLL_SECONDS).toInt(),
    )
    private var activeCandidate: ActiveCandidate? = null
    private var lastLevelPublicationNanos = 0L

    private val _state = MutableStateFlow<AudioMonitorState>(AudioMonitorState.Stopped)
    val state: StateFlow<AudioMonitorState> = _state.asStateFlow()

    private val _normalizedLevel = MutableStateFlow(0.0)
    val normalizedLevel: StateFlow<Double> = _normalizedLevel.asStateFlow()

    private val _isWritingClip = MutableStateFlow(false)
    val isWritingClip: StateFlow<Boolean> = _isWritingClip.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastClassification = MutableStateFlow<SleepSoundClassification?>(null)
    val lastClassification: StateFlow<SleepSoundClassification?> =
        _lastClassification.asStateFlow()

    @Volatile
    var onClap: (() -> Unit)? = null

    @Volatile
    var onMovement: ((SleepSoundClassification) -> Unit)? = null

    @Volatile
    var onClipSaved: ((File) -> Unit)? = null

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    /** Applies detector thresholds on the capture thread at the next PCM frame. */
    fun configure(configuration: AudioDetectorConfiguration) {
        requestedConfiguration = configuration.copy()
    }

    /**
     * Enables or disables candidate file creation without stopping clap and movement detection.
     * The capture thread applies the change at a frame boundary so an in-flight file is never
     * mutated concurrently with [processFrame].
     */
    fun setRecordingEnabled(enabled: Boolean) {
        requestedRecordingEnabled = enabled
    }

    /** Starts monitoring when RECORD_AUDIO is already granted. Repeated calls are safe. */
    fun start() {
        val permissionGranted = try {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) {
            false
        }
        if (!permissionGranted) {
            publishStartFailure("마이크 권한이 없어 소리 감지를 사용할 수 없습니다.")
            return
        }

        synchronized(lifecycleLock) {
            if (workerThread?.isAlive == true) return

            stopRequested = false
            resetProcessingState()
            _errorMessage.value = null
            _state.value = AudioMonitorState.Starting
            workerThread = Thread(::captureLoop, WORKER_THREAD_NAME).also(Thread::start)
        }
    }

    /** Stops capture once. Calling it repeatedly or concurrently is harmless. */
    fun stop() {
        val (threadToJoin, recordToStop) = synchronized(lifecycleLock) {
            stopRequested = true
            val activeThread = workerThread

            if (activeThread == null) {
                resetProcessingState()
                _errorMessage.value = null
                _state.value = AudioMonitorState.Stopped
                return
            }
            _state.value = AudioMonitorState.Stopping
            activeThread to audioRecord
        }

        runCatching {
            if (recordToStop?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recordToStop.stop()
            }
        }

        if (threadToJoin !== Thread.currentThread()) {
            runCatching { threadToJoin.join(STOP_JOIN_TIMEOUT_MILLIS) }
            if (threadToJoin.isAlive) {
                runCatching { recordToStop?.release() }
                runCatching { threadToJoin.join(STOP_JOIN_TIMEOUT_MILLIS) }
            }
        }
    }

    override fun close() {
        stop()
        recordingFinalizationExecutor.shutdown()
    }

    private fun captureLoop() {
        var localRecord: AudioRecord? = null
        var terminalError: String? = null

        try {
            val minimumBufferBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minimumBufferBytes <= 0) {
                throw AudioMonitorException("지원되는 마이크 입력 형식을 찾을 수 없습니다.")
            }

            val frameSampleCount = max(1, sampleRate / ANALYSIS_FRAMES_PER_SECOND)
            val audioBufferBytes = max(
                minimumBufferBytes * 2,
                frameSampleCount * Short.SIZE_BYTES * 4,
            )
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(format)
                .setBufferSizeInBytes(audioBufferBytes)
                .build()
            localRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw AudioMonitorException("마이크 입력을 초기화할 수 없습니다.")
            }

            synchronized(lifecycleLock) {
                if (stopRequested) return
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw AudioMonitorException("마이크 입력을 시작할 수 없습니다.")
                }
                audioRecord = record
                _state.value = AudioMonitorState.Monitoring
            }

            val samples = ShortArray(frameSampleCount)
            while (!stopRequested) {
                val sampleCount = record.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING,
                )
                when {
                    sampleCount > 0 -> processFrame(samples, sampleCount)
                    sampleCount == 0 -> Thread.yield()
                    stopRequested -> Unit
                    else -> throw AudioMonitorException(audioReadError(sampleCount))
                }
            }
        } catch (_: SecurityException) {
            if (!stopRequested) {
                terminalError = "마이크 권한이 없어 소리 감지를 사용할 수 없습니다."
            }
        } catch (error: Exception) {
            if (!stopRequested) {
                terminalError = error.message ?: "마이크 입력 중 오류가 발생했습니다."
            }
        } finally {
            runCatching {
                if (localRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    localRecord.stop()
                }
            }
            runCatching { localRecord?.release() }
            finishCandidateOnTermination()
            resetProcessingState()

            synchronized(lifecycleLock) {
                if (audioRecord === localRecord) audioRecord = null
                if (workerThread === Thread.currentThread()) {
                    workerThread = null
                    if (terminalError != null) {
                        _errorMessage.value = terminalError
                        _state.value = AudioMonitorState.Error(terminalError)
                    } else {
                        _state.value = AudioMonitorState.Stopped
                    }
                }
            }
        }
    }

    private fun processFrame(samples: ShortArray, sampleCount: Int) {
        applyRequestedRecordingPreference()
        val analysis = Pcm16AudioAnalyzer.analyze(
            samples = samples,
            count = sampleCount,
            sampleRate = sampleRate,
        )
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        detector.configuration = requestedConfiguration
        val detection = detector.analyze(
            rmsDB = analysis.rmsDB,
            peakDB = analysis.peakDB,
            bufferDuration = analysis.features.duration,
            now = nowNanos / NANOS_PER_SECOND,
        )

        if (detection.clapDetected) {
            dispatchCallback { onClap?.invoke() }
        }

        classifier.analyze(
            features = analysis.features,
            detection = detection,
        )?.let(::handleClassification)

        if (recordingEnabled) {
            processCandidate(
                samples = samples,
                sampleCount = sampleCount,
                detection = detection,
                nowNanos = nowNanos,
            )
        }

        if (
            lastLevelPublicationNanos == 0L ||
            nowNanos - lastLevelPublicationNanos >= LEVEL_PUBLICATION_INTERVAL_NANOS
        ) {
            lastLevelPublicationNanos = nowNanos
            _normalizedLevel.value = analysis.normalizedLevel
        }
    }

    private fun handleClassification(classification: SleepSoundClassification) {
        _lastClassification.value = classification
        if (SleepSoundWakePolicy.shouldWake(classification)) {
            dispatchCallback { onMovement?.invoke(classification) }
        }

        val candidate = activeCandidate ?: return
        if (SleepSoundRecordingPolicy.shouldKeep(classification)) {
            candidate.isApproved = true
        } else if (!candidate.isApproved) {
            discardCandidate()
        }
    }

    private fun processCandidate(
        samples: ShortArray,
        sampleCount: Int,
        detection: AudioDetection,
        nowNanos: Long,
    ) {
        val candidate = activeCandidate
        if (candidate == null) {
            preRoll.add(samples, sampleCount)
            if (detection.soundBegan) {
                startCandidate(nowNanos)
            }
            return
        }

        val currentRecording = candidate.recording
        val hadOpenSegment = currentRecording != null
        if (currentRecording != null) {
            try {
                currentRecording.append(samples, sampleCount)
            } catch (_: Exception) {
                discardCandidate()
                publishRecordingError("녹음 파일을 저장할 수 없습니다.")
                return
            }
        }

        if (detection.isAboveSoundThreshold) {
            candidate.silenceDeadlineNanos = nowNanos + POST_ROLL_NANOS
        }

        when (
            AudioCandidateLifecyclePolicy.actionForFrame(
                hasOpenSegment = hadOpenSegment,
                segmentStartedAtNanos = candidate.startedAtNanos,
                silenceDeadlineNanos = candidate.silenceDeadlineNanos,
                nowNanos = nowNanos,
                isAboveSoundThreshold = detection.isAboveSoundThreshold,
                isApproved = candidate.isApproved,
            )
        ) {
            AudioCandidateFrameAction.RETAIN -> Unit
            AudioCandidateFrameAction.OPEN_CONTINUATION -> {
                val continuation = openContinuation(candidate, nowNanos) ?: return
                try {
                    continuation.append(samples, sampleCount)
                } catch (_: Exception) {
                    discardCandidate()
                    publishRecordingError("녹음 파일을 저장할 수 없습니다.")
                    return
                }
            }
            AudioCandidateFrameAction.SEAL_AND_WAIT -> sealCurrentSegment(candidate)
            AudioCandidateFrameAction.SEAL_AND_CONTINUE -> {
                if (sealCurrentSegment(candidate)) {
                    openContinuation(candidate, nowNanos)
                }
            }
            AudioCandidateFrameAction.FINALIZE -> finalizeCandidate()
        }
    }

    private fun startCandidate(nowNanos: Long) {
        val recording = try {
            recordingRepository.beginWavCandidate(
                createdAt = Instant.now(),
                sampleRate = sampleRate,
            )
        } catch (_: Exception) {
            preRoll.clear()
            publishRecordingError("녹음 파일을 시작할 수 없습니다.")
            return
        }

        try {
            preRoll.forEachChunk { chunk -> recording.append(chunk) }
        } catch (_: Exception) {
            recording.discard()
            preRoll.clear()
            publishRecordingError("사전 녹음 구간을 저장할 수 없습니다.")
            return
        }

        preRoll.clear()
        activeCandidate = ActiveCandidate(
            recording = recording,
            startedAtNanos = nowNanos,
            silenceDeadlineNanos = nowNanos + POST_ROLL_NANOS,
        )
        _errorMessage.value = null
        _isWritingClip.value = true
    }

    private fun sealCurrentSegment(candidate: ActiveCandidate): Boolean {
        val rolledRecording = candidate.recording ?: return false
        try {
            rolledRecording.seal()
            candidate.sealedSegments += rolledRecording
            candidate.recording = null
        } catch (_: Exception) {
            discardCandidate()
            publishRecordingError("90초 녹음 조각을 마무리할 수 없습니다.")
            return false
        }
        return true
    }

    private fun openContinuation(
        candidate: ActiveCandidate,
        nowNanos: Long,
    ): PendingWavRecording? {
        val nextRecording = try {
            recordingRepository.beginWavCandidate(
                createdAt = Instant.now(),
                sampleRate = sampleRate,
            )
        } catch (_: Exception) {
            discardCandidate()
            publishRecordingError("다음 녹음 조각을 시작할 수 없습니다.")
            return null
        }
        candidate.recording = nextRecording
        candidate.startedAtNanos = nowNanos
        candidate.silenceDeadlineNanos = nowNanos + POST_ROLL_NANOS
        return nextRecording
    }

    private fun finalizeCandidate() {
        val candidate = activeCandidate ?: return
        activeCandidate = null
        _isWritingClip.value = false
        preRoll.clear()

        val recordings = buildList {
            addAll(candidate.sealedSegments)
            candidate.recording?.let(::add)
        }

        if (!candidate.isApproved) {
            recordings.forEach(PendingWavRecording::discard)
            return
        }

        val committedRecordings = recordings.mapNotNull { recording ->
            try {
                recording.commitWav()
                recording
            } catch (_: Exception) {
                recording.discard()
                null
            }
        }
        if (committedRecordings.size != recordings.size) {
            publishRecordingError("승인된 녹음 조각 일부를 보관할 수 없습니다.")
        }

        val finalizeTask = Runnable {
            var failed = false
            committedRecordings.forEach { recording ->
                try {
                    val savedFile = recording.finalizeCommitted()
                    dispatchCallback { onClipSaved?.invoke(savedFile) }
                } catch (_: Exception) {
                    failed = true
                }
            }
            recordingRepository.reload()
            if (failed) {
                publishRecordingError("승인된 녹음 조각 일부를 보관할 수 없습니다.")
            }
        }
        try {
            recordingFinalizationExecutor.execute(finalizeTask)
        } catch (_: RejectedExecutionException) {
            // close()와 경합해도 이미 확정한 WAV를 잃지 않고 현재 스레드에서 마무리합니다.
            finalizeTask.run()
        }
    }

    private fun discardCandidate() {
        val candidate = activeCandidate ?: return
        activeCandidate = null
        candidate.recording?.discard()
        candidate.sealedSegments.forEach(PendingWavRecording::discard)
        preRoll.clear()
        _isWritingClip.value = false
    }

    private fun finishCandidateOnTermination() {
        if (activeCandidate?.isApproved == true) {
            finalizeCandidate()
        } else {
            discardCandidate()
        }
    }

    private fun applyRequestedRecordingPreference() {
        val requested = requestedRecordingEnabled
        if (recordingEnabled == requested) return

        if (!requested) {
            finishCandidateOnTermination()
            preRoll.clear()
        } else {
            detector.reset()
            classifier.reset()
            preRoll.clear()
        }
        recordingEnabled = requested
    }

    private fun resetProcessingState() {
        detector.reset()
        classifier.reset()
        preRoll.clear()
        lastLevelPublicationNanos = 0L
        _normalizedLevel.value = 0.0
        _isWritingClip.value = false
    }

    private fun publishStartFailure(message: String) {
        synchronized(lifecycleLock) {
            if (workerThread?.isAlive == true) return
            stopRequested = true
            resetProcessingState()
            _errorMessage.value = message
            _state.value = AudioMonitorState.Error(message)
        }
    }

    private fun publishRecordingError(message: String) {
        _errorMessage.value = message
    }

    private fun dispatchCallback(block: () -> Unit) {
        runCatching {
            callbackExecutor.execute {
                runCatching(block)
            }
        }
    }

    private fun audioReadError(code: Int): String = when (code) {
        AudioRecord.ERROR_BAD_VALUE -> "마이크 버퍼 설정이 올바르지 않습니다."
        AudioRecord.ERROR_INVALID_OPERATION -> "마이크 입력을 사용할 수 없습니다."
        AudioRecord.ERROR_DEAD_OBJECT -> "마이크 입력 연결이 끊어졌습니다."
        AudioRecord.ERROR -> "마이크 입력 중 오류가 발생했습니다."
        else -> "마이크 입력 중 오류가 발생했습니다. ($code)"
    }

    private data class ActiveCandidate(
        var recording: PendingWavRecording?,
        var startedAtNanos: Long,
        var silenceDeadlineNanos: Long,
        var isApproved: Boolean = false,
        val sealedSegments: MutableList<PendingWavRecording> = mutableListOf(),
    )

    private class AudioMonitorException(message: String) : Exception(message)

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val DEFAULT_SOUND_THRESHOLD_DB = -36f
        private const val PRE_ROLL_SECONDS = 0.8
        private const val POST_ROLL_SECONDS = 1.4
        private const val ANALYSIS_FRAMES_PER_SECOND = 50
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val LEVEL_PUBLICATION_INTERVAL_NANOS = 80_000_000L
        private const val POST_ROLL_NANOS = 1_400_000_000L
        private const val STOP_JOIN_TIMEOUT_MILLIS = 1_000L
        private const val WORKER_THREAD_NAME = "stand-audio-monitor"
        private const val RECORDING_FINALIZATION_THREAD_NAME = "stand-recording-finalizer"
    }
}

internal object AudioClipDurationPolicy {
    const val MaximumClipDurationNanos = 90_000_000_000L

    fun shouldRoll(startedAtNanos: Long, nowNanos: Long): Boolean =
        nowNanos >= startedAtNanos &&
            nowNanos - startedAtNanos >= MaximumClipDurationNanos
}

internal enum class AudioCandidateFrameAction {
    RETAIN,
    OPEN_CONTINUATION,
    SEAL_AND_WAIT,
    SEAL_AND_CONTINUE,
    FINALIZE,
}

/**
 * Keeps maximum-length segment sealing separate from event finalization. A sealed-only candidate
 * remains pending until the classifier has approved it and the normal post-roll deadline passes.
 * Rejected classifications are removed immediately when their classifier result is handled.
 */
internal object AudioCandidateLifecyclePolicy {
    fun actionForFrame(
        hasOpenSegment: Boolean,
        segmentStartedAtNanos: Long,
        silenceDeadlineNanos: Long,
        nowNanos: Long,
        isAboveSoundThreshold: Boolean,
        isApproved: Boolean,
    ): AudioCandidateFrameAction {
        if (
            hasOpenSegment &&
            AudioClipDurationPolicy.shouldRoll(segmentStartedAtNanos, nowNanos)
        ) {
            return if (isAboveSoundThreshold) {
                AudioCandidateFrameAction.SEAL_AND_CONTINUE
            } else {
                AudioCandidateFrameAction.SEAL_AND_WAIT
            }
        }

        if (!hasOpenSegment && isAboveSoundThreshold) {
            return AudioCandidateFrameAction.OPEN_CONTINUATION
        }
        if (isApproved && nowNanos >= silenceDeadlineNanos) {
            return AudioCandidateFrameAction.FINALIZE
        }
        return AudioCandidateFrameAction.RETAIN
    }
}

internal class Pcm16PreRollBuffer(
    private val maximumSamples: Int,
) {
    private val chunks = ArrayDeque<ShortArray>()
    var sampleCount: Int = 0
        private set

    init {
        require(maximumSamples >= 0)
    }

    fun add(samples: ShortArray, count: Int = samples.size) {
        require(count in 0..samples.size)
        if (count == 0 || maximumSamples == 0) return

        chunks.addLast(samples.copyOf(count))
        sampleCount += count
        trimToCapacity()
    }

    fun forEachChunk(action: (ShortArray) -> Unit) {
        chunks.forEach(action)
    }

    fun snapshot(): ShortArray {
        val result = ShortArray(sampleCount)
        var destinationIndex = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, destinationOffset = destinationIndex)
            destinationIndex += chunk.size
        }
        return result
    }

    fun clear() {
        chunks.clear()
        sampleCount = 0
    }

    private fun trimToCapacity() {
        while (sampleCount > maximumSamples) {
            val excess = sampleCount - maximumSamples
            val first = chunks.removeFirst()
            if (first.size <= excess) {
                sampleCount -= first.size
            } else {
                chunks.addFirst(first.copyOfRange(excess, first.size))
                sampleCount -= excess
            }
        }
    }
}

internal data class Pcm16Analysis(
    val rmsDB: Float,
    val peakDB: Float,
    val normalizedLevel: Double,
    val features: SleepSoundFeatures,
)

internal object Pcm16AudioAnalyzer {
    private const val LOW_PASS_CUTOFF_HZ = 400.0
    private const val DB_FLOOR_AMPLITUDE = 0.000_031_622_78

    fun analyze(
        samples: ShortArray,
        count: Int = samples.size,
        sampleRate: Int,
    ): Pcm16Analysis {
        require(count in 1..samples.size)
        require(sampleRate > 0)

        val smoothing = exp(-2.0 * PI * LOW_PASS_CUTOFF_HZ / sampleRate)
        var sumOfSquares = 0.0
        var peak = 0.0
        var lowPassSample = 0.0
        var lowEnergy = 0.0
        var totalEnergy = 0.0
        var zeroCrossings = 0
        var previousSample = samples[0].toDouble() / -Short.MIN_VALUE.toDouble()

        repeat(count) { index ->
            val sample = samples[index].toDouble() / -Short.MIN_VALUE.toDouble()
            val magnitude = abs(sample)
            sumOfSquares += sample * sample
            peak = max(peak, magnitude)
            lowPassSample = (1.0 - smoothing) * sample + smoothing * lowPassSample
            lowEnergy += lowPassSample * lowPassSample
            totalEnergy += sample * sample
            if (index > 0 && (sample >= 0.0) != (previousSample >= 0.0)) {
                zeroCrossings += 1
            }
            previousSample = sample
        }

        val rms = sqrt(sumOfSquares / count)
        val rmsDB = (20.0 * log10(max(rms, DB_FLOOR_AMPLITUDE))).toFloat()
        val peakDB = (20.0 * log10(max(peak, DB_FLOOR_AMPLITUDE))).toFloat()
        val zeroCrossingRate = if (count > 1) {
            zeroCrossings.toDouble() / (count - 1).toDouble()
        } else {
            0.0
        }
        val lowFrequencyRatio = if (totalEnergy > 0.0) {
            min(1.0, lowEnergy / totalEnergy)
        } else {
            0.0
        }

        return Pcm16Analysis(
            rmsDB = rmsDB,
            peakDB = peakDB,
            normalizedLevel = ((rmsDB + 70f) / 55f).toDouble().coerceIn(0.0, 1.0),
            features = SleepSoundFeatures(
                rmsDB = rmsDB,
                peakDB = peakDB,
                zeroCrossingRate = zeroCrossingRate,
                lowFrequencyRatio = lowFrequencyRatio,
                duration = count.toDouble() / sampleRate.toDouble(),
            ),
        )
    }
}
