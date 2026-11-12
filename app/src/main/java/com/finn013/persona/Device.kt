package com.finn013.persona

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.LocaleList
import android.view.WindowManager
import android.webkit.WebSettings
import java.util.TimeZone
import kotlin.math.roundToInt

/** Поле «устройства» в профиле. */
data class Field(val key: String, val label: String)

object Keys {
    const val UA = "ua"
    const val MODEL = "model"
    const val ANDROID = "android"
    const val PLATFORM = "platform"
    const val ARCH = "arch"
    const val SW = "sw"
    const val SH = "sh"
    const val DPR = "dpr"
    const val CORES = "cores"
    const val MEMORY = "memory"
    const val TOUCH = "touch"
    const val GPU_V = "gpuV"
    const val GPU_R = "gpuR"
    const val LANG = "lang"
    const val TZ = "tz"

    val FIELDS = listOf(
        Field(MODEL, "Модель телефона"),
        Field(ANDROID, "Версия Android"),
        Field(SW, "Ширина экрана (CSS px)"),
        Field(SH, "Высота экрана (CSS px)"),
        Field(DPR, "Плотность пикселей (devicePixelRatio)"),
        Field(CORES, "Ядер процессора"),
        Field(ARCH, "Архитектура процессора"),
        Field(MEMORY, "Память, ГБ (сайтам видно 0.5–8)"),
        Field(GPU_V, "Видеочип: производитель"),
        Field(GPU_R, "Видеочип: модель"),
        Field(TOUCH, "Точек касания"),
        Field(PLATFORM, "Платформа (navigator.platform)"),
        Field(LANG, "Языки через запятую"),
        Field(TZ, "Часовой пояс"),
        Field(UA, "User-Agent (пусто = как у Chrome, модель уходит в Client Hints)")
    )
}

/** Настоящие параметры телефона — показываются серыми подсказками. */
object Device {
    @Volatile private var cache: Map<String, String>? = null
    @Volatile private var gpu: Pair<String, String>? = null

    fun real(ctx: Context): Map<String, String> {
        cache?.let { return it }
        val wm = ctx.getSystemService(WindowManager::class.java)
        val density = ctx.resources.displayMetrics.density
        val (wPx, hPx) = if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds; b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m); m.widthPixels to m.heightPixels
        }
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / 1024.0 / 1024.0 / 1024.0
        val devMem = listOf(0.25, 0.5, 1.0, 2.0, 4.0, 8.0).lastOrNull { it <= gb + 0.5 } ?: 8.0
        val g = gpu ?: queryGpu().also { gpu = it }
        val map = mapOf(
            Keys.UA to cleanUa(ctx),
            Keys.MODEL to Build.MODEL,
            Keys.ANDROID to Build.VERSION.RELEASE,
            Keys.PLATFORM to "Linux " + (System.getProperty("os.arch") ?: "aarch64"),
            Keys.ARCH to (Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"),
            Keys.SW to (minOf(wPx, hPx) / density).roundToInt().toString(),
            Keys.SH to (maxOf(wPx, hPx) / density).roundToInt().toString(),
            Keys.DPR to trimNum(density.toDouble()),
            Keys.CORES to Runtime.getRuntime().availableProcessors().toString(),
            Keys.MEMORY to trimNum(devMem),
            Keys.TOUCH to "5",
            Keys.GPU_V to g.first,
            Keys.GPU_R to g.second,
            Keys.LANG to LocaleList.getDefault().toLanguageTags().replace(",", ", "),
            Keys.TZ to TimeZone.getDefault().id
        )
        cache = map
        return map
    }

    /** User-Agent WebView без пометки «wv» — так Google и другие пускают войти. */
    fun cleanUa(ctx: Context): String = WebSettings.getDefaultUserAgent(ctx)
        .replace("; wv)", ")")
        .replace(" Version/4.0", "")

    fun chromeVersion(ctx: Context): String =
        Regex("Chrome/([\\d.]+)").find(WebSettings.getDefaultUserAgent(ctx))?.groupValues?.get(1) ?: "130.0.0.0"

    /**
     * User-Agent in Chrome's "reduced" form (Chrome 110+): Android version and model are frozen
     * to "Android 10; K" exactly like real Chrome; the spoofed model and Android version are sent
     * through Client Hints (HTTP Sec-CH-UA-* and navigator.userAgentData) instead.
     */
    fun buildUa(ctx: Context, @Suppress("UNUSED_PARAMETER") android: String, @Suppress("UNUSED_PARAMETER") model: String, mobile: Boolean = true): String {
        val major = chromeVersion(ctx).substringBefore('.')
        val mob = if (mobile) "Mobile " else ""
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 ${mob}Safari/537.36"
    }

    fun desktopUa(ctx: Context): String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion(ctx)} Safari/537.36"

    private fun trimNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else ((d * 1000).roundToInt() / 1000.0).toString()

    /** Название видеочипа через временный OpenGL-контекст (то же видят сайты через WebGL). */
    private fun queryGpu(): Pair<String, String> {
        return try {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            EGL14.eglInitialize(dpy, ver, 0, ver, 1)
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, num, 0)
            val ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            val v = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
            val r = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)
            v to r
        } catch (e: Throwable) {
            "" to ""
        }
    }
}

/** Готовые «устройства» — поля заполняются согласованно. */
data class Preset(val name: String, val mobile: Boolean, val values: Map<String, String>)

object Presets {
    private fun p(name: String, model: String, android: String, sw: Int, sh: Int, dpr: String,
                  cores: Int, mem: Int, gpuV: String, gpuR: String, mobile: Boolean = true) =
        Preset(name, mobile, mapOf(
            Keys.MODEL to model, Keys.ANDROID to android, Keys.SW to "$sw", Keys.SH to "$sh",
            Keys.DPR to dpr, Keys.CORES to "$cores", Keys.MEMORY to "$mem", Keys.ARCH to "arm64-v8a",
            Keys.GPU_V to gpuV, Keys.GPU_R to gpuR, Keys.TOUCH to "5", Keys.PLATFORM to "Linux armv8l"
        ))

    val ALL = listOf(
        p("Google Pixel 8", "Pixel 8", "14", 412, 915, "2.625", 9, 8, "ARM", "Mali-G715"),
        p("Google Pixel 7a", "Pixel 7a", "14", 412, 915, "2.625", 8, 8, "ARM", "Mali-G710"),
        p("Samsung Galaxy S23", "SM-S911B", "14", 360, 780, "3", 8, 8, "Qualcomm", "Adreno (TM) 740"),
        p("Samsung Galaxy A54", "SM-A546B", "14", 384, 854, "2.8125", 8, 8, "ARM", "Mali-G68"),
        p("Xiaomi Redmi Note 12", "23021RAAEG", "13", 393, 873, "2.75", 8, 4, "Qualcomm", "Adreno (TM) 610"),
        p("Xiaomi 13", "2211133G", "14", 393, 851, "2.75", 8, 8, "Qualcomm", "Adreno (TM) 740"),
        p("OnePlus 11", "CPH2449", "14", 412, 919, "3.5", 8, 8, "Qualcomm", "Adreno (TM) 740"),
        p("Samsung Galaxy Tab S9 (планшет)", "SM-X710", "14", 800, 1280, "2", 8, 8, "Qualcomm", "Adreno (TM) 740", mobile = false)
    )
}
