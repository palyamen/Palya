package com.astra.netspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
            text = "by Astra"
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
            text = "Включить индикатор"
            setOnClickListener { enableOverlay() }
        }
        val stop = Button(this).apply {
            text = "Выключить индикатор"
            setOnClickListener {
                prefs.edit().putBoolean("enabled", false).apply()
                stopService(Intent(this@MainActivity, SpeedService::class.java))
                refreshStatus()
            }
        }
        val settings = Button(this).apply {
            text = "Разрешение поверх окон"
            setOnClickListener { openOverlaySettings() }
        }

        root.addView(title, matchWidth())
        root.addView(subtitle, matchWidth())
        root.addView(status, matchWidth())
        root.addView(start, matchWidth())
        root.addView(stop, matchWidth())
        root.addView(settings, matchWidth())
        setContentView(root)
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        prefs.edit().putBoolean("enabled", true).apply()
        val intent = Intent(this, SpeedService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        refreshStatus()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
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
        val allowed = Settings.canDrawOverlays(this)
        val state = if (enabled) "включён" else "выключен"
        val permission = if (allowed) "есть" else "нужно выдать"
        status.text = "Индикатор: $state\nРазрешение поверх окон: $permission"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
