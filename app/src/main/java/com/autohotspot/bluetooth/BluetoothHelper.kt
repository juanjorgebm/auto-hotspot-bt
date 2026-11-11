package com.autohotspot.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.autohotspot.util.Permissions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class PairedDevice(val name: String, val address: String)

class BluetoothHelper(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    val isAvailable: Boolean get() = adapter != null

    val isEnabled: Boolean
        get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /** Dispositivos vinculados (requiere BLUETOOTH_CONNECT en Android 12+). */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        if (!Permissions.hasBluetoothConnect(appContext)) return emptyList()
        return runCatching {
            adapter?.bondedDevices.orEmpty()
                .map { PairedDevice(name = it.name ?: it.address, address = it.address) }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    /**
     * ¿Está el dispositivo conectado ahora mismo? Se usa al activar el servicio (por si el
     * coche ya estaba conectado) y tras un reinicio.
     *
     * 1) BluetoothDevice.isConnected() es oculto pero muy fiable (cualquier enlace ACL).
     * 2) Fallback público: perfiles HEADSET (manos libres) y A2DP (audio).
     */
    @SuppressLint("MissingPermission")
    suspend fun isDeviceConnected(address: String): Boolean {
        val adapter = adapter ?: return false
        if (!Permissions.hasBluetoothConnect(appContext)) return false

        val viaHiddenApi = runCatching {
            val device = adapter.getRemoteDevice(address)
            BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
        }.getOrNull()
        if (viaHiddenApi != null) return viaHiddenApi

        return listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
            .any { isConnectedOnProfile(adapter, it, address) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun isConnectedOnProfile(
        adapter: BluetoothAdapter,
        profile: Int,
        address: String
    ): Boolean = withTimeoutOrNull(3_000) {
        suspendCancellableCoroutine { cont ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    val connected = runCatching {
                        proxy.connectedDevices.any { it.address == address }
                    }.getOrDefault(false)
                    adapter.closeProfileProxy(p, proxy)
                    if (cont.isActive) cont.resume(connected)
                }

                override fun onServiceDisconnected(p: Int) = Unit
            }
            if (!adapter.getProfileProxy(appContext, listener, profile) && cont.isActive) {
                cont.resume(false)
            }
        }
    } ?: false
}
