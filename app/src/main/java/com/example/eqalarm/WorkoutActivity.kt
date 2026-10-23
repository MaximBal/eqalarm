package com.example.eqalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Рисует скелет поверх превью камеры, чтобы было видно, что приложение вас «видит». */
class PoseOverlay(ctx: Context) : View(ctx) {
    private var pose: Pose? = null
    private var iw = 1
    private var ih = 1
    private var mirror = true
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.OK; style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 124, 156, 255)
        strokeWidth = ctx.dpf(3)
        strokeCap = Paint.Cap.ROUND
    }
    private val links = intArrayOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE
    )

    fun set(p: Pose?, w: Int, h: Int, mirrored: Boolean) {
        pose = p; iw = max(1, w); ih = max(1, h); mirror = mirrored
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val p = pose ?: return
        val scale = max(width / iw.toFloat(), height / ih.toFloat()) // как FILL_CENTER у превью
        val ox = (width - iw * scale) / 2f
        val oy = (height - ih * scale) / 2f
        fun x(l: PoseLandmark): Float = ((if (mirror) iw - l.position.x else l.position.x) * scale) + ox
        fun y(l: PoseLandmark): Float = l.position.y * scale + oy
        var i = 0
        while (i < links.size) {
            val a = p.getPoseLandmark(links[i]); val b = p.getPoseLandmark(links[i + 1])
            if (a != null && b != null && a.inFrameLikelihood > 0.5f && b.inFrameLikelihood > 0.5f)
                c.drawLine(x(a), y(a), x(b), y(b), line)
            i += 2
        }
        val r = context.dpf(4)
        for (t in links.toSet()) {
            val l = p.getPoseLandmark(t) ?: continue
            if (l.inFrameLikelihood > 0.5f) c.drawCircle(x(l), y(l), r, dot)
        }
    }
}

class WorkoutActivity : ComponentActivity() {
    companion object {
        @Volatile var visible = false
        const val TARGET_MS = 5 * 60 * 1000L   // 5 минут активных упражнений
        const val TEST_MS = 20_000L            // для теста — 20 секунд
        const val GRACE_MS = 5000L             // дольше этого без движения — звонок включается
        const val NO_POSE_MS = 2000L
        const val WATCHDOG_MS = 10_000L        // нет кадров/модель не работает → уравнения
    }

    private var isTest = false
    private var targetMs = TARGET_MS
    private lateinit var exercises: List<Exercise>
    private var idx = 0
    private var totalReps = 0
    private var activeMs = 0L
    private var lastActivity = 0L
    private var lastPose = 0L
    private var lastTick = 0L
    private var startedAt = 0L
    private var mirror = true
    private var finished = false
    @Volatile private var goodFrames = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var executor: ExecutorService
    private lateinit var detector: PoseDetector

    private lateinit var previewView: PreviewView
    private lateinit var overlay: PoseOverlay
    private lateinit var titleText: TextView
    private lateinit var tipText: TextView
    private lateinit var repsText: TextView
    private lateinit var timeText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        isTest = intent.getBooleanExtra("test", false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fallback("Нет доступа к камере — вместо тренировки уравнения")
            return
        }
        exercises = Exercises.build(Prefs.workoutMask(this))
        targetMs = if (isTest) TEST_MS else TARGET_MS
        buildUi()
        showExercise()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (isTest) endTest() } // в настоящем звонке «назад» заблокирован
        })

        executor = Executors.newSingleThreadExecutor()
        detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
        startCamera()
        startedAt = SystemClock.elapsedRealtime()
        lastTick = startedAt
        handler.post(tick)
        if (!isTest) try { startLockTask() } catch (e: Exception) {}
    }

    // ── Интерфейс ───────────────────────────────────────────────
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(20))
            setBackgroundColor(Ui.BG)
        }
        if (isTest) root.addView(tv("ТЕСТОВЫЙ ЗВОНОК", 12f, Ui.ACCENT, true).apply {
            letterSpacing = 0.14f; setPadding(dp(4), 0, 0, dp(6))
        })
        root.addView(tv("Тренировка", 26f, Ui.TEXT, true).apply { setPadding(dp(4), 0, 0, 0) })

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        overlay = PoseOverlay(this)
        val frame = FrameLayout(this).apply {
            background = shape(Ui.CARD, dpf(24))
            clipToOutline = true
            addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })

        val panel = card()
        titleText = tv("", 24f, Ui.TEXT, true)
        tipText = tv("", 13f, Ui.MUTED)
        repsText = tv("0", 40f, Ui.ACCENT, true).apply { gravity = Gravity.CENTER }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleText); addView(tipText)
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), 0, 0, 0)
            addView(repsText)
            addView(tv("повторов", 11f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(right)
        })
        timeText = tv("", 15f, Ui.TEXT, true)
        panel.addView(timeText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(14); bottomMargin = dp(8)
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Ui.ACCENT_B)
            progressBackgroundTintList = ColorStateList.valueOf(Ui.CARD2)
        }
        panel.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        statusText = tv("", 15f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        panel.addView(statusText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        root.addView(panel)

        if (isTest) {
            root.addView(pillButton("Завершить тест", BtnKind.DANGER).apply { setOnClickListener { endTest() } },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        setContentView(root)
    }

    private fun showExercise() {
        val ex = exercises[idx]
        titleText.text = if (exercises.size > 1) "${ex.title}  ·  ${idx + 1}/${exercises.size}" else ex.title
        tipText.text = ex.tip
    }

    private fun setStatus(text: String, fg: Int, bg: Int) {
        statusText.text = text
        statusText.setTextColor(fg)
        statusText.background = shape(bg, dpf(14))
    }

    private fun updateUi(active: Boolean, poseOk: Boolean) {
        val remain = max(0L, (targetMs - activeMs + 999) / 1000)
        timeText.text = "Осталось активных упражнений: %d:%02d".format(remain / 60, remain % 60)
        progress.progress = min(1000L, activeMs * 1000 / targetMs).toInt()
        repsText.text = totalReps.toString()
        when {
            !poseOk -> setStatus("Не вижу вас целиком — отойдите от камеры, чтобы было видно всё тело",
                Color.parseColor("#FFC857"), Color.parseColor("#3A2F1A"))
            !active -> setStatus("Звук включён — двигайтесь, не останавливайтесь!",
                Ui.DANGER, Color.parseColor("#3A1F27"))
            else -> setStatus("Отлично! Звук выключен — продолжайте",
                Ui.OK, Color.parseColor("#12382C"))
        }
    }

    // ── Камера и распознавание ──────────────────────────────────
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val rot = proxy.imageInfo.rotationDegrees
                    val w = if (rot % 180 == 0) proxy.width else proxy.height
                    val h = if (rot % 180 == 0) proxy.height else proxy.width
                    detector.process(InputImage.fromMediaImage(media, rot))
                        .addOnSuccessListener { pose -> goodFrames++; onPose(pose, w, h) }
                        .addOnCompleteListener { proxy.close() }
                }
                val front = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                mirror = front
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis
                )
            } catch (e: Exception) {
                fallback("Камера недоступна — вместо тренировки уравнения")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onPose(pose: Pose, w: Int, h: Int) {
        if (finished) return
        overlay.set(pose, w, h, mirror)
        val ex = exercises[idx]
        val before = ex.reps
        val r = ex.update(pose)
        val now = SystemClock.elapsedRealtime()
        if (r != Exercise.NO_DATA) lastPose = now
        if (r == Exercise.EVENT) lastActivity = now
        totalReps += ex.reps - before
    }

    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            val now = SystemClock.elapsedRealtime()
            val dt = min(now - lastTick, 500L)
            lastTick = now

            // Страховка: если камера/модель не дают кадров — не оставляем вас в ловушке
            if (goodFrames == 0 && now - startedAt > WATCHDOG_MS) {
                fallback("Не удалось запустить распознавание — вместо тренировки уравнения")
                return
            }

            val poseOk = lastPose != 0L && now - lastPose < NO_POSE_MS
            val active = poseOk && lastActivity != 0L && now - lastActivity < GRACE_MS
            if (active) {
                activeMs += dt
                // «Пульс»: звук приглушён ещё на 1,2 с. Остановились или закрыли экран — пульс прекратится
                AlarmService.mutedUntil = System.currentTimeMillis() + 1200
            }

            val n = exercises.size
            val ni = min(n - 1, (activeMs * n / targetMs).toInt())
            if (ni != idx) { idx = ni; exercises[idx].reset(); showExercise() }

            updateUi(active, poseOk)
            if (activeMs >= targetMs) complete() else handler.postDelayed(this, 200)
        }
    }

    // ── Завершение ──────────────────────────────────────────────
    private fun stopAlarmAndClose(message: String) {
        finished = true
        handler.removeCallbacksAndMessages(null)
        AlarmService.mutedUntil = 0L
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.STOP))
        try { stopLockTask() } catch (e: Exception) {}
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun complete() =
        stopAlarmAndClose(if (isTest) "Тест пройден — всё работает" else "Тренировка выполнена. Доброе утро!")

    private fun endTest() = stopAlarmAndClose("Тест завершён")

    /** Камера не работает — переключаемся на уравнения, чтобы будильник можно было выключить. */
    private fun fallback(reason: String) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        AlarmService.mutedUntil = 0L
        AlarmService.forceEquations = true
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, AlarmActivity::class.java)
                .putExtra("mode", "ring").putExtra("test", isTest)
        )
        finish()
    }

    override fun onResume() { super.onResume(); visible = true }
    override fun onPause() { visible = false; super.onPause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::detector.isInitialized) detector.close()
        if (::executor.isInitialized) executor.shutdown()
        super.onDestroy()
    }
}
