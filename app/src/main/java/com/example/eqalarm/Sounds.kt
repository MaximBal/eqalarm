package com.example.eqalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/** Встроенные мелодии будильника (лежат в res/raw). */
object Sounds {
    class Item(val res: Int, val title: String, val desc: String)

    val items = listOf(
        Item(R.raw.alarm_classic, "Классический звонок", "Резкие электронные сигналы"),
        Item(R.raw.alarm_siren, "Сирена", "Качающийся вой — будит наверняка"),
        Item(R.raw.alarm_radar, "Радар", "Чистый сигнал с эхом"),
        Item(R.raw.alarm_bells, "Колокольчики", "Мелодичный перезвон"),
        Item(R.raw.alarm_sunrise, "Рассвет", "Светлое восходящее арпеджио")
    )

    fun uri(ctx: Context, item: Item) = "android.resource://${ctx.packageName}/${item.res}"
    fun defaultUri(ctx: Context) = uri(ctx, items[0])

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun open(ctx: Context, uri: String): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(ctx, Uri.parse(uri))
            mp.isLooping = true
            mp.setVolume(1f, 1f)
            mp.prepare()
            mp.start()
            mp
        } catch (e: Exception) {
            mp.release(); null
        }
    }

    /** Выбранная мелодия → встроенная по умолчанию → системный будильник. */
    fun play(ctx: Context, uri: String?): MediaPlayer? =
        (uri?.let { open(ctx, it) })
            ?: open(ctx, defaultUri(ctx))
            ?: open(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
}

object Audio {
    /** Выставляет громкость потока будильника по настройке (10–100 %). */
    fun applyAlarmVolume(ctx: Context) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = Math.round(max * Prefs.volume(ctx) / 100f).coerceIn(1, max)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != target)
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: Exception) { /* на некоторых прошивках запрещено — не страшно */ }
    }
}
