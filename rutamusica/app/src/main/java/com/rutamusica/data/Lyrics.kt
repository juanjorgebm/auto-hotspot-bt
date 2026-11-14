package com.rutamusica.data

import android.content.Context
import com.rutamusica.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val synced: List<LyricLine>,   // vacío si solo hay letra sin tiempos
    val plain: String,
    val instrumental: Boolean,
    val matched: String,           // "Artista – Canción" que se encontró en LRCLIB
) {
    val hasSynced get() = synced.isNotEmpty()
}

/**
 * Letras desde LRCLIB (lrclib.net), una base de datos pública y gratuita de letras sincronizadas.
 * Se guardan en caché en el dispositivo para no buscarlas otra vez.
 */
class LyricsRepo(context: Context) {
    private val http = Http.client(context)
    private val cacheDir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /** Artista y canción adivinados a partir del título del video de YouTube. */
    data class Guess(val artist: String, val title: String)

    suspend fun find(track: Track, durationSec: Double, manual: Guess? = null, useCache: Boolean = true): Lyrics? =
        withContext(Dispatchers.IO) {
            val cache = File(cacheDir, track.videoId + ".json")
            if (useCache && manual == null && cache.exists()) {
                runCatching { return@withContext fromCache(JSONObject(cache.readText())) }
            }
            val g = manual ?: guess(track)
            val attempts = mutableListOf<Map<String, String>>()
            if (g.artist.isNotBlank()) attempts += mapOf("track_name" to g.title, "artist_name" to g.artist)
            attempts += mapOf("q" to "${g.artist} ${g.title}".trim())
            if (manual == null) {
                val raw = clean(track.title)
                if (raw.isNotBlank() && raw != g.title) attempts += mapOf("q" to raw)
                // Sin artista: solo el nombre de la canción
                if (g.artist.isNotBlank()) attempts += mapOf("track_name" to g.title)
            }

            var plainFallback: JSONObject? = null
            for (params in attempts) {
                val results = runCatching { search(params) }.getOrNull() ?: continue
                val best = pick(results, durationSec, synced = true)
                if (best != null) return@withContext save(cache, best)
                if (plainFallback == null) plainFallback = pick(results, durationSec, synced = false)
            }
            plainFallback?.let { save(cache, it) }
        }

    fun clearCache(videoId: String) {
        File(cacheDir, "$videoId.json").delete()
    }

    private fun search(params: Map<String, String>): JSONArray {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("LRCLIB HTTP ${resp.code}")
            return JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
        }
    }

    /** El resultado con letra (sincronizada si se pide) cuya duración se parezca más a la del video. */
    private fun pick(arr: JSONArray, durationSec: Double, synced: Boolean): JSONObject? {
        var best: JSONObject? = null
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ok = if (synced) o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }
            else o.optString("plainLyrics").let { it.isNotBlank() && it != "null" } || o.optBoolean("instrumental")
            if (!ok) continue
            val d = o.optDouble("duration", 0.0)
            val diff = if (durationSec > 0 && d > 0) abs(d - durationSec) else i.toDouble()
            if (diff < bestDiff) { bestDiff = diff; best = o }
        }
        return best
    }

    private fun save(cache: File, o: JSONObject): Lyrics {
        val c = JSONObject()
            .put("synced", o.optString("syncedLyrics").takeIf { it != "null" }.orEmpty())
            .put("plain", o.optString("plainLyrics").takeIf { it != "null" }.orEmpty())
            .put("instrumental", o.optBoolean("instrumental"))
            .put("matched", "${o.optString("artistName")} – ${o.optString("trackName")}")
        runCatching { cache.writeText(c.toString()) }
        return fromCache(c)
    }

    private fun fromCache(c: JSONObject) = Lyrics(
        synced = parseLrc(c.optString("synced")),
        plain = c.optString("plain"),
        instrumental = c.optBoolean("instrumental"),
        matched = c.optString("matched"),
    )

    companion object {
        private val TIME = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")

        fun parseLrc(lrc: String): List<LyricLine> {
            if (lrc.isBlank()) return emptyList()
            val out = mutableListOf<LyricLine>()
            for (line in lrc.lines()) {
                val stamps = TIME.findAll(line).toList()
                if (stamps.isEmpty()) continue
                val text = line.substring(stamps.last().range.last + 1).trim()
                for (m in stamps) {
                    val min = m.groupValues[1].toLongOrNull() ?: continue
                    val sec = m.groupValues[2].replace(':', '.').toDoubleOrNull() ?: continue
                    out += LyricLine(min * 60_000 + (sec * 1000).toLong(), text)
                }
            }
            return out.sortedBy { it.timeMs }
        }

        private val NOISE = Regex(
            """(?i)\s*[(\[【][^)\]】]*(official|oficial|video|vídeo|audio|lyric|letra|visuali[sz]er|remaster|hd|4k|mv|clip|en vivo|live|cover|explicit)[^)\]】]*[)\]】]"""
        )
        private val FEAT = Regex("""(?i)\s+(ft\.?|feat\.?|featuring)\s+.*$""")

        /** Quita "(Official Video)", "[Letra]", "| ..." etc. */
        fun clean(s: String): String {
            var t = NOISE.replace(s, "")
            t = t.substringBefore(" | ").substringBefore(" // ")
            t = t.replace(Regex("""(?i)\s*\b(official (music )?video|video oficial|audio oficial|lyrics?)\b\s*$"""), "")
            return t.replace(Regex("""\s+"""), " ").trim().trim('-', '–', '—', ' ', '"', '\'')
        }

        fun guess(track: Track): Guess {
            val title = clean(track.title)
            val channel = track.channel
                .replace(Regex("""(?i)\s*-\s*topic$"""), "")
                .replace(Regex("""(?i)vevo$"""), "")
                .replace(Regex("""(?i)\s*(official|oficial)$"""), "")
                .trim()
            val parts = title.split(Regex("""\s+[-–—]\s+"""), limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                Guess(artist = parts[0].replace(FEAT, "").trim(), title = parts[1].replace(FEAT, "").trim())
            } else {
                Guess(artist = channel, title = title.replace(FEAT, "").trim())
            }
        }
    }
}
