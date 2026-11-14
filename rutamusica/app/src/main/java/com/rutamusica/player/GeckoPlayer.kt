package com.rutamusica.player

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import java.net.URLEncoder

/**
 * Reproductor oficial de YouTube mostrado con el motor de Firefox (GeckoView) que va dentro de la app.
 * Se usa cuando el navegador del sistema de la pantalla es demasiado viejo (p. ej. versión 39 de 2014).
 *
 * Se abre la página oficial youtube.com/embed/<id> y un pequeño complemento (assets/ytbridge)
 * le pasa a la app el estado y el tiempo del reproductor para sincronizar la letra.
 */
class GeckoPlayer(context: Context) : PlayerEngine {
    override var listener: PlayerEngine.Listener? = null
    override val engineName = "Motor Gecko incluido"
    override var lastErrorText: String? = null
        private set

    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
    )
    override val view: GeckoView = GeckoView(context)

    private var port: WebExtension.Port? = null
    private var extReady = false
    private var pending: Pair<String, Double>? = null
    private var destroyed = false

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val m = message as? JSONObject ?: return
            when (m.optString("type")) {
                "ready" -> listener?.onReady()
                "state" -> listener?.onState(m.optInt("s", -1))
                "time" -> listener?.onTime(m.optDouble("t", 0.0), m.optDouble("d", 0.0))
                "error" -> {
                    lastErrorText = m.optString("text")
                    listener?.onError(PlayerEngine.TEXT_ERROR)
                }
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (this@GeckoPlayer.port === port) this@GeckoPlayer.port = null
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            this@GeckoPlayer.port = port
            port.setDelegate(portDelegate)
        }
    }

    init {
        val rt = runtime(context)
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
        // Solo se permiten páginas del reproductor (si se toca el logo de YouTube no se sale de la app).
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val u = request.uri
                val ok = u.startsWith("https://www.youtube.com/embed/") || u.startsWith("about:") ||
                    u.startsWith("https://www.youtube-nocookie.com/embed/")
                return GeckoResult.fromValue(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }
        }
        session.open(rt)
        view.setSession(session)

        rt.webExtensionController
            .ensureBuiltIn("resource://android/assets/ytbridge/", "ytbridge@rutamusica.app")
            .accept({ ext ->
                if (ext != null && !destroyed) {
                    session.webExtensionController.setMessageDelegate(ext, messageDelegate, "rutamusica")
                    extReady = true
                    pending?.let { (id, s) -> pending = null; load(id, s) }
                }
            }, { e ->
                Log.e("RutaMusica", "No se pudo instalar el puente", e)
                lastErrorText = "no se pudo preparar el reproductor"
                listener?.onError(-1)
            })
    }

    override fun load(videoId: String, startSec: Double) {
        if (!extReady) {
            pending = videoId to startSec
            return
        }
        val safe = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val origin = URLEncoder.encode(YtPlayer.ORIGIN, "UTF-8")
        val url = "https://www.youtube.com/embed/$safe?autoplay=1&playsinline=1&rel=0&fs=0&iv_load_policy=3" +
            "&enablejsapi=1&start=${startSec.toInt()}&origin=$origin&widget_referrer=$origin"
        port = null
        lastErrorText = null
        // YouTube pide saber qué app muestra el reproductor (cabecera Referer).
        session.load(GeckoSession.Loader().uri(url).referrer(YtPlayer.ORIGIN + "/"))
    }

    private fun send(cmd: String, t: Double? = null) {
        val o = JSONObject().put("cmd", cmd)
        if (t != null) o.put("t", t)
        try { port?.postMessage(o) } catch (_: Throwable) {}
    }

    override fun play() = send("play")
    override fun pause() = send("pause")
    override fun seek(sec: Double) = send("seek", sec)

    override fun destroy() {
        destroyed = true
        listener = null
        try { view.releaseSession() } catch (_: Throwable) {}
        session.close()
    }

    companion object {
        @Volatile private var runtime: GeckoRuntime? = null

        fun runtime(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(false)
                    .build()
            ).also { runtime = it }
        }
    }
}
