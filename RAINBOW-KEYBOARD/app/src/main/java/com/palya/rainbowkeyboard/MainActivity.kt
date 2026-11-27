package com.palya.rainbowkeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val palette = intArrayOf(
        Color.rgb(255, 65, 75), Color.rgb(255, 166, 35),
        Color.rgb(238, 205, 45), Color.rgb(65, 190, 105),
        Color.rgb(40, 175, 225), Color.rgb(92, 112, 235),
        Color.rgb(175, 95, 220), Color.rgb(235, 105, 175)
    )
    private lateinit var editor: EditText
    private lateinit var preview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 39)
        window.navigationBarColor = Color.rgb(16, 24, 39)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(16, 24, 39))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = "🌈 Rainbow Keyboard"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap().apply { bottomMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "Пиши сообщение — каждое слово получит свой цвет"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, matchWrap().apply { bottomMargin = dp(16) })

        editor = EditText(this).apply {
            hint = "Напиши что-нибудь..."
            textSize = 18f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.TOP
            minLines = 4
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(35, 48, 69), 14)
        }
        content.addView(editor, matchWrap().apply { bottomMargin = dp(14) })

        content.addView(TextView(this).apply {
            text = "Предпросмотр"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, matchWrap().apply { bottomMargin = dp(8) })

        preview = TextView(this).apply {
            text = "Твои цветные слова появятся здесь"
            textSize = 21f
            setTextColor(Color.LTGRAY)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(35, 48, 69), 14)
        }
        content.addView(preview, matchWrap().apply { bottomMargin = dp(16) })

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        content.addView(actionButton("Скопировать цветной текст") {
            val plain = editor.text.toString()
            if (plain.isBlank()) {
                Toast.makeText(this, "Сначала напиши сообщение", Toast.LENGTH_SHORT).show()
            } else {
                val html = makeHtml(plain)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newHtmlText("Rainbow message", plain, html))
                Toast.makeText(this, "Скопировано. Попробуй вставить в нужное приложение", Toast.LENGTH_LONG).show()
            }
        })
        content.addView(actionButton("Включить клавиатуру") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(actionButton("Выбрать Rainbow Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        content.addView(TextView(this).apply {
            text = "Важно: цвет при вставке зависит от поддержки форматирования принимающим приложением. В обычных текстовых полях цвета могут не сохраниться."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(12), dp(4), dp(8))
        }, matchWrap())
        setContentView(page)
    }

    private fun updatePreview(value: String) {
        if (value.isEmpty()) {
            preview.text = "Твои цветные слова появятся здесь"
            return
        }
        val styled = SpannableString(value)
        var wordIndex = 0
        Regex("\\S+").findAll(value).forEach { match ->
            styled.setSpan(
                ForegroundColorSpan(palette[wordIndex % palette.size]),
                match.range.first, match.range.last + 1, 0
            )
            wordIndex++
        }
        preview.text = styled
    }

    private fun makeHtml(value: String): String {
        val safe = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val words = Regex("\\S+").findAll(safe).toList()
        if (words.isEmpty()) return "<html><body></body></html>"
        val out = StringBuilder("<html><body style='font-family:sans-serif;font-size:18px'>")
        var cursor = 0
        words.forEachIndexed { index, match ->
            out.append(safe.substring(cursor, match.range.first).replace("\n", "<br>"))
            val color = String.format("#%06X", 0xFFFFFF and palette[index % palette.size])
            out.append("<span style='color:$color'>").append(match.value).append("</span>")
            cursor = match.range.last + 1
        }
        out.append(safe.substring(cursor).replace("\n", "<br>"))
        out.append("</body></html>")
        return out.toString()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(91, 73, 190), 12)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { action() }
        layoutParams = matchWrap().apply { bottomMargin = dp(10) }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
