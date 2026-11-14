package com.rutatv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutatv.data.Profile

private val avatarColors = listOf(Color(0xFFFF8A3D), Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFBA68C8), Color(0xFFFFD54F))

@Composable
fun ProfileAvatar(p: Profile, index: Int, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(avatarColors[index % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.name.trim().take(1).uppercase(), color = Color(0xFF10141A),
            fontSize = (size / 2.2).sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ProfilePicker(profiles: List<Profile>, last: Profile?, onPick: (Profile) -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¿Quién va a ver?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(28.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(profiles.size) { i ->
                val p = profiles[i]
                val isLast = p.id == last?.id
                Column(
                    Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel)
                        .then(if (isLast) Modifier.border(3.dp, Orange, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable { onPick(p) }
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileAvatar(p, i, 110)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                    if (p.vivaProfileId.isNotEmpty()) Text("VivaPlayer", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(
                    Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel)
                        .clickable(onClick = onAdd)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(110.dp).clip(CircleShape).background(PanelHi), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = Muted, modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Agregar / importar", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
        }
        if (last != null) {
            Spacer(Modifier.height(32.dp))
            Row {
                BigButton("Continuar como ${last.name}", Modifier.width(420.dp)) { onPick(last) }
            }
        }
    }
}
