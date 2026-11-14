package com.rutatv.data

import android.content.Context
import com.rutatv.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Punto único de acceso a datos: perfiles, catálogo (con caché en memoria), favoritos, recientes y progreso. */
class Repo(context: Context) {
    private val appContext = context.applicationContext
    val prefs = Prefs(context)
    val http = Http.client(context)

    private val _profiles = MutableStateFlow(prefs.profiles)
    val profiles: StateFlow<List<Profile>> = _profiles

    /** Perfil activo; null = hay que elegir perfil. */
    private val _active = MutableStateFlow<Profile?>(null)
    val active: StateFlow<Profile?> = _active

    private val _favorites = MutableStateFlow<List<Item>>(emptyList())
    val favorites: StateFlow<List<Item>> = _favorites

    private val _recents = MutableStateFlow<List<Item>>(emptyList())
    val recents: StateFlow<List<Item>> = _recents

    private val catCache = ConcurrentHashMap<Kind, List<Category>>()
    private val streamCache = ConcurrentHashMap<String, List<Item>>()

    private val pid: String get() = _active.value?.id ?: ""

    fun xtream(): XtreamApi = XtreamApi(_active.value?.account ?: XtreamAccount("", "", ""), http)

    // ---------- Perfiles ----------
    fun selectProfile(p: Profile?) {
        val changedAccount = _active.value?.account != p?.account
        _active.value = p
        prefs.activeProfileId = p?.id ?: ""
        _favorites.value = p?.let { prefs.favorites(it.id) } ?: emptyList()
        _recents.value = p?.let { prefs.recents(it.id) } ?: emptyList()
        if (changedAccount) clearCache()
        com.rutatv.widget.Widgets.refresh(appContext)
    }

    fun lastProfile(): Profile? = _profiles.value.firstOrNull { it.id == prefs.activeProfileId }

    /** Agrega o actualiza un perfil (por id). */
    fun upsertProfile(p: Profile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        prefs.profiles = list
        _profiles.value = list
        if (_active.value?.id == p.id) selectProfile(p)
    }

    fun deleteProfile(p: Profile) {
        val list = _profiles.value.filter { it.id != p.id }
        prefs.profiles = list
        prefs.deleteProfileData(p.id)
        _profiles.value = list
        if (_active.value?.id == p.id) selectProfile(null)
    }

    fun clearCache() {
        catCache.clear(); streamCache.clear()
    }

    // ---------- Catálogo ----------
    suspend fun categories(kind: Kind): List<Category> = withContext(Dispatchers.IO) {
        catCache[kind] ?: xtream().categories(kind).also { catCache[kind] = it }
    }

    suspend fun streams(kind: Kind, categoryId: String?): List<Item> = withContext(Dispatchers.IO) {
        val key = "${kind.name}:${categoryId ?: "*"}"
        streamCache[key] ?: run {
            // Si ya tenemos el catálogo completo, filtramos en memoria.
            val all = streamCache["${kind.name}:*"]
            val list = if (categoryId != null && all != null) all.filter { it.categoryId == categoryId }
            else xtream().streams(kind, categoryId)
            streamCache[key] = list
            list
        }
    }

    suspend fun search(kind: Kind, query: String): List<Item> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val all = streams(kind, null)
        return withContext(Dispatchers.Default) { all.filter { normalize(it.name).contains(q) }.take(500) }
    }

    suspend fun seriesInfo(id: String): SeriesInfo = withContext(Dispatchers.IO) { xtream().seriesInfo(id) }

    // ---------- Favoritos (del perfil activo) ----------
    fun isFavorite(item: Item) = _favorites.value.any { it.key == item.key }

    fun toggleFavorite(item: Item) {
        val list = _favorites.value.toMutableList()
        val idx = list.indexOfFirst { it.key == item.key }
        if (idx >= 0) list.removeAt(idx) else list.add(item.copy(fromViva = false))
        setFavorites(list)
    }

    private fun setFavorites(list: List<Item>) {
        if (pid.isEmpty()) return
        prefs.setFavorites(pid, list)
        _favorites.value = list
        com.rutatv.widget.Widgets.refresh(appContext)
    }

    /**
     * Reemplaza los favoritos importados de VivaPlayer de un perfil (los locales se conservan).
     * Empareja cada favorito con el catálogo Xtream por id o por nombre para tener el id
     * correcto, el logo y la extensión.
     */
    suspend fun importVivaFavorites(profile: Profile, items: List<Item>): Int = withContext(Dispatchers.IO) {
        val api = XtreamApi(profile.account, http)
        val catalogs = HashMap<Kind, List<Item>>()
        val fixed = items.map { fav ->
            try {
                // Solo se descarga el catálogo completo de canales; películas/series solo si ya están en memoria
                // (el de películas es enorme y en pantallas de carro con poca memoria puede cerrar la app).
                val catalog = catalogs.getOrPut(fav.kind) {
                    when (fav.kind) {
                        Kind.LIVE -> if (profile.id == pid) streams(Kind.LIVE, null) else api.streams(Kind.LIVE, null)
                        Kind.EPISODE -> emptyList()
                        else -> if (profile.id == pid) streamCache["${fav.kind.name}:*"] ?: emptyList() else emptyList()
                    }
                }
                val match = catalog.firstOrNull { it.id == fav.id }
                    ?: catalog.firstOrNull { normalize(it.name) == normalize(fav.name) }
                match?.copy(fromViva = true) ?: fav
            } catch (e: Throwable) {
                fav
            }
        }
        val local = prefs.favorites(profile.id).filter { !it.fromViva }
        val merged = LinkedHashMap<String, Item>()
        fixed.forEach { merged[it.key] = it }
        local.forEach { merged.putIfAbsent(it.key, it) }
        val result = merged.values.toList()
        prefs.setFavorites(profile.id, result)
        if (profile.id == pid) _favorites.value = result
        com.rutatv.widget.Widgets.refresh(appContext)
        fixed.size
    }

    // ---------- Recientes y progreso ----------
    fun addRecent(item: Item) {
        if (pid.isEmpty()) return
        val list = _recents.value.filter { it.key != item.key }.toMutableList()
        list.add(0, item.copy(fromViva = false))
        val trimmed = list.take(40)
        prefs.setRecents(pid, trimmed)
        _recents.value = trimmed
        com.rutatv.widget.Widgets.refresh(appContext)
    }

    fun position(item: Item) = prefs.position(pid, item.key)
    fun savePosition(item: Item, ms: Long) { if (pid.isNotEmpty()) prefs.savePosition(pid, item.key, ms) }

    companion object {
        fun normalize(s: String): String {
            val n = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            return n.replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        }
    }
}
