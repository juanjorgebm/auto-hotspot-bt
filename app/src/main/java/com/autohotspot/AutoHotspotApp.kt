package com.autohotspot

import android.app.Application
import android.os.Build
import android.util.Log
import com.autohotspot.util.Notifications
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AutoHotspotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        unsealHiddenApis()
        Notifications.createChannels(this)
    }

    /**
     * Desde Android 9 (API 28) el sistema bloquea el acceso por reflexión a las APIs
     * ocultas (@hide / @SystemApi). La API de tethering (TetheringManager.startTethering,
     * ConnectivityManager.startTethering, WifiManager.isWifiApEnabled) es oculta, así que
     * hay que eximir a la app de esa restricción. HiddenApiBypass lo hace solo para este
     * proceso, sin root.
     *
     * Importante: esto NO concede permisos. Solo permite *llamar* al método; el servicio de
     * sistema seguirá comprobando TETHER_PRIVILEGED o WRITE_SETTINGS (ver TetheringController).
     */
    private fun unsealHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
                .onFailure { Log.w(TAG, "No se pudieron desbloquear APIs ocultas", it) }
        }
    }

    companion object {
        const val TAG = "AutoHotspot"
    }
}
