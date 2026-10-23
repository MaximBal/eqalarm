package com.example.eqalarm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Палитра и мелкие помощники для интерфейса без XML-разметки. */
object Ui {
    val BG = Color.parseColor("#0B1220")
    val CARD = Color.parseColor("#141C2F")
    val CARD2 = Color.parseColor("#1F2A44")
    val ACCENT = Color.parseColor("#7C9CFF")
    val ACCENT_A = Color.parseColor("#7C5CFF")
    val ACCENT_B = Color.parseColor("#4C8DFF")
    val TEXT = Color.parseColor("#F2F5FF")
    val MUTED = Color.parseColor("#8B96B3")
    val DANGER = Color.parseColor("#FF7B7B")
    val OK = Color.parseColor("#3DDC97")
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Int): Float = v * resources.displayMetrics.density

fun shape(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun ripple(content: Drawable, radius: Float): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, shape(Color.WHITE, radius))

enum class BtnKind { PRIMARY, SECONDARY, DANGER, GHOST }

/** Кнопка, которая сама тускнеет в выключенном состоянии. */
class PillButton(c: Context) : Button(c) {
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}

fun Context.pillButton(label: String, kind: BtnKind = BtnKind.PRIMARY): Button {
    val r = dpf(18)
    val bg: Drawable = when (kind) {
        BtnKind.PRIMARY -> GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Ui.ACCENT_A, Ui.ACCENT_B)
        ).apply { cornerRadius = r }
        BtnKind.SECONDARY -> shape(Ui.CARD2, r)
        BtnKind.DANGER -> shape(Color.parseColor("#2A1A24"), r, Ui.DANGER, dp(1))
        BtnKind.GHOST -> shape(Color.TRANSPARENT, r)
    }
    val fg = when (kind) {
        BtnKind.PRIMARY -> Color.WHITE
        BtnKind.SECONDARY -> Ui.TEXT
        BtnKind.DANGER -> Ui.DANGER
        BtnKind.GHOST -> Ui.MUTED
    }
    return PillButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        typeface = Ui.MEDIUM
        setTextColor(fg)
        stateListAnimator = null
        elevation = 0f
        minWidth = 0; minimumWidth = 0
        minHeight = dp(54); minimumHeight = dp(54)
        setPadding(dp(16), 0, dp(16), 0)
        background = ripple(bg, r)
    }
}

fun Context.tv(t: String, sp: Float, color: Int = Ui.TEXT, medium: Boolean = false): TextView =
    TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (medium) typeface = Ui.MEDIUM
    }

/** Маленький заголовок раздела капсом. */
fun Context.label(t: String): TextView = tv(t, 12f, Ui.MUTED, true).apply {
    letterSpacing = 0.12f
    setPadding(0, 0, 0, dp(10))
}

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = shape(Ui.CARD, dpf(24))
    setPadding(dp(20), dp(18), dp(20), dp(20))
}

/** Круглая «таблетка» дня недели. */
class DayChip(ctx: Context, name: String) : TextView(ctx) {
    var checked = false
        set(v) { field = v; render() }

    init {
        text = name
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Ui.MEDIUM
        isClickable = true
        render()
    }

    private fun render() {
        val r = context.dpf(20)
        background = ripple(shape(if (checked) Ui.ACCENT_B else Ui.CARD2, r), r)
        setTextColor(if (checked) Color.WHITE else Ui.MUTED)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}
