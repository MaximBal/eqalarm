package com.example.eqalarm

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object AlarmScheduler {
    /** Ближайший звонок с учётом дней недели и отпуска; null, если не выбран ни один день. */
    fun nextTrigger(ctx: Context): Long? {
        val h = Prefs.hour(ctx)
        val m = Prefs.minute(ctx)
        val days = Prefs.days(ctx)
        val from = Prefs.skipUntil(ctx)
        if (days == 0) return null
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        for (i in 0..800) {
            c.timeInMillis = now
            c.add(Calendar.DAY_OF_YEAR, i)
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // пн = 0 ... вс = 6
            if (c.timeInMillis > now && c.timeInMillis >= from && (days and (1 shl dow)) != 0)
                return c.timeInMillis
        }
        return null
    }

    private fun fire(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Есть ли право ставить точные будильники (до Android 12 оно не требуется). */
    fun canSchedule(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Открывает системный экран «Будильники и напоминания» для этого приложения. */
    fun openExactAlarmSettings(a: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val pkg = Uri.parse("package:${a.packageName}")
        return try {
            a.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            true
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                true
            } catch (e2: Exception) { false }
        }
    }

    /** true — будильник поставлен (или отменён, если нечего ставить); false — нет разрешения. */
    fun schedule(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val t = nextTrigger(ctx)
        if (t == null) { am.cancel(fire(ctx)); return true }
        if (!canSchedule(ctx)) return false
        val show = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), fire(ctx))
            true
        } catch (e: SecurityException) {
            false // разрешение отозвали между проверкой и вызовом
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(fire(ctx))
    }
}
