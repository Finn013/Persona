package com.finn013.persona

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.webkit.WebView
import java.io.File

/**
 * Profile isolation for Persona 0.2.
 *
 * Every profile runs the full Fulguris browser in its own Android process ("slot"), the same way
 * Fulguris isolates its incognito mode. Each slot process uses its own WebView data directory
 * ([WebView.setDataDirectorySuffix]), so cookies, logins, localStorage, IndexedDB, cache and
 * service workers never mix between profiles. The process also gets its own files directory
 * (tabs/sessions) and its own history database, see [fulguris.App].
 *
 * Each slot is a dedicated activity class ([fulguris.activity.MainActivity] subclass) with its
 * own task affinity, so every profile is a separate window in Android's Recents.
 */
object Persona {
    /** Number of slots declared in the manifest (Slot0 … Slot11). */
    const val SLOTS = 12
    private const val PROCESS_PREFIX = ":persona"
    const val SUFFIX_PREFIX = "persona"

    const val EXTRA_PROFILE = "com.finn013.persona.PROFILE"

    /** Slot of the current process, or null in the main (launcher) process. */
    val slot: Int? by lazy {
        val pn = Application.getProcessName() ?: return@lazy null
        val i = pn.indexOf(PROCESS_PREFIX)
        if (i < 0) null else pn.substring(i + PROCESS_PREFIX.length).toIntOrNull()
    }

    /** Profile served by this process, null in the main process. */
    @Volatile
    var current: Profile? = null
        private set

    /** Call from Application.onCreate, before any WebView is created. */
    fun initProcess(app: Application) {
        val s = slot ?: return
        WebView.setDataDirectorySuffix(SUFFIX_PREFIX + s)
        current = Profiles.bySlot(app, s)
    }

    fun suffix(slot: Int) = SUFFIX_PREFIX + slot

    /** Activity class of a slot. */
    fun slotClass(slot: Int): Class<out Activity> = Class.forName("com.finn013.persona.slots.Slot$slot").asSubclass(Activity::class.java)

    /** Open a profile window (and optionally a URL in a new tab of it). */
    fun open(ctx: Context, p: Profile, url: String?) {
        if (p.slot !in 0 until SLOTS) return
        val i = Intent(ctx, slotClass(p.slot)).apply {
            putExtra(EXTRA_PROFILE, p.id)
            if (!url.isNullOrBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(normalize(url))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }

    /** Close the profile's window and stop its process. */
    fun stop(ctx: Context, slot: Int) {
        val cls = "com.finn013.persona.slots.Slot$slot"
        val am = ctx.getSystemService(ActivityManager::class.java)
        am.appTasks.forEach { t ->
            try {
                val info = t.taskInfo
                if (info.baseIntent.component?.className == cls || info.baseActivity?.className == cls ||
                    info.topActivity?.className == cls) t.finishAndRemoveTask()
            } catch (_: Exception) {}
        }
        val name = ctx.packageName + PROCESS_PREFIX + slot
        am.runningAppProcesses?.forEach { pi ->
            if (pi.processName == name && pi.pid != Process.myPid()) Process.killProcess(pi.pid)
        }
    }

    fun isRunning(ctx: Context, slot: Int): Boolean {
        val name = ctx.packageName + PROCESS_PREFIX + slot
        return ctx.getSystemService(ActivityManager::class.java).runningAppProcesses?.any { it.processName == name } == true
    }

    /**
     * Delete everything a slot stored: WebView data dir (cookies, logins, site storage, cache),
     * tabs/sessions and history. Must be called after [stop].
     */
    fun wipeSlot(ctx: Context, slot: Int) {
        val sfx = suffix(slot)
        val dataDir = ctx.dataDir
        // WebView: app_webview_<suffix>, app_textures_<suffix>, … and cache/WebView/<suffix> style dirs.
        dataDir.listFiles()?.forEach { f -> if (f.name.endsWith("_$sfx")) f.deleteRecursively() }
        ctx.cacheDir.walkTopDown().maxDepth(2).filter { it.name == sfx || it.name.endsWith("_$sfx") }
            .toList().forEach { it.deleteRecursively() }
        // Fulguris files (tabs, sessions, generated pages) of that slot.
        File(dataDir, "files/$sfx").deleteRecursively()
        // Per-slot databases (history).
        File(dataDir, "databases").listFiles()?.forEach { f -> if (f.name.substringBefore('-').endsWith("_$sfx")) f.delete() }
    }

    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return t
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(t)) return t
        if (!t.contains(' ') && t.contains('.')) return "https://$t"
        return "https://www.google.com/search?q=" + Uri.encode(t)
    }
}
