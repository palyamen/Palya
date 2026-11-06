package com.astra.netspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("net_speed", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var stop: Button

    private val navy = 0xFF071329.toInt()
    private val panel = 0xFF102747.toInt()
    private val cyan = 0xFF39D9FF.toInt()
    private val green = 0xFF55E68A.toInt()
    private val white = 0xFFF4F8FF.toInt()
    private val muted = 0xFF9AB6D5.toInt()

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
            setPadding(dp(22), dp(26), dp(22), dp(24))
            background = gradient(intArrayOf(0xFF071329.toInt(), 0xFF0B1D38.toInt()), 0)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        val brand = TextView(this).apply {
            text = "NET SPEED"
            textSize = 29f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(white)
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        val subtitle = TextView(this).apply {
            text = "by Astra  ✦  версия 2.1"
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        root.addView(brand, matchWidth())
        root.addView(subtitle, matchWidth())

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(20))
            background = rounded(panel, 26, 0xFF1B4168.toInt(), 1)
        }
        val speedCaption = text("ТЕКУЩАЯ СКОРОСТЬ", 12f, muted, true).apply {
            letterSpacing = 0.12f
        }
        val speedValue = text("↓  ЗАГРУЗКА     ↑  ОТДАЧА", 15f, white, true)
        val speedHint = text("Скорость отображается в уведомлении Android", 13f, muted, false)
        speedCard.addView(speedCaption, matchWidth())
        speedCard.addView(speedValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        speedCard.addView(speedHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        root.addView(speedCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

        val stateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(0xFF0D203B.toInt(), 22, 0xFF1B4168.toInt(), 1)
        }
        val stateTitle = text("СОСТОЯНИЕ ИНДИКАТОРА", 12f, muted, true)
        status = text("Индикатор выключен", 17f, white, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(5))
        }
        stateCard.addView(stateTitle, matchWidth())
        stateCard.addView(status, matchWidth())
        root.addView(stateCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        start = pillButton("ВКЛЮЧИТЬ ИНДИКАТОР", cyan, navy)
        start.setOnClickListener {
            prefs.edit().putBoolean("enabled", true).apply()
            val intent = Intent(this@MainActivity, SpeedService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            refreshStatus()
        }
        stop = pillButton("ВЫКЛЮЧИТЬ ИНДИКАТОР", 0xFF203B59.toInt(), white)
        stop.setOnClickListener {
            prefs.edit().putBoolean("enabled", false).apply()
            stopService(Intent(this@MainActivity, SpeedService::class.java))
            refreshStatus()
        }
        root.addView(start, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(22) })
        root.addView(stop, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(12) })

        val footer = text(
            "Без плавающих окон и специальных разрешений. Открой шторку уведомлений, чтобы увидеть скорость.",
            12f, muted, false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(26), 0, 0)
        }
        root.addView(footer, matchWidth())
        setContentView(scroll)
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
        status.text = if (enabled) "●  Индикатор включён" else "○  Индикатор выключен"
        status.setTextColor(if (enabled) green else white)
        start.alpha = if (enabled) 0.55f else 1f
        stop.alpha = if (enabled) 1f else 0.55f
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    private fun pillButton(label: String, color: Int, textColor: Int): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(textColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            background = rounded(color, 28, 0x00000000, 0)
            stateListAnimator = null
            elevation = 0f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(18), 0, dp(18), 0)
        }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }

    private fun gradient(colors: IntArray, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}