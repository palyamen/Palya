package com.palya.rainbowkeyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class RainbowImeService : InputMethodService() {
    private var english = false
    private var shifted = false
    private var symbols = false
    private lateinit var keyboard: LinearLayout

    override fun onCreateInputView(): View {
        keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(5))
            setBackgroundColor(Color.rgb(22, 29, 45))
        }
        renderKeys()
        return keyboard
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = attribute?.let {
            it.inputType and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        } ?: false
    }

    private fun renderKeys() {
        keyboard.removeAllViews()
        val rows = if (symbols) {
            listOf(
                listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("@","#","$","%","&","-","+","(",")","/"),
                listOf("=","*","\"","'",":",";","!","?","⌫"),
                listOf("ABC","🌐",",","space",".","↵")
            )
        } else if (english) {
            listOf(
                listOf("q","w","e","r","t","y","u","i","o","p"),
                listOf("a","s","d","f","g","h","j","k","l"),
                listOf("⇧","z","x","c","v","b","n","m","⌫"),
                listOf("?123","🌐",",","space",".","↵")
            )
        } else {
            listOf(
                listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
                listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
                listOf("⇧","я","ч","с","м","и","т","ь","б","ю","⌫"),
                listOf("?123","🌐",",","space",".","↵")
            )
        }

        rows.forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { key -> line.addView(makeKey(key, rowIndex)) }
            keyboard.addView(line, LinearLayout.LayoutParams(-1, dp(if (rowIndex == 3) 49 else 47)))
        }
    }

    private fun makeKey(label: String, row: Int): View {
        val special = label in listOf("⇧", "⌫", "?123", "ABC", "🌐", "↵", "space")
        val button = Button(this).apply {
            text = when (label) {
                "space" -> "Русский / English"
                else -> label
            }
            textSize = if (label == "space") 10f else 17f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (special) Color.rgb(75, 65, 135) else Color.rgb(49, 61, 82))
                cornerRadius = dp(7).toFloat()
            }
            setPadding(dp(1), 0, dp(1), 0)
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { handleKey(label) }
        }
        val weight = if (label == "space") 4.2f else if (label == "🌐" || label == "?123" || label == "ABC") 1.35f else 1f
        val params = LinearLayout.LayoutParams(0, -1, weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        button.layoutParams = params
        return button
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        when (key) {
            "⌫" -> {
                val selected = connection.getSelectedText(0)
                if (!selected.isNullOrEmpty()) connection.commitText("", 1)
                else connection.deleteSurroundingText(1, 0)
            }
            "↵" -> connection.commitText("\n", 1)
            "space" -> connection.commitText(" ", 1)
            "🌐" -> {
                english = !english
                symbols = false
                renderKeys()
            }
            "?123" -> { symbols = true; renderKeys() }
            "ABC" -> { symbols = false; renderKeys() }
            "⇧" -> { shifted = !shifted; renderKeys() }
            else -> {
                val value = if (shifted) key.uppercase() else key
                connection.commitText(value, 1)
                if (shifted) { shifted = false; renderKeys() }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
