package com.rutamusica.data

import android.content.Context

/** Lo que la app guarda en el dispositivo. Nada de esto va dentro del APK ni del repositorio. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("rutamusica", Context.MODE_PRIVATE)

    private fun s(k: String) = sp.getString(k, "") ?: ""
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    /** Llave de Google Cloud (tipo "TV y dispositivos de entrada limitada"). */
    var clientId: String get() = s("client_id"); set(v) = put("client_id", v.trim())
    var clientSecret: String get() = s("client_secret"); set(v) = put("client_secret", v.trim())

    var refreshToken: String get() = s("refresh_token"); set(v) = put("refresh_token", v)
    var accessToken: String get() = s("access_token"); set(v) = put("access_token", v)
    var accessExpiry: Long
        get() = sp.getLong("access_expiry", 0L)
        set(v) = sp.edit().putLong("access_expiry", v).apply()
    var channelName: String get() = s("channel_name"); set(v) = put("channel_name", v)

    /** Ajuste de la letra (ms): positivo = la letra va más tarde. Se guarda por video. */
    fun lyricsOffset(videoId: String): Long = sp.getLong("off_$videoId", 0L)
    fun setLyricsOffset(videoId: String, ms: Long) {
        val e = sp.edit()
        if (ms == 0L) e.remove("off_$videoId") else e.putLong("off_$videoId", ms)
        e.apply()
    }

    /** Búsqueda de letra corregida a mano por video ("artista|canción"). */
    fun lyricsQuery(videoId: String): String = s("lq_$videoId")
    fun setLyricsQuery(videoId: String, q: String) = put("lq_$videoId", q)

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()

    /** Motor del reproductor: "auto", "system" (navegador del sistema) o "gecko" (incluido en la app). */
    var engine: String get() = s("engine").ifBlank { "auto" }; set(v) = put("engine", v)

    var lastCrash: String get() = s("last_crash"); set(v) = put("last_crash", v)

    fun signOut() {
        sp.edit().remove("refresh_token").remove("access_token").remove("access_expiry").remove("channel_name").apply()
    }

    fun clearClient() {
        signOut()
        sp.edit().remove("client_id").remove("client_secret").apply()
    }
}
