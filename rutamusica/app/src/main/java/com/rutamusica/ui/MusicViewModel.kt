package com.rutamusica.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamusica.data.GoogleAuth
import com.rutamusica.data.Lyrics
import com.rutamusica.data.LyricsRepo
import com.rutamusica.data.Playlist
import com.rutamusica.data.Prefs
import com.rutamusica.data.Track
import com.rutamusica.data.YouTubeApi
import com.rutamusica.player.YtPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Stage { Key, Login, Ready }
enum class Tab { Lyrics, Library }

sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Found(val lyrics: Lyrics) : LyricsState()
    object None : LyricsState()
    data class Failed(val message: String) : LyricsState()
}

class MusicViewModel(app: Application) : AndroidViewModel(app), YtPlayer.Listener {
    val prefs = Prefs(app)
    private val auth = GoogleAuth(app, prefs)
    private val yt = YouTubeApi(app, auth)
    private val lyricsRepo = LyricsRepo(app)

    // ---------- Cuenta ----------
    var stage by mutableStateOf(currentStage())
        private set
    var deviceCode by mutableStateOf<GoogleAuth.DeviceCode?>(null)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var channelName by mutableStateOf(prefs.channelName)
        private set
    private var loginJob: Job? = null

    private fun currentStage() = when {
        !auth.hasClient -> Stage.Key
        !auth.isSignedIn -> Stage.Login
        else -> Stage.Ready
    }

    fun saveKey(id: String, secret: String) {
        prefs.clientId = id
        prefs.clientSecret = secret
        authError = null
        stage = currentStage()
    }

    fun startLogin() {
        loginJob?.cancel()
        authError = null
        loginJob = viewModelScope.launch {
            try {
                var dc = auth.start()
                deviceCode = dc
                var interval = dc.intervalSec * 1000L
                while (System.currentTimeMillis() < dc.expiresAt) {
                    delay(interval)
                    when (auth.poll(dc)) {
                        GoogleAuth.PollResult.Done -> {
                            deviceCode = null
                            stage = Stage.Ready
                            loadLibrary()
                            return@launch
                        }
                        GoogleAuth.PollResult.SlowDown -> interval += 5000
                        GoogleAuth.PollResult.Pending -> Unit
                    }
                }
                deviceCode = null
                authError = "El código venció. Toca \"Conectar\" otra vez."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deviceCode = null
                authError = e.message ?: "No se pudo conectar con Google. Revisa el internet."
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        deviceCode = null
    }

    fun signOut() {
        player?.pause()
        prefs.signOut()
        playlists = emptyList(); tracks = emptyList(); openPlaylist = null
        queue = emptyList(); original = emptyList(); index = -1
        channelName = ""
        stage = currentStage()
    }

    fun changeKey() {
        signOut()
        prefs.clearClient()
        stage = currentStage()
    }

    // ---------- Biblioteca ----------
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var libLoading by mutableStateOf(false)
        private set
    var libError by mutableStateOf<String?>(null)
        private set
    var openPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var tracksLoading by mutableStateOf(false)
        private set
    var tracksError by mutableStateOf<String?>(null)
        private set
    private var tracksJob: Job? = null

    init {
        if (stage == Stage.Ready) loadLibrary()
    }

    fun loadLibrary() {
        libLoading = true
        libError = null
        viewModelScope.launch {
            try {
                val (name, likes) = yt.me()
                channelName = name
                prefs.channelName = name
                val mine = yt.myPlaylists()
                val liked = Playlist(likes ?: "LL", "Me gusta", 0, null, special = true)
                playlists = listOf(liked) + mine
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                libError = e.message ?: "No se pudo leer tu cuenta de YouTube. Revisa el internet."
                stage = currentStage()
            } finally {
                libLoading = false
            }
        }
    }

    fun open(p: Playlist) {
        openPlaylist = p
        tracks = emptyList()
        tracksError = null
        tracksLoading = true
        tracksJob?.cancel()
        tracksJob = viewModelScope.launch {
            try {
                tracks = yt.playlistItems(p.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tracksError = e.message ?: "No se pudo abrir la lista."
            } finally {
                tracksLoading = false
            }
        }
    }

    fun closePlaylist() {
        tracksJob?.cancel()
        openPlaylist = null
    }

    // ---------- Reproducción ----------
    var player: YtPlayer? = null
        set(value) {
            field = value
            value?.listener = this
        }

    private var original: List<Track> = emptyList()
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    val current: Track? get() = queue.getOrNull(index)

    var playerState by mutableIntStateOf(-1)
        private set
    var position by mutableDoubleStateOf(0.0)
        private set
    var duration by mutableDoubleStateOf(0.0)
        private set
    var shuffle by mutableStateOf(prefs.shuffle)
        private set
    var playerMessage by mutableStateOf<String?>(null)
        private set
    var tab by mutableStateOf(Tab.Library)

    val isPlaying get() = playerState == YtPlayer.PLAYING || playerState == YtPlayer.BUFFERING
    private var errorsInARow = 0
    private var skipJob: Job? = null

    /** Reproduce la lista abierta desde la canción [start]. */
    fun playFrom(list: List<Track>, start: Int, forceShuffle: Boolean? = null) {
        if (list.isEmpty()) return
        forceShuffle?.let { shuffle = it; prefs.shuffle = it }
        original = list
        val first = list[start.coerceIn(0, list.size - 1)]
        queue = if (shuffle) listOf(first) + (list - first).shuffled() else list
        index = queue.indexOf(first)
        errorsInARow = 0
        tab = Tab.Lyrics
        loadCurrent()
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        prefs.shuffle = shuffle
        val cur = current ?: return
        queue = if (shuffle) listOf(cur) + (original - cur).shuffled() else original
        index = queue.indexOf(cur).coerceAtLeast(0)
    }

    private fun loadCurrent() {
        val t = current ?: return
        skipJob?.cancel()
        playerMessage = null
        position = 0.0
        duration = 0.0
        offsetMs = prefs.lyricsOffset(t.videoId)
        player?.load(t.videoId)
        fetchLyrics(t)
    }

    fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size   // al terminar la lista vuelve a empezar
        loadCurrent()
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (position > 5) {
            player?.seek(0.0)
            return
        }
        index = if (index <= 0) queue.size - 1 else index - 1
        loadCurrent()
    }

    fun playPause() {
        if (current == null) return
        if (isPlaying) player?.pause() else player?.play()
    }

    fun pause() { player?.pause() }

    fun seekTo(sec: Double) { player?.seek(sec) }

    var playerReady by mutableStateOf(false)
        private set

    override fun onReady() {
        playerReady = true
    }

    override fun onState(state: Int) {
        playerState = state
        if (state == YtPlayer.PLAYING) {
            errorsInARow = 0
            playerMessage = null
        }
        if (state == YtPlayer.ENDED) next()
    }

    override fun onError(code: Int) {
        val t = current
        playerMessage = when (code) {
            101, 150 -> "El dueño de este video no permite reproducirlo fuera de YouTube. Pasando a la siguiente…"
            100 -> "Este video ya no existe o es privado. Pasando a la siguiente…"
            2 -> "Video no válido. Pasando a la siguiente…"
            5 -> "La pantalla no pudo reproducir este video. Pasando a la siguiente…"
            152, 153 -> "YouTube rechazó el reproductor (error $code). Avísale a Claude."
            -1 -> "No se pudo cargar el reproductor de YouTube. Revisa el internet."
            else -> "Error del reproductor de YouTube ($code)."
        }
        if (t == null || code == -1 || code == 152 || code == 153) return
        errorsInARow++
        if (errorsInARow >= 8) {
            playerMessage = "Varias canciones seguidas no se pudieron reproducir. Revisa el internet o prueba otra lista."
            return
        }
        skipJob?.cancel()
        skipJob = viewModelScope.launch {
            delay(2500)
            if (current == t) next()
        }
    }

    override fun onTime(seconds: Double, duration: Double) {
        position = seconds
        if (duration > 0) this.duration = duration
    }

    // ---------- Letra ----------
    var lyricsState by mutableStateOf<LyricsState>(LyricsState.Idle)
        private set
    var offsetMs by mutableLongStateOf(0L)
        private set
    private var lyricsJob: Job? = null

    fun guessFor(t: Track): LyricsRepo.Guess {
        val saved = prefs.lyricsQuery(t.videoId)
        if (saved.contains('|')) return LyricsRepo.Guess(saved.substringBefore('|'), saved.substringAfter('|'))
        return LyricsRepo.guess(t)
    }

    private fun fetchLyrics(t: Track, manual: LyricsRepo.Guess? = null, useCache: Boolean = true) {
        lyricsJob?.cancel()
        lyricsState = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            // Espera a saber cuánto dura el video (ayuda a elegir la versión correcta de la letra).
            var waited = 0
            while (duration <= 0 && waited < 4000) { delay(200); waited += 200 }
            val m = manual ?: prefs.lyricsQuery(t.videoId).takeIf { it.contains('|') }
                ?.let { LyricsRepo.Guess(it.substringBefore('|'), it.substringAfter('|')) }
            lyricsState = try {
                val l = lyricsRepo.find(t, duration, m, useCache)
                if (l == null) LyricsState.None else LyricsState.Found(l)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LyricsState.Failed("No se pudo buscar la letra. Revisa el internet.")
            }
        }
    }

    fun retryLyrics() {
        val t = current ?: return
        lyricsRepo.clearCache(t.videoId)
        fetchLyrics(t, useCache = false)
    }

    fun searchLyrics(artist: String, title: String) {
        val t = current ?: return
        prefs.setLyricsQuery(t.videoId, "${artist.trim()}|${title.trim()}")
        lyricsRepo.clearCache(t.videoId)
        fetchLyrics(t, LyricsRepo.Guess(artist.trim(), title.trim()), useCache = false)
    }

    fun shiftLyrics(deltaMs: Long) {
        val t = current ?: return
        offsetMs += deltaMs
        prefs.setLyricsOffset(t.videoId, offsetMs)
    }

    fun resetOffset() {
        val t = current ?: return
        offsetMs = 0
        prefs.setLyricsOffset(t.videoId, 0)
    }

    override fun onCleared() {
        player?.listener = null
        super.onCleared()
    }
}
