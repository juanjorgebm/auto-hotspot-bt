package com.autohotspot.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.autohotspot.shizuku.ShizukuHelper
import com.autohotspot.shizuku.ShizukuNetwork

/**
 * Control del Wi-Fi (modo cliente).
 *
 * LIMITACIÓN DE ANDROID: desde Android 10 (API 29) WifiManager.setWifiEnabled() está
 * obsoleto y para apps normales (targetSdk >= 29) NO hace nada y devuelve false. Solo apps de
 * sistema, Device Owner o con root pueden encender/apagar el Wi-Fi.
 *
 * En la práctica no es grave:
 *  - Al ENCENDER el hotspot, Android apaga el Wi-Fi cliente automáticamente en la mayoría de
 *    móviles (salvo los que soportan Wi-Fi + hotspot simultáneos, "STA/AP concurrency").
 *  - Al APAGAR el hotspot, Android suele restaurar el Wi-Fi si estaba encendido antes.
 * Si no ocurre, el servicio muestra una notificación que abre el panel de Wi-Fi con un toque.
 */
class WifiController(context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(WifiManager::class.java)
    private val shizuku = ShizukuNetwork(context)

    val isWifiEnabled: Boolean get() = wifiManager?.isWifiEnabled == true

    /** Devuelve true si el estado final es el pedido (aplicado directamente o ya lo estaba). */
    fun setWifiEnabled(enabled: Boolean): Boolean {
        val wm = wifiManager ?: return false
        if (wm.isWifiEnabled == enabled) return true
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wm.setWifiEnabled(enabled)
        } else if (ShizukuHelper.isReady()) {
            // Con Shizuku hacemos lo mismo que `adb shell svc wifi enable/disable`.
            shizuku.setWifiEnabled(enabled)
        } else {
            false // No permitido para apps normales en Android 10+.
        }
    }
}
