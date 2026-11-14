package com.rutatv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutatv.RutaApp
import com.rutatv.data.Profile
import com.rutatv.data.VivaApi
import com.rutatv.data.XtreamAccount
import com.rutatv.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Configuración inicial. Dos caminos:
 *  - VivaPlayer: token de revendedor + usuario de cliente → importa SOLO los perfiles y favoritos de ese usuario.
 *  - Manual: servidor, usuario y contraseña Xtream de un perfil.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, onCancel: (() -> Unit)?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Configurar RutaTV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancelar", style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab, containerColor = Bg) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.height(64.dp)) {
                Text("Importar de VivaPlayer", style = MaterialTheme.typography.labelLarge)
            }
            Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.height(64.dp)) {
                Text("Perfil manual (Xtream)", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (tab == 0) VivaImport(onDone) else ManualSetup(onDone)
    }
}

@Composable
private fun VivaImport(onDone: () -> Unit) {
    val repo = RutaApp.repo
    val scope = rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf(repo.prefs.vivaToken) }
    var username by rememberSaveable { mutableStateOf(repo.prefs.vivaUsername) }
    var rememberToken by rememberSaveable { mutableStateOf(repo.prefs.vivaToken.isNotEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var vivaRef by remember { mutableStateOf<VivaApi?>(null) }
    var xServer by rememberSaveable { mutableStateOf("") }
    var xUser by rememberSaveable { mutableStateOf("") }
    var xPass by rememberSaveable { mutableStateOf("") }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Pega el token de la API de revendedor y escribe tu usuario de cliente. " +
                    "Solo se lee ese usuario: sus perfiles, servidores y favoritos. Ningún otro cliente se muestra ni se guarda.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it.trim() }, singleLine = true,
                label = { Text("Usuario de cliente") }, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token, onValueChange = { token = it.trim() }, singleLine = true,
                label = { Text("Token de API (viva_…)") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { rememberToken = !rememberToken }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberToken, onCheckedChange = { rememberToken = it })
                Text("Recordar el token en este aparato (para actualizar favoritos después)", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(if (busy) "Importando…" else "Importar perfiles", enabled = !busy && token.isNotBlank() && username.isNotBlank()) {
                    busy = true; finished = false; log.clear(); raw = ""; result = null
                    val viva = VivaApi(token, repo.http)
                    vivaRef = viva
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.IO) { importFromViva(viva, username) { line -> scope.launch { log.add(line) } } }
                            repo.prefs.vivaUsername = username
                            repo.prefs.vivaToken = if (rememberToken) token else ""
                            result = r
                            if (r.pending.isNotEmpty()) {
                                xServer = r.server; xUser = r.username
                            } else log.add("Listo: ${r.saved} perfil(es) importado(s).")
                            finished = r.saved > 0
                        } catch (e: Exception) {
                            log.add("Error: ${friendlyError(e)}")
                        } finally {
                            raw = viva.lastRaw
                            busy = false
                        }
                    }
                }
                if (finished) BigButton("Elegir perfil", onClick = onDone)
            }
            val r = result
            val viva = vivaRef
            if (r != null && viva != null && r.pending.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Contraseña de la cuenta Xtream", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "VivaPlayer no entrega la contraseña por la API. Escríbela una vez (es la de tu línea IPTV); " +
                            "se usará para: ${r.pending.joinToString(", ") { it.label }}. Se guarda solo en este aparato.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(xServer, { xServer = it.trim() }, label = { Text("Servidor") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(xUser, { xUser = it.trim() }, label = { Text("Usuario Xtream") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(xPass, { xPass = it.trim() }, label = { Text("Contraseña Xtream") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    BigButton(if (busy) "Probando…" else "Guardar perfiles", enabled = !busy && xServer.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()) {
                        busy = true
                        scope.launch {
                            val acc = XtreamAccount(XtreamApi.normalizeServer(xServer), xUser, xPass)
                            try {
                                val info = withContext(Dispatchers.IO) { XtreamApi(acc, repo.http).login() }
                                log.add("Cuenta Xtream correcta. $info")
                                withContext(Dispatchers.IO) {
                                    r.pending.forEach { p -> saveVivaProfile(viva, r.customerId, p, acc) { line -> scope.launch { log.add(line) } } }
                                }
                                log.add("Listo: ${r.saved + r.pending.size} perfil(es) importado(s).")
                                result = ImportResult(r.saved + r.pending.size, r.customerId, emptyList(), "", "")
                                finished = true
                            } catch (e: Exception) {
                                log.add("No se pudo entrar con esa contraseña: ${friendlyError(e)}")
                            } finally {
                                raw = viva.lastRaw
                                busy = false
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(
            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Progreso", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (log.isEmpty()) Text("Aún no se ha importado nada.", color = Muted)
            log.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
            if (raw.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Ocultar detalles técnicos" else "Ver detalles técnicos")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(raw))
                        log.add("Detalles copiados (las contraseñas y el token van ocultos). Pégalos en el chat.")
                    }) { Text("Copiar detalles") }
                }
                if (showRaw) SelectionContainer {
                    Text(raw, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

/** Resultado de la importación: perfiles guardados y los que esperan la contraseña Xtream. */
class ImportResult(
    val saved: Int,
    val customerId: String,
    val pending: List<VivaApi.Pick>,
    val server: String,
    val username: String,
)

/** Importa los perfiles del usuario indicado. */
private suspend fun importFromViva(viva: VivaApi, username: String, log: (String) -> Unit): ImportResult {
    val repo = RutaApp.repo
    log("Buscando el usuario $username…")
    val customer = viva.findCustomer(username)
        ?: throw IllegalStateException("No se encontró el usuario \"$username\" en tu cuenta de revendedor.")
    repo.prefs.vivaCustomerId = customer.id
    log("Usuario encontrado.")

    var picks = viva.profiles(customer.id, customer.obj)
    log("Perfiles encontrados: ${picks.size}")
    if (picks.isEmpty()) {
        picks = listOf(VivaApi.Pick(customer.id, username, "", customer.obj))
    }
    var saved = 0
    val pending = ArrayList<VivaApi.Pick>()
    var server = ""
    var user = ""
    for (p in picks) {
        val res = try {
            viva.accountFromProfile(p.obj, customer.obj)
        } catch (e: Exception) {
            VivaApi.AccountResult(null, friendlyError(e))
        }
        val acc = res.account
        if (acc == null || !acc.isComplete) {
            // Los perfiles de VivaPlayer (Juan Jorge, Gerald…) comparten la cuenta Xtream del cliente;
            // la API no devuelve la contraseña, así que se pide una vez.
            pending.add(p)
            if (server.isEmpty()) server = res.server ?: ""
            if (user.isEmpty()) user = res.username ?: ""
            continue
        }
        saveVivaProfile(viva, customer.id, p, acc, log)
        saved++
    }
    if (pending.isNotEmpty()) log("Falta la contraseña Xtream para ${pending.size} perfil(es). Escríbela abajo una sola vez.")
    return ImportResult(saved, customer.id, pending, server, user)
}

private suspend fun saveVivaProfile(viva: VivaApi, customerId: String, p: VivaApi.Pick, acc: XtreamAccount, log: (String) -> Unit) {
    val repo = RutaApp.repo
    val profile = Profile(id = "viva_${p.id}", name = p.label, account = acc, vivaProfileId = p.id)
    repo.upsertProfile(profile)
    try {
        val favs = viva.favorites(customerId, p.id)
        val n = repo.importVivaFavorites(profile, favs)
        log("${p.label}: perfil guardado, $n favorito(s).")
    } catch (e: Exception) {
        log("${p.label}: perfil guardado; no se pudieron leer los favoritos (${friendlyError(e)})")
    }
}

/** Pantalla para actualizar favoritos de un perfil que ya existe (desde Ajustes). */
suspend fun refreshVivaFavorites(token: String, profile: Profile): String {
    val repo = RutaApp.repo
    val viva = VivaApi(token, repo.http)
    var customerId = repo.prefs.vivaCustomerId
    if (customerId.isEmpty()) {
        customerId = withContext(Dispatchers.IO) { viva.findCustomer(repo.prefs.vivaUsername) }?.id
            ?: return "No se encontró el usuario de cliente."
        repo.prefs.vivaCustomerId = customerId
    }
    val favs = withContext(Dispatchers.IO) { viva.favorites(customerId, profile.vivaProfileId.ifBlank { null }) }
    val n = repo.importVivaFavorites(profile, favs)
    return "$n favorito(s) actualizados desde VivaPlayer."
}

@Composable
private fun ManualSetup(onDone: () -> Unit) {
    val repo = RutaApp.repo
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(0.7f).verticalScroll(rememberScrollState())) {
        Text(
            "También puedes pegar en «Servidor» un enlace M3U completo (get.php?username=…&password=…) y se rellena solo.",
            color = Muted, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del perfil") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(server, { v ->
            val parsed = if (v.contains("username=")) XtreamApi.parsePlaylistUrl(v) else null
            if (parsed != null) { server = parsed.server; user = parsed.username; pass = parsed.password } else server = v.trim()
        }, label = { Text("Servidor (http://dominio:puerto)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(user, { user = it.trim() }, label = { Text("Usuario") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(pass, { pass = it.trim() }, label = { Text("Contraseña") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        BigButton(if (busy) "Probando…" else "Guardar perfil", enabled = !busy && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            busy = true; status = ""
            scope.launch {
                val acc = XtreamAccount(XtreamApi.normalizeServer(server), user, pass)
                try {
                    val info = withContext(Dispatchers.IO) { XtreamApi(acc, repo.http).login() }
                    repo.upsertProfile(Profile(UUID.randomUUID().toString(), name.ifBlank { user }, acc))
                    status = "Conectado. $info"
                    onDone()
                } catch (e: Exception) {
                    status = "No se pudo conectar: ${friendlyError(e)}"
                } finally {
                    busy = false
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
