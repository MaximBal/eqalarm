package com.example.eqalarm

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var timeText: TextView
    private lateinit var statusText: TextView
    private lateinit var armBtn: Button
    private lateinit var disarmBtn: Button
    private lateinit var soundText: TextView
    private lateinit var systemSoundBtn: Button
    private lateinit var fileBtn: Button
    private lateinit var previewBtn: Button
    private lateinit var volSeek: SeekBar
    private lateinit var volText: TextView
    private lateinit var vacText: TextView
    private lateinit var vacBtn: Button
    private lateinit var vacClearBtn: Button
    private lateinit var testBtn: Button
    private lateinit var stopTestBtn: Button
    private lateinit var permCard: LinearLayout
    private lateinit var permText: TextView
    private lateinit var permBtn: Button

    private val dayChips = ArrayList<DayChip>()
    private val modeRows = ArrayList<Pair<LinearLayout, TextView>>()
    private val exChips = ArrayList<DayChip>()
    private lateinit var exBox: LinearLayout
    private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val soundRows = ArrayList<Triple<Sounds.Item, LinearLayout, TextView>>()
    private var preview: MediaPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    /** Пользователь ушёл в системные настройки за разрешением на точные будильники. */
    private var awaitingExactSettings = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(40))
            setBackgroundColor(Ui.BG)
        }
        fun add(v: View, bottom: Int = 14) = root.addView(
            v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
        )

        // ── Шапка ────────────────────────────────────────────────
        add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(tv("Будильник", 30f, Ui.TEXT, true))
            addView(tv("Просыпайтесь через математику", 14f, Ui.MUTED))
        }, 18)

        // ── Нет разрешений (показывается при необходимости) ─────
        permCard = card().apply {
            background = shape(Color.parseColor("#2A2230"), dpf(24), Color.parseColor("#5A3A4A"), dp(1))
        }
        permText = tv("", 14f, Ui.TEXT)
        permBtn = pillButton("Открыть настройки", BtnKind.SECONDARY)
        permCard.addView(label("ВАЖНО"))
        permCard.addView(permText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        permCard.addView(permBtn)
        add(permCard)

        // ── Главная карточка: время и включение ─────────────────
        val hero = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        timeText = tv("07:00", 76f, Ui.TEXT).apply {
            typeface = Ui.LIGHT; gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(4))
            background = ripple(shape(Color.TRANSPARENT, dpf(20)), dpf(20))
            isClickable = true
        }
        statusText = tv("", 15f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        armBtn = pillButton("Включить будильник")
        disarmBtn = pillButton("Выключить будильник", BtnKind.DANGER)
        hero.addView(timeText)
        hero.addView(tv("нажмите на время, чтобы изменить", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        hero.addView(statusText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(16); bottomMargin = dp(18)
        })
        hero.addView(armBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        hero.addView(disarmBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        add(hero)

        // ── Дни недели ──────────────────────────────────────────
        val daysCard = card()
        daysCard.addView(label("ДНИ НЕДЕЛИ"))
        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dayNames.forEachIndexed { i, n ->
            val chip = DayChip(this, n)
            chip.setOnClickListener {
                if (AlarmService.ringing) return@setOnClickListener
                chip.checked = !chip.checked
                var mask = Prefs.days(this)
                mask = if (chip.checked) mask or (1 shl i) else mask and (1 shl i).inv()
                Prefs.setDays(this, mask)
                reschedule(); refresh()
            }
            dayChips.add(chip)
            daysRow.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        daysCard.addView(daysRow)
        add(daysCard)

        // ── Режим выключения ────────────────────────────────────
        val modeCard = card()
        modeCard.addView(label("КАК ВЫКЛЮЧАТЬ"))
        val rowEq = radioRow("Уравнения", "Решить три квадратных уравнения") { setMode(Prefs.MODE_EQ) }
        val rowWo = radioRow("Тренировка", "5 минут упражнений перед камерой") { setMode(Prefs.MODE_WORKOUT) }
        modeRows.add(rowEq); modeRows.add(rowWo)
        modeCard.addView(rowEq.first, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(4) })
        modeCard.addView(rowWo.first, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        exBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        exBox.addView(tv("Упражнения", 13f, Ui.MUTED).apply { setPadding(dp(4), dp(12), 0, dp(8)) })
        for (start in intArrayOf(0, 2)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until start + 2) {
                val chip = DayChip(this, Exercises.names[i])
                chip.textSize = 13f
                chip.setOnClickListener {
                    val nm = Prefs.workoutMask(this) xor (1 shl i)
                    if (nm == 0) {
                        Toast.makeText(this, "Оставьте хотя бы одно упражнение", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    Prefs.setWorkoutMask(this, nm); refresh()
                }
                exChips.add(chip)
                line.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            exBox.addView(line)
        }
        exBox.addView(tv(
            "Пока вы занимаетесь, звук выключен. Остановились больше чем на 5 секунд — будильник снова звонит. " +
            "Время идёт только во время упражнений, раньше выключить нельзя. Поставьте телефон так, чтобы вас было видно " +
            "целиком (для отжиманий — сбоку). Если камера не заработает, будильник предложит уравнения.",
            12f, Ui.MUTED).apply { setPadding(dp(4), dp(10), dp(4), 0) })
        modeCard.addView(exBox)
        add(modeCard)

        // ── Мелодия ─────────────────────────────────────────────
        val soundCard = card()
        soundCard.addView(label("МЕЛОДИЯ"))
        Sounds.items.forEach { item ->
            val dot = tv("○", 20f, Ui.MUTED)
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(tv(item.title, 16f, Ui.TEXT, true))
                addView(tv(item.desc, 12f, Ui.MUTED))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                addView(dot); addView(col)
                setOnClickListener {
                    Prefs.setSound(this@MainActivity, Sounds.uri(this@MainActivity, item), item.title)
                    if (preview != null) startPreview()
                    refresh()
                }
            }
            soundRows.add(Triple(item, row, dot))
            soundCard.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(4) })
        }
        soundText = tv("", 13f, Ui.MUTED).apply { setPadding(dp(4), dp(10), dp(4), dp(10)) }
        soundCard.addView(soundText)
        systemSoundBtn = pillButton("Системная", BtnKind.SECONDARY).apply { textSize = 14f }
        fileBtn = pillButton("Свой файл", BtnKind.SECONDARY).apply { textSize = 14f }
        soundCard.addView(LinearLayout(this).apply {
            addView(systemSoundBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            addView(fileBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        })
        previewBtn = pillButton("▶  Прослушать", BtnKind.PRIMARY)
        soundCard.addView(previewBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        add(soundCard)

        // ── Громкость ───────────────────────────────────────────
        val volCard = card()
        volText = tv("", 15f, Ui.ACCENT, true)
        volCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("ГРОМКОСТЬ").apply { setPadding(0, 0, 0, 0) },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(volText)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
        volSeek = SeekBar(this).apply {
            max = 100; min = 10
            progress = Prefs.volume(this@MainActivity)
            progressTintList = ColorStateList.valueOf(Ui.ACCENT_B)
            thumbTintList = ColorStateList.valueOf(Ui.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(Ui.CARD2)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    Prefs.setVolume(this@MainActivity, p)
                    volText.text = "$p%"
                    Audio.applyAlarmVolume(this@MainActivity)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        volCard.addView(volSeek, LinearLayout.LayoutParams(MATCH_PARENT, dp(36)))
        volCard.addView(tv("Во время звонка будильник сам удерживает эту громкость — убавить её кнопками не получится.",
            12f, Ui.MUTED).apply { setPadding(0, dp(6), 0, 0) })
        add(volCard)

        // ── Отпуск ──────────────────────────────────────────────
        val vacCard = card()
        vacCard.addView(label("ОТПУСК"))
        vacText = tv("", 15f, Ui.TEXT)
        vacBtn = pillButton("Выбрать дату возвращения", BtnKind.SECONDARY)
        vacClearBtn = pillButton("Отменить отпуск", BtnKind.GHOST)
        vacCard.addView(vacText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        vacCard.addView(vacBtn)
        vacCard.addView(vacClearBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        add(vacCard)

        // ── Проверка ────────────────────────────────────────────
        val testCard = card()
        testCard.addView(label("ПРОВЕРКА"))
        testCard.addView(tv("Запустит настоящий звонок выбранного режима, чтобы вы убедились, что звук, экран и камера работают. " +
            "В режиме тренировки тест длится 20 секунд упражнений. Завершить его можно в любой момент, расписание он не затрагивает.", 14f, Ui.MUTED),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
        testBtn = pillButton("Протестировать будильник", BtnKind.SECONDARY)
        stopTestBtn = pillButton("Остановить тест", BtnKind.DANGER)
        testCard.addView(testBtn)
        testCard.addView(stopTestBtn)
        add(testCard, 0)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Ui.BG)
            addView(root)
        })

        // ── Обработчики ─────────────────────────────────────────
        timeText.setOnClickListener {
            if (Prefs.armed(this)) {
                Toast.makeText(this, "Сначала выключите будильник", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                { _, h, m -> Prefs.setTime(this, h, m); refresh() },
                Prefs.hour(this), Prefs.minute(this), true).show()
        }
        systemSoundBtn.setOnClickListener {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            startActivityForResult(i, 1)
        }
        fileBtn.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(i, 2)
        }
        previewBtn.setOnClickListener { if (preview != null) stopPreview() else startPreview() }

        vacBtn.setOnClickListener {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val d = DatePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                { _, y, mo, day ->
                    val r = Calendar.getInstance().apply {
                        set(y, mo, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }
                    Prefs.setSkipUntil(this, r.timeInMillis)
                    reschedule(); refresh()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            d.datePicker.minDate = System.currentTimeMillis()
            d.show()
        }
        vacClearBtn.setOnClickListener {
            Prefs.setSkipUntil(this, 0L)
            reschedule(); refresh()
        }
        permBtn.setOnClickListener {
            val pkg = Uri.parse("package:$packageName")
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            else if (Build.VERSION.SDK_INT >= 34)
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        }
        armBtn.setOnClickListener { tryArm() }
        // Выключить можно в любой момент, пока будильник не звонит
        disarmBtn.setOnClickListener {
            if (AlarmService.ringing) return@setOnClickListener
            Prefs.setArmed(this, false)
            AlarmScheduler.cancel(this)
            refresh()
        }

        testBtn.setOnClickListener {
            if (AlarmService.ringing) return@setOnClickListener
            if (Prefs.mode(this) == Prefs.MODE_WORKOUT && !hasCamera()) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 12)
                return@setOnClickListener
            }
            startTest()
        }
        stopTestBtn.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction(AlarmService.STOP))
            ui.postDelayed({ refresh() }, 400)
        }
    }

    private fun radioRow(title: String, desc: String, click: () -> Unit): Pair<LinearLayout, TextView> {
        val dot = tv("○", 20f, Ui.MUTED)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(tv(title, 16f, Ui.TEXT, true))
            addView(tv(desc, 12f, Ui.MUTED))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            addView(dot); addView(col)
            setOnClickListener { click() }
        }
        return Pair(row, dot)
    }

    private fun setMode(m: Int) { Prefs.setMode(this, m); refresh() }

    private fun hasCamera() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startTest() {
        if (AlarmService.ringing) return
        stopPreview()
        Audio.applyAlarmVolume(this)
        startForegroundService(
            Intent(this, AlarmService::class.java).putExtra(AlarmService.EXTRA_TEST, true)
        )
        ui.postDelayed({ refresh() }, 400)
    }

    // ── Прослушивание ───────────────────────────────────────────
    private fun startPreview() {
        stopPreview()
        Audio.applyAlarmVolume(this)
        preview = Sounds.play(this, Prefs.sound(this))
        if (preview == null) Toast.makeText(this, "Не удалось воспроизвести мелодию", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun stopPreview() {
        preview?.release(); preview = null
        if (::previewBtn.isInitialized) previewBtn.text = "▶  Прослушать"
    }

    override fun onPause() { stopPreview(); super.onPause() }
    override fun onDestroy() { stopPreview(); super.onDestroy() }

    // ── Включение будильника ────────────────────────────────────
    /** Пересчёт при смене дней/отпуска; если разрешение пропало — честно выключаем. */
    private fun reschedule() {
        if (Prefs.armed(this) && !AlarmScheduler.schedule(this)) {
            Prefs.setArmed(this, false)
            Toast.makeText(this, "Нет разрешения на точные будильники — будильник выключен", Toast.LENGTH_LONG).show()
        }
    }

    /** Цепочка: дни → уведомления → точные будильники → постановка. */
    private fun tryArm(askNotifications: Boolean = true) {
        if (Prefs.days(this) == 0) {
            Toast.makeText(this, "Выберите хотя бы один день недели", Toast.LENGTH_LONG).show()
            return
        }
        if (Prefs.mode(this) == Prefs.MODE_WORKOUT && !hasCamera()) {
            Toast.makeText(this, "Для режима тренировки нужен доступ к камере", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 11)
            return
        }
        if (askNotifications && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            return
        }
        if (!AlarmScheduler.canSchedule(this)) {
            Toast.makeText(this, "Разрешите «Будильники и напоминания» для этого приложения", Toast.LENGTH_LONG).show()
            awaitingExactSettings = AlarmScheduler.openExactAlarmSettings(this)
            return
        }
        if (AlarmScheduler.schedule(this)) {
            Prefs.setArmed(this, true)
        } else {
            Toast.makeText(this, "Не удалось поставить будильник: нет разрешения", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingExactSettings) {
            awaitingExactSettings = false
            if (AlarmScheduler.canSchedule(this)) tryArm(askNotifications = false)
            else Toast.makeText(this, "Разрешение не выдано — будильник не включён", Toast.LENGTH_LONG).show()
        }
        refresh()
        ui.postDelayed({ refresh() }, 400) // сервис мог остановиться уже после возврата на экран
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        val ok = r.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (code) {
            10 -> {
                if (!ok) Toast.makeText(this, "Без разрешения на уведомления экран будильника может не появиться", Toast.LENGTH_LONG).show()
                // Идём дальше в любом случае, иначе при отказе кнопка «Включить» навсегда зависнет
                tryArm(askNotifications = false)
            }
            11 -> if (ok) tryArm() else
                Toast.makeText(this, "Без камеры режим тренировки не работает — будильник не включён", Toast.LENGTH_LONG).show()
            12 -> if (ok) startTest() else
                Toast.makeText(this, "Без камеры тест тренировки невозможен", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data == null || Prefs.armed(this)) return
        if (req == 1) {
            @Suppress("DEPRECATION")
            val u = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val name = u?.let { RingtoneManager.getRingtone(this, it)?.getTitle(this) }
            Prefs.setSound(this, u?.toString(), name)
        } else if (req == 2) {
            val u = data.data ?: return
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            var name = "Свой файл"
            contentResolver.query(u, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val ix = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ix >= 0) name = it.getString(ix)
                }
            }
            Prefs.setSound(this, u.toString(), name)
        }
        if (preview != null) startPreview()
        refresh()
    }

    // ── Обновление экрана ───────────────────────────────────────
    private fun refresh() {
        val armed = Prefs.armed(this)
        val ringing = AlarmService.ringing
        val testing = ringing && AlarmService.testing
        val fmt = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))

        timeText.text = "%02d:%02d".format(Prefs.hour(this), Prefs.minute(this))
        timeText.alpha = if (armed) 0.55f else 1f

        // Время и звук меняются только при выключенном будильнике
        val lock = armed
        soundRows.forEach { (item, row, dot) ->
            val sel = Prefs.sound(this) == Sounds.uri(this, item)
            dot.text = if (sel) "●" else "○"
            dot.setTextColor(if (sel) Ui.ACCENT else Ui.MUTED)
            row.background = ripple(shape(if (sel) Ui.CARD2 else Color.TRANSPARENT, dpf(14)), dpf(14))
            row.isEnabled = !lock
            row.alpha = if (lock) 0.5f else 1f
        }
        val builtIn = Sounds.items.any { Prefs.sound(this) == Sounds.uri(this, it) }
        soundText.text = if (builtIn) "Выбрана встроенная мелодия" else "Выбрано: " + Prefs.soundName(this)
        systemSoundBtn.isEnabled = !lock; fileBtn.isEnabled = !lock
        previewBtn.text = if (preview != null) "■  Остановить" else "▶  Прослушать"

        volSeek.progress = Prefs.volume(this)
        volSeek.isEnabled = !lock
        volText.text = "${Prefs.volume(this)}%"

        val mask = Prefs.days(this)
        dayChips.forEachIndexed { i, t -> t.checked = (mask and (1 shl i)) != 0; t.isEnabled = !ringing }

        val mode = Prefs.mode(this)
        modeRows.forEachIndexed { i, (row, dot) ->
            val sel = i == mode
            dot.text = if (sel) "●" else "○"
            dot.setTextColor(if (sel) Ui.ACCENT else Ui.MUTED)
            row.background = ripple(shape(if (sel) Ui.CARD2 else Color.TRANSPARENT, dpf(14)), dpf(14))
            row.isEnabled = !lock
            row.alpha = if (lock) 0.5f else 1f
        }
        exBox.visibility = if (mode == Prefs.MODE_WORKOUT) View.VISIBLE else View.GONE
        val wm = Prefs.workoutMask(this)
        exChips.forEachIndexed { i, c -> c.checked = (wm and (1 shl i)) != 0; c.isEnabled = !lock }

        val skip = Prefs.skipUntil(this)
        val onVacation = skip > System.currentTimeMillis()
        vacText.text = if (onVacation)
            "Звонка не будет до " + SimpleDateFormat("d MMMM", Locale("ru")).format(skip) +
                " — в этот день будильник вернётся"
        else "Отпуск не задан"
        vacBtn.isEnabled = !ringing
        vacClearBtn.visibility = if (onVacation) View.VISIBLE else View.GONE
        vacClearBtn.isEnabled = !ringing

        armBtn.visibility = if (armed) View.GONE else View.VISIBLE
        disarmBtn.visibility = if (armed) View.VISIBLE else View.GONE
        disarmBtn.isEnabled = !ringing

        testBtn.visibility = if (testing) View.GONE else View.VISIBLE
        testBtn.isEnabled = !ringing
        stopTestBtn.visibility = if (testing) View.VISIBLE else View.GONE

        statusText.setTextColor(if (armed || ringing) Ui.ACCENT else Ui.MUTED)
        statusText.text = when {
            testing -> "Идёт тест. Выполните задание или остановите тест."
            ringing -> "Будильник звонит. Выполните задание, чтобы выключить."
            !armed -> "Будильник выключен"
            else -> {
                val t = AlarmScheduler.nextTrigger(this)
                if (t == null) "Включён, но не выбран ни один день"
                else "Ближайший звонок: " + fmt.format(t)
            }
        }

        val nm = getSystemService(NotificationManager::class.java)
        when {
            !Settings.canDrawOverlays(this) -> {
                permText.text = "Разрешите показ поверх других окон — тогда экран будильника нельзя будет свернуть."
                permCard.visibility = View.VISIBLE
            }
            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() -> {
                permText.text = "Разрешите полноэкранные уведомления — без них экран будильника не откроется на заблокированном телефоне."
                permCard.visibility = View.VISIBLE
            }
            else -> permCard.visibility = View.GONE
        }
    }
}
