package com.rutamusica.data

import android.content.Context
import com.rutamusica.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class Playlist(val id: String, val title: String, val count: Int, val thumb: String?, val special: Boolean = false)

data class Track(val videoId: String, val title: String, val channel: String, val thumb: String?)

/**
 * Lectura de la cuenta con la YouTube Data API v3 oficial (solo lectura).
 * No se descargan ni se extraen videos: la reproducción la hace el reproductor oficial de YouTube.
 */
class YouTubeApi(context: Context, private val auth: GoogleAuth) {
    private val http = Http.client(context)

    class ApiException(message: String) : IOException(message)

    private suspend fun get(path: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val url: HttpUrl = "https://www.googleapis.com/youtube/v3/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        var token = auth.accessToken()
        repeat(2) { attempt ->
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return@withContext JSONObject(txt)
                if (resp.code == 401 && attempt == 0) {
                    token = auth.accessToken(forceRefresh = true)
                } else {
                    throw ApiException(explain(txt, resp.code))
                }
            }
        }
        throw ApiException("No se pudo leer YouTube")
    }

    private fun explain(txt: String, code: Int): String {
        val err = runCatching { JSONObject(txt).getJSONObject("error") }.getOrNull()
        val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        return when (reason) {
            "accessNotConfigured", "SERVICE_DISABLED" ->
                "Falta activar \"YouTube Data API v3\" en tu proyecto de Google Cloud."
            "quotaExceeded", "dailyLimitExceeded" ->
                "Se acabó el cupo diario gratis de YouTube para tu llave. Mañana vuelve a funcionar."
            "playlistNotFound" -> "Esa lista no existe o es privada."
            else -> if (err?.optString("status") == "PERMISSION_DENIED" && txt.contains("has not been used"))
                "Falta activar \"YouTube Data API v3\" en tu proyecto de Google Cloud."
            else "Error de YouTube (HTTP $code) ${err?.optString("message").orEmpty()}".trim()
        }
    }

    /** Nombre del canal de la cuenta conectada y el id de su lista de "Me gusta". */
    suspend fun me(): Pair<String, String?> {
        val o = get("channels", mapOf("part" to "snippet,contentDetails", "mine" to "true"))
        val item = o.optJSONArray("items")?.optJSONObject(0) ?: return "Tu cuenta" to "LL"
        val name = item.optJSONObject("snippet")?.optString("title").orEmpty().ifBlank { "Tu cuenta" }
        val likes = item.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("likes")
        return name to (likes?.takeIf { it.isNotBlank() } ?: "LL")
    }

    suspend fun myPlaylists(): List<Playlist> {
        val out = mutableListOf<Playlist>()
        var page: String? = null
        var pages = 0
        do {
            val p = mutableMapOf("part" to "snippet,contentDetails", "mine" to "true", "maxResults" to "50")
            page?.let { p["pageToken"] = it }
            val o = get("playlists", p)
            val items = o.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val it = items!!.getJSONObject(i)
                val sn = it.optJSONObject("snippet") ?: continue
                out += Playlist(
                    id = it.getString("id"),
                    title = sn.optString("title"),
                    count = it.optJSONObject("contentDetails")?.optInt("itemCount", 0) ?: 0,
                    thumb = thumb(sn),
                )
            }
            page = o.optString("nextPageToken").takeIf { it.isNotBlank() }
            pages++
        } while (page != null && pages < 10)
        return out
    }

    suspend fun playlistItems(playlistId: String, max: Int = 1000): List<Track> {
        val out = mutableListOf<Track>()
        var page: String? = null
        do {
            val p = mutableMapOf("part" to "snippet,contentDetails", "playlistId" to playlistId, "maxResults" to "50")
            page?.let { p["pageToken"] = it }
            val o = get("playlistItems", p)
            val items = o.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val sn = items!!.getJSONObject(i).optJSONObject("snippet") ?: continue
                val vid = sn.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                val title = sn.optString("title")
                if (vid.isBlank() || title == "Deleted video" || title == "Private video") continue
                out += Track(
                    videoId = vid,
                    title = title,
                    channel = sn.optString("videoOwnerChannelTitle"),
                    thumb = thumb(sn),
                )
            }
            page = o.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (page != null && out.size < max)
        return out
    }

    private fun thumb(sn: JSONObject): String? {
        val t = sn.optJSONObject("thumbnails") ?: return null
        for (k in listOf("medium", "high", "default")) {
            t.optJSONObject(k)?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
