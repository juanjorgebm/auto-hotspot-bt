package com.rutamusica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rutamusica.data.Track

/**
 * Modo Videos: pantalla nativa con filas de portadas grandes (tipo Netflix).
 * Al tocar un video se abre a pantalla grande con el reproductor oficial de YouTube.
 */
@Composable
fun VideosScreen(vm: MusicViewModel) {
    var query by remember { mutableStateOf("") }
    // El cuadro de búsqueda solo aparece al tocar la lupa (así el teclado no sale solo al entrar).
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val doSearch = { keyboard?.hide(); vm.searchVideos(query) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(28.dp)).background(Accent).clickable { vm.switchYouTubeMode(false) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("Música", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Text("Videos", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.weight(1f))
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar videos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.width(340.dp).focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
                IconButton(onClick = { keyboard?.hide(); searching = false }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Close, "Cerrar búsqueda", tint = Muted, modifier = Modifier.size(30.dp))
                }
            }
            IconButton(onClick = { if (searching && query.isNotBlank()) doSearch() else searching = true }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Search, "Buscar", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { vm.loadVideoHome() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Refresh, "Recargar", tint = Muted, modifier = Modifier.size(30.dp))
            }
        }

        when {
            vm.videoLoading && vm.videoRows.isEmpty() && vm.videoSearchResults.isEmpty() -> Loading("Cargando tus videos…")
            vm.videoRows.isEmpty() && vm.videoSearchResults.isEmpty() ->
                MessageBox("No hay videos para mostrar", vm.videoError) { BigButton("Reintentar") { vm.loadVideoHome() } }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (vm.videoSearchResults.isNotEmpty()) {
                    item(key = "search") {
                        VideoRowView("Resultados de búsqueda", vm.videoSearchResults, onClose = { query = ""; vm.searchVideos("") }) { i ->
                            vm.watch(vm.videoSearchResults, i)
                        }
                    }
                }
                vm.videoRows.forEachIndexed { idx, row ->
                    item(key = "row$idx-${row.title}") {
                        VideoRowView(row.title, row.items) { i -> vm.watch(row.items, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRowView(title: String, items: List<Track>, onClose: (() -> Unit)? = null, onPlay: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = Muted) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { i, t -> "$i-${t.videoId}" }) { i, t -> VideoCard(t) { onPlay(i) } }
        }
    }
}

@Composable
private fun VideoCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(300.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Panel)) {
            if (t.thumb != null) AsyncImage(t.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.channel.removeSuffix(" - Topic"), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
