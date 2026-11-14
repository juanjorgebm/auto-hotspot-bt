package com.rutatv.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rutatv.RutaApp
import com.rutatv.data.Item
import com.rutatv.data.Kind
import com.rutatv.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lista que se está reproduciendo (para pasar de canal con los botones ‹ ›). */
object PlayQueue {
    var items: List<Item> = emptyList()
    var index: Int = 0

    fun start(context: Context, list: List<Item>, startIndex: Int) {
        items = list
        index = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
}

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private val repo get() = RutaApp.repo
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var starView: TextView
    private lateinit var messageView: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView
    private var retries = 0
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val current: Item? get() = PlayQueue.items.getOrNull(PlayQueue.index)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        if (current == null) { finish(); return }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun roundBg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }

    private fun bigButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        gravity = Gravity.CENTER
        background = roundBg(0x99000000.toInt())
        minWidth = dp(72); minHeight = dp(72)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 4000
            keepScreenOn = true
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis ->
                topBar.visibility = vis
                prevBtn.visibility = if (canStep()) vis else View.GONE
                nextBtn.visibility = if (canStep()) vis else View.GONE
                if (vis == View.VISIBLE) hideSystemUi()
            })
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0x88000000.toInt())
        }
        topBar.addView(bigButton("←") { finish() })
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), 0, dp(16), 0)
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(bigButton("⤢") { toggleResize() })
        topBar.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        starView = bigButton("☆") { toggleFavorite() }
        topBar.addView(starView)
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        prevBtn = bigButton("‹") { step(-1) }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f) }
        nextBtn = bigButton("›") { step(1) }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f) }
        root.addView(prevBtn, FrameLayout.LayoutParams(dp(88), dp(120), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(16) })
        root.addView(nextBtn, FrameLayout.LayoutParams(dp(88), dp(120), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(16) })

        messageView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            background = roundBg(0xCC000000.toInt())
            setPadding(dp(24), dp(16), dp(24), dp(16))
            visibility = View.GONE
        }
        root.addView(messageView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        setContentView(root)
        hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun createPlayer(): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(Http.client(this)).setUserAgent(Http.USER_AGENT)
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_000, 4_000)
            .build()
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build().apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = handleError(error)
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) { messageView.visibility = View.GONE; retries = 0 }
                        if (state == Player.STATE_ENDED && current?.kind != Kind.LIVE) {
                            savePosition(reset = true)
                            // Siguiente episodio automáticamente.
                            if (current?.kind == Kind.EPISODE && PlayQueue.index < PlayQueue.items.size - 1) step(1)
                        }
                    }
                })
            }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            player = createPlayer()
            playerView.player = player
            playCurrent()
        }
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun playCurrent() {
        val item = current ?: return
        titleView.text = item.name
        updateStar()
        prevBtn.visibility = if (canStep()) View.VISIBLE else View.GONE
        nextBtn.visibility = if (canStep()) View.VISIBLE else View.GONE
        repo.addRecent(item)
        scope.launch {
            val url = try {
                resolveUrl(item)
            } catch (e: Exception) {
                showMessage("No se pudo preparar: ${e.message}")
                return@launch
            }
            val p = player ?: return@launch
            val builder = MediaItem.Builder().setUri(url)
            if (url.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            p.setMediaItem(builder.build())
            if (item.kind != Kind.LIVE) {
                val pos = repo.position(item)
                if (pos > 10_000) p.seekTo(pos)
            }
            p.prepare()
            p.playWhenReady = true
        }
    }

    private suspend fun resolveUrl(item: Item): String {
        val x = repo.xtream()
        return when (item.kind) {
            Kind.LIVE -> x.liveUrl(item.id, repo.prefs.liveFormat)
            Kind.MOVIE -> {
                val ext = item.ext ?: withContext(Dispatchers.IO) { x.movieExtension(item.id) } ?: "mp4"
                x.movieUrl(item.id, ext)
            }
            Kind.EPISODE -> x.episodeUrl(item.id, item.ext ?: "mp4")
            Kind.SERIES -> throw IllegalStateException("Elige un episodio")
        }
    }

    private fun handleError(error: PlaybackException) {
        val item = current ?: return
        if (item.kind == Kind.LIVE && retries < 3) {
            retries++
            showMessage("Reconectando… ($retries/3)")
            handler.postDelayed({
                // Si el formato .ts falla, el segundo intento prueba HLS (.m3u8) y viceversa.
                if (retries == 2) {
                    val alt = if (repo.prefs.liveFormat == "ts") "m3u8" else "ts"
                    val p = player ?: return@postDelayed
                    val url = repo.xtream().liveUrl(item.id, alt)
                    val b = MediaItem.Builder().setUri(url)
                    if (alt == "m3u8") b.setMimeType(MimeTypes.APPLICATION_M3U8)
                    p.setMediaItem(b.build()); p.prepare(); p.playWhenReady = true
                } else playCurrent()
            }, 2500)
        } else {
            showMessage("No se puede reproducir.\n${error.errorCodeName}")
        }
    }

    private fun showMessage(text: String) {
        messageView.text = text
        messageView.visibility = View.VISIBLE
    }

    /** Botones ‹ › para canales en vivo y episodios (no para películas). */
    private fun canStep(): Boolean {
        val k = current?.kind
        return (k == Kind.LIVE || k == Kind.EPISODE) && PlayQueue.items.size > 1
    }

    private fun step(delta: Int) {
        val n = PlayQueue.items.size
        if (n < 2) return
        if (player?.playbackState != Player.STATE_ENDED) savePosition()
        PlayQueue.index = (PlayQueue.index + delta + n) % n
        retries = 0
        messageView.visibility = View.GONE
        playCurrent()
    }

    private fun toggleFavorite() {
        val item = current ?: return
        if (item.kind == Kind.EPISODE) {
            Toast.makeText(this, "Marca como favorita la serie desde su ficha", Toast.LENGTH_SHORT).show()
            return
        }
        repo.toggleFavorite(item)
        updateStar()
    }

    private fun updateStar() {
        val item = current ?: return
        val fav = repo.isFavorite(item)
        starView.text = if (fav) "★" else "☆"
        starView.setTextColor(if (fav) 0xFFFF8A3D.toInt() else Color.WHITE)
    }

    private fun toggleResize() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = resizeMode
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Llenar pantalla (recorta)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar"
            else -> "Ajustar"
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun savePosition(reset: Boolean = false) {
        val item = current ?: return
        val p = player ?: return
        if (item.kind == Kind.LIVE) return
        repo.savePosition(item, if (reset) 0 else p.currentPosition)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> { step(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
