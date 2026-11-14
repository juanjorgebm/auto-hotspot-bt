package com.rutamusica.ui

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import android.webkit.WebSettings
import com.rutamusica.player.GeckoPlayer
import com.rutamusica.player.PlayerEngine
import com.rutamusica.player.YtPlayer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()
    private lateinit var player: PlayerEngine
    private var session: MediaSessionCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En el carro la pantalla no se debe apagar mientras suena la música.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = when (vm.prefs.engine) {
            "gecko" -> GeckoPlayer(this)
            "system" -> YtPlayer(this)
            // El reproductor de YouTube no funciona con navegadores del sistema muy viejos (la pantalla trae el 39).
            else -> if (systemWebViewVersion(this) >= 70) YtPlayer(this) else GeckoPlayer(this)
        }
        vm.player = player
        setupMediaSession()

        setContent {
            MusicTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) { App(vm, player.view) }
            }
        }
    }

    /** Permite usar los botones del volante (siguiente, anterior, pausa) mientras la app está abierta. */
    private fun setupMediaSession() {
        val s = MediaSessionCompat(this, "RutaMusica")
        s.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { if (!vm.isPlaying) vm.playPause() }
            override fun onPause() { if (vm.isPlaying) vm.playPause() }
            override fun onSkipToNext() = vm.next()
            override fun onSkipToPrevious() = vm.previous()
            override fun onStop() { vm.pause() }
        })
        @Suppress("DEPRECATION")
        s.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        s.isActive = true
        session = s

        lifecycleScope.launch {
            snapshotFlow { Triple(vm.isPlaying, vm.current, vm.duration) }.collect { (playing, track, dur) ->
                val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
                s.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(
                            if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                            (vm.position * 1000).toLong(), 1f,
                        )
                        .build()
                )
                if (track != null) {
                    val g = vm.guessFor(track)
                    s.setMetadata(
                        MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, g.title.ifBlank { track.title })
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, g.artist)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (dur * 1000).toLong())
                            .build()
                    )
                }
            }
        }
    }

    companion object {
        fun systemWebViewVersion(context: android.content.Context): Int = try {
            Regex("""Chrome/(\d+)""").find(WebSettings.getDefaultUserAgent(context))?.groupValues?.get(1)?.toInt() ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    override fun onStop() {
        super.onStop()
        // Regla de YouTube: el reproductor no puede sonar escondido, así que se pausa al salir de la app.
        vm.pause()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (vm.player === player) vm.player = null
        player.destroy()
        super.onDestroy()
    }
}
