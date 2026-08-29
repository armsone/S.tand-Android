package com.armsone.stand.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.InternetRadioReconnectPolicy
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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

object VolumeAdjustmentPolicy {
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

object RadioStreamResolutionPolicy {
    fun inferMimeType(url: String, contentTypeHeader: String?): String? {
        val baseContentType = contentTypeHeader?.substringBefore(';')?.trim()?.lowercase()
        if (baseContentType != null) {
            when (baseContentType) {
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
                "audio/x-mpegurl",
                "audio/mpegurl" -> return MimeTypes.APPLICATION_M3U8
                "audio/mpeg", "audio/mp3" -> return MimeTypes.AUDIO_MPEG
                "audio/aac", "audio/aacp" -> return MimeTypes.AUDIO_AAC
            }
        }
        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
        if (cleanUrl.endsWith(".m3u8")) {
            return MimeTypes.APPLICATION_M3U8
        }
        if (cleanUrl.endsWith(".mp3")) {
            return MimeTypes.AUDIO_MPEG
        }
        if (cleanUrl.endsWith(".aac")) {
            return MimeTypes.AUDIO_AAC
        }
        return null
    }

    fun buildMediaItem(targetUrl: String, mimeType: String?): MediaItem {
        val builder = MediaItem.Builder().setUri(targetUrl)
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        return builder.build()
    }
}

class InternetRadioPlayer(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val frameworkAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val media3AudioAttributes = Media3AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(frameworkAudioAttributes)
        .setOnAudioFocusChangeListener { change ->
            handler.post { handleAudioFocusChange(change) }
        }
        .build()
    private val mutableState = MutableStateFlow<InternetRadioState>(InternetRadioState.Idle)
    val state: StateFlow<InternetRadioState> = mutableState.asStateFlow()
    private val mutableVolume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = mutableVolume.asStateFlow()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private var activeCall: Call? = null
    private var exoPlayer: ExoPlayer? = null
    private var receiverRegistered = false
    private var currentConfiguration: InternetRadioConfiguration? = null
    private var retryAttempt = 0
    private var explicitlyStopped = true
    private var resumeAfterFocusGain = false
    private var currentGeneration = 0L
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
        val generation = ++currentGeneration
        releasePlayer(abandonFocus = false)
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("다른 오디오가 사용 중입니다.", radio)
            return
        }
        mutableState.value = InternetRadioState.Loading(radio.id, radio.displayName)
        registerNoisyReceiver()
        handler.postDelayed(timeout, CONNECTION_TIMEOUT_MILLIS)

        resolveAndStart(radio, generation)
    }

    private fun resolveAndStart(radio: InternetRadioConfiguration, generation: Long) {
        val request = runCatching {
            Request.Builder()
                .url(radio.streamUrl)
                .head()
                .build()
        }.getOrNull()

        if (request == null) {
            startExoPlayer(radio, radio.streamUrl, null, generation)
            return
        }

        val call = okHttpClient.newCall(request)
        activeCall = call
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val isSuccessful = response.isSuccessful
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type")
                response.close()
                handler.post {
                    if (generation != currentGeneration || explicitlyStopped) return@post
                    if (activeCall === call) {
                        activeCall = null
                    }
                    val targetUrl = if (isSuccessful) finalUrl else radio.streamUrl
                    val mimeType = RadioStreamResolutionPolicy.inferMimeType(
                        targetUrl,
                        contentType.takeIf { isSuccessful },
                    )
                    startExoPlayer(radio, targetUrl, mimeType, generation)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (generation != currentGeneration || explicitlyStopped) return@post
                    if (activeCall === call) {
                        activeCall = null
                    }
                    val mimeType = RadioStreamResolutionPolicy.inferMimeType(radio.streamUrl, null)
                    startExoPlayer(radio, radio.streamUrl, mimeType, generation)
                }
            }
        })
    }

    private fun startExoPlayer(
        radio: InternetRadioConfiguration,
        streamUrl: String,
        mimeType: String?,
        generation: Long,
    ) {
        if (generation != currentGeneration || explicitlyStopped) return

        val mediaItem = RadioStreamResolutionPolicy.buildMediaItem(streamUrl, mimeType)

        val player = runCatching {
            ExoPlayer.Builder(appContext)
                .setAudioAttributes(media3AudioAttributes, false)
                .build().apply {
                    volume = mutableVolume.value
                    setMediaItem(mediaItem)
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (generation != currentGeneration || explicitlyStopped) return
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    if (playWhenReady) {
                                        handler.removeCallbacks(timeout)
                                        retryAttempt = 0
                                        mutableState.value = InternetRadioState.Playing(
                                            radio.id,
                                            radio.displayName,
                                        )
                                    }
                                }
                                Player.STATE_ENDED -> handleConnectionFailure()
                                else -> Unit
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (generation != currentGeneration || explicitlyStopped) return
                            if (isPlaying) {
                                handler.removeCallbacks(timeout)
                                retryAttempt = 0
                                mutableState.value = InternetRadioState.Playing(
                                    radio.id,
                                    radio.displayName,
                                )
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (generation != currentGeneration || explicitlyStopped) return
                            handleConnectionFailure()
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
        }.getOrElse {
            handleConnectionFailure()
            return
        }

        exoPlayer = player
    }

    fun updateVolume(level: Float) {
        val normalized = VolumeAdjustmentPolicy.clamped(level)
        mutableVolume.value = normalized
        exoPlayer?.volume = normalized
    }

    fun stop() {
        explicitlyStopped = true
        resumeAfterFocusGain = false
        currentConfiguration = null
        retryAttempt = 0
        currentGeneration++
        handler.removeCallbacks(retry)
        releasePlayer(abandonFocus = true)
        mutableState.value = InternetRadioState.Idle
    }

    private fun handleConnectionFailure() {
        if (explicitlyStopped) return
        val radio = currentConfiguration ?: return
        currentGeneration++
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
        currentGeneration++
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
        activeCall?.cancel()
        activeCall = null
        exoPlayer?.runCatching {
            stop()
            release()
        }
        exoPlayer = null
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
                currentGeneration++
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

    override fun close() {
        stop()
        okHttpClient.dispatcher.cancelAll()
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 30_000L
    }
}
