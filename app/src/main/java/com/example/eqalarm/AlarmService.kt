package com.example.eqalarm

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AlarmService : Service() {
    companion object {
        const val STOP = "stop"
        const val EXTRA_TEST = "test"
        private const val CH = "alarm"
        @Volatile var ringing = false
        /** true — это пробный звонок из приложения, а не настоящий будильник. */
        @Volatile var testing = false
        /**
         * Звук приглушён до этого момента (System.currentTimeMillis).
         * Экран тренировки продлевает его, пока вы реально занимаетесь; если экран закрыт,
         * завис или вы остановились — срок истекает и звонок включается сам.
         */
        @Volatile var mutedUntil = 0L
        /** Камера недоступна — вместо тренировки показываем уравнения. */
        @Volatile var forceEquations = false
    }

    private var player: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!ringing) return
            Audio.applyAlarmVolume(this@AlarmService)
            ensureSound(silent = System.currentTimeMillis() < mutedUntil)
            if (!AlarmActivity.visible && !WorkoutActivity.visible) launch()
            h.postDelayed(this, 500)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopAlarm(); return START_NOT_STICKY }
        if (intent != null) {
            testing = intent.getBooleanExtra(EXTRA_TEST, false)
            forceEquations = false
            mutedUntil = 0L
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        ringing = true
        h.removeCallbacks(tick); h.post(tick)
        return if (testing) START_NOT_STICKY else START_STICKY
    }

    private fun workoutMode() = Prefs.mode(this) == Prefs.MODE_WORKOUT && !forceEquations

    private fun activityIntent() = Intent(
        this, if (workoutMode()) WorkoutActivity::class.java else AlarmActivity::class.java
    )
        .putExtra("mode", "ring")
        .putExtra("test", testing)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 3, activityIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when {
            testing -> "Пробный звонок — можно завершить в любой момент"
            workoutMode() -> "Позанимайтесь 5 минут перед камерой, чтобы выключить"
            else -> "Решите три уравнения, чтобы выключить"
        }
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (testing) "Тест будильника" else "Подъём!")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    private fun launch() {
        // Работает при выданном разрешении «Поверх других окон»
        try { startActivity(activityIntent()) } catch (e: Exception) {}
    }

    private fun startPlayer() {
        player?.release(); player = null
        player = Sounds.play(this, Prefs.sound(this))
    }

    /** silent = true — ставим звук на паузу (идёт тренировка), иначе гарантируем, что он играет. */
    private fun ensureSound(silent: Boolean) {
        val p = player
        if (silent) {
            try { if (p != null && p.isPlaying) p.pause() } catch (e: IllegalStateException) {}
            return
        }
        if (p == null) { startPlayer(); return }
        try { if (!p.isPlaying) p.start() } catch (e: IllegalStateException) { startPlayer() }
    }

    private fun stopAlarm() {
        ringing = false
        testing = false
        forceEquations = false
        mutedUntil = 0L
        h.removeCallbacks(tick)
        player?.release(); player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { player?.release(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
