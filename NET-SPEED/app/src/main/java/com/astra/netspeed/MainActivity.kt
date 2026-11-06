package com.astra.netspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("net_speed", MODE_PRIVATE) }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        askNotificationPermission()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(24))
            setBackgroundColor(0xFF101827.toInt())
        }

        val title = TextView(this).apply {
            text = "NET SPEED"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "by Astra · версия 2.0"
            textSize = 16f
            setTextColor(0xFF8FB8FF.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFE5E7EB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
        }
        val start = Button(this).apply {
            text = "Включить скорость в уведомлении"
            setOnClickListener {
                prefs.edit().putBoolean("enabled", true).apply()
                val intent = Intent(this@MainActivity, SpeedService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                refreshStatus()
            }
        }
        val stop = Button(this).apply {
            text = "Выключить индикатор"
            setOnClickListener {
                prefs.edit().putBoolean("enabled", false).apply()
                stopService(Intent(this@MainActivity, SpeedService::class.java))
                refreshStatus()
            }
        }
        val hint = TextView(this).apply {
            text = "Скорость загрузки и отдачи обновляется в постоянном уведомлении. Открой шторку уведомлений, чтобы увидеть цифры."
            textSize = 14f
            setTextColor(0xFFB8C4D8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }

        root.addView(title, matchWidth())
        root.addView(subtitle, matchWidth())
        root.addView(status, matchWidth())
        root.addView(start, matchWidth())
        root.addView(stop, matchWidth())
        root.addView(hint, matchWidth())
        setContentView(root)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }

    private fun refreshStatus() {
        val enabled = prefs.getBoolean("enabled", false)
        status.text = if (enabled) {
            "Индикатор: включён"
        } else {
            "Индикатор: выключен"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
