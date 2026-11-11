package com.autohotspot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autohotspot.bluetooth.PairedDevice
import com.autohotspot.shizuku.ShizukuHelper
import com.autohotspot.ui.theme.AutoHotspotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onRequestRuntimePermissions: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onShizukuAction: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectDevice: (PairedDevice) -> Unit,
    onToggleService: (Boolean) -> Unit,
    onTestActivate: () -> Unit,
    onTestRevert: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Hotspot BT") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PermissionsCard(
                state, onRequestRuntimePermissions, onOpenWriteSettings, onOpenBatterySettings,
                onShizukuAction
            )
            DeviceCard(state, onSelectDevice, onOpenBluetoothSettings)
            ServiceCard(state, onToggleService)
            TestCard(state, onTestActivate, onTestRevert)
            InfoCard()
        }
    }
}

// --------------------------------------------------------------------------- Permisos

@Composable
private fun PermissionsCard(
    state: UiState,
    onRequestRuntimePermissions: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onShizukuAction: () -> Unit
) {
    SectionCard(title = "1. Permisos") {
        PermissionRow(
            title = "Bluetooth",
            description = "Leer dispositivos vinculados y detectar la conexión.",
            granted = state.hasBluetoothPermission,
            actionLabel = "Conceder",
            onAction = onRequestRuntimePermissions
        )
        PermissionRow(
            title = "Notificaciones",
            description = "Necesario para el servicio en segundo plano y los avisos.",
            granted = state.hasNotificationPermission,
            actionLabel = "Conceder",
            onAction = onRequestRuntimePermissions
        )
        PermissionRow(
            title = "Modificar ajustes del sistema",
            description = "Permiso especial que Android exige para encender la Zona WiFi. " +
                "Se concede en Ajustes, no con un diálogo.",
            granted = state.canWriteSettings,
            actionLabel = "Abrir ajustes",
            onAction = onOpenWriteSettings
        )
        val (shizukuDescription, shizukuAction) = when (state.shizukuStatus) {
            ShizukuHelper.Status.NOT_INSTALLED ->
                "No instalado. Necesario si tu móvil rechaza el permiso anterior (p. ej. Xiaomi)." to "Instalar"
            ShizukuHelper.Status.NOT_RUNNING ->
                "Instalado pero detenido. Ábrelo e inícialo con «Depuración inalámbrica»." to "Abrir"
            ShizukuHelper.Status.NO_PERMISSION ->
                "En ejecución. Falta autorizar a esta app." to "Autorizar"
            ShizukuHelper.Status.READY ->
                "Listo: la app controla la Zona WiFi y el Wi-Fi con permisos de sistema." to ""
        }
        PermissionRow(
            title = "Shizuku (recomendado)",
            description = shizukuDescription,
            granted = state.shizukuReady,
            actionLabel = shizukuAction,
            onAction = onShizukuAction
        )
        PermissionRow(
            title = "Sin optimización de batería (recomendado)",
            description = "Evita que el fabricante detenga el servicio.",
            granted = state.ignoringBatteryOptimizations,
            actionLabel = "Abrir ajustes",
            onAction = onOpenBatterySettings
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (granted) "Concedido" else "Pendiente",
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// --------------------------------------------------------------------------- Dispositivo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCard(
    state: UiState,
    onSelectDevice: (PairedDevice) -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    SectionCard(title = "2. Dispositivo Bluetooth") {
        when {
            !state.bluetoothAvailable -> Text("Este dispositivo no tiene Bluetooth.")
            !state.hasBluetoothPermission -> Text("Concede el permiso de Bluetooth para ver la lista.")
            !state.bluetoothEnabled -> {
                Text("El Bluetooth está apagado.")
                OutlinedButton(onClick = onOpenBluetoothSettings) { Text("Abrir ajustes de Bluetooth") }
            }
            state.pairedDevices.isEmpty() -> {
                Text("No hay dispositivos vinculados. Vincula primero el manos libres del coche.")
                OutlinedButton(onClick = onOpenBluetoothSettings) { Text("Abrir ajustes de Bluetooth") }
            }
            else -> {
                var expanded by remember { mutableStateOf(false) }
                val selectedLabel = state.pairedDevices
                    .firstOrNull { it.address == state.selectedAddress }?.name
                    ?: state.selectedName
                    ?: "Selecciona un dispositivo"

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Activa el hotspot al conectar con") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.pairedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(device.name)
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSelectDevice(device)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- Servicio

@Composable
private fun ServiceCard(state: UiState, onToggleService: (Boolean) -> Unit) {
    SectionCard(title = "3. Servicio automático") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.serviceEnabled) "Activado" else "Desactivado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Vigila el Bluetooth en segundo plano",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.serviceEnabled,
                onCheckedChange = onToggleService,
                // Siempre se puede desactivar; para activar hacen falta permisos y dispositivo.
                enabled = state.serviceEnabled || state.canEnableService
            )
        }
        if (!state.hasHotspotControl) {
            Text(
                "Sin Shizuku ni «Modificar ajustes del sistema» la app no podrá encender la " +
                    "Zona WiFi y solo te mostrará una notificación para hacerlo con un toque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        HorizontalDivider()
        Text("Zona WiFi: ${if (state.hotspotActive) "ENCENDIDA" else "apagada"}")
        Text(
            state.lastStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TestCard(state: UiState, onTestActivate: () -> Unit, onTestRevert: () -> Unit) {
    SectionCard(title = "4. Probar en este móvil") {
        Text(
            "Comprueba si tu móvil permite el control automático antes de ir al coche.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onTestActivate, enabled = state.hasNotificationPermission) {
                Text("Encender hotspot")
            }
            OutlinedButton(onClick = onTestRevert, enabled = state.hasNotificationPermission) {
                Text("Revertir")
            }
        }
    }
}

@Composable
private fun InfoCard() {
    SectionCard(title = "Limitaciones de Android") {
        Text(
            "• Android no ofrece una API pública para la Zona WiFi. La app usa la API interna " +
                "del sistema; funciona en muchos móviles pero el fabricante u operador pueden " +
                "bloquearla. Si falla, recibirás una notificación que abre el ajuste directamente.\n" +
                "• Desde Android 10 las apps no pueden apagar/encender el Wi-Fi. Normalmente " +
                "Android lo apaga solo al encender la Zona WiFi y lo restaura al apagarla.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    AutoHotspotTheme {
        MainScreen(
            state = UiState(
                bluetoothEnabled = true,
                hasBluetoothPermission = true,
                hasNotificationPermission = true,
                pairedDevices = listOf(PairedDevice("Manos libres coche", "00:11:22:33:44:55")),
                selectedAddress = "00:11:22:33:44:55",
                lastStatus = "[10:00:00] Vigilando «Manos libres coche»"
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}
