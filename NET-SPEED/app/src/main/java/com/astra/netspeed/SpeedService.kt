package com.astra.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

class SpeedService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var indicator: TextView? = null
    private var previousRx = TrafficStats.getTotalRxBytes()
    private var previousTx = TrafficStats.getTotalTxBytes()
    private var running = false

    private val updater = object : Runnable {
        override fun run() {
            if (!running) return
            updateSpeed()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showIndicator()
        running = true
        handler.removeCallbacks(updater)
        handler.post(updater)
        return START_STICKY
    }

    private fun startAsForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NET SPEED by Astra")
            .setContentText("Индикатор скорости интернета работает")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Скорость интернета",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showIndicator() {
        if (indicator != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        indicator = TextView(this).apply {
            text = "↓ 0 KB/s  ↑ 0 KB/s"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD17243A.toInt())
            setPadding(18, 8, 18, 8)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 8
            y = 8
        }
        try {
            windowManager?.addView(indicator, params)
        } catch (_: Exception) {
            indicator = null
            stopSelf()
        }
    }

    private fun updateSpeed() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0 || previousRx < 0 || previousTx < 0) {
            indicator?.text = "↓ unavailable  ↑ unavailable"
            return
        }
        val down = ((rx - previousRx).coerceAtLeast(0)).toDouble()
        val up = ((tx - previousTx).coerceAtLeast(0)).toDouble()
        previousRx = rx
        previousTx = tx
        indicator?.text = "↓ ${formatRate(down)}  ↑ ${formatRate(up)}"
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
        try {
            indicator?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        indicator = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "net_speed_status"
        private const val NOTIFICATION_ID = 1207
    }
}
