package com.autohotspot.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autohotspot.ui.theme.AutoHotspotTheme
import com.autohotspot.util.Permissions

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** BLUETOOTH_CONNECT (12+) y POST_NOTIFICATIONS (13+). */
    private val runtimePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.refresh()
            // Denegado "para siempre": Android ya no mostrará el diálogo, así que llevamos al
            // usuario a la ficha de la app para que lo conceda a mano.
            val permanentlyDenied = result.filterValues { !it }.keys
                .any { !shouldShowRequestPermissionRationale(it) }
            if (permanentlyDenied) openAppDetails()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoHotspotTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                MainScreen(
                    state = state,
                    onRequestRuntimePermissions = ::requestRuntimePermissions,
                    onOpenWriteSettings = { open(Permissions.writeSettingsIntent(this)) },
                    onOpenBatterySettings = { open(Permissions.batteryOptimizationIntent()) },
                    onOpenBluetoothSettings = { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onRefresh = viewModel::refresh,
                    onSelectDevice = viewModel::selectDevice,
                    onToggleService = viewModel::setServiceEnabled,
                    onTestActivate = viewModel::testActivate,
                    onTestRevert = viewModel::testRevert
                )
            }
        }
    }

    /** Al volver de Ajustes (WRITE_SETTINGS, batería…) refrescamos el estado. */
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun requestRuntimePermissions() {
        val missing = Permissions.missingRuntimePermissions(this)
        if (missing.isNotEmpty()) runtimePermissionsLauncher.launch(missing) else openAppDetails()
    }

    private fun openAppDetails() = open(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
    )

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
