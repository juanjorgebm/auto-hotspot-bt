package com.rutatv.net

import android.content.Context
import android.util.Log
import com.rutatv.R
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP compartido.
 *
 * Android 5.1 trae certificados raíz antiguos: no confía en Let's Encrypt (ISRG Root X1),
 * ni en varias raíces modernas. Se añaden a mano las raíces de res/raw/extra_roots.pem
 * además de las del sistema, para que funcionen las conexiones HTTPS (API de VivaPlayer,
 * logos, servidores Xtream con HTTPS).
 */
object Http {
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    @Volatile
    private var client: OkHttpClient? = null

    fun client(context: Context): OkHttpClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val c = build(context.applicationContext)
            client = c
            return c
        }
    }

    private fun build(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.header("User-Agent") == null) {
                    chain.proceed(req.newBuilder().header("User-Agent", USER_AGENT).build())
                } else chain.proceed(req)
            }
        try {
            val certs = HandshakeCertificates.Builder().addPlatformTrustedCertificates()
            extraRoots(context).forEach { certs.addTrustedCertificate(it) }
            val hc = certs.build()
            builder.sslSocketFactory(hc.sslSocketFactory(), hc.trustManager)
        } catch (t: Throwable) {
            Log.w("RutaTV", "No se pudieron añadir raíces extra", t)
        }
        return builder.build()
    }

    private fun extraRoots(context: Context): List<X509Certificate> {
        val pem = context.resources.openRawResource(R.raw.extra_roots).bufferedReader().use { it.readText() }
        val marker = "-----BEGIN CERTIFICATE-----"
        return pem.split(marker).filter { it.contains("END CERTIFICATE") }.mapNotNull { part ->
            try {
                (marker + part.substringBefore("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----")
                    .decodeCertificatePem()
            } catch (t: Throwable) {
                null
            }
        }
    }
}
