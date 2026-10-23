package com.example.eqalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs.armed(c)) return
        // Сначала звоним, потом планируем следующий раз — сбой планирования не должен глушить звонок
        c.startForegroundService(Intent(c, AlarmService::class.java))
        AlarmScheduler.schedule(c) // следующий звонок по расписанию
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs.armed(c)) AlarmScheduler.schedule(c)
    }
}
