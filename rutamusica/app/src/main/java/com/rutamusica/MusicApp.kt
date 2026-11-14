package com.rutamusica

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rutamusica.net.Http

class MusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
    }

    /** Si la app se cierra por un error, guarda el detalle para mostrarlo en Ajustes. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                getSharedPreferences("rutamusica", MODE_PRIVATE).edit()
                    .putString("last_crash", "RutaMúsica ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" + sw.toString().take(6000))
                    .commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Http.client(this) }
        .crossfade(false)
        .build()

    companion object {
        lateinit var instance: MusicApp
            private set
    }
}
