package com.rutamusica.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rutamusica.player.YouTubeBrowser

/** Modo YouTube: la página de YouTube a pantalla completa, con una barra lateral pequeña. */
@Composable
fun YouTubeScreen(vm: MusicViewModel, browser: YouTubeBrowser) {
    LaunchedEffect(Unit) { browser.start() }
    Row(Modifier.fillMaxSize().background(Color.Black)) {
        if (!browser.fullscreen) {
            Column(
                Modifier.width(96.dp).fillMaxHeight().background(Bg).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SideButton("Música", Icons.Filled.MusicNote, Accent) { vm.setYouTubeMode(false) }
                Spacer(Modifier.height(8.dp))
                SideButton("Atrás", Icons.AutoMirrored.Filled.ArrowBack, if (browser.canGoBack) Color.White else Muted) {
                    if (browser.canGoBack) browser.back()
                }
                SideButton("Inicio", Icons.Filled.Home) { browser.home() }
                SideButton("Suscrip.", Icons.Filled.Subscriptions) { browser.subscriptions() }
                SideButton("Biblioteca", Icons.Filled.VideoLibrary) { browser.library() }
            }
        }
        AndroidView(
            factory = {
                (browser.view.parent as? ViewGroup)?.removeView(browser.view)
                browser.view
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SideButton(text: String, icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, text, tint = tint, modifier = Modifier.size(34.dp))
        Text(text, color = tint, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
