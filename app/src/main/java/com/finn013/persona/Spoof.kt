package com.finn013.persona

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Per-WebView setup for the profile served by this process: device spoofing (User-Agent,
 * Client Hints, document-start script), no X-Requested-With header, Safe Browsing on.
 * Called by Fulguris' WebPageTab for every tab WebView.
 */
object Spoof {

    fun isMobile(p: Profile): Boolean = p.value("mobile") != "0"

    /** Applied to every new tab WebView (all processes; spoofing only inside a profile). */
    @SuppressLint("RequiresFeature")
    fun setup(ctx: Context, w: WebView) {
        val s = w.settings
        // Safe Browsing on by default.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(s, true)
            } else {
                s.safeBrowsingEnabled = true
            }
        } catch (e: Throwable) { Timber.w(e, "Safe Browsing") }

        // Do not tell sites this is an app WebView (helps Google sign-in).
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
            }
        } catch (e: Throwable) { Timber.w(e, "X-Requested-With") }

        val p = Persona.current ?: return
        val script = script(ctx, p) ?: return
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(w, script, setOf("*"))
            }
        } catch (e: Throwable) { Timber.w(e, "document start script") }
    }

    /** User-Agent for the current profile, or null to keep Fulguris' own choice. */
    fun userAgent(ctx: Context): String? {
        val p = Persona.current ?: return null
        p.value(Keys.UA).takeIf { it.isNotEmpty() }?.let { return it }
        if (!p.hasSpoof()) return null
        val real = Device.real(ctx)
        return Device.buildUa(ctx, p.value(Keys.ANDROID).ifEmpty { real[Keys.ANDROID]!! },
            p.value(Keys.MODEL).ifEmpty { real[Keys.MODEL]!! }, isMobile(p))
    }

    /** HTTP Client Hints (Sec-CH-UA-*) matching the spoofed device. */
    @SuppressLint("RequiresFeature")
    fun clientHints(ctx: Context, s: WebSettings): Boolean {
        val p = Persona.current ?: return false
        if (!needsUad(p)) return false
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return false
        return try {
            val real = Device.real(ctx)
            val full = Device.chromeVersion(ctx)
            val major = full.substringBefore('.')
            fun bv(brand: String, maj: String, fv: String) = UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand).setMajorVersion(maj).setFullVersion(fv).build()
            val brands = listOf(bv("Chromium", major, full), bv("Google Chrome", major, full), bv("Not?A_Brand", "99", "99.0.0.0"))
            val a = p.value(Keys.ARCH).ifEmpty { real[Keys.ARCH]!! }
            val md = UserAgentMetadata.Builder()
                .setBrandVersionList(brands)
                .setFullVersion(full)
                .setPlatform("Android")
                .setPlatformVersion(p.value(Keys.ANDROID).ifEmpty { real[Keys.ANDROID]!! })
                .setModel(p.value(Keys.MODEL).ifEmpty { real[Keys.MODEL]!! })
                .setMobile(isMobile(p))
                .setArchitecture(if (a.startsWith("x86")) "x86" else "arm")
                .setBitness(if (a.contains("64")) 64 else 32)
                .setWow64(false)
                .build()
            WebSettingsCompat.setUserAgentMetadata(s, md)
            true
        } catch (e: Throwable) {
            Timber.w(e, "Client hints")
            false
        }
    }

    private fun needsUad(p: Profile) = p.value(Keys.MODEL).isNotEmpty() || p.value(Keys.ANDROID).isNotEmpty() ||
        p.value(Keys.ARCH).isNotEmpty() || p.value(Keys.UA).isNotEmpty() || !isMobile(p)

    private var template: String? = null

    fun script(ctx: Context, p: Profile): String? {
        val cfg = JSONObject()
        val direct = listOf(Keys.PLATFORM, Keys.CORES, Keys.MEMORY, Keys.TOUCH, Keys.SW, Keys.SH,
            Keys.DPR, Keys.GPU_V, Keys.GPU_R, Keys.LANG, Keys.TZ)
        direct.forEach { k -> p.value(k).takeIf { it.isNotEmpty() }?.let { cfg.put(k, it) } }

        if (needsUad(p)) {
            val real = Device.real(ctx)
            val full = Device.chromeVersion(ctx)
            val major = full.substringBefore('.')
            fun brands(v: String) = JSONArray().apply {
                put(JSONObject().put("brand", "Chromium").put("version", v))
                put(JSONObject().put("brand", "Google Chrome").put("version", v))
                put(JSONObject().put("brand", "Not?A_Brand").put("version", if (v == major) "99" else "99.0.0.0"))
            }
            val a = p.value(Keys.ARCH).ifEmpty { real[Keys.ARCH]!! }
            cfg.put("uad", JSONObject().apply {
                put("brands", brands(major))
                put("fullVersionList", brands(full))
                put("fullVersion", full)
                put("mobile", isMobile(p))
                put("model", p.value(Keys.MODEL).ifEmpty { real[Keys.MODEL]!! })
                put("platformVersion", p.value(Keys.ANDROID).ifEmpty { real[Keys.ANDROID]!! })
                put("arch", if (a.startsWith("x86")) "x86" else "arm")
                put("bitness", if (a.contains("64")) "64" else "32")
            })
        }
        if (cfg.length() == 0) return null
        val t = template ?: ctx.assets.open("spoof.js").bufferedReader().use { it.readText() }.also { template = it }
        return t.replace("__CFG__", cfg.toString())
    }
}
