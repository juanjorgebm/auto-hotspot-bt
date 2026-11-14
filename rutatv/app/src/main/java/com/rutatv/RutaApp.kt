package com.rutatv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rutatv.data.Repo
import com.rutatv.net.Http

class RutaApp : Application(), ImageLoaderFactory {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        repo = Repo(this)
    }

    /** Si la app se cierra por un error, guarda el detalle para mostrarlo en Ajustes. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                getSharedPreferences("rutatv", MODE_PRIVATE).edit()
                    .putString("last_crash", "RutaTV ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" + sw.toString().take(6000))
                    .commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Http.client(this) }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()

    companion object {
        lateinit var instance: RutaApp
            private set
        val repo: Repo get() = instance.repo
    }
}
