package com.rutamusica.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession

/**
 * "Modo YouTube": la página real de YouTube (m.youtube.com) dentro de la app, con el motor de Firefox.
 * Es YouTube tal cual (recomendados, suscripciones, búsqueda); la sesión de Google se queda guardada.
 */
class YouTubeBrowser(context: Context) {
    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
    )
    val view: GeckoView = GeckoView(context)

    var canGoBack by mutableStateOf(false)
        private set
    var fullscreen by mutableStateOf(false)
        private set
    private var started = false
    private var media: MediaSession? = null

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@YouTubeBrowser.canGoBack = canGoBack
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                // Las ventanas nuevas se abren en la misma pantalla.
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    session.loadUri(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                session.loadUri(uri)
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                fullscreen = fullScreen
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                media = mediaSession
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (media === mediaSession) media = null
            }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                val allow = perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                return GeckoResult.fromValue(
                    if (allow) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                )
            }
        }
        session.open(GeckoPlayer.runtime(context))
        view.setSession(session)
    }

    fun start() {
        if (!started) {
            started = true
            session.loadUri(HOME)
        }
        session.setActive(true)
    }

    fun home() = session.loadUri(HOME)
    fun subscriptions() = session.loadUri("https://m.youtube.com/feed/subscriptions")
    fun library() = session.loadUri("https://m.youtube.com/feed/library")
    fun back() = session.goBack()
    fun exitFullscreen() = session.exitFullScreen()

    /** Pausa lo que esté sonando (al volver al modo Música o al salir de la app). */
    fun pause() {
        try { media?.pause() } catch (_: Throwable) {}
    }

    fun destroy() {
        try { view.releaseSession() } catch (_: Throwable) {}
        session.close()
    }

    companion object {
        const val HOME = "https://m.youtube.com/"
    }
}
