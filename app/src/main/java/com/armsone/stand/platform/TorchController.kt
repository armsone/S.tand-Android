package com.armsone.stand.platform

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TorchState(
    val isAvailable: Boolean = false,
    val isOn: Boolean = false,
    val level: Float = 0f,
    val maximumStrengthLevel: Int = 1,
    val lastError: String? = null,
)

/** Pure normalized-value mapping for API 33+ torch strength levels. */
object TorchStrengthPolicy {
    fun strengthLevel(requestedLevel: Double, maximumStrengthLevel: Int): Int {
        if (!requestedLevel.isFinite() || requestedLevel <= 0.0) return 0
        val maximum = maximumStrengthLevel.coerceAtLeast(1)
        val normalized = requestedLevel.coerceIn(0.0, 1.0)
        return (normalized * maximum).roundToInt().coerceIn(1, maximum)
    }

    fun normalizedLevel(strengthLevel: Int, maximumStrengthLevel: Int): Float {
        val maximum = maximumStrengthLevel.coerceAtLeast(1)
        return (strengthLevel.coerceIn(0, maximum).toFloat() / maximum.toFloat())
            .coerceIn(0f, 1f)
    }
}

/**
 * Safely controls the first rear-facing camera flash.
 *
 * Positive levels use discrete strength control on API 33+ hardware that
 * advertises it; older or binary hardware maps every positive request to on.
 * Camera permission, camera-in-use, and unsupported-device failures are
 * reflected in [state] and never escape to callers.
 */
class TorchController(context: Context) : AutoCloseable {
    private val cameraManager = context.applicationContext
        .getSystemService(CameraManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val mutableState = MutableStateFlow(TorchState())

    val state: StateFlow<TorchState> = mutableState.asStateFlow()

    private var cameraId: String? = null
    private var maximumStrengthLevel = 1
    private var callbackRegistered = false
    private var released = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(callbackCameraId: String) {
            synchronized(lock) {
                if (callbackCameraId != cameraId || released) return
                mutableState.value = mutableState.value.copy(
                    isAvailable = false,
                    isOn = false,
                    level = 0f,
                    lastError = "Torch temporarily unavailable",
                )
            }
        }

        override fun onTorchModeChanged(callbackCameraId: String, enabled: Boolean) {
            synchronized(lock) {
                if (callbackCameraId != cameraId || released) return
                val previous = mutableState.value
                mutableState.value = previous.copy(
                    isAvailable = true,
                    isOn = enabled,
                    level = when {
                        !enabled -> 0f
                        previous.level > 0f -> previous.level
                        else -> 1f
                    },
                    lastError = null,
                )
            }
        }
    }

    init {
        synchronized(lock) {
            refreshHardwareLocked()
            registerCallbackLocked()
        }
    }

    fun setLevel(requestedLevel: Float): Boolean = setLevel(requestedLevel.toDouble())

    fun setLevel(requestedLevel: Double): Boolean = synchronized(lock) {
        if (released) return@synchronized false
        if (!requestedLevel.isFinite() || requestedLevel <= 0.0) {
            return@synchronized turnOffLocked()
        }
        if (!ensureHardwareLocked()) return@synchronized false

        val manager = cameraManager ?: return@synchronized failLocked(
            "Camera service unavailable",
        )
        val id = cameraId ?: return@synchronized failLocked("Rear flash unavailable")
        val normalized = requestedLevel.coerceIn(0.0, 1.0)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                maximumStrengthLevel > 1
            ) {
                val strength = TorchStrengthPolicy.strengthLevel(
                    requestedLevel = normalized,
                    maximumStrengthLevel = maximumStrengthLevel,
                )
                turnOnWithStrength(manager, id, strength)
                mutableState.value = TorchState(
                    isAvailable = true,
                    isOn = true,
                    level = TorchStrengthPolicy.normalizedLevel(
                        strengthLevel = strength,
                        maximumStrengthLevel = maximumStrengthLevel,
                    ),
                    maximumStrengthLevel = maximumStrengthLevel,
                )
            } else {
                manager.setTorchMode(id, true)
                mutableState.value = TorchState(
                    isAvailable = true,
                    isOn = true,
                    level = 1f,
                    maximumStrengthLevel = maximumStrengthLevel,
                )
            }
            true
        } catch (error: Exception) {
            failLocked(error.safeMessage("Unable to enable rear flash"))
        }
    }

    fun turnOff(): Boolean = synchronized(lock) {
        if (released) return@synchronized false
        turnOffLocked()
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            turnOffLocked()
            if (callbackRegistered) {
                runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
                callbackRegistered = false
            }
            released = true
            mutableState.value = mutableState.value.copy(isOn = false, level = 0f)
        }
    }

    override fun close() = release()

    private fun turnOffLocked(): Boolean {
        val manager = cameraManager
        val id = cameraId
        if (manager == null || id == null) {
            mutableState.value = mutableState.value.copy(
                isAvailable = false,
                isOn = false,
                level = 0f,
            )
            return false
        }

        return try {
            manager.setTorchMode(id, false)
            mutableState.value = mutableState.value.copy(
                isAvailable = true,
                isOn = false,
                level = 0f,
                lastError = null,
            )
            true
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                isOn = false,
                level = 0f,
                lastError = error.safeMessage("Unable to disable rear flash"),
            )
            false
        }
    }

    private fun ensureHardwareLocked(): Boolean {
        if (cameraId == null && !refreshHardwareLocked()) return false
        registerCallbackLocked()
        return cameraId != null
    }

    private fun refreshHardwareLocked(): Boolean {
        val manager = cameraManager
            ?: return failLocked("Camera service unavailable")

        return try {
            val rearFlashCamera = manager.cameraIdList.firstOrNull { candidateId ->
                val characteristics = manager.getCameraCharacteristics(candidateId)
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK &&
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (rearFlashCamera == null) {
                cameraId = null
                maximumStrengthLevel = 1
                mutableState.value = TorchState(isAvailable = false)
                false
            } else {
                cameraId = rearFlashCamera
                maximumStrengthLevel = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    readMaximumStrength(
                        manager.getCameraCharacteristics(rearFlashCamera),
                    )
                } else {
                    1
                }
                mutableState.value = TorchState(
                    isAvailable = true,
                    maximumStrengthLevel = maximumStrengthLevel,
                )
                true
            }
        } catch (error: Exception) {
            cameraId = null
            maximumStrengthLevel = 1
            failLocked(error.safeMessage("Unable to inspect rear flash"))
        }
    }

    private fun registerCallbackLocked() {
        val manager = cameraManager ?: return
        if (callbackRegistered || cameraId == null || released) return
        callbackRegistered = runCatching {
            manager.registerTorchCallback(torchCallback, mainHandler)
            true
        }.getOrDefault(false)
    }

    private fun failLocked(message: String): Boolean {
        mutableState.value = mutableState.value.copy(lastError = message)
        return false
    }

    @SuppressLint("NewApi")
    private fun readMaximumStrength(characteristics: CameraCharacteristics): Int =
        (characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1)
            .coerceAtLeast(1)

    @SuppressLint("NewApi")
    private fun turnOnWithStrength(
        manager: CameraManager,
        targetCameraId: String,
        strengthLevel: Int,
    ) {
        manager.turnOnTorchWithStrengthLevel(targetCameraId, strengthLevel)
    }

    private fun Throwable.safeMessage(fallback: String): String =
        localizedMessage?.takeIf(String::isNotBlank) ?: fallback
}
