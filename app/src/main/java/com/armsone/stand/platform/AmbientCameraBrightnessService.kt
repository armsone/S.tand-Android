package com.armsone.stand.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AmbientCameraState {
    DISABLED,
    PERMISSION_NEEDED,
    DENIED,
    MEASURING,
    READY,
    UNAVAILABLE,
}

data class AmbientCameraReading(
    val value: Float,
    val measuredAtElapsedRealtimeNanos: Long,
    val lensFacing: Int,
) {
    val isDark: Boolean
        get() = value < AmbientCameraPolicy.DarkThreshold
}

object AmbientCameraPolicy {
    const val DarkThreshold = 0.16f
    const val MaximumReadingAgeNanos = 90_000_000_000L

    fun adjustedBrightness(
        averageLuma: Float,
        iso: Int?,
        exposureTimeNanos: Long?,
    ): Float {
        val luma = averageLuma.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val isoFactor = max(0.5, (iso ?: 100).coerceAtLeast(1) / 100.0)
        val exposureSeconds = max(
            1.0 / 4_000.0,
            (exposureTimeNanos ?: DEFAULT_EXPOSURE_NANOS).coerceAtLeast(1L) / 1e9,
        )
        val exposureFactor = max(0.25, exposureSeconds * 60.0)
        val compensation = sqrt(isoFactor * exposureFactor)
        return (luma / max(0.5, compensation)).toFloat().coerceIn(0f, 1f)
    }

    fun median(samples: List<Float>): Float? {
        val values = samples.filter(Float::isFinite).map { it.coerceIn(0f, 1f) }.sorted()
        return values.takeIf(List<Float>::isNotEmpty)?.get(values.size / 2)
    }

    fun isFresh(reading: AmbientCameraReading?, nowNanos: Long): Boolean {
        if (reading == null || nowNanos < reading.measuredAtElapsedRealtimeNanos) return false
        return nowNanos - reading.measuredAtElapsedRealtimeNanos < MaximumReadingAgeNanos
    }

    private const val DEFAULT_EXPOSURE_NANOS = 16_666_667L
}

/**
 * Opens one camera only for a short Y-plane luminance measurement. No image is encoded, saved,
 * uploaded, or exposed to callers.
 */
@Suppress("DEPRECATION") // createCaptureSession(List, ...) is required for minSdk 26.
class AmbientCameraBrightnessService(
    context: Context,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
) : Closeable {
    private val applicationContext = context.applicationContext
    private val cameraManager = applicationContext.getSystemService(CameraManager::class.java)
    private val workerThread = HandlerThread(WORKER_THREAD_NAME).apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mutableState = MutableStateFlow(AmbientCameraState.DISABLED)
    val state: StateFlow<AmbientCameraState> = mutableState.asStateFlow()
    private val mutableReading = MutableStateFlow<AmbientCameraReading?>(null)
    val reading: StateFlow<AmbientCameraReading?> = mutableReading.asStateFlow()

    private var enabled = false
    private var permissionDenied = false
    private var closed = false
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var completion: ((AmbientCameraReading?) -> Unit)? = null
    private val samples = mutableListOf<Float>()
    private var receivedFrameCount = 0
    private var latestIso: Int? = null
    private var latestExposureNanos: Long? = null
    private var latestAeState: Int? = null
    private var activeLensFacing = CameraCharacteristics.LENS_FACING_FRONT
    private val timeout = Runnable { finishMeasurement(reading = makeReading(), failed = true) }

    fun setEnabled(enabled: Boolean, hasPermission: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            cancel()
            mutableReading.value = null
            mutableState.value = AmbientCameraState.DISABLED
            return
        }
        mutableState.value = when {
            !hasAnyCamera() -> AmbientCameraState.UNAVAILABLE
            hasPermission -> {
                permissionDenied = false
                AmbientCameraState.READY
            }
            permissionDenied -> AmbientCameraState.DENIED
            else -> AmbientCameraState.PERMISSION_NEEDED
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!enabled) return
        permissionDenied = !granted
        mutableState.value = when {
            granted -> AmbientCameraState.READY
            else -> AmbientCameraState.DENIED
        }
    }

    fun measureOnce(
        preferBackCamera: Boolean = false,
        completion: (AmbientCameraReading?) -> Unit = {},
    ) {
        if (closed || !enabled) {
            callbackExecutor.execute { completion(null) }
            return
        }
        if (!hasCameraPermission()) {
            mutableState.value = if (permissionDenied) {
                AmbientCameraState.DENIED
            } else {
                AmbientCameraState.PERMISSION_NEEDED
            }
            callbackExecutor.execute { completion(null) }
            return
        }
        workerHandler.post {
            startMeasurement(preferBackCamera, completion)
        }
    }

    fun cancel() {
        if (closed) return
        workerHandler.post { finishMeasurement(reading = null, failed = false) }
    }

    private fun startMeasurement(
        preferBackCamera: Boolean,
        callback: (AmbientCameraReading?) -> Unit,
    ) {
        finishMeasurement(reading = null, failed = false, notify = false)
        completion = callback
        samples.clear()
        receivedFrameCount = 0
        latestIso = null
        latestExposureNanos = null
        latestAeState = null

        val selected = selectCamera(preferBackCamera)
        if (selected == null) {
            finishMeasurement(reading = null, failed = true)
            return
        }
        val (cameraId, lensFacing) = selected
        activeLensFacing = lensFacing
        mutableState.value = AmbientCameraState.MEASURING
        try {
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (mutableState.value != AmbientCameraState.MEASURING) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        createSession(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        finishMeasurement(reading = makeReading(), failed = true)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        finishMeasurement(reading = makeReading(), failed = true)
                    }
                },
                workerHandler,
            )
            workerHandler.postDelayed(timeout, MEASUREMENT_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            finishMeasurement(reading = null, failed = false)
            mutableState.value = AmbientCameraState.PERMISSION_NEEDED
        } catch (_: RuntimeException) {
            finishMeasurement(reading = null, failed = true)
        }
    }

    private fun createSession(camera: CameraDevice) {
        val reader = ImageReader.newInstance(
            FRAME_WIDTH,
            FRAME_HEIGHT,
            ImageFormat.YUV_420_888,
            MAX_IMAGES,
        )
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use(::consumeImage)
        }, workerHandler)
        try {
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (mutableState.value != AmbientCameraState.MEASURING) {
                            session.close()
                            return
                        }
                        captureSession = session
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        session.setRepeatingRequest(
                            request,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult,
                                ) {
                                    latestIso = result.get(TotalCaptureResult.SENSOR_SENSITIVITY)
                                    latestExposureNanos =
                                        result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME)
                                    latestAeState = result.get(TotalCaptureResult.CONTROL_AE_STATE)
                                }
                            },
                            workerHandler,
                        )
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        finishMeasurement(reading = null, failed = true)
                    }
                },
                workerHandler,
            )
        } catch (_: RuntimeException) {
            finishMeasurement(reading = null, failed = true)
        }
    }

    private fun consumeImage(image: Image) {
        receivedFrameCount += 1
        if (receivedFrameCount < MINIMUM_WARMUP_FRAMES) return
        val exposureIsAdjusting = latestAeState == CaptureRequest.CONTROL_AE_STATE_SEARCHING ||
            latestAeState == CaptureRequest.CONTROL_AE_STATE_PRECAPTURE
        if (exposureIsAdjusting && receivedFrameCount < MAXIMUM_WARMUP_FRAMES) return

        val averageLuma = averageYPlane(image) ?: return
        samples += AmbientCameraPolicy.adjustedBrightness(
            averageLuma = averageLuma,
            iso = latestIso,
            exposureTimeNanos = latestExposureNanos,
        )
        if (samples.size >= REQUIRED_SAMPLES) {
            finishMeasurement(reading = makeReading(), failed = false)
        }
    }

    private fun averageYPlane(image: Image): Float? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var total = 0L
        var count = 0L
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val index = y * rowStride + x * pixelStride
                if (index < buffer.limit()) {
                    total += buffer.get(index).toInt() and 0xff
                    count += 1
                }
                x += SAMPLE_STEP_PIXELS
            }
            y += SAMPLE_STEP_PIXELS
        }
        if (count == 0L) return null
        // Limited-range YUV maps nominal black...white to 16...235.
        return (((total.toDouble() / count) - 16.0) / 219.0).toFloat().coerceIn(0f, 1f)
    }

    private fun makeReading(): AmbientCameraReading? = AmbientCameraPolicy.median(samples)?.let {
        AmbientCameraReading(
            value = it,
            measuredAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            lensFacing = activeLensFacing,
        )
    }

    private fun finishMeasurement(
        reading: AmbientCameraReading?,
        failed: Boolean,
        notify: Boolean = true,
    ) {
        workerHandler.removeCallbacks(timeout)
        runCatching { captureSession?.stopRepeating() }
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        captureSession = null
        cameraDevice = null
        imageReader = null
        samples.clear()
        receivedFrameCount = 0
        if (reading != null) mutableReading.value = reading
        mutableState.value = when {
            !enabled -> AmbientCameraState.DISABLED
            reading != null -> AmbientCameraState.READY
            failed -> AmbientCameraState.UNAVAILABLE
            hasCameraPermission() -> AmbientCameraState.READY
            else -> AmbientCameraState.PERMISSION_NEEDED
        }
        val callback = completion
        completion = null
        if (notify && callback != null) callbackExecutor.execute { callback(reading) }
    }

    private fun selectCamera(preferBack: Boolean): Pair<String, Int>? {
        val preferred = if (preferBack) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        val candidates = runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList())
        return candidates.mapNotNull { id ->
            val facing = runCatching {
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
            }.getOrNull() ?: return@mapNotNull null
            id to facing
        }.sortedBy { (_, facing) -> if (facing == preferred) 0 else 1 }.firstOrNull()
    }

    private fun hasAnyCamera(): Boolean = runCatching {
        cameraManager.cameraIdList.isNotEmpty()
    }.getOrDefault(false)

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    override fun close() {
        if (closed) return
        closed = true
        workerHandler.post {
            finishMeasurement(reading = null, failed = false)
            workerThread.quitSafely()
        }
    }

    companion object {
        private const val WORKER_THREAD_NAME = "stand-ambient-camera"
        private const val FRAME_WIDTH = 320
        private const val FRAME_HEIGHT = 240
        private const val MAX_IMAGES = 2
        private const val SAMPLE_STEP_PIXELS = 16
        private const val MINIMUM_WARMUP_FRAMES = 8
        private const val MAXIMUM_WARMUP_FRAMES = 20
        private const val REQUIRED_SAMPLES = 5
        private const val MEASUREMENT_TIMEOUT_MILLIS = 3_000L
    }
}
