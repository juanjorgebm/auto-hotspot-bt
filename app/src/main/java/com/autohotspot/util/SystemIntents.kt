package com.autohotspot.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Intents para abrir pantallas del sistema cuando la app no puede actuar por sí misma. */
object SystemIntents {

    /**
     * Pantalla "Zona WiFi / Compartir conexión". No hay una acción pública estándar, así que
     * se prueban varias (AOSP, Samsung/Pixel) y, si ninguna existe, se abre "Redes".
     */
    fun tetherSettings(context: Context): Intent {
        val candidates = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$TetherSettingsActivity"
            ),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        val pm = context.packageManager
        val intent = candidates.firstOrNull { it.resolveActivity(pm) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Panel flotante de Wi-Fi (Android 10+) o ajustes de Wi-Fi (Android 8–9). */
    fun wifiPanel(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
