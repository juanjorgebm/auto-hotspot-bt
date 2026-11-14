package com.rutatv.data

import android.content.Context
import org.json.JSONArray

/**
 * Todo lo que la app guarda en el dispositivo (nada va dentro del APK).
 * Favoritos, recientes y progreso se guardan POR PERFIL.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("rutatv", Context.MODE_PRIVATE)

    var profiles: List<Profile>
        get() = try {
            val arr = JSONArray(sp.getString("profiles", "[]"))
            (0 until arr.length()).mapNotNull { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val arr = JSONArray(); v.forEach { arr.put(it.toJson()) }
            sp.edit().putString("profiles", arr.toString()).apply()
        }

    var activeProfileId: String
        get() = sp.getString("active_profile", "") ?: ""
        set(v) = sp.edit().putString("active_profile", v).apply()

    /** Token de revendedor: solo se guarda si el usuario marca "Recordar token". */
    var vivaToken: String
        get() = sp.getString("viva_token", "") ?: ""
        set(v) = sp.edit().putString("viva_token", v).apply()

    /** Usuario de cliente de VivaPlayer (p. ej. el del propio dueño de la app). */
    var vivaUsername: String
        get() = sp.getString("viva_username", "") ?: ""
        set(v) = sp.edit().putString("viva_username", v).apply()

    var vivaCustomerId: String
        get() = sp.getString("viva_customer", "") ?: ""
        set(v) = sp.edit().putString("viva_customer", v).apply()

    /** "ts" (por defecto) o "m3u8" para los canales en vivo. */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(v) = sp.edit().putString("live_format", v).apply()

    fun favorites(pid: String) = readItems("favorites_$pid")
    fun setFavorites(pid: String, v: List<Item>) = writeItems("favorites_$pid", v)
    fun recents(pid: String) = readItems("recents_$pid")
    fun setRecents(pid: String, v: List<Item>) = writeItems("recents_$pid", v)

    fun position(pid: String, key: String): Long = sp.getLong("pos_${pid}_$key", 0L)
    fun savePosition(pid: String, key: String, ms: Long) = sp.edit().putLong("pos_${pid}_$key", ms).apply()

    private fun readItems(name: String): List<Item> {
        val raw = sp.getString(name, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { Item.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeItems(name: String, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        sp.edit().putString(name, arr.toString()).apply()
    }

    fun newMovies(server: String) = readItems("newmovies_" + server.hashCode())
    fun setNewMovies(server: String, v: List<Item>) {
        writeItems("newmovies_" + server.hashCode(), v)
        sp.edit().putLong("newmovies_t_" + server.hashCode(), System.currentTimeMillis()).apply()
    }
    fun newMoviesTime(server: String) = sp.getLong("newmovies_t_" + server.hashCode(), 0L)

    var lastCrash: String
        get() = sp.getString("last_crash", "") ?: ""
        set(v) = sp.edit().putString("last_crash", v).apply()

    fun deleteProfileData(pid: String) {
        val e = sp.edit().remove("favorites_$pid").remove("recents_$pid")
        sp.all.keys.filter { it.startsWith("pos_${pid}_") }.forEach { e.remove(it) }
        e.apply()
    }

    fun clearVivaLink() {
        sp.edit().remove("viva_token").remove("viva_customer").apply()
    }
}
