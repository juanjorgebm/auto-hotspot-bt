package com.rutamusica.ui

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.View
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
import com.rutamusica.player.YouTubeBrowser
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()
    private lateinit var player: PlayerEngine
    private var session: MediaSessionCompat? = null
    private var browserOrNull: YouTubeBrowser? = null
    private val browser: YouTubeBrowser
        get() = browserOrNull ?: YouTubeBrowser(this).also { browserOrNull = it }

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
        // Al volver al modo Música se pausa lo que suene en YouTube.
        lifecycleScope.launch {
            snapshotFlow { vm.youTubeMode }.collect { if (!it) browserOrNull?.pause() }
        }
        // Botón "atrás" del carro: en YouTube va a la página anterior; en modo video vuelve a la letra.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val b = browserOrNull
                when {
                    vm.youTubeMode && b != null && b.fullscreen -> b.exitFullscreen()
                    vm.youTubeMode && b != null && b.canGoBack -> b.back()
                    vm.youTubeMode -> vm.switchYouTubeMode(false)
                    vm.videoMode -> vm.toggleVideoMode()
                    else -> moveTaskToBack(true)
                }
            }
        })
        // Video a pantalla completa: también se intenta esconder la barra del sistema.
        lifecycleScope.launch {
            snapshotFlow { (vm.videoMode && !vm.controlsVisible) || (vm.youTubeMode && browserOrNull?.fullscreen == true) }.collect { applyImmersive(it) }
        }

        setContent {
            MusicTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) { App(vm, player.view) { browser } }
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

    /** Cualquier toque en la pantalla vuelve a mostrar los controles del modo video. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) vm.userInteracted()
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive(on: Boolean) {
        window.decorView.systemUiVisibility = if (on) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onStop() {
        super.onStop()
        // Regla de YouTube: el reproductor no puede sonar escondido, así que se pausa al salir de la app.
        vm.pause()
        browserOrNull?.pause()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (vm.player === player) vm.player = null
        player.destroy()
        browserOrNull?.destroy()
        super.onDestroy()
    }
}
