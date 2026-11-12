package com.finn013.persona

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import fulguris.R

/**
 * Persona launcher: the list of profiles. Also the target for links opened from other apps
 * ("open in which profile?").
 */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var bar: MaterialToolbar
    /** URL to open when started from a link / share. */
    private var pendingUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUrl = linkFrom(intent)

        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = MaterialToolbar(this)
        bar.menu.add(0, 1, 0, "Настройки браузера")
        bar.menu.add(0, 2, 0, "О программе")
        bar.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(Intent(this, fulguris.activity.SettingsActivity::class.java))
                2 -> AlertDialog.Builder(this).setTitle("Persona 0.2")
                    .setMessage("Браузер с профилями: у каждого профиля свои вкладки, куки, входы, история и «устройство».\n\n" +
                        "Основан на Fulguris (© Stéphane Lenclud, CPAL-1.0) и Lightning Browser (© Anthony Restaino, MPL-2.0). " +
                        "Исходный код: github.com/Finn013/Persona")
                    .setPositiveButton("OK", null).show()
            }
            true
        }
        col.addView(bar)

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            col.addView(TextView(this).apply {
                text = "⚠ Android System WebView устарел: подмена устройства работать не будет. " +
                    "Обновите «Android System WebView» (или Chrome) в Play Маркете."
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(0x33FF6B6B)
            })
        }

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(96))
        }
        scroll.addView(list)
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col)

        val fab = ExtendedFloatingActionButton(this).apply {
            text = "Новый профиль"
            setIconResource(R.drawable.persona_ic_add)
            setOnClickListener {
                if (Profiles.freeSlot(Profiles.all(this@ProfilesActivity)) < 0) {
                    Toast.makeText(this@ProfilesActivity, "Достигнут предел: ${Persona.SLOTS} профилей", Toast.LENGTH_LONG).show()
                } else {
                    startActivity(Intent(this@ProfilesActivity, EditProfileActivity::class.java))
                }
            }
        }
        root.addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(16), dp(16), dp(16), dp(24))
        })
        applyInsets(root)
        setContentView(root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingUrl = linkFrom(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun linkFrom(i: Intent?): String? = when (i?.action) {
        Intent.ACTION_VIEW -> i.dataString
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { t ->
            Regex("https?://\\S+").find(t)?.value ?: t
        }
        Intent.ACTION_WEB_SEARCH -> i.getStringExtra("query")
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun render() {
        bar.title = if (pendingUrl != null) "Открыть в профиле" else "Persona"
        bar.subtitle = pendingUrl?.take(60) ?: "Профили, которые работают одновременно"
        list.removeAllViews()
        val profiles = Profiles.all(this)
        if (profiles.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Пока нет профилей.\n\nСоздайте первый: у каждого профиля свои вкладки, куки, входы и история, " +
                    "и все они открыты одновременно — каждый в своём окне."
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(64), dp(24), dp(24))
                textSize = 16f
            })
            return
        }
        profiles.forEach { p -> list.addView(card(p)) }
    }

    private fun card(p: Profile): View {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = (p.color and 0x00FFFFFF) or 0x66000000
            useCompatPadding = true
            setOnClickListener { openProfile(p) }
            setOnLongClickListener { actions(p); true }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(8), dp(14))
        }
        val dot = TextView(this).apply {
            text = p.name.take(1).uppercase()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setBackgroundResource(R.drawable.persona_circle)
            backgroundTintList = ColorStateList.valueOf(p.color)
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(44), dp(44)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        val running = Persona.isRunning(this, p.slot)
        texts.addView(TextView(this).apply {
            text = if (running) "${p.name}  ●" else p.name
            textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        })
        val device = when {
            p.preset.isNotEmpty() -> "📱 ${p.preset}"
            p.hasSpoof() -> "📱 Своё устройство"
            else -> "📱 Как есть"
        }
        val start = p.startUrl.ifBlank { "домашняя страница" }.removePrefix("https://").removePrefix("http://")
        texts.addView(TextView(this).apply { text = "$device · $start"; alpha = 0.7f; textSize = 13f })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "⋮"; textSize = 22f; gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { actions(p) }
        })
        card.addView(row)
        return card
    }

    private fun openProfile(p: Profile) {
        val url = pendingUrl
        // Without a URL the window restores its tabs; a fresh profile opens its start page
        // (the profile start page is the Fulguris home page inside that profile).
        Persona.open(this, p, url)
        if (url != null) { pendingUrl = null; finish() }
    }

    private fun actions(p: Profile) {
        val items = arrayOf("Открыть", "Изменить", "Ярлык на рабочий стол", "Закрыть окно профиля",
            "Стереть данные (выйти везде)", "Удалить профиль")
        AlertDialog.Builder(this).setTitle(p.name).setItems(items) { _, i ->
            when (i) {
                0 -> openProfile(p)
                1 -> startActivity(Intent(this, EditProfileActivity::class.java).putExtra(EditProfileActivity.EXTRA_ID, p.id))
                2 -> Shortcuts.pin(this, p, p.startUrl.ifBlank { HOME }, p.name, null)
                3 -> { Persona.stop(this, p.slot); Handler(Looper.getMainLooper()).postDelayed({ render() }, 500) }
                4 -> confirm("Стереть вкладки, куки, входы, историю и данные сайтов профиля «${p.name}»?") { wipe(p, delete = false) }
                5 -> confirm("Удалить профиль «${p.name}» вместе со всеми входами?") { wipe(p, delete = true) }
            }
        }.show()
    }

    private fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Да") { _, _ -> ok() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun wipe(p: Profile, delete: Boolean) {
        Persona.stop(this, p.slot)
        // Give the killed process a moment to release its files.
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                Persona.wipeSlot(this, p.slot)
                runOnUiThread {
                    if (delete) { Profiles.delete(this, p.id); Shortcuts.remove(this, p) }
                    Toast.makeText(this, if (delete) "Профиль удалён" else "Данные стёрты", Toast.LENGTH_SHORT).show()
                    render()
                }
            }.start()
        }, 700)
    }

    private fun applyInsets(v: View) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val HOME = "https://www.google.com"
    }
}
