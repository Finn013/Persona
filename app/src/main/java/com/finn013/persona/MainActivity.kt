package com.finn013.persona

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = MaterialToolbar(this).apply {
            title = "Persona"
            subtitle = "Профили, которые работают одновременно"
        }
        col.addView(bar)

        if (!Engine.multiProfileSupported()) {
            col.addView(TextView(this).apply {
                text = "⚠ Ваш Android System WebView не поддерживает отдельные профили. " +
                    "Обновите «Android System WebView» (или Chrome) в Play Маркете — иначе входы будут общими."
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
            setIconResource(R.drawable.ic_add)
            setOnClickListener { startActivity(Intent(this@MainActivity, EditProfileActivity::class.java)) }
        }
        root.addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(16), dp(16), dp(16), dp(24))
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val profiles = Profiles.all(this)
        if (profiles.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Пока нет профилей.\n\nСоздайте первый: у каждого профиля свои куки и входы, " +
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
            setOnClickListener { open(this@MainActivity, p, null) }
            setOnLongClickListener { actions(p); true }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val dot = TextView(this).apply {
            text = p.name.take(1).uppercase()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setBackgroundResource(R.drawable.circle)
            backgroundTintList = ColorStateList.valueOf(p.color)
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(44), dp(44)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        texts.addView(TextView(this).apply { text = p.name; textSize = 17f; setTypeface(typeface, Typeface.BOLD) })
        val device = when {
            p.preset.isNotEmpty() -> "📱 ${p.preset}"
            p.hasSpoof() -> "📱 Своё устройство"
            else -> "📱 Как есть"
        }
        val start = p.startUrl.ifBlank { "google.com" }.removePrefix("https://").removePrefix("http://")
        texts.addView(TextView(this).apply { text = "$device · $start"; alpha = 0.7f; textSize = 13f })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        return card
    }

    private fun actions(p: Profile) {
        val items = arrayOf("Открыть", "Изменить", "Ярлык на рабочий стол", "Стереть данные (выйти везде)", "Удалить профиль")
        AlertDialog.Builder(this).setTitle(p.name).setItems(items) { _, i ->
            when (i) {
                0 -> open(this, p, null)
                1 -> startActivity(Intent(this, EditProfileActivity::class.java).putExtra(EditProfileActivity.EXTRA_ID, p.id))
                2 -> Shortcuts.pin(this, p, p.startUrl.ifBlank { BrowserActivity.HOME }, p.name, null)
                3 -> confirm("Стереть куки, входы и данные сайтов профиля «${p.name}»?") { wipe(p, delete = false) }
                4 -> confirm("Удалить профиль «${p.name}» вместе со всеми входами?") { wipe(p, delete = true) }
            }
        }.show()
    }

    private fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Да") { _, _ -> ok() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun wipe(p: Profile, delete: Boolean) {
        closeWindows(this, p.id)
        // Окна закрываются не мгновенно — даём WebView освободить профиль.
        Handler(Looper.getMainLooper()).postDelayed({
            if (Engine.wipe(p)) {
                if (delete) { Profiles.delete(this, p.id); Shortcuts.remove(this, p) }
                Toast.makeText(this, if (delete) "Профиль удалён" else "Данные стёрты", Toast.LENGTH_SHORT).show()
                render()
            } else {
                Toast.makeText(this, "Профиль ещё открыт — закройте его окна и повторите", Toast.LENGTH_LONG).show()
            }
        }, 800)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun windowUri(p: Profile, url: String?): Uri = Uri.Builder().scheme("persona").authority("open")
            .appendQueryParameter("p", p.id).apply { if (url != null) appendQueryParameter("u", url) }.build()

        fun open(ctx: Context, p: Profile, url: String?) {
            if (!Engine.multiProfileSupported() && Profiles.all(ctx).size > 1) {
                Toast.makeText(ctx, "Без обновлённого WebView профили не изолированы", Toast.LENGTH_LONG).show()
            }
            val i = Intent(Intent.ACTION_VIEW, windowUri(p, url), ctx, BrowserActivity::class.java)
            if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }

        fun closeWindows(ctx: Context, profileId: String) {
            val am = ctx.getSystemService(ActivityManager::class.java)
            am.appTasks.forEach { t ->
                try {
                    val data = t.taskInfo.baseIntent.data
                    if (data?.scheme == "persona" && data.getQueryParameter("p") == profileId) t.finishAndRemoveTask()
                } catch (_: Exception) {}
            }
        }
    }
}

