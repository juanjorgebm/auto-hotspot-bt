package com.rutamusica.ui

import android.webkit.WebSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rutamusica.BuildConfig
import com.rutamusica.update.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(vm: MusicViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateText by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(vm.prefs.lastCrash) }
    val webview = remember {
        runCatching {
            Regex("""Chrome/(\d+)""").find(WebSettings.getDefaultUserAgent(context))?.groupValues?.get(1)
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ajustes") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Cuenta de YouTube: ${vm.channelName.ifBlank { "conectada" }}")
                SoftButton("Recargar mis listas", Modifier.fillMaxWidth()) { vm.loadLibrary(); onClose() }
                SoftButton("Cerrar sesión", Modifier.fillMaxWidth()) { vm.signOut(); onClose() }
                SoftButton("Cambiar la llave de Google", Modifier.fillMaxWidth()) { vm.changeKey(); onClose() }
                HorizontalDivider()

                Text("Versión ${BuildConfig.VERSION_NAME}")
                val r = release
                if (r != null && r.isNewer) {
                    BigButton("Instalar versión ${r.versionName}", Modifier.fillMaxWidth(), enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val f = Updater.download(context, r) { p ->
                                    updateText = if (p >= 0) "Descargando… $p%" else "Descargando…"
                                }
                                updateText = "Abriendo el instalador…"
                                if (!Updater.install(context, f)) updateText = "Da permiso para instalar y toca otra vez."
                            } catch (e: Exception) {
                                updateText = "No se pudo descargar: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                } else {
                    SoftButton("Buscar actualización", Modifier.fillMaxWidth()) {
                        if (busy) return@SoftButton
                        busy = true
                        updateText = "Buscando…"
                        scope.launch {
                            try {
                                val rel = Updater.check(context)
                                release = rel
                                updateText = if (rel.isNewer) "Hay una versión nueva: ${rel.versionName}. ${rel.notes}"
                                else "Ya tienes la última versión."
                            } catch (e: Exception) {
                                updateText = "No se pudo buscar: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                updateText?.let { Text(it, color = Muted) }
                HorizontalDivider()

                Text(
                    "Navegador interno (WebView): " + (webview?.let { "versión $it" } ?: "desconocido") +
                        (if ((webview?.toIntOrNull() ?: 999) < 70) ". Es muy viejo: si el video no carga, actualiza \"Android System WebView\"." else ""),
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "La música se pausa si sales de la app: YouTube no permite que su reproductor suene escondido.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                if (crash.isNotBlank()) {
                    HorizontalDivider()
                    Text("Último cierre inesperado:", color = MaterialTheme.colorScheme.error)
                    Text(crash.take(1500), style = MaterialTheme.typography.bodySmall, color = Muted)
                    TextButton(onClick = { vm.prefs.lastCrash = ""; crash = "" }) { Text("Borrar") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}
