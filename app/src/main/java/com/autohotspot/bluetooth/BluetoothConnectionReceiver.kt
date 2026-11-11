package com.autohotspot.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.autohotspot.AutoHotspotApp.Companion.TAG
import com.autohotspot.data.SettingsRepository
import com.autohotspot.service.HotspotForegroundService

/**
 * Recibe ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED aunque la app esté cerrada.
 *
 * - En Android 12+ el sistema solo entrega estos broadcasts si la app tiene BLUETOOTH_CONNECT.
 * - Recibir un broadcast de Bluetooth que exige BLUETOOTH_CONNECT es una de las excepciones
 *   que permiten iniciar un servicio en primer plano desde segundo plano (Android 12+).
 *
 * El receiver no hace trabajo pesado: filtra el dispositivo y delega en el servicio.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = SettingsRepository.get(context)
        if (!repo.serviceEnabled) return
        val target = repo.targetDeviceAddress ?: return

        val device = IntentCompat.getParcelableExtra(
            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
        ) ?: return
        if (!device.address.equals(target, ignoreCase = true)) return

        val action = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> HotspotForegroundService.ACTION_DEVICE_CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> HotspotForegroundService.ACTION_DEVICE_DISCONNECTED
            else -> return
        }
        Log.i(TAG, "Evento Bluetooth ${intent.action} de $target")
        HotspotForegroundService.send(context, action)
    }
}
