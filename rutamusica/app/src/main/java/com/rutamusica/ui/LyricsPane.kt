package com.rutamusica.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutamusica.data.LyricLine
import com.rutamusica.data.Lyrics

@Composable
fun LyricsPane(vm: MusicViewModel) {
    var showSearch by remember { mutableStateOf(false) }
    val track = vm.current
    if (track == null) {
        MessageBox("Aquí sale la letra", "Elige una canción en \"Mis listas\" y la letra aparecerá aquí para cantarla.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val st = vm.lyricsState) {
                LyricsState.Idle, LyricsState.Loading -> Loading("Buscando la letra…")
                LyricsState.None -> MessageBox(
                    "No encontré la letra de esta canción",
                    "Puede que el título del video no diga bien el artista o la canción.",
                ) {
                    BigButton("Escribir artista y canción") { showSearch = true }
                    SoftButton("Buscar otra vez") { vm.retryLyrics() }
                }
                is LyricsState.Failed -> MessageBox("No se pudo buscar la letra", st.message) {
                    BigButton("Reintentar") { vm.retryLyrics() }
                }
                is LyricsState.Found -> FoundLyrics(vm, st.lyrics)
            }
        }
        // Barra inferior: ajustar tiempo y corregir canción
        val found = vm.lyricsState as? LyricsState.Found
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (found?.lyrics?.hasSynced == true) {
                IconButton(onClick = { vm.shiftLyrics(-500) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Remove, "Letra antes", tint = Color.White)
                }
                Text(
                    "Tiempo de la letra: " + (if (vm.offsetMs == 0L) "normal" else "%+.1f s".format(vm.offsetMs / 1000.0)),
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { vm.resetOffset() },
                )
                IconButton(onClick = { vm.shiftLyrics(500) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Add, "Letra después", tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            if (found != null) {
                TextButton(onClick = { showSearch = true }) {
                    Icon(Icons.Filled.Edit, null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("¿Letra equivocada?", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showSearch) {
        val g = remember(track) { vm.guessFor(track) }
        var artist by remember { mutableStateOf(g.artist) }
        var title by remember { mutableStateOf(g.title) }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Buscar la letra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, label = { Text("Canción") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    vm.searchLyrics(artist, title)
                    showSearch = false
                }) { Text("Buscar") }
            },
            dismissButton = { TextButton(onClick = { showSearch = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FoundLyrics(vm: MusicViewModel, lyrics: Lyrics) {
    when {
        lyrics.hasSynced -> Karaoke(vm, lyrics.synced, lyrics.matched)
        lyrics.plain.isNotBlank() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(
                "Esta canción solo tiene la letra sin tiempos, así que no avanza sola. Desliza para seguirla.",
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.size(12.dp))
            Text(lyrics.plain, fontSize = 24.sp, lineHeight = 34.sp)
            Spacer(Modifier.size(12.dp))
            Text("Letra: ${lyrics.matched} · LRCLIB", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        lyrics.instrumental -> MessageBox("🎵 Canción instrumental", "Esta canción no tiene letra.")
        else -> MessageBox("No encontré la letra de esta canción")
    }
}

/** Letra sincronizada: la línea actual se resalta y se va "llenando" de color como en un karaoke. */
@Composable
private fun Karaoke(vm: MusicViewModel, lines: List<LyricLine>, matched: String) {
    // Tiempo actual suavizado: el reproductor avisa cada 200 ms y aquí se interpola cuadro a cuadro.
    var nowMs by remember { mutableLongStateOf(0L) }
    val pos = vm.position
    val playing = vm.isPlaying
    LaunchedEffect(pos, playing) {
        val base = (pos * 1000).toLong()
        val start = SystemClock.uptimeMillis()
        nowMs = base
        if (playing) {
            while (true) {
                withFrameMillis { }
                nowMs = base + (SystemClock.uptimeMillis() - start).coerceAtMost(1500)
            }
        }
    }
    val offset = vm.offsetMs
    val current by remember(lines) {
        derivedStateOf {
            val t = nowMs - offset
            var idx = -1
            for (i in lines.indices) { if (lines[i].timeMs <= t) idx = i else break }
            idx
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 220.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val text = line.text.ifBlank { "♪ ♪ ♪" }
            val seek: () -> Unit = { vm.seekTo(((line.timeMs + offset) / 1000.0).coerceAtLeast(0.0)) }
            when {
                i == current -> {
                    val end = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + 5000)
                    Box(Modifier.fillMaxWidth().clickable(onClick = seek)) {
                        Text(text, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = Accent,
                            modifier = Modifier.drawWithContent {
                                val span = (end - line.timeMs).coerceAtLeast(1)
                                val f = ((nowMs - offset - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
                                clipRect(right = size.width * f) { this@drawWithContent.drawContent() }
                            },
                        )
                    }
                }
                i < current -> Text(
                    text, fontSize = 22.sp, lineHeight = 28.sp, color = Muted.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = seek),
                )
                else -> Text(
                    text, fontSize = 26.sp, lineHeight = 33.sp,
                    color = if (i == current + 1) Color.White else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = seek),
                )
            }
        }
        item {
            Text(
                "Letra: $matched · LRCLIB", color = Muted, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
