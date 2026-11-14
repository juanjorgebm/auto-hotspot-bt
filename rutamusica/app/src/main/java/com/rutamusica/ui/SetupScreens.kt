package com.rutamusica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paso 1: la llave de Google Cloud (se hace una sola vez). */
@Composable
fun KeyScreen(vm: MusicViewModel) {
    var id by remember { mutableStateOf(vm.prefs.clientId) }
    var secret by remember { mutableStateOf(vm.prefs.clientSecret) }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RutaMúsica", style = MaterialTheme.typography.headlineMedium, color = Accent)
            Text("Paso 1 de 2: llave de Google", style = MaterialTheme.typography.titleLarge)
            Text(
                "Para leer tus listas de YouTube, Google pide una llave gratis que se crea una sola vez " +
                    "en console.cloud.google.com (te mandé la guía paso a paso en el chat).",
                color = Muted,
            )
            Text(
                "La llave debe ser del tipo \"TV y dispositivos de entrada limitada\" y el proyecto debe tener " +
                    "activada la \"YouTube Data API v3\".",
                color = Muted,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = id, onValueChange = { id = it.trim() },
                label = { Text("ID de cliente") },
                placeholder = { Text("…apps.googleusercontent.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret, onValueChange = { secret = it.trim() },
                label = { Text("Secreto del cliente") },
                placeholder = { Text("GOCSPX-…") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (id.isNotBlank() && !id.endsWith(".apps.googleusercontent.com")) {
                Text("El ID de cliente normalmente termina en .apps.googleusercontent.com", color = MaterialTheme.colorScheme.error)
            }
            BigButton("Guardar y seguir", Modifier.fillMaxWidth(), enabled = id.isNotBlank() && secret.isNotBlank()) {
                vm.saveKey(id, secret)
            }
        }
    }
}

/** Paso 2: conectar la cuenta escribiendo un código en el celular. */
@Composable
fun LoginScreen(vm: MusicViewModel) {
    val dc = vm.deviceCode
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("RutaMúsica", style = MaterialTheme.typography.headlineMedium, color = Accent)
        Text("Paso 2 de 2: conectar tu cuenta de YouTube", style = MaterialTheme.typography.titleLarge)
        if (dc == null) {
            Text(
                "Vas a necesitar tu celular con internet. La app solo puede LEER tus listas: no publica ni borra nada.",
                color = Muted,
            )
            vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton("Conectar mi cuenta") { vm.startLogin() }
            SoftButton("Cambiar la llave de Google") { vm.changeKey() }
        } else {
            Text("1. En el celular abre esta página:", style = MaterialTheme.typography.bodyLarge)
            Text(
                dc.verificationUrl.removePrefix("https://").removePrefix("www."),
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Accent,
            )
            Text("2. Escribe este código:", style = MaterialTheme.typography.bodyLarge)
            Text(dc.userCode, fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text(
                "3. Elige tu cuenta de YouTube y toca \"Permitir\". Si sale \"Google no verificó esta app\", " +
                    "toca \"Avanzado\" y luego \"Ir a…\" (la app es tuya).",
                color = Muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Accent)
                Spacer(Modifier.width(12.dp))
                Text("Esperando que confirmes en el celular…", color = Muted)
            }
            SoftButton("Cancelar") { vm.cancelLogin() }
        }
        Spacer(Modifier.height(8.dp))
    }
}
