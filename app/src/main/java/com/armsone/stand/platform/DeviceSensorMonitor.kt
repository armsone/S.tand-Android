package com.armsone.stand.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.armsone.stand.model.AmbientLightPolicy
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AmbientLightReading(
    val rawLux: Float,
    val normalizedBrightness: Float,
)

/** Fallback for devices that do not expose a usable ambient-light sensor. */
object DisplayBrightnessPolicy {
    fun normalized(systemBrightness: Int, maximumBrightness: Int = 255): Float? {
        if (systemBrightness < 0 || maximumBrightness <= 0) return null
        return (systemBrightness.toFloat() / maximumBrightness.toFloat()).coerceIn(0f, 1f)
    }
}

data class DeviceSensorState(
    val isRunning: Boolean = false,
    val ambientLight: AmbientLightReading? = null,
    val isFaceDown: Boolean = false,
    val lastMovementElapsedRealtimeNanos: Long? = null,
    val lightSensorAvailable: Boolean = false,
    val accelerationSensorAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val postureSensorAvailable: Boolean = false,
    val monitoringMode: DeviceSensorMonitoringMode = DeviceSensorMonitoringMode.STOPPED,
)

enum class DeviceSensorMonitoringMode { STOPPED, AMBIENT_ONLY, SLEEP_CARE }

object DeviceSensorMonitoringPolicy {
    fun mode(
        isForeground: Boolean,
        isSessionActive: Boolean,
        environmentMode: com.armsone.stand.model.EnvironmentDisplayMode,
    ): DeviceSensorMonitoringMode = when {
        !isForeground || !isSessionActive -> DeviceSensorMonitoringMode.STOPPED
        environmentMode == com.armsone.stand.model.EnvironmentDisplayMode.MATE ->
            DeviceSensorMonitoringMode.SLEEP_CARE
        else -> DeviceSensorMonitoringMode.AMBIENT_ONLY
    }
}

/** Pure movement thresholds shared by the Android sensor adapter and JVM tests. */
object DeviceMovementPolicy {
    const val ACCELERATION_THRESHOLD_G = 0.16f
    const val ROTATION_THRESHOLD_RADIANS_PER_SECOND = 1.4f
    const val REFRACTORY_INTERVAL_NANOS = 2_000_000_000L

    fun vectorMagnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    fun detectsMovement(
        accelerationMagnitudeG: Float,
        rotationMagnitudeRadiansPerSecond: Float,
    ): Boolean =
        accelerationMagnitudeG >= ACCELERATION_THRESHOLD_G ||
            rotationMagnitudeRadiansPerSecond >= ROTATION_THRESHOLD_RADIANS_PER_SECOND

    fun isOutsideRefractoryWindow(
        nowElapsedRealtimeNanos: Long,
        lastMovementElapsedRealtimeNanos: Long?,
    ): Boolean = lastMovementElapsedRealtimeNanos == null ||
        nowElapsedRealtimeNanos - lastMovementElapsedRealtimeNanos >=
        REFRACTORY_INTERVAL_NANOS
}

/**
 * Android's positive z-axis points out through the screen. A device resting
 * screen-up is therefore near +1 g and a screen-down device is near -1 g.
 */
object DevicePosturePolicy {
    const val FACE_DOWN_ENTER_Z_G = -0.82f
    const val FACE_DOWN_EXIT_Z_G = -0.62f

    fun isFaceDown(zGravityG: Float, currentlyFaceDown: Boolean): Boolean =
        if (currentlyFaceDown) {
            zGravityG < FACE_DOWN_EXIT_Z_G
        } else {
            zGravityG <= FACE_DOWN_ENTER_Z_G
        }
}

/**
 * Owns the optional light, motion, and posture sensors used by S.tand.
 *
 * Sensor events are processed on a dedicated thread. StateFlow can be
 * collected from any coroutine; callbacks are delivered on the main thread.
 * Missing sensors and registration failures simply leave the corresponding
 * availability flag false.
 */
class DeviceSensorMonitor(
    context: Context,
    private val onAmbientLightChanged: (AmbientLightReading) -> Unit = {},
    private val onMovement: () -> Unit = {},
    private val onFaceDownChanged: (Boolean) -> Unit = {},
) : SensorEventListener, AutoCloseable {
    private val sensorManager = context.applicationContext
        .getSystemService(SensorManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()

    private val lightSensor = defaultSensor(Sensor.TYPE_LIGHT)
    private val linearAccelerationSensor = defaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = defaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = defaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = defaultSensor(Sensor.TYPE_GRAVITY)
    private val movementAccelerationSensor = linearAccelerationSensor ?: accelerometer
    private val postureSensor = gravitySensor ?: accelerometer

    private val mutableState = MutableStateFlow(
        DeviceSensorState(
            lightSensorAvailable = lightSensor != null,
            accelerationSensorAvailable = movementAccelerationSensor != null,
            gyroscopeAvailable = gyroscope != null,
            postureSensorAvailable = postureSensor != null,
        ),
    )
    val state: StateFlow<DeviceSensorState> = mutableState.asStateFlow()

    @Volatile
    private var started = false
    private var monitoringMode = DeviceSensorMonitoringMode.STOPPED
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    @Volatile
    private var lifecycleGeneration = 0L
    private var lastMovementElapsedRealtimeNanos: Long? = null

    private val gravityEstimate = FloatArray(3)
    private var gravityEstimateInitialized = false
    private var hardwareGravitySeen = false

    fun start() = startSleepCare()

    fun startAmbientOnly() = start(DeviceSensorMonitoringMode.AMBIENT_ONLY)

    fun startSleepCare() = start(DeviceSensorMonitoringMode.SLEEP_CARE)

    private fun start(requestedMode: DeviceSensorMonitoringMode) {
        if (started && monitoringMode != requestedMode) stop()
        synchronized(lifecycleLock) {
            if (started && monitoringMode == requestedMode) return
            lifecycleGeneration += 1L
            started = true
            monitoringMode = requestedMode
            resetTransientSensorState()

            val manager = sensorManager
            if (manager == null) {
                started = false
                monitoringMode = DeviceSensorMonitoringMode.STOPPED
                mutableState.update {
                    it.copy(
                        isRunning = false,
                        lightSensorAvailable = false,
                        accelerationSensorAvailable = false,
                        gyroscopeAvailable = false,
                        postureSensorAvailable = false,
                        monitoringMode = DeviceSensorMonitoringMode.STOPPED,
                    )
                }
                return
            }

            val requestedSensors = linkedMapOf<Int, Pair<Sensor, Int>>()
            fun request(sensor: Sensor?, delay: Int) {
                if (sensor == null) return
                val previous = requestedSensors[sensor.type]
                if (previous == null || delay < previous.second) {
                    requestedSensors[sensor.type] = sensor to delay
                }
            }

            request(lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (requestedMode == DeviceSensorMonitoringMode.SLEEP_CARE) {
                request(movementAccelerationSensor, SensorManager.SENSOR_DELAY_GAME)
                request(gyroscope, SensorManager.SENSOR_DELAY_GAME)
                request(postureSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            if (requestedSensors.isEmpty()) {
                started = false
                monitoringMode = DeviceSensorMonitoringMode.STOPPED
                mutableState.update {
                    it.copy(
                        isRunning = false,
                        monitoringMode = DeviceSensorMonitoringMode.STOPPED,
                    )
                }
                return
            }

            val thread = HandlerThread("stand-device-sensors").also(HandlerThread::start)
            val handler = Handler(thread.looper)
            val registeredTypes = mutableSetOf<Int>()
            requestedSensors.values.forEach { (sensor, delay) ->
                val registered = runCatching {
                    manager.registerListener(this, sensor, delay, handler)
                }.getOrDefault(false)
                if (registered) registeredTypes += sensor.type
            }

            if (registeredTypes.isEmpty()) {
                thread.quitSafely()
            } else {
                sensorThread = thread
                sensorHandler = handler
            }
            started = registeredTypes.isNotEmpty()
            monitoringMode = if (started) requestedMode else DeviceSensorMonitoringMode.STOPPED

            mutableState.update {
                it.copy(
                    isRunning = started,
                    lightSensorAvailable =
                        lightSensor?.type?.let(registeredTypes::contains) == true,
                    accelerationSensorAvailable =
                        movementAccelerationSensor?.type
                            ?.let(registeredTypes::contains) == true,
                    gyroscopeAvailable =
                        gyroscope?.type?.let(registeredTypes::contains) == true,
                    postureSensorAvailable =
                        postureSensor?.type?.let(registeredTypes::contains) == true,
                    monitoringMode = if (started) {
                        requestedMode
                    } else {
                        DeviceSensorMonitoringMode.STOPPED
                    },
                )
            }
        }
    }

    fun stop() {
        val notifyFaceUp: Boolean
        synchronized(lifecycleLock) {
            lifecycleGeneration += 1L
            if (!started) return
            started = false
            monitoringMode = DeviceSensorMonitoringMode.STOPPED
            notifyFaceUp = mutableState.value.isFaceDown

            runCatching { sensorManager?.unregisterListener(this) }
            sensorHandler = null
            sensorThread?.quitSafely()
            sensorThread = null
            resetTransientSensorState()
            mutableState.update {
                it.copy(
                    isRunning = false,
                    isFaceDown = false,
                    lastMovementElapsedRealtimeNanos = null,
                    monitoringMode = DeviceSensorMonitoringMode.STOPPED,
                )
            }
        }

        if (notifyFaceUp) {
            dispatchCallback { onFaceDownChanged(false) }
        }
    }

    fun release() = stop()

    override fun close() = stop()

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> handleAmbientLight(event)
            Sensor.TYPE_LINEAR_ACCELERATION -> handleLinearAcceleration(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
            Sensor.TYPE_GRAVITY -> handleGravity(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleAmbientLight(event: SensorEvent) {
        val measuredLux = event.values.firstOrNull() ?: return
        if (!measuredLux.isFinite()) return
        val rawLux = measuredLux.coerceAtLeast(0f)
        val reading = AmbientLightReading(
            rawLux = rawLux,
            normalizedBrightness = AmbientLightPolicy.normalizedLux(rawLux),
        )
        mutableState.update { it.copy(ambientLight = reading) }
        dispatchSensorCallback { onAmbientLightChanged(reading) }
    }

    private fun handleLinearAcceleration(event: SensorEvent) {
        if (event.values.size < 3) return
        val magnitude = finiteMagnitude(event.values) ?: return
        detectMovement(
            accelerationMagnitudeG = magnitude / SensorManager.GRAVITY_EARTH,
            rotationMagnitudeRadiansPerSecond = 0f,
            eventTimestampNanos = event.timestamp,
        )
    }

    private fun handleGyroscope(event: SensorEvent) {
        if (event.values.size < 3) return
        val magnitude = finiteMagnitude(event.values) ?: return
        detectMovement(
            accelerationMagnitudeG = 0f,
            rotationMagnitudeRadiansPerSecond = magnitude,
            eventTimestampNanos = event.timestamp,
        )
    }

    private fun handleGravity(event: SensorEvent) {
        if (event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) return
        for (index in 0..2) gravityEstimate[index] = event.values[index]
        gravityEstimateInitialized = true
        hardwareGravitySeen = true
        updateFaceDown(event.values[2] / SensorManager.GRAVITY_EARTH)
    }

    private fun handleAccelerometer(event: SensorEvent) {
        if (event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) return

        if (postureSensor?.type == Sensor.TYPE_ACCELEROMETER) {
            updateFaceDown(event.values[2] / SensorManager.GRAVITY_EARTH)
        }

        if (movementAccelerationSensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val linearAcceleration = fallbackLinearAcceleration(event.values) ?: return
            val magnitude = DeviceMovementPolicy.vectorMagnitude(
                linearAcceleration[0],
                linearAcceleration[1],
                linearAcceleration[2],
            )
            detectMovement(
                accelerationMagnitudeG = magnitude / SensorManager.GRAVITY_EARTH,
                rotationMagnitudeRadiansPerSecond = 0f,
                eventTimestampNanos = event.timestamp,
            )
        }
    }

    private fun detectMovement(
        accelerationMagnitudeG: Float,
        rotationMagnitudeRadiansPerSecond: Float,
        eventTimestampNanos: Long,
    ) {
        if (!DeviceMovementPolicy.detectsMovement(
                accelerationMagnitudeG = accelerationMagnitudeG,
                rotationMagnitudeRadiansPerSecond = rotationMagnitudeRadiansPerSecond,
            )
        ) {
            return
        }

        val now = eventTimestampNanos.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtimeNanos()
        if (!DeviceMovementPolicy.isOutsideRefractoryWindow(
                nowElapsedRealtimeNanos = now,
                lastMovementElapsedRealtimeNanos = lastMovementElapsedRealtimeNanos,
            )
        ) {
            return
        }

        lastMovementElapsedRealtimeNanos = now
        mutableState.update { it.copy(lastMovementElapsedRealtimeNanos = now) }
        dispatchSensorCallback(onMovement)
    }

    private fun updateFaceDown(zGravityG: Float) {
        if (!zGravityG.isFinite()) return
        val current = mutableState.value.isFaceDown
        val next = DevicePosturePolicy.isFaceDown(
            zGravityG = zGravityG,
            currentlyFaceDown = current,
        )
        if (next == current) return

        mutableState.update { it.copy(isFaceDown = next) }
        dispatchSensorCallback { onFaceDownChanged(next) }
    }

    private fun fallbackLinearAcceleration(values: FloatArray): FloatArray? {
        if (!gravityEstimateInitialized) {
            for (index in 0..2) gravityEstimate[index] = values[index]
            gravityEstimateInitialized = true
            return null
        }

        if (!hardwareGravitySeen) {
            val alpha = 0.8f
            for (index in 0..2) {
                gravityEstimate[index] =
                    alpha * gravityEstimate[index] + (1f - alpha) * values[index]
            }
        }

        return FloatArray(3) { index -> values[index] - gravityEstimate[index] }
    }

    private fun finiteMagnitude(values: FloatArray): Float? {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        return DeviceMovementPolicy.vectorMagnitude(x, y, z)
    }

    private fun resetTransientSensorState() {
        lastMovementElapsedRealtimeNanos = null
        gravityEstimate.fill(0f)
        gravityEstimateInitialized = false
        hardwareGravitySeen = false
    }

    private fun defaultSensor(type: Int): Sensor? =
        runCatching { sensorManager?.getDefaultSensor(type) }.getOrNull()

    private fun dispatchSensorCallback(callback: () -> Unit) {
        val capturedGeneration = lifecycleGeneration
        dispatchCallback {
            if (
                DeviceSensorCallbackPolicy.shouldDeliver(
                    capturedGeneration = capturedGeneration,
                    currentGeneration = lifecycleGeneration,
                    isStarted = started,
                )
            ) callback()
        }
    }

    private fun dispatchCallback(callback: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching(callback)
        } else {
            mainHandler.post { runCatching(callback) }
        }
    }
}

internal object DeviceSensorCallbackPolicy {
    fun shouldDeliver(
        capturedGeneration: Long,
        currentGeneration: Long,
        isStarted: Boolean,
    ): Boolean = isStarted && capturedGeneration == currentGeneration
}
