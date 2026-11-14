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
        repo = Repo(this)
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
