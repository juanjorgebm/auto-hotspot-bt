package com.autohotspot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autohotspot.AutoHotspotApp.Companion.TAG
import com.autohotspot.bluetooth.BluetoothHelper
import com.autohotspot.data.SettingsRepository
import com.autohotspot.network.TetheringController
import com.autohotspot.network.TetheringController.Outcome
import com.autohotspot.network.WifiController
import com.autohotspot.util.Notifications
import com.autohotspot.util.SystemIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Servicio en primer plano (tipo "connectedDevice") que:
 *  - Se mantiene vivo mientras el switch principal está activo (notificación permanente).
 *  - Recibe del BluetoothConnectionReceiver los eventos de conexión/desconexión.
 *  - Ejecuta los cambios de red de forma serializada (Mutex) para evitar carreras si el
 *    Bluetooth del coche se conecta/desconecta varias veces seguidas.
 *  - Aplica un periodo de gracia antes de revertir, porque los manos libres suelen
 *    desconectarse un instante al arrancar o al apagar el motor.
 */
class HotspotForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var pendingRevert: Job? = null

    private lateinit var repo: SettingsRepository
    private lateinit var tethering: TetheringController
    private lateinit var wifi: WifiController
    private lateinit var bluetooth: BluetoothHelper

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository.get(this)
        tethering = TetheringController(this)
        wifi = WifiController(this)
        bluetooth = BluetoothHelper(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tras startForegroundService() hay que llamar a startForeground() en < 5 s.
        if (!enterForeground(currentDescription())) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_DEVICE_CONNECTED -> onTargetConnected()
            ACTION_DEVICE_DISCONNECTED -> onTargetDisconnected()
            ACTION_TEST_ACTIVATE -> runSerialized { activate() }
            ACTION_TEST_REVERT -> runSerialized { revert(force = true) }
            // ACTION_START o reinicio del sistema (intent == null con START_STICKY)
            else -> {
                if (!repo.serviceEnabled) {
                    stopEverything()
                    return START_NOT_STICKY
                }
                runSerialized { syncWithCurrentState() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------------ eventos

    private fun onTargetConnected() {
        pendingRevert?.cancel() // reconexión dentro del periodo de gracia: no revertimos
        pendingRevert = null
        runSerialized { activate() }
    }

    private fun onTargetDisconnected() {
        pendingRevert?.cancel()
        updateStatus("Bluetooth desconectado; se revertirá en ${DISCONNECT_GRACE_MS / 1000} s")
        pendingRevert = scope.launch {
            delay(DISCONNECT_GRACE_MS)
            mutex.withLock { revert(force = false) }
        }
    }

    /** Al activar el switch o tras reiniciar: ¿el coche ya está conectado? */
    private suspend fun syncWithCurrentState() {
        val address = repo.targetDeviceAddress ?: run {
            updateStatus("Vigilando… (elige un dispositivo Bluetooth)")
            return
        }
        val connected = bluetooth.isDeviceConnected(address)
        when {
            connected && !repo.sessionActive -> activate()
            !connected && repo.sessionActive -> revert(force = false)
            else -> updateStatus("Vigilando «${repo.targetDeviceName ?: address}»")
        }
    }

    // ------------------------------------------------------------------------ acciones

    /** Bluetooth conectado → apagar Wi-Fi y encender hotspot. */
    private suspend fun activate() {
        val hotspotAlreadyOn = tethering.isHotspotEnabled()
        if (!repo.sessionActive) {
            repo.hotspotWasEnabledBefore = hotspotAlreadyOn
            repo.wifiWasEnabledBefore = wifi.isWifiEnabled
        }
        repo.sessionActive = true

        if (hotspotAlreadyOn) {
            updateStatus("La Zona WiFi ya estaba activa")
            return
        }

        // Android 8–9: apagamos el Wi-Fi nosotros. Android 10+: lo hará el sistema al
        // encender el hotspot (en móviles sin Wi-Fi + hotspot simultáneos).
        wifi.setWifiEnabled(false)

        updateStatus("Activando Zona WiFi…")
        when (val result = tethering.startWifiHotspot()) {
            Outcome.Success -> {
                Notifications.cancel(this, Notifications.ID_HOTSPOT_ACTION)
                updateStatus("Zona WiFi ACTIVADA")
                delay(3_000)
                if (wifi.isWifiEnabled && !wifi.setWifiEnabled(false)) {
                    Notifications.showActionNeeded(
                        this, Notifications.ID_WIFI_ACTION,
                        "Desactiva el Wi-Fi",
                        "Android no permite a las apps apagar el Wi-Fi. Toca para hacerlo.",
                        SystemIntents.wifiPanel()
                    )
                }
            }
            is Outcome.Failure -> {
                Log.w(TAG, "No se pudo activar el hotspot: ${result.reason}")
                if (repo.wifiWasEnabledBefore) wifi.setWifiEnabled(true)
                updateStatus("No se pudo activar automáticamente: ${result.reason}")
                // Fallback: un toque y el usuario llega directo a la pantalla de Zona WiFi.
                Notifications.showActionNeeded(
                    this, Notifications.ID_HOTSPOT_ACTION,
                    "Toca para activar la Zona WiFi",
                    "Conectado a ${repo.targetDeviceName ?: "tu dispositivo"}. " +
                        "Motivo: ${result.reason}",
                    SystemIntents.tetherSettings(this)
                )
            }
        }
    }

    /** Bluetooth desconectado → apagar hotspot y restaurar Wi-Fi. */
    private suspend fun revert(force: Boolean) {
        if (!repo.sessionActive && !force) return
        Notifications.cancel(this, Notifications.ID_HOTSPOT_ACTION)
        Notifications.cancel(this, Notifications.ID_WIFI_ACTION)

        // No apagamos un hotspot que el usuario ya tenía encendido antes de conectar.
        val shouldStopHotspot = force || !repo.hotspotWasEnabledBefore
        if (shouldStopHotspot && tethering.isHotspotEnabled()) {
            updateStatus("Desactivando Zona WiFi…")
            when (val result = tethering.stopWifiHotspot()) {
                Outcome.Success -> updateStatus("Zona WiFi DESACTIVADA")
                is Outcome.Failure -> {
                    updateStatus("No se pudo apagar la Zona WiFi: ${result.reason}")
                    Notifications.showActionNeeded(
                        this, Notifications.ID_HOTSPOT_ACTION,
                        "Toca para apagar la Zona WiFi",
                        result.reason,
                        SystemIntents.tetherSettings(this)
                    )
                }
            }
        } else {
            updateStatus("Bluetooth desconectado")
        }

        if (force || repo.wifiWasEnabledBefore) {
            if (!wifi.setWifiEnabled(true)) {
                delay(3_000) // margen para que Android restaure el Wi-Fi por sí mismo
                if (!wifi.isWifiEnabled) {
                    Notifications.showActionNeeded(
                        this, Notifications.ID_WIFI_ACTION,
                        "Reactiva el Wi-Fi",
                        "Android no permite a las apps encender el Wi-Fi. Toca para hacerlo.",
                        SystemIntents.wifiPanel()
                    )
                }
            }
        }
        repo.sessionActive = false
    }

    // ------------------------------------------------------------------------ utilidades

    private fun runSerialized(block: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                runCatching { block() }.onFailure { Log.e(TAG, "Error en el servicio", it) }
            }
            if (!repo.serviceEnabled && !mutex.isLocked) stopEverything()
        }
    }

    private fun currentDescription(): String =
        repo.targetDeviceName?.let { "Vigilando «$it»" } ?: "Vigilando conexiones Bluetooth"

    private fun updateStatus(message: String) {
        Log.i(TAG, message)
        repo.setStatus(message)
        enterForeground(message)
    }

    /**
     * Android 14+ exige declarar el tipo del servicio y tener el permiso asociado
     * (BLUETOOTH_CONNECT para "connectedDevice"); si falta, startForeground lanza
     * SecurityException. En Android 12+ también puede lanzar
     * ForegroundServiceStartNotAllowedException si se inicia desde segundo plano sin exención.
     */
    private fun enterForeground(text: String): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else 0
        ServiceCompat.startForeground(
            this, Notifications.ID_SERVICE, Notifications.serviceNotification(this, text), type
        )
        true
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo iniciar el servicio en primer plano", e)
        repo.setStatus("Error al iniciar el servicio: ${e.message}")
        false
    }

    private fun stopEverything() {
        pendingRevert?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.autohotspot.action.START"
        const val ACTION_STOP = "com.autohotspot.action.STOP"
        const val ACTION_DEVICE_CONNECTED = "com.autohotspot.action.DEVICE_CONNECTED"
        const val ACTION_DEVICE_DISCONNECTED = "com.autohotspot.action.DEVICE_DISCONNECTED"
        const val ACTION_TEST_ACTIVATE = "com.autohotspot.action.TEST_ACTIVATE"
        const val ACTION_TEST_REVERT = "com.autohotspot.action.TEST_REVERT"

        /** Margen antes de revertir tras una desconexión (evita parpadeos). */
        private const val DISCONNECT_GRACE_MS = 15_000L

        fun send(context: Context, action: String) {
            val intent = Intent(context, HotspotForegroundService::class.java).setAction(action)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // p. ej. ForegroundServiceStartNotAllowedException (Android 12+)
                Log.e(TAG, "No se pudo iniciar el servicio", e)
                SettingsRepository.get(context).setStatus("Android bloqueó el servicio: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotspotForegroundService::class.java))
        }
    }
}
