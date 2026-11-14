package com.rutamusica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchPane(vm: MusicViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    val doSearch = {
        keyboard?.hide()
        vm.search()
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Canción, artista o video") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier.weight(1f),
            )
            BigButton("Buscar", enabled = vm.searchQuery.isNotBlank() && !vm.searchLoading) { doSearch() }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = vm.musicOnly,
                onClick = { vm.setMusicOnlyFilter(!vm.musicOnly) },
                label = { Text(if (vm.musicOnly) "Solo música" else "Todos los videos") },
            )
        }
        when {
            vm.searchLoading -> Loading("Buscando en YouTube…")
            vm.searchError != null -> MessageBox("Sin resultados", vm.searchError)
            vm.searchResults.isEmpty() -> MessageBox(
                "Buscar en YouTube",
                "Escribe el nombre de una canción, artista o video. Cada búsqueda gasta parte del cupo gratis " +
                    "de tu llave (alcanza para unas 90 búsquedas al día).",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(vm.searchResults, key = { i, t -> "s$i-${t.videoId}" }) { i, t ->
                    TrackRow(t, t.videoId == vm.current?.videoId) { vm.playFrom(vm.searchResults, i, forceShuffle = false) }
                }
            }
        }
    }
}
