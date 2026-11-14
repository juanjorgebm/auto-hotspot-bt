package com.rutamusica.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Envoltorio del reproductor oficial de YouTube (IFrame Player API) dentro de un WebView.
 * Cumple las reglas de YouTube: el video se ve siempre (no se oculta ni se tapa) y no se
 * extrae el audio. Por eso la música se pausa si sales de la app.
 */
@SuppressLint("SetJavaScriptEnabled")
class YtPlayer(context: Context) : PlayerEngine {


    override var listener: PlayerEngine.Listener? = null
    override val engineName = "Navegador del sistema"
    override val lastErrorText: String? = null
    private val main = Handler(Looper.getMainLooper())

    override val view: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            // Los enlaces que el usuario toque dentro del reproductor no se abren aquí.
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
        }
        addJavascriptInterface(Bridge(), "Android")
        val html = context.assets.open("player.html").bufferedReader().use { it.readText() }
            .replace("__ORIGIN__", ORIGIN)
        // YouTube exige saber qué app inserta el reproductor (cabecera Referer): se usa el id de la app.
        loadDataWithBaseURL("$ORIGIN/", html, "text/html", "utf-8", null)
    }

    private inner class Bridge {
        @JavascriptInterface fun onReady() = main.post { listener?.onReady() }.let { }
        @JavascriptInterface fun onState(s: Int) = main.post { listener?.onState(s) }.let { }
        @JavascriptInterface fun onError(c: Int) = main.post { listener?.onError(c) }.let { }
        @JavascriptInterface fun onTime(t: Double, d: Double) = main.post { listener?.onTime(t, d) }.let { }
    }

    private fun js(code: String) { main.post { view.evaluateJavascript(code, null) } }

    override fun load(videoId: String, startSec: Double) {
        val safe = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        js("loadVideo('$safe', $startSec)")
    }
    override fun play() = js("playV()")
    override fun pause() = js("pauseV()")
    override fun seek(sec: Double) = js("seekV($sec)")

    override fun destroy() {
        listener = null
        view.destroy()
    }

    companion object {
        const val ORIGIN = "https://com.rutamusica"

    }
}
