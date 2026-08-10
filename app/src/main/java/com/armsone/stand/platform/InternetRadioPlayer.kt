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
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface InternetRadioState {
    data object Idle : InternetRadioState
    data object Loading : InternetRadioState
    data class Playing(val displayName: String) : InternetRadioState
    data class Failed(val message: String) : InternetRadioState
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
            if (change < 0) stop()
        }
        .build()
    private val mutableState = MutableStateFlow<InternetRadioState>(InternetRadioState.Idle)
    val state: StateFlow<InternetRadioState> = mutableState.asStateFlow()
    private var mediaPlayer: MediaPlayer? = null
    private var receiverRegistered = false
    private val timeout = Runnable { fail("라디오 연결 시간이 초과되었습니다.") }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = stop()
    }

    fun play(configuration: InternetRadioConfiguration) {
        val radio = configuration.normalizedOrNull() ?: run {
            fail("라디오 주소를 확인해 주세요.")
            return
        }
        releasePlayer(abandonFocus = false)
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("다른 오디오가 사용 중입니다.")
            return
        }
        mutableState.value = InternetRadioState.Loading
        registerNoisyReceiver()
        val prepared = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(radio.streamUrl)
                setOnPreparedListener {
                    handler.removeCallbacks(timeout)
                    start()
                    mutableState.value = InternetRadioState.Playing(radio.displayName)
                }
                setOnErrorListener { _, _, _ ->
                    fail("라디오 스트림을 재생할 수 없습니다.")
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            fail("라디오 스트림을 열 수 없습니다.")
            return
        }
        mediaPlayer = prepared
        handler.postDelayed(timeout, CONNECTION_TIMEOUT_MILLIS)
    }

    fun stop() {
        releasePlayer(abandonFocus = true)
        mutableState.value = InternetRadioState.Idle
    }

    private fun fail(message: String) {
        releasePlayer(abandonFocus = true)
        mutableState.value = InternetRadioState.Failed(message)
    }

    private fun releasePlayer(abandonFocus: Boolean) {
        handler.removeCallbacks(timeout)
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        unregisterNoisyReceiver()
        if (abandonFocus) audioManager.abandonAudioFocusRequest(focusRequest)
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
