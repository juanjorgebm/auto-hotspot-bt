package com.rutatv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rutatv.data.Item
import kotlinx.coroutines.CancellationException

/** Estado de una carga asíncrona. */
class LoadState<T>(val data: T?, val error: String?, val loading: Boolean)

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Pair<LoadState<T>, () -> Unit> {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState(LoadState<T>(null, null, true), *keys, attempt) {
        value = LoadState(null, null, true)
        value = try {
            LoadState(block(), null, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState(null, friendlyError(e), false)
        }
    }
    return state to { attempt++ }
}

fun friendlyError(e: Throwable): String {
    val m = e.message ?: e.javaClass.simpleName
    return when {
        e is java.net.UnknownHostException -> "Sin internet o el servidor no existe ($m)"
        e is java.net.SocketTimeoutException -> "El servidor tardó demasiado en responder"
        e is javax.net.ssl.SSLException -> "Error de conexión segura (SSL): $m"
        else -> m
    }
}

@Composable
fun Loading(text: String = "Cargando…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Orange, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(text, color = Muted)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudo cargar", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            BigButton("Reintentar", onClick = onRetry)
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun StarButton(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(
            if (active) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = "Favorito",
            tint = if (active) Orange else Muted,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
fun Logo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0E12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(2).uppercase(),
            color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

/** Fila grande para un canal en vivo. */
@Composable
fun ChannelRow(index: Int, item: Item, favorite: Boolean, onClick: () -> Unit, onStar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .heightIn(min = 84.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$index", color = Muted, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        Logo(item.icon, item.name, Modifier.size(width = 96.dp, height = 64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.fromViva) Text("Favorito de VivaPlayer", color = Orange, style = MaterialTheme.typography.bodySmall)
        }
        StarButton(favorite, onStar)
    }
}

/** Carátula de película o serie. */
@Composable
fun PosterCard(item: Item, favorite: Boolean, onClick: () -> Unit, onStar: () -> Unit) {
    Column(
        Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0xFF0B0E12))) {
            Text(
                item.name, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(8.dp), maxLines = 4,
            )
            if (!item.icon.isNullOrBlank()) {
                AsyncImage(
                    model = item.icon, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .clip(RoundedCornerShape(32.dp)).background(Color(0x99000000))
            ) { StarButton(favorite, onStar) }
            if (item.rating != null) {
                Text(
                    "★ ${item.rating}",
                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .fillMaxWidth().padding(8.dp),
                )
            }
        }
        Text(
            item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp).heightIn(min = 48.dp),
        )
    }
}

@Composable
fun SideEntry(text: String, selected: Boolean, count: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Orange.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, Orange, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (selected) Orange else Color.White, modifier = Modifier.weight(1f),
        )
        if (count != null) Text(count, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
