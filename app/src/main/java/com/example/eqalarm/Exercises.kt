package com.example.eqalarm

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/**
 * Упражнение = числовая метрика позы + два порога (покой / усилие).
 * Повтор засчитывается при возврате из «усилия» в «покой».
 * Любой переход фазы считается признаком активности.
 */
class Exercise(
    val title: String,
    val tip: String,
    private val restAbove: Boolean,   // true: покой — метрика выше порога покоя
    private val rest: Float,
    private val effort: Float,
    private val metric: (Pose) -> Float?
) {
    companion object { const val NO_DATA = -1; const val OK = 0; const val EVENT = 1 }

    private var state = 0 // 0 — неизвестно, 1 — покой, 2 — усилие
    private var smooth: Float? = null
    var reps = 0
        private set

    fun reset() { state = 0; reps = 0; smooth = null }

    fun update(p: Pose): Int {
        val raw = metric(p) ?: return NO_DATA
        val m = smooth?.let { it + 0.55f * (raw - it) } ?: raw
        smooth = m
        val isRest = if (restAbove) m > rest else m < rest
        val isEffort = if (restAbove) m < effort else m > effort
        var event = false
        if (isRest && state != 1) {
            if (state == 2) { reps++; event = true }
            state = 1
        } else if (isEffort && state != 2) {
            state = 2; event = true
        }
        return if (event) EVENT else OK
    }
}

object Exercises {
    val names = listOf("Приседания", "Прыжки", "Подъём колен", "Отжимания")
    val tips = listOf(
        "Встаньте во весь рост перед камерой и приседайте.",
        "Прыжки «звёздочка»: руки вверх и ноги врозь, затем вместе.",
        "Бег на месте с высоким подъёмом колен.",
        "Телефон на полу сбоку от вас, чтобы было видно всё тело."
    )

    private fun pt(p: Pose, type: Int): PointF? {
        val l = p.getPoseLandmark(type) ?: return null
        return if (l.inFrameLikelihood >= 0.5f) l.position else null
    }

    private fun avgY(a: PointF?, b: PointF?): Float? = when {
        a != null && b != null -> (a.y + b.y) / 2f
        a != null -> a.y
        b != null -> b.y
        else -> null
    }

    private fun angle(a: PointF, b: PointF, c: PointF): Float {
        val r = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var d = abs(Math.toDegrees(r.toDouble())).toFloat()
        if (d > 180f) d = 360f - d
        return d
    }

    private fun squat(p: Pose): Float? {
        val hip = avgY(pt(p, PoseLandmark.LEFT_HIP), pt(p, PoseLandmark.RIGHT_HIP)) ?: return null
        val knee = avgY(pt(p, PoseLandmark.LEFT_KNEE), pt(p, PoseLandmark.RIGHT_KNEE)) ?: return null
        val ankle = avgY(pt(p, PoseLandmark.LEFT_ANKLE), pt(p, PoseLandmark.RIGHT_ANKLE)) ?: return null
        val shin = ankle - knee
        if (shin < 15f) return null
        return (knee - hip) / shin // стоя ≈ 1, в приседе → 0
    }

    private fun jacks(p: Pose): Float? {
        val sh = avgY(pt(p, PoseLandmark.LEFT_SHOULDER), pt(p, PoseLandmark.RIGHT_SHOULDER)) ?: return null
        val hip = avgY(pt(p, PoseLandmark.LEFT_HIP), pt(p, PoseLandmark.RIGHT_HIP)) ?: return null
        val wrist = avgY(pt(p, PoseLandmark.LEFT_WRIST), pt(p, PoseLandmark.RIGHT_WRIST)) ?: return null
        val torso = hip - sh
        if (torso < 20f) return null
        val arms = (sh - wrist) / torso // руки вверх > 0.2, руки вниз < -0.5
        // Ноги врозь проверяем, только если лодыжки видны
        val la = pt(p, PoseLandmark.LEFT_ANKLE); val ra = pt(p, PoseLandmark.RIGHT_ANKLE)
        val ls = pt(p, PoseLandmark.LEFT_SHOULDER); val rs = pt(p, PoseLandmark.RIGHT_SHOULDER)
        val legsOpen = if (la != null && ra != null && ls != null && rs != null) {
            val shoulderW = abs(ls.x - rs.x)
            shoulderW > 5f && abs(la.x - ra.x) > 1.2f * shoulderW
        } else true
        return if (arms > 0.2f && !legsOpen) 0f else arms
    }

    private fun knees(p: Pose): Float? {
        val sh = avgY(pt(p, PoseLandmark.LEFT_SHOULDER), pt(p, PoseLandmark.RIGHT_SHOULDER)) ?: return null
        val hip = avgY(pt(p, PoseLandmark.LEFT_HIP), pt(p, PoseLandmark.RIGHT_HIP)) ?: return null
        val torso = hip - sh
        if (torso < 20f) return null
        val kl = pt(p, PoseLandmark.LEFT_KNEE)?.let { (it.y - hip) / torso }
        val kr = pt(p, PoseLandmark.RIGHT_KNEE)?.let { (it.y - hip) / torso }
        return when {
            kl != null && kr != null -> min(kl, kr)
            kl != null -> kl
            else -> kr
        } // стоя ≈ 0.85, колено у пояса → < 0.4
    }

    private fun pushup(p: Pose): Float? {
        fun side(s: Int, e: Int, w: Int): Pair<Float, Float>? {
            val a = p.getPoseLandmark(s) ?: return null
            val b = p.getPoseLandmark(e) ?: return null
            val c = p.getPoseLandmark(w) ?: return null
            val conf = min(a.inFrameLikelihood, min(b.inFrameLikelihood, c.inFrameLikelihood))
            if (conf < 0.5f) return null
            return Pair(conf, angle(a.position, b.position, c.position))
        }
        val l = side(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        val r = side(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        return when {
            l != null && r != null -> if (l.first >= r.first) l.second else r.second
            l != null -> l.second
            r != null -> r.second
            else -> null
        }
    }

    fun create(id: Int): Exercise = when (id) {
        0 -> Exercise(names[0], tips[0], true, 0.8f, 0.5f, { p -> squat(p) })
        1 -> Exercise(names[1], tips[1], false, -0.5f, 0.2f, { p -> jacks(p) })
        2 -> Exercise(names[2], tips[2], true, 0.7f, 0.4f, { p -> knees(p) })
        else -> Exercise(names[3], tips[3], true, 150f, 105f, { p -> pushup(p) })
    }

    /** Упражнения, выбранные в маске, по порядку. Если маска пустая — приседания. */
    fun build(mask: Int): List<Exercise> {
        val l = (0..3).filter { (mask and (1 shl it)) != 0 }.map { create(it) }
        return if (l.isEmpty()) listOf(create(0)) else l
    }

}
