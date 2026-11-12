package com.finn013.persona

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import fulguris.R

/**
 * Создание и правка профиля. Поля «устройства» пустые, серым — настоящие значения телефона.
 * Заполнил поле — сайтам видно твоё значение, оставил пустым — настоящее.
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var profile: Profile
    private var isNew = false
    private lateinit var nameEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var presetLabel: TextView
    private lateinit var colorRow: LinearLayout
    private val edits = linkedMapOf<String, EditText>()
    private var color = 0
    private var preset = ""
    private var mobileFlag = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = Profiles.get(this, intent.getStringExtra(EXTRA_ID))
        isNew = existing == null
        val count = Profiles.all(this).size
        profile = existing ?: Profile.new("Профиль ${count + 1}", Profile.PALETTE[count % Profile.PALETTE.size])
        color = profile.color
        preset = profile.preset
        mobileFlag = profile.value("mobile")

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(MaterialToolbar(this).apply {
            title = if (isNew) "Новый профиль" else "Профиль"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        })

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(32))
        }

        form.addView(label("Название"))
        nameEdit = edit(profile.name, "Например: Работа")
        form.addView(nameEdit)

        form.addView(label("Цвет"))
        colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        form.addView(colorRow)
        drawColors()

        form.addView(label("Стартовая (домашняя) страница профиля"))
        urlEdit = edit(profile.startUrl, "пусто = как в настройках браузера").apply { inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT }
        form.addView(urlEdit)

        form.addView(section("Устройство"))
        form.addView(TextView(this).apply {
            text = "Серым — параметры твоего телефона. Впиши своё значение, чтобы сайты видели его; " +
                "оставь пустым — будет как есть. Проще выбрать готовое устройство:"
            alpha = 0.75f; textSize = 13f
        })
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        presetRow.addView(MaterialButton(this).apply {
            text = "Прикинуться…"
            setOnClickListener { choosePreset() }
        })
        presetLabel = TextView(this).apply { setPadding(dp(12), 0, 0, 0); textSize = 14f }
        presetRow.addView(presetLabel, LinearLayout.LayoutParams(0, -2, 1f))
        form.addView(presetRow)
        updatePresetLabel()

        val real = Device.real(this)
        Keys.FIELDS.forEach { f ->
            form.addView(label(f.label))
            val e = edit(profile.value(f.key), real[f.key].orEmpty().ifEmpty { "как в системе" })
            if (f.key in NUMERIC) e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (f.key == Keys.UA) { e.setSingleLine(false); e.maxLines = 4; e.textSize = 12f }
            edits[f.key] = e
            form.addView(e)
        }

        form.addView(TextView(this).apply {
            text = "IP-адрес и сетевой отпечаток подменить нельзя — для этого нужен прокси/VPN. " +
                "Сочетай параметры одного реального телефона, иначе сайт может насторожиться."
            alpha = 0.6f; textSize = 12f
            setPadding(0, dp(16), 0, 0)
        })

        form.addView(MaterialButton(this).apply {
            text = "Сохранить"
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        val scroll = ScrollView(this).apply { addView(form) }
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(col) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(col)
    }

    private fun choosePreset() {
        val names = arrayOf("Моё устройство (сбросить)") + Presets.ALL.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Прикинуться устройством").setItems(names) { _, i ->
            if (i == 0) {
                edits.values.forEach { it.setText("") }
                preset = ""; mobileFlag = ""
            } else {
                val ps = Presets.ALL[i - 1]
                edits.forEach { (k, e) -> if (k != Keys.LANG && k != Keys.TZ) e.setText(ps.values[k].orEmpty()) }
                edits[Keys.UA]?.setText("")
                preset = ps.name
                mobileFlag = if (ps.mobile) "" else "0"
            }
            updatePresetLabel()
        }.show()
    }

    private fun updatePresetLabel() {
        presetLabel.text = if (preset.isNotEmpty()) "Сейчас: $preset" else "Сейчас: своё / как есть"
    }

    private fun save() {
        val name = nameEdit.text.toString().trim().ifEmpty { "Профиль" }
        profile.name = name
        profile.color = color
        profile.startUrl = urlEdit.text.toString().trim().let { if (it.isEmpty()) "" else Persona.normalize(it) }
        profile.spoof.clear()
        edits.forEach { (k, e) -> e.text.toString().trim().takeIf { it.isNotEmpty() }?.let { profile.spoof[k] = it } }
        if (mobileFlag.isNotEmpty()) profile.spoof["mobile"] = mobileFlag
        // Если поля поменяли вручную — это уже не чистый пресет.
        val ps = Presets.ALL.firstOrNull { it.name == preset }
        profile.preset = if (ps != null && ps.values.all { (k, v) -> k == Keys.LANG || k == Keys.TZ || profile.value(k) == v }) preset else ""
        if (isNew && Profiles.freeSlot(Profiles.all(this)) < 0) {
            Toast.makeText(this, "Достигнут предел: ${Persona.SLOTS} профилей", Toast.LENGTH_LONG).show()
            return
        }
        val wasRunning = !isNew && Persona.isRunning(this, profile.slot)
        if (wasRunning) Persona.stop(this, profile.slot)
        Profiles.save(this, profile)
        if (wasRunning) {
            Toast.makeText(this, "Сохранено. Окно профиля перезапущено, чтобы применить настройки (вкладки сохранены)", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun drawColors() {
        colorRow.removeAllViews()
        Profile.PALETTE.forEach { c ->
            val v = View(this).apply {
                setBackgroundResource(R.drawable.persona_circle)
                backgroundTintList = ColorStateList.valueOf(c)
                alpha = if (c == color) 1f else 0.35f
                scaleX = if (c == color) 1.15f else 1f; scaleY = scaleX
                setOnClickListener { color = c; drawColors() }
            }
            colorRow.addView(v, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(4), dp(6), dp(6), dp(6)) })
        }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; alpha = 0.8f
        setPadding(0, dp(12), 0, dp(2))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun edit(value: String, hintText: String) = EditText(this).apply {
        setText(value)
        hint = hintText
        setHintTextColor(Color.GRAY)
        setSingleLine(true)
        textSize = 15f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ID = "id"
        private val NUMERIC = setOf(Keys.SW, Keys.SH, Keys.DPR, Keys.CORES, Keys.MEMORY, Keys.TOUCH)
    }
}
