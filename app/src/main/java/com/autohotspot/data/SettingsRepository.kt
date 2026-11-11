package com.autohotspot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Almacena la configuración y el estado de la app.
 *
 * Se usa SharedPreferences (y no DataStore) porque el BroadcastReceiver necesita leer la
 * configuración de forma síncrona y muy rápida dentro de onReceive().
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Switch principal: ¿está activada la vigilancia en segundo plano? */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }

    /** MAC del dispositivo Bluetooth que dispara el hotspot. */
    var targetDeviceAddress: String?
        get() = prefs.getString(KEY_TARGET_ADDRESS, null)
        set(value) = prefs.edit { putString(KEY_TARGET_ADDRESS, value) }

    var targetDeviceName: String?
        get() = prefs.getString(KEY_TARGET_NAME, null)
        set(value) = prefs.edit { putString(KEY_TARGET_NAME, value) }

    /** true mientras haya una "sesión" activa (la app actuó al conectar el Bluetooth). */
    var sessionActive: Boolean
        get() = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_SESSION_ACTIVE, value) }

    /** Estado previo, para revertir solo lo que la app cambió. */
    var wifiWasEnabledBefore: Boolean
        get() = prefs.getBoolean(KEY_WIFI_BEFORE, true)
        set(value) = prefs.edit { putBoolean(KEY_WIFI_BEFORE, value) }

    var hotspotWasEnabledBefore: Boolean
        get() = prefs.getBoolean(KEY_HOTSPOT_BEFORE, false)
        set(value) = prefs.edit { putBoolean(KEY_HOTSPOT_BEFORE, value) }

    /** Último mensaje de estado, mostrado en la UI. */
    val lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, null) ?: "Sin actividad todavía"

    fun setStatus(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        prefs.edit { putString(KEY_LAST_STATUS, "[$time] $message") }
    }

    /** Emite cada vez que cambia cualquier preferencia (para refrescar la UI). */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFS_NAME = "auto_hotspot_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_TARGET_ADDRESS = "target_address"
        private const val KEY_TARGET_NAME = "target_name"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_WIFI_BEFORE = "wifi_before"
        private const val KEY_HOTSPOT_BEFORE = "hotspot_before"
        private const val KEY_LAST_STATUS = "last_status"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
