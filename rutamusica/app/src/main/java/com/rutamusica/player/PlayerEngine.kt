package com.rutamusica.player

import android.view.View

/** Reproductor oficial de YouTube, mostrado con el navegador del sistema o con el motor Gecko incluido. */
interface PlayerEngine {
    interface Listener {
        fun onReady()
        fun onState(state: Int)
        fun onError(code: Int)
        fun onTime(seconds: Double, duration: Double)
    }

    var listener: Listener?
    val view: View
    /** Nombre del motor para mostrarlo en Ajustes. */
    val engineName: String
    /** Texto del último error, si el motor no da un código de YouTube. */
    val lastErrorText: String?

    fun load(videoId: String, startSec: Double = 0.0)
    fun play()
    fun pause()
    fun seek(sec: Double)
    fun destroy()

    companion object {
        const val ENDED = 0
        const val PLAYING = 1
        const val PAUSED = 2
        const val BUFFERING = 3
        const val CUED = 5
        /** Error mostrado por el reproductor sin código (motor Gecko). */
        const val TEXT_ERROR = 999
    }
}
