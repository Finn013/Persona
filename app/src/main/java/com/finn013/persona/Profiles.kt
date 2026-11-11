package com.finn013.persona

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Один профиль = свои куки, вход, хранилище и (по желанию) своё «устройство». */
data class Profile(
    val id: String,
    var name: String,
    var color: Int,
    var startUrl: String,
    val spoof: MutableMap<String, String> = mutableMapOf(),
    var preset: String = ""
) {
    /** Имя профиля внутри движка WebView. */
    val engineName: String get() = "p_$id"

    fun value(key: String): String = spoof[key]?.trim().orEmpty()
    fun hasSpoof(): Boolean = spoof.values.any { it.isNotBlank() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("color", color)
        put("startUrl", startUrl); put("preset", preset)
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
                preset = o.optString("preset", "")
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

    fun all(ctx: Context): MutableList<Profile> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun get(ctx: Context, id: String?): Profile? = if (id == null) null else all(ctx).firstOrNull { it.id == id }

    fun save(ctx: Context, p: Profile) {
        val list = all(ctx)
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        write(ctx, list)
    }

    fun delete(ctx: Context, id: String) = write(ctx, all(ctx).filterNot { it.id == id })

    private fun write(ctx: Context, list: List<Profile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
