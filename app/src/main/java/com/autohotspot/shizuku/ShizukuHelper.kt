package com.autohotspot.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku (https://shizuku.rikka.app) ejecuta un servicio con la identidad de "shell" (la misma
 * que `adb shell`). Esa identidad tiene permisos que una app normal nunca recibe, entre ellos
 * TETHER_PRIVILEGED y NETWORK_SETTINGS. A través de ShizukuBinderWrapper la app habla con los
 * servicios del sistema "como si fuera adb", sin root.
 *
 * Requisitos para el usuario:
 *  1. Instalar Shizuku (Play Store / GitHub).
 *  2. Iniciarlo con "Depuración inalámbrica" (Android 11+) o por adb / root.
 *     Sin root hay que volver a iniciarlo tras cada reinicio del teléfono.
 *  3. Autorizar a esta app dentro de Shizuku (la app lo pide).
 */
object ShizukuHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 4242

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun status(context: Context): Status = when {
        !isInstalled(context) && !binderAlive() -> Status.NOT_INSTALLED
        !binderAlive() -> Status.NOT_RUNNING
        !hasPermission() -> Status.NO_PERMISSION
        else -> Status.READY
    }

    fun isReady(): Boolean = binderAlive() && hasPermission()

    /** El binder de Shizuku llega de forma asíncrona al arrancar el proceso: esperamos un poco. */
    suspend fun awaitReady(timeoutMs: Long = 3_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) return true
            delay(200)
        }
        return isReady()
    }

    fun requestPermission() {
        if (binderAlive() && !hasPermission()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    /** Binder de un servicio del sistema envuelto para que las llamadas se hagan como "shell". */
    fun systemServiceBinder(name: String): IBinder? =
        SystemServiceHelper.getSystemService(name)?.let { ShizukuBinderWrapper(it) }

    fun wrap(binder: IBinder): IBinder = ShizukuBinderWrapper(binder)

    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installWebIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    private fun binderAlive(): Boolean =
        runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    private fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true
    }.getOrDefault(false)
}
