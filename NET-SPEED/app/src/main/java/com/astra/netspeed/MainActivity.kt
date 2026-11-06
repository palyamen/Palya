package com.astra.netspeed

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("net_speed", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var rx0 = -1L
    private var tx0 = -1L
    private var testing = false
    private lateinit var status: TextView
    private lateinit var totalTraffic: TextView
    private lateinit var down: TextView
    private lateinit var up: TextView
    private lateinit var result: TextView
    private lateinit var test: Button
    private lateinit var start: Button
    private lateinit var stop: Button
    private val white = 0xFFF4F8FF.toInt()
    private val muted = 0xFF9AB6D5.toInt()
    private val cyan = 0xFF39D9FF.toInt()
    private val green = 0xFF55E68A.toInt()

    private val ticker = object : Runnable {
        override fun run() {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx >= 0 && tx >= 0) {
                if (rx0 >= 0 && tx0 >= 0) {
                    down.text = rate((rx - rx0).coerceAtLeast(0))
                    up.text = rate((tx - tx0).coerceAtLeast(0))
                }
                rx0 = rx
                tx0 = tx
            } else {
                down.text = "нет данных"
                up.text = "нет данных"
            }
            if (::totalTraffic.isInitialized) {
                totalTraffic.text = formatTraffic(TrafficTotal.record(this@MainActivity))
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
    }

    override fun onResume() {
        super.onResume()
        rx0 = TrafficStats.getTotalRxBytes()
        tx0 = TrafficStats.getTotalTxBytes()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (::status.isInitialized) refresh()
    }

    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    private fun screen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF071329.toInt(), 0xFF0B1D38.toInt()))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        root.addView(label("NET SPEED", 29f, white, true).apply { gravity = Gravity.CENTER }, full())
        root.addView(label("by Astra  ✦  версия 2.2", 16f, cyan, true).apply { gravity = Gravity.CENTER }, full())

        val card = panel()
        card.addView(label("ТЕКУЩАЯ СКОРОСТЬ", 12f, muted, true).apply { gravity = Gravity.CENTER }, full())
        val pair = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val d = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val u = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        d.addView(label("↓ ЗАГРУЗКА", 13f, cyan, true))
        down = label("0 KB/s", 25f, white, true); d.addView(down)
        u.addView(label("↑ ОТДАЧА", 13f, green, true))
        up = label("0 KB/s", 25f, white, true); u.addView(up)
        pair.addView(d, LinearLayout.LayoutParams(0, -2, 1f))
        pair.addView(u, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(pair, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        card.addView(label("Обновление каждую секунду", 12f, muted, false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })

        val trafficCard = panel()
        trafficCard.addView(label("СТАТИСТИКА ТРАФИКА", 12f, muted, true), full())
        trafficCard.addView(label("Скачано всего", 14f, muted, false).apply {
            setPadding(0, dp(14), 0, dp(4))
        }, full())
        totalTraffic = label(formatTraffic(TrafficTotal.record(this)), 27f, white, true)
        trafficCard.addView(totalTraffic, full())
        val resetTraffic = button("СБРОСИТЬ", 0xFF203B59.toInt(), white)
        resetTraffic.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Сбросить статистику?")
                .setMessage("Общий счётчик скачанного трафика будет обнулён.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить") { _, _ ->
                    totalTraffic.text = formatTraffic(TrafficTotal.reset(this))
                }
                .show()
        }
        trafficCard.addView(resetTraffic, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(14)
        })
        root.addView(trafficCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val testCard = panel()
        testCard.addView(label("ПРОВЕРКА СКОРОСТИ ИНТЕРНЕТА", 12f, muted, true), full())
        result = label("Измерь реальную скорость соединения", 14f, white, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)) }
        testCard.addView(result, full())
        test = button("НАЧАТЬ ПРОВЕРКУ", cyan, 0xFF071329.toInt())
        test.setOnClickListener { runTest() }
        testCard.addView(test, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(testCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val state = panel()
        state.addView(label("СОСТОЯНИЕ ИНДИКАТОРА", 12f, muted, true), full())
        status = label("Индикатор выключен", 17f, white, true).apply { setPadding(0, dp(12), 0, dp(5)) }
        state.addView(status, full())
        root.addView(state, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        start = button("ВКЛЮЧИТЬ ИНДИКАТОР", cyan, 0xFF071329.toInt())
        start.setOnClickListener {
            prefs.edit().putBoolean("enabled", true).apply()
            val i = Intent(this, SpeedService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            refresh()
        }
        stop = button("ВЫКЛЮЧИТЬ ИНДИКАТОР", 0xFF203B59.toInt(), white)
        stop.setOnClickListener {
            prefs.edit().putBoolean("enabled", false).apply()
            stopService(Intent(this, SpeedService::class.java))
            refresh()
        }
        root.addView(start, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(18) })
        root.addView(stop, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
        root.addView(label("Счётчик учитывает входящий трафик всего телефона, включая работу приложений в фоне. Данные сохраняются после закрытия приложения и перезагрузки телефона.", 12f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(22), 0, 0) }, full())
        root.addView(label("Тест скорости использует сервер Cloudflare; результат зависит от сети и расстояния до сервера.", 12f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }, full())
        setContentView(scroll)
        refresh()
    }

    private fun runTest() {
        if (testing) return
        testing = true; test.isEnabled = false; test.alpha = .55f
        result.text = "Проверяю Ping, загрузку и отдачу…"
        worker.execute {
            try {
                val ping = (1..3).map { pingOnce() }.average().toLong()
                val dl = downloadTest()
                val ul = uploadTest()
                runOnUiThread { result.text = "Ping: $ping мс\n↓ Загрузка: $dl\n↑ Отдача: $ul"; finishTest() }
            } catch (e: Exception) {
                runOnUiThread { result.text = "Тест не завершён. Проверь интернет и попробуй ещё раз."; finishTest() }
            }
        }
    }

    private fun pingOnce(): Long {
        val c = URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.connectTimeout = 10000; c.readTimeout = 10000
        val t = System.nanoTime()
        try { c.connect(); c.responseCode; return (System.nanoTime() - t) / 1_000_000 }
        finally { c.disconnect() }
    }

    private fun downloadTest(): String {
        val c = URL("https://speed.cloudflare.com/__down?bytes=5000000").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 25000
        val t = System.nanoTime()
        try {
            c.inputStream.use { input ->
                val b = ByteArray(16384); var total = 0L
                while (true) { val n = input.read(b); if (n < 0) break; total += n }
                val seconds = (System.nanoTime() - t) / 1e9
                return mbps(total * 8.0 / seconds / 1_000_000.0)
            }
        } finally { c.disconnect() }
    }

    private fun uploadTest(): String {
        val bytes = ByteArray(1_000_000)
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 25000
        c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        val t = System.nanoTime()
        try {
            c.outputStream.use { it.write(bytes) }
            if (c.responseCode !in 200..299) error("Upload error")
            val seconds = (System.nanoTime() - t) / 1e9
            return mbps(bytes.size * 8.0 / seconds / 1_000_000.0)
        } finally { c.disconnect() }
    }

    private fun mbps(v: Double) = String.format(Locale.US, "%.1f Мбит/с", v)
    private fun finishTest() { testing = false; test.isEnabled = true; test.alpha = 1f }
    private fun refresh() {
        val on = prefs.getBoolean("enabled", false)
        status.text = if (on) "●  Индикатор включён" else "○  Индикатор выключен"
        status.setTextColor(if (on) green else white)
        start.alpha = if (on) .55f else 1f; stop.alpha = if (on) 1f else .55f
    }
    private fun formatTraffic(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        var amount = value
        var unit = 0
        while (amount >= 1024.0 && unit < units.lastIndex) {
            amount /= 1024.0
            unit++
        }
        return if (unit == 0) "${amount.toLong()} ${units[unit]}"
        else String.format(Locale.US, "%.2f %s", amount, units[unit])
    }

    private fun rate(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb >= 1024) String.format(Locale.US, "%.1f MB/s", kb / 1024) else String.format(Locale.US, "%.0f KB/s", kb)
    }
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        background = shape(0xFF102747.toInt(), 24, 0xFF1B4168.toInt())
    }
    private fun label(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private fun button(s: String, bg: Int, fg: Int) = Button(this).apply {
        text = s; textSize = 13f; setTextColor(fg); isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = shape(bg, 28); stateListAnimator = null; elevation = 0f
        minHeight = 0; minimumHeight = 0; setPadding(dp(18), 0, dp(18), 0)
    }
    private fun shape(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun full() = LinearLayout.LayoutParams(-1, -2)
}