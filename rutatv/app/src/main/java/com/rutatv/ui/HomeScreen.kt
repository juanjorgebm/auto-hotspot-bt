package com.rutatv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutatv.RutaApp
import com.rutatv.data.Episode
import com.rutatv.data.Item
import com.rutatv.data.Kind
import com.rutatv.player.PlayQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
    LIVE("En vivo", Icons.Filled.LiveTv),
    MOVIES("Películas", Icons.Filled.Movie),
    SERIES("Series", Icons.Filled.VideoLibrary),
    FAVS("Favoritos", Icons.Filled.Star),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

private const val FAV = "__fav"
private const val REC = "__rec"

@Composable
fun HomeScreen(onSwitchProfile: () -> Unit, onSetup: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
    val holder = rememberSaveableStateHolder()
    val repo = RutaApp.repo
    val profile by repo.active.collectAsState()
    val profiles by repo.profiles.collectAsState()

    Row(Modifier.fillMaxSize()) {
        // Barra lateral grande: fácil de tocar en el carro.
        Column(
            Modifier.width(128.dp).fillMaxHeight().background(Panel).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            profile?.let { p ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onSwitchProfile).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileAvatar(p, profiles.indexOfFirst { it.id == p.id }.coerceAtLeast(0), 56)
                    Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(4.dp))
            Section.entries.forEach { s ->
                val sel = s == section
                Column(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Orange.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { section = s }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(s.icon, null, tint = if (sel) Orange else Muted, modifier = Modifier.size(34.dp))
                    Text(s.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Orange else Color.White)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            holder.SaveableStateProvider(section.name) {
                when (section) {
                    Section.LIVE -> Browse(Kind.LIVE)
                    Section.MOVIES -> Browse(Kind.MOVIE)
                    Section.SERIES -> Browse(Kind.SERIES)
                    Section.FAVS -> FavoritesScreen()
                    Section.SETTINGS -> SettingsScreen(onSwitchProfile, onSetup)
                }
            }
        }
    }
}

/** Abre un elemento: series → ficha; lo demás → reproductor con la lista para pasar de canal. */
@Composable
private fun rememberOpener(onSeries: (Item) -> Unit): (Item, List<Item>) -> Unit {
    val context = LocalContext.current
    return remember(onSeries) {
        { item, list ->
            if (item.kind == Kind.SERIES) onSeries(item)
            else {
                val playable = list.filter { it.kind != Kind.SERIES }
                PlayQueue.start(context, playable, playable.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
            }
        }
    }
}

@Composable
private fun ItemsView(kind: Kind, items: List<Item>, onOpen: (Item, List<Item>) -> Unit) {
    val repo = RutaApp.repo
    val favorites by repo.favorites.collectAsState()
    val favKeys = remember(favorites) { favorites.map { it.key }.toHashSet() }
    if (kind == Kind.LIVE) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items) { i, item ->
                ChannelRow(i + 1, item, item.key in favKeys, onClick = { onOpen(item, items) }, onStar = { repo.toggleFavorite(item) })
            }
        }
    } else {
        LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            itemsIndexed(items) { _, item ->
                PosterCard(item, item.key in favKeys, onClick = { onOpen(item, items) }, onStar = {
                    if (item.kind != Kind.EPISODE) repo.toggleFavorite(item)
                })
            }
        }
    }
}

@Composable
private fun Browse(kind: Kind) {
    val repo = RutaApp.repo
    val favorites by repo.favorites.collectAsState()
    val recents by repo.recents.collectAsState()
    var catId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var seriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var seriesIcon by rememberSaveable { mutableStateOf<String?>(null) }

    val open = rememberOpener { s -> seriesKey = s.id; seriesName = s.name; seriesIcon = s.icon }

    val sid = seriesKey
    if (sid != null) {
        BackHandler { seriesKey = null }
        SeriesDetail(Item(Kind.SERIES, sid, seriesName, seriesIcon)) { seriesKey = null }
        return
    }

    val fv by repo.filterVersion.collectAsState()
    val (cats, retryCats) = rememberLoad(kind, fv) { repo.categories(kind) }
    val myFavs = favorites.filter { it.kind == kind || (kind == Kind.SERIES && it.kind == Kind.EPISODE) }
    val myRecents = recents.filter { it.kind == kind || (kind == Kind.SERIES && it.kind == Kind.EPISODE) }

    LaunchedEffect(cats.data) {
        if (catId == null) {
            catId = when {
                myFavs.isNotEmpty() -> FAV
                else -> cats.data?.firstOrNull()?.id
            }
        }
    }

    val title = when (kind) { Kind.LIVE -> "TV en vivo"; Kind.MOVIE -> "Películas"; else -> "Series" }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
            when {
                cats.loading -> Loading("Cargando categorías…")
                cats.error != null -> ErrorBox(cats.error, retryCats)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item { SideEntry("★ Favoritos", catId == FAV && query.isBlank(), "${myFavs.size}") { catId = FAV; query = "" } }
                    item { SideEntry("Recientes", catId == REC && query.isBlank(), "${myRecents.size}") { catId = REC; query = "" } }
                    val list = cats.data ?: emptyList()
                    itemsIndexed(list) { _, c ->
                        SideEntry(c.name, catId == c.id && query.isBlank()) { catId = c.id; query = "" }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SearchField(query, "Buscar en ${title.lowercase()}") { query = it }
            if (query.trim().length >= 2) {
                val (res, retry) = rememberLoad(kind, query, fv) {
                    delay(450)
                    repo.search(kind, query)
                }
                when {
                    res.loading -> Loading("Buscando en todo el catálogo… (la primera vez tarda un poco)")
                    res.error != null -> ErrorBox(res.error, retry)
                    res.data.isNullOrEmpty() -> EmptyBox("Sin resultados para «$query»")
                    else -> ItemsView(kind, res.data, open)
                }
            } else when (val c = catId) {
                null -> EmptyBox("Elige una categoría")
                FAV -> if (myFavs.isEmpty()) EmptyBox("Aún no hay favoritos aquí.\nToca la ☆ de cualquier elemento para agregarlo.")
                else ItemsView(kind, myFavs, open)
                REC -> if (myRecents.isEmpty()) EmptyBox("Todavía no has visto nada.") else ItemsView(kind, myRecents, open)
                else -> {
                    val (res, retry) = rememberLoad(kind, c, fv) { repo.streams(kind, c) }
                    when {
                        res.loading -> Loading()
                        res.error != null -> ErrorBox(res.error, retry)
                        res.data.isNullOrEmpty() -> EmptyBox("Esta categoría está vacía")
                        else -> ItemsView(kind, res.data, open)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(30.dp)) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange(""); focus.clearFocus() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Clear, "Borrar", modifier = Modifier.size(30.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 64.dp),
    )
}

@Composable
private fun SeriesDetail(series: Item, onBack: () -> Unit) {
    val repo = RutaApp.repo
    val context = LocalContext.current
    val favorites by repo.favorites.collectAsState()
    val (info, retry) = rememberLoad(series.id) { repo.seriesInfo(series.id) }
    var season by rememberSaveable(series.id) { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", modifier = Modifier.size(40.dp))
            }
            Logo(info.data?.cover ?: series.icon, series.name, Modifier.size(width = 72.dp, height = 104.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(series.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                info.data?.plot?.let { Text(it, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
            }
            StarButton(favorites.any { it.key == series.key }) { repo.toggleFavorite(series) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            info.loading -> Loading("Cargando temporadas…")
            info.error != null -> ErrorBox(info.error, retry)
            info.data == null || info.data.seasons.isEmpty() -> EmptyBox("Esta serie no tiene episodios disponibles")
            else -> {
                val seasons = info.data.seasons
                val current = if (season in seasons) season else seasons.keys.first()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    itemsIndexed(seasons.keys.toList()) { _, s ->
                        FilterChip(
                            selected = s == current, onClick = { season = s },
                            label = { Text("Temporada $s", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(10.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Color(0xFF1A0E05)),
                        )
                    }
                }
                val eps: List<Episode> = seasons[current] ?: emptyList()
                val queue = eps.map { e ->
                    Item(Kind.EPISODE, e.id, "${series.name} · T${e.season}E${e.number} ${e.title}", e.image ?: series.icon, e.ext)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(eps) { i, e ->
                        val started = repo.position(queue[i]) > 10_000
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Panel)
                                .clickable { PlayQueue.start(context, queue, i) }
                                .heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${e.number}", color = Orange, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (started) Text("Continuar donde lo dejaste", color = Orange, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen() {
    val repo = RutaApp.repo
    val favorites by repo.favorites.collectAsState()
    val profile by repo.active.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var seriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var seriesIcon by rememberSaveable { mutableStateOf<String?>(null) }
    val open = rememberOpener { s -> seriesKey = s.id; seriesName = s.name; seriesIcon = s.icon }

    val sid = seriesKey
    if (sid != null) {
        BackHandler { seriesKey = null }
        SeriesDetail(Item(Kind.SERIES, sid, seriesName, seriesIcon)) { seriesKey = null }
        return
    }
    val kinds = listOf(Kind.LIVE to "En vivo", Kind.MOVIE to "Películas", Kind.SERIES to "Series")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Favoritos", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Text(profile?.name ?: "", color = Muted, style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            itemsIndexed(kinds) { i, (k, label) ->
                FilterChip(
                    selected = tab == i, onClick = { tab = i },
                    label = { Text("$label (${favorites.count { it.kind == k || (k == Kind.SERIES && it.kind == Kind.EPISODE) }})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Color(0xFF1A0E05)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val kind = kinds[tab].first
        val list = favorites.filter { it.kind == kind || (kind == Kind.SERIES && it.kind == Kind.EPISODE) }
        if (list.isEmpty()) EmptyBox("No hay favoritos en esta sección.\nLos de VivaPlayer se importan desde Ajustes.")
        else ItemsView(kind, list, open)
    }
}

@Composable
private fun SettingsScreen(onSwitchProfile: () -> Unit, onSetup: () -> Unit) {
    val repo = RutaApp.repo
    val scope = rememberCoroutineScope()
    val profile by repo.active.collectAsState()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf("") }
    var format by remember { mutableStateOf(repo.prefs.liveFormat) }
    var confirmDelete by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val p = profile ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
            Text("Perfil: ${p.name}", style = MaterialTheme.typography.titleLarge)
            Text("Servidor: ${p.account.server}", color = Muted)
            Text("Usuario: ${p.account.username}", color = Muted)
            Text(if (p.vivaProfileId.isNotEmpty()) "Importado de VivaPlayer" else "Perfil manual", color = Muted)
        }
        if (status.isNotEmpty()) Text(status, color = Orange, style = MaterialTheme.typography.bodyLarge)

        UpdateCard()

        var crash by remember { mutableStateOf(repo.prefs.lastCrash) }
        if (crash.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                Text("La app se cerró la última vez", style = MaterialTheme.typography.titleLarge)
                Text("Copia el detalle y pégalo en el chat para corregirlo.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(crash.lines().take(4).joinToString("\n"), color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Copiar detalle del error") {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(crash))
                        status = "Detalle del error copiado."
                    }
                    OutlinedButton(onClick = { repo.prefs.lastCrash = ""; crash = "" }, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text("Borrar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Cambiar de perfil", onClick = onSwitchProfile)
            BigButton("Probar conexión", enabled = !busy) {
                busy = true; status = "Probando…"
                scope.launch {
                    status = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.xtream().login() }
                    } catch (e: Exception) { "Error: ${friendlyError(e)}" }
                    busy = false
                }
            }
            BigButton("Recargar catálogo") { repo.clearCache(); status = "Catálogo borrado de la memoria; se volverá a descargar." }
        }

        if (p.vivaProfileId.isNotEmpty()) {
            Text("Favoritos de VivaPlayer", style = MaterialTheme.typography.titleLarge)
            Text(
                "VivaPlayer no permite guardar favoritos desde otra app. Aquí se copian los de este perfil; " +
                    "las estrellas que marques en RutaTV quedan solo en este aparato.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
            val saved = repo.prefs.vivaToken
            if (saved.isEmpty()) {
                OutlinedTextField(
                    token, { token = it.trim() }, label = { Text("Token de API (no se guarda)") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(0.7f), textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Actualizar favoritos", enabled = !busy && (saved.isNotEmpty() || token.isNotBlank())) {
                    busy = true; status = "Actualizando favoritos…"
                    scope.launch {
                        val (msg, details) = refreshVivaFavorites(saved.ifEmpty { token }, p)
                        status = msg; raw = details
                        busy = false
                    }
                }
                if (raw.isNotEmpty()) OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(raw))
                    status = "Detalles copiados (contraseñas y token ocultos). Pégalos en el chat."
                }, modifier = Modifier.heightIn(min = 64.dp)) { Text("Copiar detalles", style = MaterialTheme.typography.labelLarge) }
                if (saved.isNotEmpty()) OutlinedButton(onClick = { repo.prefs.vivaToken = ""; status = "Token olvidado." }, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text("Olvidar token", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Text("Contenido que esta pantalla no reproduce", style = MaterialTheme.typography.titleLarge)
        var hide4k by remember { mutableStateOf(repo.prefs.hide4k) }
        var badCount by remember { mutableIntStateOf(repo.prefs.badKeyCount()) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                .clickable { hide4k = !hide4k; repo.setHide4k(hide4k) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ocultar 4K / UHD / HEVC (H.265)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No se muestran en listas, búsqueda ni widgets los canales, películas y categorías con 4K, UHD, 2160p, " +
                        "HEVC o H.265 en el nombre, ni los que ya fallaron en esta pantalla.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            androidx.compose.material3.Switch(checked = hide4k, onCheckedChange = { hide4k = it; repo.setHide4k(it) })
        }
        if (badCount > 0) OutlinedButton(onClick = { repo.resetUnplayable(); badCount = 0 }, modifier = Modifier.heightIn(min = 64.dp)) {
            Text("Volver a mostrar los $badCount que fallaron", style = MaterialTheme.typography.labelLarge)
        }

        Text("Formato de los canales en vivo", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("ts" to "MPEG-TS (recomendado)", "m3u8" to "HLS (m3u8)").forEach { (v, label) ->
                FilterChip(
                    selected = format == v, onClick = { format = v; repo.prefs.liveFormat = v },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Color(0xFF1A0E05)),
                )
            }
        }

        Text("Perfiles", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Agregar o importar perfiles", onClick = onSetup)
            OutlinedButton(
                onClick = { if (confirmDelete) repo.deleteProfile(p) else confirmDelete = true },
                modifier = Modifier.heightIn(min = 64.dp),
            ) {
                Text(if (confirmDelete) "Toca otra vez para eliminar «${p.name}»" else "Eliminar este perfil", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text("RutaTV ${com.rutatv.BuildConfig.VERSION_NAME} · para Android 5.1 o superior", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Versión instalada: ${com.rutatv.BuildConfig.VERSION_NAME}") }
    var release by remember { mutableStateOf<com.rutatv.update.Updater.Release?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun check() {
        busy = true; text = "Buscando actualización…"
        scope.launch {
            try {
                val r = com.rutatv.update.Updater.check(context)
                release = r
                text = if (r.isNewer) "Hay una versión nueva: ${r.versionName}" + (if (r.notes.isNotBlank()) "\n${r.notes}" else "")
                else "Ya tienes la última versión (${com.rutatv.BuildConfig.VERSION_NAME})."
            } catch (e: Exception) {
                text = "No se pudo buscar: ${friendlyError(e)}"
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { check() }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
        Text("Actualizaciones", style = MaterialTheme.typography.titleLarge)
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Buscar actualización", enabled = !busy) { check() }
            val r = release
            if (r != null && r.isNewer) {
                BigButton(if (file != null) "Instalar" else "Descargar e instalar", enabled = !busy) {
                    val f = file
                    if (f != null) {
                        if (!com.rutatv.update.Updater.install(context, f)) text = "Permite «instalar apps desconocidas» para RutaTV y vuelve a tocar Instalar."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val d = com.rutatv.update.Updater.download(context, r) { pct ->
                                    text = if (pct >= 0) "Descargando… $pct%" else "Descargando…"
                                }
                                file = d
                                text = "Descarga lista."
                                if (!com.rutatv.update.Updater.install(context, d)) text = "Permite «instalar apps desconocidas» para RutaTV y vuelve a tocar Instalar."
                            } catch (e: Exception) {
                                text = "Error al descargar: ${friendlyError(e)}"
                            }
                            busy = false
                        }
                    }
                }
            }
        }
    }
}
