package com.autohotspot.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Resumen de permisos por versión de Android:
 *
 * | Permiso                         | API 26–30            | API 31–32       | API 33+         |
 * |---------------------------------|----------------------|-----------------|-----------------|
 * | BLUETOOTH / BLUETOOTH_ADMIN     | normal (automático)  | —               | —               |
 * | BLUETOOTH_CONNECT               | —                    | runtime         | runtime         |
 * | POST_NOTIFICATIONS              | —                    | —               | runtime         |
 * | WRITE_SETTINGS                  | especial (Ajustes)   | especial        | especial        |
 * | Optimización de batería         | recomendado (Ajustes)| recomendado     | recomendado     |
 */
object Permissions {

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothConnect(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else true

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true

    /** Permiso especial "Modificar ajustes del sistema". */
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /** Permisos de tiempo de ejecución que faltan en esta versión de Android. */
    fun missingRuntimePermissions(context: Context): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnect(context)) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications(context)) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun writeSettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Abre la lista de optimización de batería. No usamos
     * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS porque requiere un permiso que Google
     * Play restringe; esta pantalla no necesita permiso.
     */
    fun batteryOptimizationIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
