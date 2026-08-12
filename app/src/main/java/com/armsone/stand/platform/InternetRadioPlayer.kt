package com.armsone.stand.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.InternetRadioReconnectPolicy
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface InternetRadioState {
    data object Idle : InternetRadioState
    data class Loading(val channelID: String, val displayName: String) : InternetRadioState
    data class Playing(val channelID: String, val displayName: String) : InternetRadioState
    data class Reconnecting(
        val channelID: String,
        val displayName: String,
        val attempt: Int,
        val delaySeconds: Int,
    ) : InternetRadioState
    data class Failed(
        val channelID: String?,
        val displayName: String?,
        val message: String,
    ) : InternetRadioState
}

object RadioVolumePolicy {
    const val HORIZONTAL_DRAG_TRAVEL_RATIO = 0.5f

    fun clamped(level: Float): Float = level.coerceIn(0f, 1f)

    fun level(
        startingAt: Float,
        horizontalTranslationPx: Float,
        viewportWidthPx: Float,
    ): Float {
        val travel = (viewportWidthPx * HORIZONTAL_DRAG_TRAVEL_RATIO).coerceAtLeast(1f)
        return clamped(startingAt + horizontalTranslationPx / travel)
    }
}

class InternetRadioPlayer(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            handler.post { handleAudioFocusChange(change) }
        }
        .build()
    private val mutableState = MutableStateFlow<InternetRadioState>(InternetRadioState.Idle)
    val state: StateFlow<InternetRadioState> = mutableState.asStateFlow()
    private val mutableVolume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = mutableVolume.asStateFlow()
    private var mediaPlayer: MediaPlayer? = null
    private var receiverRegistered = false
    private var currentConfiguration: InternetRadioConfiguration? = null
    private var retryAttempt = 0
    private var explicitlyStopped = true
    private var resumeAfterFocusGain = false
    private val timeout = Runnable { handleConnectionFailure() }
    private val retry = Runnable { currentConfiguration?.let(::startConnection) }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = stop()
    }

    fun play(configuration: InternetRadioConfiguration) {
        val radio = configuration.normalizedOrNull() ?: run {
            fail("라디오 주소를 확인해 주세요.", configuration)
            return
        }
        handler.removeCallbacks(retry)
        explicitlyStopped = false
        resumeAfterFocusGain = false
        currentConfiguration = radio
        retryAttempt = 0
        startConnection(radio)
    }

    private fun startConnection(radio: InternetRadioConfiguration) {
        releasePlayer(abandonFocus = false)
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("다른 오디오가 사용 중입니다.", radio)
            return
        }
        mutableState.value = InternetRadioState.Loading(radio.id, radio.displayName)
        registerNoisyReceiver()
        val prepared = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setVolume(mutableVolume.value, mutableVolume.value)
                setDataSource(radio.streamUrl)
                setOnPreparedListener {
                    handler.removeCallbacks(timeout)
                    start()
                    retryAttempt = 0
                    mutableState.value = InternetRadioState.Playing(radio.id, radio.displayName)
                }
                setOnErrorListener { _, _, _ ->
                    handleConnectionFailure()
                    true
                }
                setOnCompletionListener { handleConnectionFailure() }
                prepareAsync()
            }
        }.getOrElse {
            handleConnectionFailure()
            return
        }
        mediaPlayer = prepared
        handler.postDelayed(timeout, CONNECTION_TIMEOUT_MILLIS)
    }

    fun updateVolume(level: Float) {
        val normalized = RadioVolumePolicy.clamped(level)
        mutableVolume.value = normalized
        mediaPlayer?.setVolume(normalized, normalized)
    }

    fun stop() {
        explicitlyStopped = true
        resumeAfterFocusGain = false
        currentConfiguration = null
        retryAttempt = 0
        handler.removeCallbacks(retry)
        releasePlayer(abandonFocus = true)
        mutableState.value = InternetRadioState.Idle
    }

    private fun handleConnectionFailure() {
        if (explicitlyStopped) return
        val radio = currentConfiguration ?: return
        releasePlayer(abandonFocus = false)
        val delaySeconds = InternetRadioReconnectPolicy.delaySeconds(retryAttempt) ?: run {
            fail("라디오 연결을 복구하지 못했습니다.", radio)
            return
        }
        retryAttempt += 1
        mutableState.value = InternetRadioState.Reconnecting(
            channelID = radio.id,
            displayName = radio.displayName,
            attempt = retryAttempt,
            delaySeconds = delaySeconds,
        )
        handler.postDelayed(retry, delaySeconds * 1_000L)
    }

    private fun fail(
        message: String,
        configuration: InternetRadioConfiguration? = currentConfiguration,
    ) {
        explicitlyStopped = true
        resumeAfterFocusGain = false
        currentConfiguration = null
        handler.removeCallbacks(retry)
        releasePlayer(abandonFocus = true)
        mutableState.value = InternetRadioState.Failed(
            channelID = configuration?.id,
            displayName = configuration?.displayName,
            message = message,
        )
    }

    private fun releasePlayer(abandonFocus: Boolean) {
        handler.removeCallbacks(timeout)
        mediaPlayer?.setOnPreparedListener(null)
        mediaPlayer?.setOnErrorListener(null)
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        unregisterNoisyReceiver()
        if (abandonFocus) audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun handleAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!resumeAfterFocusGain || explicitlyStopped) return
                resumeAfterFocusGain = false
                currentConfiguration?.let(::startConnection)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                if (explicitlyStopped || currentConfiguration == null) return
                resumeAfterFocusGain = true
                handler.removeCallbacks(retry)
                releasePlayer(abandonFocus = false)
                mutableState.value = InternetRadioState.Idle
            }
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }

    private fun registerNoisyReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        receiverRegistered = false
    }

    override fun close() = stop()

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 30_000L
    }
}
