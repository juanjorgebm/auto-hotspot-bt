package com.rutamusica.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.rutamusica.BuildConfig
import com.rutamusica.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Actualizador propio. Cada build de GitHub Actions publica en la release "rutamusica-latest":
 *   - version.json  {"versionCode": 12, "versionName": "1.0.12", "notes": "...", "apk": "<url>"}
 *   - rutamusica.apk
 * La app compara versionCode con el suyo, descarga el APK y abre el instalador de Android.
 */
object Updater {
    const val VERSION_URL = "https://github.com/juanjorgebm/auto-hotspot-bt/releases/download/rutamusica-latest/version.json"
    const val APK_URL = "https://github.com/juanjorgebm/auto-hotspot-bt/releases/download/rutamusica-latest/rutamusica.apk"

    data class Release(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String) {
        val isNewer get() = versionCode > BuildConfig.VERSION_CODE
    }

    suspend fun check(context: Context): Release = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(VERSION_URL).header("Cache-Control", "no-cache").build()
        Http.client(context).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("No se pudo consultar la versión (HTTP ${resp.code})")
            val o = JSONObject(resp.body?.string() ?: "{}")
            Release(
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName", "?"),
                notes = o.optString("notes", ""),
                apkUrl = o.optString("apk").ifBlank { APK_URL },
            )
        }
    }

    /** Descarga el APK informando el progreso (0..100, o -1 si no se sabe el tamaño). */
    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "rutamusica-${release.versionCode}.apk")
        val req = Request.Builder().url(release.apkUrl).build()
        Http.client(context).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Descarga fallida (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Descarga vacía")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -2
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != last) { last = pct; withContext(Dispatchers.Main) { onProgress(pct) } }
                    }
                }
            }
        }
        file.setReadable(true, false)
        file
    }

    /** Abre el instalador. En Android 8+ puede pedir antes permiso para "instalar apps desconocidas". */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else Uri.fromFile(file)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        return true
    }
}
