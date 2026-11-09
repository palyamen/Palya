package com.astra.calc

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 19, 41)
    private val panel = Color.rgb(25, 42, 72)
    private val key = Color.rgb(35, 57, 88)
    private val cyan = Color.rgb(76, 190, 255)
    private val white = Color.rgb(245, 249, 255)
    private val muted = Color.rgb(157, 184, 215)
    private val nf = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
    private lateinit var quantity: TextView
    private lateinit var details: TextView
    private lateinit var total: TextView
    private lateinit var totalCaption: TextView
    private var entry = ""
    private var accumulator: Int? = null
    private var pending: String? = null
    private var showingResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refresh()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(7, 19, 41), Color.rgb(12, 31, 57)))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        root.addView(TextView(this).apply {
            text = "✦  ASTRA CALC"; textSize = 27f; setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD); gravity = Gravity.CENTER
        }, full())
        root.addView(TextView(this).apply {
            text = "КАЛЬКУЛЯТОР ОБУВНОГО ЦЕХА"; textSize = 11f
            letterSpacing = 0.12f; setTextColor(cyan); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(28)))

        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15)); background = rounded(panel, 20)
        }
        display.addView(label("КОЛИЧЕСТВО КОМПЛЕКТОВ", 11f, muted, true), full())
        quantity = label("0", 42f, white, true).apply { gravity = Gravity.END }
        display.addView(quantity, full())
        details = label("1 комплект = 8 пар", 14f, cyan, false).apply { gravity = Gravity.END }
        display.addView(details, full())
        root.addView(display, LinearLayout.LayoutParams(-1, dp(126)).apply { topMargin = dp(20) })

        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("⌫","0","C")).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { value ->
                val b = makeButton(value, if (value == "C") Color.rgb(125,55,72) else key, white)
                b.setOnClickListener { when (value) {
                    "C" -> clearAll()
                    "⌫" -> backspace()
                    else -> digit(value)
                } }
                line.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            root.addView(line, LinearLayout.LayoutParams(-1, dp(66)))
        }

        val ops = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("＋  ПЛЮС", "−  МИНУС").forEach { op ->
            val symbol = if (op.startsWith("＋")) "+" else "-"
            val b = makeButton(op, Color.rgb(38,77,111), white).apply { textSize = 16f }
            b.setOnClickListener { setOperation(symbol) }
            ops.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                setMargins(dp(4), dp(8), dp(4), dp(4))
            })
        }
        root.addView(ops, LinearLayout.LayoutParams(-1, dp(70)))

        root.addView(label("ВЫБЕРИ РАСЦЕНКУ ЗА ПАРУ", 12f, muted, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, dp(40)))
        val rates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Triple("ЛЕТО",60,Color.rgb(36,102,139)),
            Triple("БАЙКА",65,Color.rgb(42,91,133)),
            Triple("ЗИМА",70,Color.rgb(40,83,125))).forEach { item ->
            val b = makeButton("${item.first}\n${item.second} ₽", item.third, white).apply { textSize = 15f }
            b.setOnClickListener { calculate(item.second) }
            rates.addView(b, LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                setMargins(dp(4), dp(2), dp(4), dp(4))
            })
        }
        root.addView(rates, LinearLayout.LayoutParams(-1, dp(84)))

        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.rgb(22,51,78), 20)
        }
        totalCaption = label("ИТОГО К ОПЛАТЕ", 12f, muted, true).apply { gravity = Gravity.CENTER }
        total = label("0 ₽", 34f, cyan, true).apply { gravity = Gravity.CENTER }
        resultCard.addView(totalCaption, full()); resultCard.addView(total, full())
        root.addView(resultCard, LinearLayout.LayoutParams(-1, dp(112)).apply { topMargin = dp(12) })
        root.addView(label("Комплекты × 8 пар × цена за пару", 12f, muted, false).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(38)))
        setContentView(scroll)
    }

    private fun digit(value: String) {
        if (showingResult) { entry = ""; accumulator = null; pending = null; showingResult = false }
        if (entry.length < 8) entry = (entry + value).trimStart('0').ifEmpty { "0" }
        refresh()
    }

    private fun setOperation(op: String) {
        val current = entry.toIntOrNull() ?: 0
        if (pending != null && accumulator != null && entry.isNotEmpty())
            accumulator = apply(accumulator!!, current, pending!!)
        else if (accumulator == null) accumulator = current
        pending = op; entry = ""; showingResult = false
        total.text = "—"; totalCaption.text = "ВВЕДИ СЛЕДУЮЩЕЕ КОЛИЧЕСТВО"
        refresh()
    }

    private fun calculate(rate: Int) {
        val current = entry.toIntOrNull() ?: 0
        val kits = if (pending != null && accumulator != null) apply(accumulator!!, current, pending!!) else current
        val safeKits = kits.coerceAtLeast(0)
        val pairs = safeKits.toLong() * 8L
        val amount = pairs * rate
        quantity.text = nf.format(safeKits)
        details.text = "${nf.format(pairs)} пар  ·  ${rate} ₽ за пару"
        totalCaption.text = "ИТОГО К ОПЛАТЕ · " + when (rate) { 60 -> "ЛЕТО"; 65 -> "БАЙКА"; else -> "ЗИМА" }
        total.text = "${nf.format(amount)} ₽"
        entry = safeKits.toString(); accumulator = null; pending = null; showingResult = true
    }

    private fun apply(a: Int, b: Int, op: String): Int =
        if (op == "+") (a.toLong() + b.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else (a.toLong() - b.toLong()).coerceAtLeast(0).toInt()

    private fun clearAll() {
        entry = ""; accumulator = null; pending = null; showingResult = false
        totalCaption.text = "ИТОГО К ОПЛАТЕ"; total.text = "0 ₽"; refresh()
    }
    private fun backspace() {
        if (showingResult) { clearAll(); return }
        entry = entry.dropLast(1); refresh()
    }
    private fun refresh() {
        quantity.text = if (entry.isEmpty()) "0" else nf.format(entry.toIntOrNull() ?: 0)
        details.text = when (pending) {
            "+" -> "Добавь комплекты и нажми расценку"
            "-" -> "Вычти комплекты и нажми расценку"
            else -> "1 комплект = 8 пар"
        }
    }
    private fun makeButton(text: String, color: Int, textColor: Int) = Button(this).apply {
        this.text = text; setTextColor(textColor); textSize = 21f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false; background = rounded(color, 16); stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(35,135,200,255))
    }
    private fun full() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
