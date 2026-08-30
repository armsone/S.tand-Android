package com.armsone.stand.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.armsone.stand.MainActivity
import com.armsone.stand.R

/**
 * Minimal production foreground service guarding S.tand Mate-mode microphone monitoring
 * in the background with FOREGROUND_SERVICE_TYPE_MICROPHONE and a truthful Korean status notification.
 */
class MateMonitoringService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: DEFAULT_STATUS_TEXT
        startMonitoring(statusText)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startMonitoring(statusText: String) {
        acquireWakeLock()
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopMonitoring() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "STand:MateMonitoringWakeLock",
            )?.apply {
                setReferenceCounted(false)
                // The foreground service is explicitly stopped when the app returns or
                // monitoring becomes ineligible. The OS releases this lock if the process dies.
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: RuntimeException) {}
        wakeLock = null
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("stand://open")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.stand_brand_icon)
            .setContentTitle("S.tand")
            .setContentText(statusText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "매이트 모드 소리 감시 상태 알림"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "stand_mate_monitoring"
        const val CHANNEL_NAME = "매이트 모드 소리 감시"
        const val NOTIFICATION_ID = 5101

        const val ACTION_START = "com.armsone.stand.audio.START_MONITORING"
        const val ACTION_STOP = "com.armsone.stand.audio.STOP_MONITORING"
        const val ACTION_UPDATE_STATUS = "com.armsone.stand.audio.UPDATE_STATUS"
        const val EXTRA_STATUS_TEXT = "status_text"

        const val DEFAULT_STATUS_TEXT = "소리 감시 중"

        fun start(context: Context, statusText: String? = null) {
            val intent = Intent(context, MateMonitoringService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATUS_TEXT, statusText ?: DEFAULT_STATUS_TEXT)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: RuntimeException) {}
        }

        fun updateStatus(context: Context, statusText: String) {
            val intent = Intent(context, MateMonitoringService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: RuntimeException) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, MateMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: RuntimeException) {
                try {
                    context.stopService(intent)
                } catch (_: RuntimeException) {}
            }
        }
    }
}
