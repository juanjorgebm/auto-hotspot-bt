package com.rutamusica.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.rutamusica.data.Playlist
import com.rutamusica.data.Track

@Composable
fun App(vm: MusicViewModel, playerView: WebView) {
    when (vm.stage) {
        Stage.Key -> KeyScreen(vm)
        Stage.Login -> LoginScreen(vm)
        Stage.Ready -> MainScreen(vm, playerView)
    }
}

@Composable
fun MainScreen(vm: MusicViewModel, playerView: WebView) {
    var showSettings by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PlayerPane(vm, playerView, Modifier.weight(0.42f).fillMaxHeight())
        Column(Modifier.weight(0.58f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabButton("Letra", Icons.Filled.Mic, vm.tab == Tab.Lyrics) { vm.tab = Tab.Lyrics }
                Spacer(Modifier.width(8.dp))
                TabButton("Mis listas", Icons.Filled.LibraryMusic, vm.tab == Tab.Library) { vm.tab = Tab.Library }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Settings, "Ajustes", tint = Muted, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Panel)
            ) {
                when (vm.tab) {
                    Tab.Lyrics -> LyricsPane(vm)
                    Tab.Library -> LibraryPane(vm)
                }
            }
        }
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun TabButton(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (selected) Accent else PanelHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

// ------------------------------------------------------------------ Reproductor

@Composable
private fun PlayerPane(vm: MusicViewModel, playerView: WebView, modifier: Modifier) {
    val track = vm.current
    Column(modifier) {
        // El video de YouTube siempre visible (regla de YouTube: no se puede esconder ni tapar).
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    (playerView.parent as? ViewGroup)?.removeView(playerView)
                    playerView
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (track == null) {
                Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, null, tint = Accent, modifier = Modifier.size(56.dp))
                        Text("Elige una canción en \"Mis listas\"", color = Muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (track != null) {
            val g = remember(track) { vm.guessFor(track) }
            Text(
                g.title.ifBlank { track.title }, style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(g.artist.ifBlank { track.channel }, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        vm.playerMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
        Spacer(Modifier.weight(1f))
        SeekBar(vm)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Filled.Shuffle, "Aleatorio", 60, if (vm.shuffle) Accent else Muted, PanelHi.copy(alpha = 0f)) { vm.toggleShuffle() }
            CircleButton(Icons.Filled.SkipPrevious, "Anterior", 68) { vm.previous() }
            CircleButton(
                if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Reproducir o pausar", 84, Color.White, Accent,
            ) { vm.playPause() }
            CircleButton(Icons.Filled.SkipNext, "Siguiente", 68) { vm.next() }
            Spacer(Modifier.size(60.dp))
        }
    }
}

@Composable
private fun SeekBar(vm: MusicViewModel) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = vm.duration
    val pos = if (dragging) dragValue.toDouble() else vm.position
    Column {
        Slider(
            value = if (dur > 0) pos.toFloat().coerceIn(0f, dur.toFloat()) else 0f,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo(dragValue.toDouble()); dragging = false },
            valueRange = 0f..(if (dur > 0) dur.toFloat() else 1f),
            enabled = dur > 0,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(pos), color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(dur), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    desc: String,
    sizeDp: Int,
    tint: Color = Color.White,
    bg: Color = PanelHi,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size((sizeDp * 0.55f).dp))
    }
}

// ------------------------------------------------------------------ Biblioteca

@Composable
private fun LibraryPane(vm: MusicViewModel) {
    val open = vm.openPlaylist
    if (open == null) {
        when {
            vm.libLoading -> Loading("Leyendo tus listas de YouTube…")
            vm.libError != null -> MessageBox("No se pudieron leer tus listas", vm.libError) {
                BigButton("Reintentar") { vm.loadLibrary() }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(190.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItemsIndexed(vm.playlists) { _, p -> PlaylistCard(p) { vm.open(p) } }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closePlaylist() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", modifier = Modifier.size(32.dp))
                }
                Text(
                    open.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (vm.tracks.isNotEmpty()) {
                    CircleButton(Icons.Filled.PlayArrow, "Reproducir todo", 56, Color.White, Accent) {
                        vm.playFrom(vm.tracks, 0, forceShuffle = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    CircleButton(Icons.Filled.Shuffle, "Aleatorio", 56) {
                        vm.playFrom(vm.tracks, vm.tracks.indices.random(), forceShuffle = true)
                    }
                }
            }
            when {
                vm.tracksLoading -> Loading("Abriendo la lista…")
                vm.tracksError != null -> MessageBox("No se pudo abrir la lista", vm.tracksError) {
                    BigButton("Reintentar") { vm.open(open) }
                }
                vm.tracks.isEmpty() -> MessageBox("Esta lista está vacía")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                    itemsIndexed(vm.tracks, key = { i, t -> "$i-${t.videoId}" }) { i, t ->
                        TrackRow(t, t.videoId == vm.current?.videoId) { vm.playFrom(vm.tracks, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(p: Playlist, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(PanelHi).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Bg), contentAlignment = Alignment.Center) {
            if (p.special) {
                Icon(Icons.Filled.Favorite, null, tint = Accent, modifier = Modifier.size(56.dp))
            } else if (p.thumb != null) {
                AsyncImage(p.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.LibraryMusic, null, tint = Muted, modifier = Modifier.size(48.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!p.special) Text("${p.count} canciones", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (playing) Accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Bg)) {
            if (t.thumb != null) AsyncImage(t.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                color = if (playing) Accent else Color.White,
            )
            Text(t.channel.removeSuffix(" - Topic"), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
