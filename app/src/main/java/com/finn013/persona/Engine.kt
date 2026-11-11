package com.finn013.persona

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/** Всё, что связано с изоляцией профилей и подменой устройства. */
object Engine {

    fun multiProfileSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun cookies(p: Profile): CookieManager =
        if (multiProfileSupported()) ProfileStore.getInstance().getOrCreateProfile(p.engineName).cookieManager
        else CookieManager.getInstance()

    /** Полностью стереть данные профиля (куки, вход, кэш, хранилище). false — профиль ещё открыт. */
    fun wipe(p: Profile): Boolean {
        if (!multiProfileSupported()) return true
        return try {
            val store = ProfileStore.getInstance()
            if (store.getProfile(p.engineName) != null) store.deleteProfile(p.engineName)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun isMobile(p: Profile): Boolean = p.value("mobile") != "0"

    fun userAgent(ctx: Context, p: Profile): String {
        p.value(Keys.UA).takeIf { it.isNotEmpty() }?.let { return it }
        val model = p.value(Keys.MODEL)
        val android = p.value(Keys.ANDROID)
        if (model.isEmpty() && android.isEmpty() && isMobile(p)) return Device.cleanUa(ctx)
        val real = Device.real(ctx)
        return Device.buildUa(ctx, android.ifEmpty { real[Keys.ANDROID]!! }, model.ifEmpty { real[Keys.MODEL]!! }, isMobile(p))
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(ctx: Context, w: WebView, p: Profile, desktop: Boolean = false) {
        if (multiProfileSupported()) {
            ProfileStore.getInstance().getOrCreateProfile(p.engineName)
            WebViewCompat.setProfile(w, p.engineName)
        }
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportZoom(true)
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.allowFileAccess = false
        s.userAgentString = if (desktop) Device.desktopUa(ctx) else userAgent(ctx, p)

        cookies(p).apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, true)
        }

        // Не сообщать сайтам, что это встроенный браузер приложения.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
        }

        val script = spoofScript(ctx, p, desktop)
        if (script != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(w, script, setOf("*"))
        }
    }

    private var template: String? = null

    fun spoofScript(ctx: Context, p: Profile, desktop: Boolean): String? {
        val cfg = JSONObject()
        val direct = listOf(Keys.PLATFORM, Keys.CORES, Keys.MEMORY, Keys.TOUCH, Keys.SW, Keys.SH,
            Keys.DPR, Keys.GPU_V, Keys.GPU_R, Keys.LANG, Keys.TZ)
        direct.forEach { k -> p.value(k).takeIf { it.isNotEmpty() }?.let { cfg.put(k, it) } }

        val ua = if (desktop) Device.desktopUa(ctx) else userAgent(ctx, p)
        val needUa = desktop || p.hasSpoof()
        if (needUa) cfg.put("ua", ua)
        if (desktop) cfg.put("platform", "Linux x86_64")

        val model = p.value(Keys.MODEL)
        val android = p.value(Keys.ANDROID)
        val arch = p.value(Keys.ARCH)
        if (!desktop && (model.isNotEmpty() || android.isNotEmpty() || arch.isNotEmpty() || p.value(Keys.UA).isNotEmpty() || !isMobile(p))) {
            val real = Device.real(ctx)
            val full = Device.chromeVersion(ctx)
            val major = full.substringBefore('.')
            fun brands(v: String) = JSONArray().apply {
                put(JSONObject().put("brand", "Chromium").put("version", v))
                put(JSONObject().put("brand", "Google Chrome").put("version", v))
                put(JSONObject().put("brand", "Not?A_Brand").put("version", if (v == major) "99" else "99.0.0.0"))
            }
            val a = arch.ifEmpty { real[Keys.ARCH]!! }
            cfg.put("uad", JSONObject().apply {
                put("brands", brands(major))
                put("fullVersionList", brands(full))
                put("fullVersion", full)
                put("mobile", isMobile(p))
                put("model", model.ifEmpty { real[Keys.MODEL]!! })
                put("platformVersion", android.ifEmpty { real[Keys.ANDROID]!! })
                put("arch", if (a.startsWith("x86")) "x86" else "arm")
                put("bitness", if (a.contains("64")) "64" else "32")
            })
        }
        if (cfg.length() == 0) return null
        val t = template ?: ctx.assets.open("spoof.js").bufferedReader().use { it.readText() }.also { template = it }
        return t.replace("__CFG__", cfg.toString())
    }
}
