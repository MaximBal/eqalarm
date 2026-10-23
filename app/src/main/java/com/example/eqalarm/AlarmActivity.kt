package com.example.eqalarm

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlin.math.abs
import kotlin.random.Random

class AlarmActivity : Activity() {
    companion object { @Volatile var visible = false }

    private var mode = "ring"   // "ring" — будильник звонит, "disarm" — выключение
    private var isTest = false  // пробный звонок: можно выйти в любой момент
    private var step = 0
    private var roots = intArrayOf(0, 0)
    private lateinit var stepLabel: TextView
    private lateinit var eq: TextView
    private lateinit var eqCard: LinearLayout
    private lateinit var x1: EditText
    private lateinit var x2: EditText
    private lateinit var msg: TextView
    private val segs = ArrayList<View>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        mode = intent.getStringExtra("mode") ?: "ring"
        isTest = intent.getBooleanExtra("test", false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        next()
        // Закрепление экрана (если включено в настройках Android) — только для настоящего звонка
        if (mode == "ring" && !isTest) try { startLockTask() } catch (e: Exception) {}
    }

    private fun field(h: String) = EditText(this).apply {
        hint = h; textSize = 26f; typeface = Ui.MEDIUM
        gravity = Gravity.CENTER
        setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        background = shape(Ui.CARD2, dpf(18))
        setPadding(dp(12), dp(16), dp(12), dp(16))
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(28))
            setBackgroundColor(Ui.BG)
        }
        if (isTest) root.addView(tv("ТЕСТОВЫЙ ЗВОНОК", 12f, Ui.ACCENT, true).apply {
            letterSpacing = 0.14f; setPadding(0, 0, 0, dp(8))
        })
        val title = when {
            isTest -> "Так будет выглядеть звонок"
            mode == "ring" -> "Подъём!"
            else -> "Выключение будильника"
        }
        root.addView(tv(title, 28f, Ui.TEXT, true))
        root.addView(tv("Решите три уравнения, чтобы выключить будильник. Корни — целые числа, минус вводите знаком «-».", 14f, Ui.MUTED)
            .apply { setPadding(0, dp(8), 0, dp(24)) })

        // Полоска прогресса из трёх сегментов
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) {
            val s = View(this)
            segs.add(s)
            bar.addView(s, LinearLayout.LayoutParams(0, dp(6), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(20) })

        // Карточка с уравнением
        eqCard = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        stepLabel = label("")
        eq = tv("", 34f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(20, 36, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        eqCard.addView(stepLabel)
        eqCard.addView(eq, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)))
        root.addView(eqCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // Поля ввода корней
        x1 = field("x₁"); x2 = field("x₂")
        x2.imeOptions = EditorInfo.IME_ACTION_DONE
        x2.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { check(); true } else false
        }
        val row = LinearLayout(this).apply {
            addView(x1, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            addView(x2, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        }
        root.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        msg = tv("", 15f, Ui.DANGER).apply { setPadding(dp(4), dp(14), dp(4), dp(14)) }
        root.addView(msg)

        root.addView(pillButton("Проверить").apply { setOnClickListener { check() } },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        if (mode == "disarm") {
            root.addView(pillButton("Отмена", BtnKind.GHOST).apply { setOnClickListener { finish() } },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        if (isTest) {
            root.addView(pillButton("Завершить тест", BtnKind.DANGER).apply { setOnClickListener { endTest() } },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Ui.BG)
            addView(root)
        })
    }

    private fun next() {
        stepLabel.text = "УРАВНЕНИЕ ${step + 1} ИЗ 3"
        segs.forEachIndexed { i, v ->
            v.background = shape(if (i <= step) Ui.ACCENT_B else Ui.CARD2, dpf(3))
        }
        val a = intArrayOf(1, 1, 2)[step]
        val r = intArrayOf(6, 9, 5)[step]
        var r1 = 0; var r2 = 0
        do { r1 = Random.nextInt(-r, r + 1); r2 = Random.nextInt(-r, r + 1) }
        while (r1 == r2 || (r1 == 0 && r2 == 0))
        roots = intArrayOf(minOf(r1, r2), maxOf(r1, r2))
        val b = -a * (r1 + r2)
        val c = a * r1 * r2
        var s = (if (a == 1) "" else "$a") + "x²"
        if (b != 0) s += (if (b < 0) " − " else " + ") + (if (abs(b) == 1) "" else "${abs(b)}") + "x"
        if (c != 0) s += (if (c < 0) " − " else " + ") + abs(c)
        eq.text = "$s = 0"
        x1.setText(""); x2.setText(""); msg.text = ""
        x1.requestFocus()
    }

    private fun num(e: EditText) = e.text.toString().trim().replace('−', '-').toIntOrNull()

    private fun check() {
        val a = num(x1); val b = num(x2)
        if (a == null || b == null || minOf(a, b) != roots[0] || maxOf(a, b) != roots[1]) {
            msg.setTextColor(Ui.DANGER)
            msg.text = "Неверно. Подставьте корни в уравнение и проверьте."
            ObjectAnimator.ofFloat(eqCard, "translationX", 0f, -24f, 24f, -16f, 16f, -8f, 0f)
                .setDuration(380).start()
            return
        }
        if (++step < 3) {
            next()
            msg.setTextColor(Ui.OK); msg.text = "Верно! Следующее уравнение."
            return
        }
        when {
            mode == "ring" -> {
                startService(Intent(this, AlarmService::class.java).setAction(AlarmService.STOP))
                try { stopLockTask() } catch (e: Exception) {}
            }
            else -> {
                Prefs.setArmed(this, false)
                AlarmScheduler.cancel(this)
            }
        }
        finish()
    }

    private fun endTest() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.STOP))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isTest) endTest()
        else if (mode == "disarm") super.onBackPressed()
    }

    override fun onResume() { super.onResume(); visible = true }
    override fun onPause() { visible = false; super.onPause() }
}
