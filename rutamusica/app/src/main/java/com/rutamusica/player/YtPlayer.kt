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
class YtPlayer(context: Context) {

    interface Listener {
        fun onReady()
        fun onState(state: Int)
        fun onError(code: Int)
        fun onTime(seconds: Double, duration: Double)
    }

    var listener: Listener? = null
    private val main = Handler(Looper.getMainLooper())

    val view: WebView = WebView(context).apply {
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

    private fun js(code: String) = main.post { view.evaluateJavascript(code, null) }

    fun load(videoId: String, startSec: Double = 0.0) {
        val safe = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        js("loadVideo('$safe', $startSec)")
    }
    fun play() = js("playV()")
    fun pause() = js("pauseV()")
    fun seek(sec: Double) = js("seekV($sec)")

    fun destroy() {
        listener = null
        view.destroy()
    }

    companion object {
        const val ORIGIN = "https://com.rutamusica"

        // Estados de YouTube
        const val ENDED = 0
        const val PLAYING = 1
        const val PAUSED = 2
        const val BUFFERING = 3
        const val CUED = 5
    }
}
