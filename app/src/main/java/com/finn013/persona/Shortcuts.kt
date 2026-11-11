package com.finn013.persona

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Ярлыки «сайт + профиль» на рабочем столе — замена PWA, сколько угодно штук. */
object Shortcuts {

    fun pin(ctx: Context, p: Profile, url: String, label: String, favicon: Bitmap?) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            Toast.makeText(ctx, "Лаунчер не поддерживает ярлыки", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, MainActivity.windowUri(p, url), ctx, BrowserActivity::class.java)
        val id = "s_${p.id}_${url.hashCode()}"
        val info = ShortcutInfoCompat.Builder(ctx, id)
            .setShortLabel(label.take(24).ifBlank { p.name })
            .setLongLabel("$label · ${p.name}".take(48))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(p, label, favicon)))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }

    fun remove(ctx: Context, p: Profile) {
        try {
            val ids = ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }.filter { it.startsWith("s_${p.id}_") }
            if (ids.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(ctx, ids, "Профиль удалён")
        } catch (_: Exception) {}
    }

    /** Адаптивная иконка 108dp: цвет профиля + значок сайта (или буква) + метка профиля. */
    private fun icon(p: Profile, label: String, favicon: Bitmap?): Bitmap {
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = p.color
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val inner = RectF(size * 0.34f, size * 0.27f, size * 0.66f, size * 0.59f)
        if (favicon != null) {
            paint.color = Color.WHITE
            c.drawRoundRect(RectF(size * 0.30f, size * 0.23f, size * 0.70f, size * 0.63f), 36f, 36f, paint)
            c.drawBitmap(Bitmap.createScaledBitmap(favicon, inner.width().toInt(), inner.height().toInt(), true),
                inner.left, inner.top, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = size * 0.30f
            c.drawText(label.take(1).uppercase(), size / 2f, size * 0.43f + paint.textSize * 0.35f, paint)
        }
        // Метка профиля внизу.
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.075f
        c.drawText(p.name.take(10), size / 2f, size * 0.75f, paint)
        return bmp
    }
}
