package com.example.eqalarm

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("eqalarm", Context.MODE_PRIVATE)
    fun hour(c: Context) = p(c).getInt("h", 7)
    fun minute(c: Context) = p(c).getInt("m", 0)
    fun setTime(c: Context, h: Int, m: Int) = p(c).edit().putInt("h", h).putInt("m", m).apply()

    /** URI мелодии; по умолчанию — первая встроенная. */
    fun sound(c: Context): String = p(c).getString("snd", null) ?: Sounds.defaultUri(c)
    fun soundName(c: Context): String = p(c).getString("sndName", null) ?: Sounds.items[0].title
    fun setSound(c: Context, uri: String?, name: String?) =
        p(c).edit().putString("snd", uri).putString("sndName", name).apply()

    /** Громкость будильника, 10–100 %. */
    fun volume(c: Context) = p(c).getInt("vol", 100).coerceIn(10, 100)
    fun setVolume(c: Context, v: Int) = p(c).edit().putInt("vol", v.coerceIn(10, 100)).apply()

    /** Режим выключения: 0 — уравнения, 1 — тренировка перед камерой. */
    const val MODE_EQ = 0
    const val MODE_WORKOUT = 1
    fun mode(c: Context) = p(c).getInt("mode", MODE_EQ)
    fun setMode(c: Context, v: Int) = p(c).edit().putInt("mode", v).apply()

    /** Упражнения тренировки: бит 0 приседания, 1 прыжки, 2 подъём коленей, 3 отжимания. */
    fun workoutMask(c: Context) = p(c).getInt("wmask", 7)
    fun setWorkoutMask(c: Context, v: Int) = p(c).edit().putInt("wmask", v).apply()

    fun armed(c: Context) = p(c).getBoolean("armed", false)
    fun setArmed(c: Context, v: Boolean) = p(c).edit().putBoolean("armed", v).apply()

    /** Дни недели: бит 0 = понедельник ... бит 6 = воскресенье. По умолчанию пн–пт. */
    fun days(c: Context) = p(c).getInt("days", 31)
    fun setDays(c: Context, v: Int) = p(c).edit().putInt("days", v).apply()

    /** Отпуск: будильник не звонит до начала этого дня (мс), 0 — отпуска нет. */
    fun skipUntil(c: Context) = p(c).getLong("skip", 0L)
    fun setSkipUntil(c: Context, v: Long) = p(c).edit().putLong("skip", v).apply()
}
