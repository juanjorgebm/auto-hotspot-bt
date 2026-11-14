package com.rutatv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.InputStreamReader

/** Cliente de la API Xtream estándar (player_api.php) del proveedor. */
class XtreamApi(private val account: XtreamAccount, private val client: OkHttpClient) {

    val base: String = normalizeServer(account.server)

    private fun apiUrl(action: String?, vararg params: Pair<String, String>): HttpUrl {
        val b = ("$base/player_api.php").toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("URL de servidor no válida: ${account.server}")
        b.addQueryParameter("username", account.username)
        b.addQueryParameter("password", account.password)
        if (action != null) b.addQueryParameter("action", action)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun <T> get(url: HttpUrl, parse: (JsonReader) -> T): T {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("El servidor respondió ${resp.code}")
            val body = resp.body ?: throw IOException("Respuesta vacía")
            val reader = JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            reader.isLenient = true
            return parse(reader)
        }
    }

    private fun getText(url: HttpUrl): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("El servidor respondió ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    /** Comprueba usuario y contraseña. Devuelve un texto con el estado de la cuenta. */
    fun login(): String {
        val text = getText(apiUrl(null))
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("El servidor no devolvió una respuesta Xtream válida")
        }
        val ui = o.optJSONObject("user_info") ?: throw IOException("Usuario o contraseña incorrectos")
        if (ui.optString("auth") == "0") throw IOException("Usuario o contraseña incorrectos")
        val status = ui.optString("status")
        val exp = ui.optString("exp_date").toLongOrNull()
        val expText = if (exp != null && exp > 0) {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(exp * 1000))
        } else "sin fecha"
        return "Estado: ${status.ifBlank { "?" }} · vence: $expText"
    }

    fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            else -> "get_series_categories"
        }
        return get(apiUrl(action)) { r ->
            val out = ArrayList<Category>()
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue(); return@get out
            }
            r.beginArray()
            while (r.hasNext()) {
                var id = ""
                var name = ""
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "category_id" -> id = r.anyString() ?: ""
                        "category_name" -> name = r.anyString() ?: ""
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isNotBlank()) out.add(Category(id, name.trim()))
            }
            r.endArray()
            out
        }
    }

    /** Canales / películas / series. Sin categoría trae el catálogo completo. */
    fun streams(kind: Kind, categoryId: String?): List<Item> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            else -> "get_series"
        }
        val url = if (categoryId != null) apiUrl(action, "category_id" to categoryId) else apiUrl(action)
        return get(url) { r ->
            val out = ArrayList<Item>()
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue(); return@get out
            }
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                var id: String? = null
                var name = ""
                var icon: String? = null
                var ext: String? = null
                var cat: String? = null
                var rating: String? = null
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id", "series_id" -> id = r.anyString()
                        "name", "title" -> { val n = r.anyString(); if (!n.isNullOrBlank() && name.isBlank()) name = n }
                        "stream_icon", "cover" -> icon = r.anyString()
                        "container_extension" -> ext = r.anyString()
                        "category_id" -> cat = r.anyString()
                        "rating" -> rating = r.anyString()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (!id.isNullOrBlank()) {
                    out.add(
                        Item(
                            kind = kind, id = id, name = name.trim(),
                            icon = icon?.takeIf { it.startsWith("http") },
                            ext = ext?.ifBlank { null }, categoryId = cat,
                            rating = rating?.takeIf { it.isNotBlank() && it != "0" },
                        )
                    )
                }
            }
            r.endArray()
            out
        }
    }

    /** Las [limit] películas agregadas más recientemente (campo "added" del catálogo). */
    fun latestMovies(limit: Int): List<Item> {
        val heap = java.util.PriorityQueue<Pair<Long, Item>>(limit + 1, compareBy { it.first })
        get(apiUrl("get_vod_streams")) { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return@get }
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                var id: String? = null
                var name = ""
                var icon: String? = null
                var ext: String? = null
                var rating: String? = null
                var added = 0L
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id" -> id = r.anyString()
                        "name" -> name = r.anyString() ?: ""
                        "stream_icon" -> icon = r.anyString()
                        "container_extension" -> ext = r.anyString()
                        "rating" -> rating = r.anyString()
                        "added" -> added = r.anyString()?.toLongOrNull() ?: 0L
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id != null && (heap.size < limit || added > heap.peek()!!.first)) {
                    heap.add(added to Item(Kind.MOVIE, id, name.trim(), icon?.takeIf { it.startsWith("http") },
                        ext?.ifBlank { null }, null, rating?.takeIf { it.isNotBlank() && it != "0" }))
                    if (heap.size > limit) heap.poll()
                }
            }
            r.endArray()
        }
        return heap.sortedByDescending { it.first }.map { it.second }
    }

    fun seriesInfo(seriesId: String): SeriesInfo {
        val text = getText(apiUrl("get_series_info", "series_id" to seriesId))
        val root = JSONTokener(text).nextValue() as? JSONObject
            ?: throw IOException("Respuesta de serie no válida")
        val info = root.optJSONObject("info")
        val seasons = sortedMapOf<Int, MutableList<Episode>>()
        fun addEpisode(o: JSONObject, seasonHint: Int) {
            val id = o.optString("id").ifBlank { return }
            val season = o.optString("season").toIntOrNull() ?: seasonHint
            val num = o.optString("episode_num").toIntOrNull() ?: 0
            val img = o.optJSONObject("info")?.optString("movie_image")?.takeIf { it.startsWith("http") }
            seasons.getOrPut(season) { mutableListOf() }.add(
                Episode(
                    id = id, season = season, number = num,
                    title = o.optString("title").ifBlank { "Episodio $num" },
                    ext = o.optString("container_extension").ifBlank { "mp4" },
                    image = img,
                )
            )
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k ->
                val arr = eps.optJSONArray(k) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, k.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val s = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until s.length()) s.optJSONObject(j)?.let { addEpisode(it, i + 1) }
                    is JSONObject -> addEpisode(s, 1)
                }
            }
        }
        seasons.values.forEach { list -> list.sortBy { it.number } }
        return SeriesInfo(
            name = info?.optString("name") ?: "",
            plot = info?.optString("plot")?.ifBlank { null },
            cover = info?.optString("cover")?.takeIf { it.startsWith("http") },
            seasons = seasons,
        )
    }

    /** Extensión del archivo de una película (mp4, mkv...). */
    fun movieExtension(vodId: String): String? = try {
        val text = getText(apiUrl("get_vod_info", "vod_id" to vodId))
        JSONObject(text).optJSONObject("movie_data")?.optString("container_extension")?.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    fun liveUrl(id: String, format: String) = "$base/live/${account.username}/${account.password}/$id.$format"
    fun movieUrl(id: String, ext: String) = "$base/movie/${account.username}/${account.password}/$id.$ext"
    fun episodeUrl(id: String, ext: String) = "$base/series/${account.username}/${account.password}/$id.$ext"

    companion object {
        /** Acepta "dominio.com:8080", "http://dominio.com:8080/", ".../player_api.php", etc. */
        fun normalizeServer(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            val idx = listOf("/player_api.php", "/get.php", "/xmltv.php", "/c/", "/live/")
                .map { s.indexOf(it, ignoreCase = true) }.filter { it > 8 }.minOrNull()
            if (idx != null) s = s.substring(0, idx)
            return s.trimEnd('/')
        }

        /** Saca servidor, usuario y contraseña de un enlace M3U tipo get.php?username=..&password=.. */
        fun parsePlaylistUrl(url: String): XtreamAccount? {
            val u = url.trim().toHttpUrlOrNull() ?: return null
            val user = u.queryParameter("username") ?: return null
            val pass = u.queryParameter("password") ?: return null
            val port = if ((u.scheme == "http" && u.port == 80) || (u.scheme == "https" && u.port == 443)) "" else ":${u.port}"
            return XtreamAccount("${u.scheme}://${u.host}$port", user, pass)
        }
    }
}

/** Lee un valor JSON como texto sin importar si viene como número, texto o null. */
internal fun JsonReader.anyString(): String? = when (peek()) {
    JsonToken.STRING -> nextString()
    JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    JsonToken.NULL -> { nextNull(); null }
    else -> { skipValue(); null }
}
