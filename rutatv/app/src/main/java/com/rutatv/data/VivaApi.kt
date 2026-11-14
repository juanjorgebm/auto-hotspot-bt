package com.rutatv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

/**
 * Cliente mínimo de la API de revendedor de VivaPlayer. Solo se usan lecturas:
 * list_customers, list_customer_profiles, list_servers y export_customer_favorites.
 *
 * La forma exacta de las respuestas no está documentada en la página exportada, así que
 * los datos se buscan con nombres de campo habituales (ver [Dig]). La última respuesta se
 * guarda en [lastRaw] para poder diagnosticar desde la pantalla de configuración.
 */
class VivaApi(private val token: String, private val client: OkHttpClient) {

    private val rawLog = StringBuilder()

    /** Respuestas de la API (solo del usuario elegido) con contraseñas y tokens ocultos. */
    val lastRaw: String get() = rawLog.toString()

    fun note(text: String) {
        rawLog.append(text).append("\n\n")
    }

    private var serversCache: List<JSONObject>? = null

    data class Pick(val id: String, val label: String, val detail: String, val obj: JSONObject)

    private fun call(action: String, extra: Map<String, Any> = emptyMap(), log: Boolean = true): Any? {
        val body = JSONObject().put("action", action)
        extra.forEach { (k, v) -> body.put(k, v) }
        val req = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            val parsed = try { JSONTokener(text).nextValue() } catch (e: Exception) { null }
            if (log || !resp.isSuccessful) {
                val shown = when (parsed) {
                    is JSONObject -> (Dig.mask(parsed) as JSONObject).toString(2)
                    is JSONArray -> (Dig.mask(parsed) as JSONArray).toString(2)
                    else -> text
                }
                note("[$action] HTTP ${resp.code}\n" + shown.take(8000))
            }
            if (!resp.isSuccessful) {
                val msg = (parsed as? JSONObject)?.let { Dig.errorMessage(it) } ?: text.take(200)
                throw IOException("VivaPlayer ($action): HTTP ${resp.code} $msg")
            }
            if (parsed is JSONObject && parsed.optBoolean("success", true) == false) {
                throw IOException("VivaPlayer ($action): ${Dig.errorMessage(parsed) ?: "error"}")
            }
            return parsed
        }
    }

    /** Cuando no sabemos el nombre exacto del parámetro, lo mandamos con los nombres habituales. */
    private fun idParams(prefix: String, id: String): Map<String, Any> {
        val v: Any = id.toLongOrNull() ?: id
        return when (prefix) {
            "customer" -> mapOf("customer_id" to v, "customerId" to v, "user_id" to v, "id" to v)
            else -> mapOf("profile_id" to v, "profileId" to v)
        }
    }

    /**
     * Busca SOLO el cliente con ese usuario. Los demás clientes de la cuenta de revendedor
     * se descartan sin mostrarse ni guardarse (tampoco quedan en [lastRaw]).
     */
    fun findCustomer(username: String): Pick? {
        val wanted = username.trim().lowercase()
        val raw = call("list_customers", mapOf("search" to username.trim(), "query" to username.trim(), "username" to username.trim()), log = false)
        val arr = Dig.firstObjectArray(raw, "customers", "data", "items", "results", "users")
        val match = arr?.let { Dig.objects(it) }?.firstOrNull { o ->
            listOf("username", "user", "login", "email", "name", "customer_username", "xtream_username")
                .mapNotNull { k -> Dig.str(o, k)?.trim()?.lowercase() }
                .any { it == wanted || it.substringBefore("@") == wanted }
        }
        note("[list_customers] (solo tu usuario)\n" + (match?.let { (Dig.mask(it) as JSONObject).toString(2).take(8000) } ?: "no se encontró el usuario $username"))
        val o = match ?: return null
        val id = Dig.str(o, "id", "customer_id", "uuid", "user_id") ?: return null
        val label = Dig.str(o, "username", "name", "email") ?: username
        return Pick(id, label, Dig.str(o, "status", "expires_at", "exp_date") ?: "", o)
    }

    fun profiles(customerId: String, customerObj: JSONObject?): List<Pick> {
        val raw = try {
            call("list_customer_profiles", idParams("customer", customerId))
        } catch (e: IOException) {
            // Si falla, intenta con los perfiles que vengan dentro del propio cliente.
            customerObj ?: throw e
        }
        var arr = Dig.firstObjectArray(raw, "profiles", "data", "items", "results")
        if ((arr == null || arr.length() == 0) && customerObj != null) {
            arr = Dig.firstObjectArray(customerObj, "profiles", "xtream_profiles")
        }
        val list = arr?.let { Dig.objects(it) } ?: emptyList()
        return list.mapIndexed { i, o ->
            val id = Dig.str(o, "id", "profile_id", "uuid") ?: "$i"
            val label = Dig.str(o, "name", "profile_name", "label", "display_name", "username") ?: "Perfil ${i + 1}"
            val detail = listOfNotNull(Dig.str(o, "username", "xtream_username", "user"), Dig.serverText(o))
                .filter { it != label }.joinToString(" · ")
            Pick(id, label, detail, o)
        }
    }

    fun servers(): List<JSONObject> {
        serversCache?.let { return it }
        val list = try {
            Dig.firstObjectArray(call("list_servers"), "servers", "data", "items", "results")?.let { Dig.objects(it) } ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        serversCache = list
        return list
    }

    /** Resultado de buscar credenciales: la cuenta o el motivo por el que faltan. */
    data class AccountResult(val account: XtreamAccount?, val problem: String)

    /**
     * Saca servidor/usuario/contraseña de un perfil. Lo que falte en el perfil se busca en el
     * cliente y, para el servidor, en list_servers (por id o, si solo hay uno, ese).
     */
    fun accountFromProfile(profile: JSONObject, customer: JSONObject? = null): AccountResult {
        val sources = listOfNotNull(profile, customer)
        // 1) Un enlace M3U completo en cualquier campo.
        for (src in sources) {
            Dig.allStrings(src).firstOrNull { it.contains("username=") && it.contains("password=") }?.let { url ->
                XtreamApi.parsePlaylistUrl(url)?.let { return AccountResult(it, "") }
            }
        }
        fun first(vararg keys: String): String? = sources.firstNotNullOfOrNull { Dig.str(it, *keys) }
        val user = first(*Dig.USER_KEYS)
        val pass = first(*Dig.PASS_KEYS)
        var server = sources.firstNotNullOfOrNull { Dig.serverText(it) }
        if (server == null) {
            val all = servers()
            val ref = first("server_id", "xtream_server_id", "serverId", "dns_id", "server_uuid", "xtream_server")
            val byId = ref?.let { r -> all.firstOrNull { s -> listOf("id", "server_id", "uuid", "_id").any { Dig.str(s, it) == r } } }
            val chosen = byId ?: all.singleOrNull()
            if (chosen != null) server = Dig.serverText(chosen)
        }
        val missing = listOfNotNull(
            if (server == null) "servidor" else null,
            if (user == null) "usuario" else null,
            if (pass == null) "contraseña" else null,
        )
        if (missing.isNotEmpty()) {
            val keys = profile.keys().asSequence().toList().joinToString(", ")
            return AccountResult(null, "falta ${missing.joinToString(", ")} (campos del perfil: $keys)")
        }
        return AccountResult(XtreamAccount(XtreamApi.normalizeServer(server!!), user!!, pass!!), "")
    }

    /** Favoritos guardados en VivaPlayer para ese cliente (solo lectura). */
    fun favorites(customerId: String, profileId: String?): List<Item> {
        val params = HashMap(idParams("customer", customerId))
        if (!profileId.isNullOrBlank()) params.putAll(idParams("profile", profileId))
        val raw = call("export_customer_favorites", params)
        return Dig.favoriteItems(raw, profileId)
    }

    companion object {
        const val URL = "https://tkawefrodukvaqmqgous.supabase.co/functions/v1/reseller-api"
    }
}

/** Utilidades para buscar datos en JSON de forma tolerante. */
object Dig {
    val USER_KEYS = arrayOf("username", "xtream_username", "xtream_user", "user_name", "user", "login", "usuario", "account", "line_username")
    val PASS_KEYS = arrayOf("password", "xtream_password", "xtream_pass", "pass", "passwd", "pwd", "contrasena", "line_password", "secret")

    private val SECRET = Regex("pass|token|secret|pwd|contras", RegexOption.IGNORE_CASE)

    /** Copia del JSON con contraseñas/tokens ocultos (se ve el nombre del campo y el largo). */
    fun mask(v: Any?): Any? = when (v) {
        is JSONObject -> JSONObject().also { out ->
            v.keys().forEach { k ->
                val c = v.opt(k)
                out.put(k, if (SECRET.containsMatchIn(k) && c != null && c !is JSONObject && c !is JSONArray && c != JSONObject.NULL)
                    "•••(${c.toString().length})" else mask(c))
            }
        }
        is JSONArray -> JSONArray().also { out -> for (i in 0 until v.length()) out.put(mask(v.opt(i))) }
        is String -> if (v.contains("password=")) v.replace(Regex("password=[^&]*"), "password=•••") else v
        else -> v
    }

    fun objects(arr: JSONArray): List<JSONObject> = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

    /** Primer arreglo de objetos que encuentre, mirando primero las claves preferidas. */
    fun firstObjectArray(v: Any?, vararg preferred: String, depth: Int = 0): JSONArray? {
        if (depth > 4 || v == null) return null
        when (v) {
            is JSONArray -> {
                if (v.length() == 0) return v
                if (v.optJSONObject(0) != null) return v
                return null
            }
            is JSONObject -> {
                for (k in preferred) {
                    val child = v.opt(k) ?: continue
                    firstObjectArray(child, *preferred, depth = depth + 1)?.let { return it }
                }
                val keys = v.keys().asSequence().toList()
                for (k in keys) {
                    val child = v.opt(k)
                    if (child is JSONArray || child is JSONObject) {
                        firstObjectArray(child, *preferred, depth = depth + 1)?.let { if (it.length() > 0) return it }
                    }
                }
            }
        }
        return null
    }

    /** Primer valor de texto no vacío entre las claves dadas (sin importar mayúsculas), con un nivel de anidación. */
    fun str(o: JSONObject, vararg keys: String): String? {
        direct(o, *keys)?.let { return it }
        val it = o.keys()
        while (it.hasNext()) {
            val child = o.opt(it.next())
            if (child is JSONObject) direct(child, *keys)?.let { v -> return v }
        }
        return null
    }

    private fun direct(o: JSONObject, vararg keys: String): String? {
        val map = HashMap<String, String>()
        o.keys().forEach { map[it.lowercase()] = it }
        for (k in keys) {
            val real = map[k.lowercase()] ?: continue
            val v = o.opt(real)
            if (v == null || v == JSONObject.NULL || v is JSONObject || v is JSONArray) continue
            val s = v.toString().trim()
            if (s.isNotEmpty() && s != "null") return s
        }
        return null
    }

    /** Construye "protocolo://host:puerto" a partir de un objeto servidor o perfil. */
    fun serverText(o: JSONObject): String? {
        for (key in listOf("server", "xtream_server", "xtream")) {
            val child = o.optJSONObject(key)
            if (child != null) serverText(child)?.let { return it }
        }
        val hostLike = Regex("^(https?://)?[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(:\\d+)?(/.*)?$|^(https?://)?\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
        val url = listOf("server_url", "url", "host", "dns", "domain", "base_url", "xtream_url", "portal", "portal_url",
            "server", "server_host", "server_address", "address", "hostname")
            .mapNotNull { direct(o, it) }
            .firstOrNull { it.contains("username=") || hostLike.matches(it.trim()) }
            ?: return null
        if (url.contains("username=")) XtreamApi.parsePlaylistUrl(url)?.let { return it.server }
        var s = url
        if (!s.contains("://")) {
            val proto = direct(o, "protocol", "scheme")?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "http"
            s = "$proto://$s"
        }
        val port = direct(o, "port", "http_port")
        val hostPart = s.substringAfter("://").substringBefore("/")
        if (port != null && !hostPart.contains(":") && port != "80" && port != "443") {
            s = s.replaceFirst(hostPart, "$hostPart:$port")
        }
        return s
    }

    fun allStrings(v: Any?, depth: Int = 0): List<String> {
        if (depth > 4) return emptyList()
        return when (v) {
            is JSONObject -> v.keys().asSequence().toList().flatMap { allStrings(v.opt(it), depth + 1) }
            is JSONArray -> (0 until v.length()).flatMap { allStrings(v.opt(it), depth + 1) }
            is String -> listOf(v)
            else -> emptyList()
        }
    }

    fun errorMessage(o: JSONObject): String? {
        val e = o.opt("error")
        if (e is JSONObject) return str(e, "message", "code")
        return str(o, "message", "error", "code")
    }

    private val ID_KEYS = arrayOf("stream_id", "streamId", "content_id", "contentId", "item_id", "channel_id",
        "vod_id", "movie_id", "series_id", "seriesId", "media_id", "external_id", "id")
    private val NAME_KEYS = arrayOf("name", "title", "stream_name", "channel_name", "content_name", "item_name")
    private val TYPE_KEYS = arrayOf("type", "stream_type", "content_type", "kind", "media_type", "item_type", "category")

    private fun kindFrom(text: String?): Kind? {
        val t = text?.lowercase() ?: return null
        return when {
            t.contains("series") || t.contains("serie") || t.contains("show") -> Kind.SERIES
            t.contains("movie") || t.contains("vod") || t.contains("film") || t.contains("pelic") -> Kind.MOVIE
            t.contains("live") || t.contains("channel") || t.contains("canal") || t == "tv" -> Kind.LIVE
            else -> null
        }
    }

    /** Recorre cualquier JSON y saca los elementos que parecen favoritos. */
    fun favoriteItems(v: Any?, profileId: String? = null): List<Item> {
        val out = LinkedHashMap<String, Item>()
        fun walk(x: Any?, pathKind: Kind?, depth: Int) {
            if (depth > 6 || x == null) return
            when (x) {
                is JSONArray -> for (i in 0 until x.length()) walk(x.opt(i), pathKind, depth + 1)
                is JSONObject -> {
                    // Si la respuesta trae favoritos de varios perfiles, se queda solo con los del perfil pedido.
                    val owner = direct(x, "profile_id", "profileId")
                    if (!profileId.isNullOrBlank() && owner != null && owner != profileId) return
                    val name = direct(x, *NAME_KEYS)
                    val id = direct(x, *ID_KEYS)
                    val looksLikeItem = name != null && id != null && x.keys().asSequence().none { k ->
                        val c = x.opt(k); c is JSONArray && c.length() > 0 && c.optJSONObject(0) != null
                    }
                    if (looksLikeItem) {
                        val kind = kindFrom(direct(x, *TYPE_KEYS))
                            ?: if (direct(x, "series_id", "seriesId") != null && direct(x, "stream_id") == null) Kind.SERIES else null
                            ?: pathKind ?: Kind.LIVE
                        val streamId = if (kind == Kind.SERIES) direct(x, "series_id", "seriesId", *ID_KEYS)!! else id!!
                        val item = Item(
                            kind = kind, id = streamId, name = name!!,
                            icon = direct(x, "stream_icon", "icon", "logo", "cover", "image", "poster", "thumbnail")
                                ?.takeIf { it.startsWith("http") },
                            ext = direct(x, "container_extension", "extension", "ext"),
                            categoryId = direct(x, "category_id"),
                            fromViva = true,
                        )
                        out.putIfAbsent(item.key, item)
                    } else {
                        x.keys().asSequence().toList().forEach { k ->
                            walk(x.opt(k), kindFrom(k) ?: pathKind, depth + 1)
                        }
                    }
                }
            }
        }
        walk(v, null, 0)
        return out.values.toList()
    }
}
