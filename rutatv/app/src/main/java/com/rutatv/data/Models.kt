package com.rutatv.data

import org.json.JSONObject

enum class Kind { LIVE, MOVIE, SERIES, EPISODE }

data class Category(val id: String, val name: String)

/** Un canal, película, serie o episodio. */
data class Item(
    val kind: Kind,
    val id: String,
    val name: String,
    val icon: String? = null,
    val ext: String? = null,
    val categoryId: String? = null,
    val rating: String? = null,
    val fromViva: Boolean = false,
) {
    val key: String get() = "${kind.name}:$id"

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("id", id)
        put("name", name)
        icon?.let { put("icon", it) }
        ext?.let { put("ext", it) }
        categoryId?.let { put("cat", it) }
        rating?.let { put("rating", it) }
        if (fromViva) put("viva", true)
    }

    companion object {
        fun fromJson(o: JSONObject): Item? = try {
            Item(
                kind = Kind.valueOf(o.getString("kind")),
                id = o.getString("id"),
                name = o.optString("name"),
                icon = o.optString("icon").ifBlank { null },
                ext = o.optString("ext").ifBlank { null },
                categoryId = o.optString("cat").ifBlank { null },
                rating = o.optString("rating").ifBlank { null },
                fromViva = o.optBoolean("viva", false),
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val image: String?,
)

data class SeriesInfo(
    val name: String,
    val plot: String?,
    val cover: String?,
    val seasons: Map<Int, List<Episode>>,
)

/** Credenciales Xtream (servidor + usuario + contraseña). */
data class XtreamAccount(val server: String, val username: String, val password: String) {
    val isComplete get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/**
 * Un perfil (como los de VivaPlayer): sus propias credenciales Xtream, favoritos,
 * recientes y progreso. [vivaProfileId] vacío = perfil creado a mano.
 */
data class Profile(
    val id: String,
    val name: String,
    val account: XtreamAccount,
    val vivaProfileId: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name)
        put("server", account.server); put("user", account.username); put("pass", account.password)
        put("viva", vivaProfileId)
    }

    companion object {
        fun fromJson(o: JSONObject): Profile? = try {
            Profile(
                id = o.getString("id"),
                name = o.optString("name").ifBlank { "Perfil" },
                account = XtreamAccount(o.optString("server"), o.optString("user"), o.optString("pass")),
                vivaProfileId = o.optString("viva"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
