package com.autohotspot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autohotspot.bluetooth.BluetoothHelper
import com.autohotspot.bluetooth.PairedDevice
import com.autohotspot.data.SettingsRepository
import com.autohotspot.network.TetheringController
import com.autohotspot.service.HotspotForegroundService
import com.autohotspot.shizuku.ShizukuHelper
import com.autohotspot.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val canWriteSettings: Boolean = false,
    val shizukuStatus: ShizukuHelper.Status = ShizukuHelper.Status.NOT_INSTALLED,
    val ignoringBatteryOptimizations: Boolean = false,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val selectedAddress: String? = null,
    val selectedName: String? = null,
    val serviceEnabled: Boolean = false,
    val hotspotActive: Boolean = false,
    val lastStatus: String = ""
) {
    val shizukuReady: Boolean get() = shizukuStatus == ShizukuHelper.Status.READY

    /** Hace falta al menos una vía para controlar la Zona WiFi. */
    val hasHotspotControl: Boolean get() = canWriteSettings || shizukuReady

    val canEnableService: Boolean
        get() = hasBluetoothPermission && hasNotificationPermission && selectedAddress != null
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository.get(app)
    private val bluetooth = BluetoothHelper(app)
    private val tethering = TetheringController(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.changes().collect { refresh() } }
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        _state.value = UiState(
            bluetoothAvailable = bluetooth.isAvailable,
            bluetoothEnabled = bluetooth.isEnabled,
            hasBluetoothPermission = Permissions.hasBluetoothConnect(ctx),
            hasNotificationPermission = Permissions.hasNotifications(ctx),
            canWriteSettings = Permissions.canWriteSettings(ctx),
            shizukuStatus = ShizukuHelper.status(ctx),
            ignoringBatteryOptimizations = Permissions.isIgnoringBatteryOptimizations(ctx),
            pairedDevices = bluetooth.pairedDevices(),
            selectedAddress = repo.targetDeviceAddress,
            selectedName = repo.targetDeviceName,
            serviceEnabled = repo.serviceEnabled,
            hotspotActive = tethering.isHotspotEnabled(),
            lastStatus = repo.lastStatus
        )
    }

    fun requestShizukuPermission() = ShizukuHelper.requestPermission()

    fun selectDevice(device: PairedDevice) {
        repo.targetDeviceAddress = device.address
        repo.targetDeviceName = device.name
        repo.sessionActive = false
        if (repo.serviceEnabled) send(HotspotForegroundService.ACTION_START)
    }

    fun setServiceEnabled(enabled: Boolean) {
        repo.serviceEnabled = enabled
        if (enabled) {
            send(HotspotForegroundService.ACTION_START)
        } else {
            HotspotForegroundService.stop(getApplication())
            repo.setStatus("Servicio detenido")
        }
    }

    /** Botones de prueba: permiten comprobar si este móvil acepta el tethering automático. */
    fun testActivate() = send(HotspotForegroundService.ACTION_TEST_ACTIVATE)
    fun testRevert() = send(HotspotForegroundService.ACTION_TEST_REVERT)

    private fun send(action: String) = HotspotForegroundService.send(getApplication(), action)
}
