package com.astra.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Locale

class SpeedService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private var previousRx = TrafficStats.getTotalRxBytes()
    private var previousTx = TrafficStats.getTotalTxBytes()
    private var running = false

    private val updater = object : Runnable {
        override fun run() {
            if (!running) return
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        previousRx = TrafficStats.getTotalRxBytes()
        previousTx = TrafficStats.getTotalTxBytes()
        startAsForeground("↓ 0 KB/s   ↑ 0 KB/s")
        running = true
        handler.removeCallbacks(updater)
        handler.postDelayed(updater, 1000L)
        return START_STICKY
    }

    private fun startAsForeground(speed: String) {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = buildNotification(speed, openIntent)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(speed: String, pendingIntent: PendingIntent): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(speed)
            .setContentText("NET SPEED by Astra · скорость интернета")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Скорость интернета",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Текущая скорость загрузки и отдачи данных"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        TrafficTotal.record(this)
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0 || previousRx < 0 || previousTx < 0) return

        val down = (rx - previousRx).coerceAtLeast(0).toDouble()
        val up = (tx - previousTx).coerceAtLeast(0).toDouble()
        previousRx = rx
        previousTx = tx

        val speed = "↓ ${formatRate(down)}   ↑ ${formatRate(up)}"
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        notificationManager.notify(NOTIFICATION_ID, buildNotification(speed, openIntent))
    }

    private fun formatRate(bytes: Double): String {
        val kb = bytes / 1024.0
        return if (kb >= 1024.0) {
            String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(updater)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "net_speed_notification"
        private const val NOTIFICATION_ID = 1207
    }
}
