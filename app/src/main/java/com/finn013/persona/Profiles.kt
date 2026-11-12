package com.finn013.persona

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One profile = its own cookies, logins, storage, tabs, history and (optionally) its own "device".
 * Every profile is bound to a fixed [slot]: a dedicated Android process with its own WebView data
 * directory (see [Persona]). The JSON format is compatible with Persona 0.1 ("slot" is new).
 */
data class Profile(
    val id: String,
    var name: String,
    var color: Int,
    var startUrl: String,
    val spoof: MutableMap<String, String> = mutableMapOf(),
    var preset: String = "",
    var slot: Int = -1
) {
    fun value(key: String): String = spoof[key]?.trim().orEmpty()
    fun hasSpoof(): Boolean = spoof.values.any { it.isNotBlank() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("color", color)
        put("startUrl", startUrl); put("preset", preset); put("slot", slot)
        put("spoof", JSONObject().apply { spoof.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val s = mutableMapOf<String, String>()
            o.optJSONObject("spoof")?.let { j -> j.keys().forEach { k -> s[k] = j.optString(k) } }
            return Profile(
                id = o.getString("id"),
                name = o.optString("name", "Профиль"),
                color = o.optInt("color", PALETTE[0]),
                startUrl = o.optString("startUrl", ""),
                spoof = s,
                preset = o.optString("preset", ""),
                slot = o.optInt("slot", -1)
            )
        }

        fun new(name: String, color: Int) = Profile(UUID.randomUUID().toString().take(8), name, color, "")

        val PALETTE = intArrayOf(
            0xFF7C6CF2.toInt(), 0xFFFF6B8B.toInt(), 0xFF4ECDC4.toInt(), 0xFFFFB347.toInt(),
            0xFF4D96FF.toInt(), 0xFF6BCB77.toInt(), 0xFFB565D9.toInt(), 0xFFE85D3F.toInt()
        )
    }
}

object Profiles {
    private const val PREFS = "profiles"
    private const val KEY = "list"

    /** All profiles. Profiles coming from 0.1 (no slot yet) get a free slot assigned here. */
    @Synchronized
    fun all(ctx: Context): MutableList<Profile> {
        val raw = prefs(ctx).getString(KEY, null) ?: return mutableListOf()
        val list = try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
        var changed = false
        list.forEach { p ->
            if (p.slot !in 0 until Persona.SLOTS || list.count { it.slot == p.slot } > 1) {
                val free = freeSlot(list)
                if (free >= 0) { p.slot = free; changed = true }
            }
        }
        if (changed) write(ctx, list)
        return list
    }

    fun get(ctx: Context, id: String?): Profile? = if (id == null) null else all(ctx).firstOrNull { it.id == id }

    fun bySlot(ctx: Context, slot: Int): Profile? = all(ctx).firstOrNull { it.slot == slot }

    /** Lowest slot no profile uses, or -1 when all [Persona.SLOTS] are taken. */
    fun freeSlot(list: List<Profile>): Int =
        (0 until Persona.SLOTS).firstOrNull { s -> list.none { it.slot == s } } ?: -1

    @Synchronized
    fun save(ctx: Context, p: Profile) {
        val list = all(ctx)
        if (p.slot !in 0 until Persona.SLOTS || list.any { it.slot == p.slot && it.id != p.id }) {
            p.slot = freeSlot(list.filter { it.id != p.id })
        }
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        write(ctx, list)
    }

    @Synchronized
    fun delete(ctx: Context, id: String) = write(ctx, all(ctx).filterNot { it.id == id })

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun write(ctx: Context, list: List<Profile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        // commit(): profile windows run in other processes and read this file when they start.
        prefs(ctx).edit().putString(KEY, arr.toString()).commit()
    }
}
