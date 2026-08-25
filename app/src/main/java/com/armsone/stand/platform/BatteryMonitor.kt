package com.armsone.stand.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.armsone.stand.model.BatteryProtectionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceBatteryState(
    val levelFraction: Float? = null,
    val isCharging: Boolean = false,
    val shouldProtect: Boolean = false,
)

/**
 * Publishes the sticky ACTION_BATTERY_CHANGED value and subsequent updates.
 * Registration and unregistration are idempotent, and a missing/malformed
 * sticky broadcast is represented by an unknown level instead of an error.
 */
class BatteryMonitor(
    context: Context,
    private val onBatteryChanged: (DeviceBatteryState) -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()

    private val mutableState = MutableStateFlow(
        readStickyBatteryIntent()?.let(::stateFromIntent) ?: DeviceBatteryState(),
    )
    val state: StateFlow<DeviceBatteryState> = mutableState.asStateFlow()

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                publish(stateFromIntent(intent))
            }
        }
    }

    fun start() {
        synchronized(lifecycleLock) {
            if (receiverRegistered) return

            val registration = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(
                        receiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(receiver, filter)
                }
            }

            receiverRegistered = registration.isSuccess
            registration.getOrNull()?.let { publish(stateFromIntent(it)) }
        }
    }

    fun refresh() {
        readStickyBatteryIntent()?.let { publish(stateFromIntent(it)) }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (!receiverRegistered) return
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    fun release() = stop()

    override fun close() = stop()

    private fun readStickyBatteryIntent(): Intent? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                null as BroadcastReceiver?,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(null, filter)
        }
    }.getOrNull()

    private fun stateFromIntent(intent: Intent): DeviceBatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val isPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        val levelFraction = BatteryProtectionPolicy.normalizedLevel(level, scale, isPresent)

        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return DeviceBatteryState(
            levelFraction = levelFraction,
            isCharging = isCharging,
            shouldProtect = BatteryProtectionPolicy.shouldProtect(
                levelFraction,
                isCharging,
            ),
        )
    }

    private fun publish(next: DeviceBatteryState) {
        if (next == mutableState.value) return
        mutableState.value = next
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { onBatteryChanged(next) }
        } else {
            mainHandler.post { runCatching { onBatteryChanged(next) } }
        }
    }
}
