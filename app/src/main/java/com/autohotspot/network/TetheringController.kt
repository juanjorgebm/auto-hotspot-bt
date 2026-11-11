package com.autohotspot.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.dx.stock.ProxyBuilder
import com.autohotspot.AutoHotspotApp.Companion.TAG
import com.autohotspot.shizuku.ShizukuHelper
import com.autohotspot.shizuku.ShizukuNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * ============================================================================
 *  CÓMO FUNCIONA (Y POR QUÉ ES DELICADO) EL TETHERING EN ANDROID
 * ============================================================================
 *
 * Android NO ofrece una API pública para que una app normal active la Zona WiFi
 * con conexión compartida:
 *
 *  - WifiManager.setWifiApEnabled() se eliminó en Android 8.0.
 *  - WifiManager.startLocalOnlyHotspot() es pública, pero crea una red SIN internet y con
 *    nombre/contraseña aleatorios: no sirve para compartir datos con el coche.
 *  - ConnectivityManager.startTethering() (Android 7–10) y TetheringManager.startTethering()
 *    (Android 11+) son @SystemApi: ocultas para el SDK.
 *
 * Lo que sí funciona en muchos dispositivos (esta clase lo implementa):
 *
 *  1. Llamar a esas APIs ocultas por reflexión (desbloqueadas con HiddenApiBypass en
 *     AutoHotspotApp).
 *  2. El servicio del sistema comprueba el permiso del llamante. En AOSP acepta
 *     TETHER_PRIVILEGED (solo apps de sistema) **o** WRITE_SETTINGS ("Modificar ajustes del
 *     sistema"), que el usuario puede conceder a cualquier app desde Ajustes.
 *  3. Si el operador exige "entitlement check" (aprovisionamiento de tethering), el sistema
 *     puede rechazar la petición o exigir TETHER_PRIVILEGED.
 *
 * Por tanto: es "best effort". Funciona en muchos Pixel/Samsung/Xiaomi con Android 8–15,
 * pero un fabricante, una actualización o el operador pueden bloquearlo. Si falla, el
 * servicio recurre a una notificación que abre los ajustes de Zona WiFi con un toque.
 *
 * Alternativas más robustas (ver README):
 *  - Root / Magisk: instalar la app como priv-app → obtiene TETHER_PRIVILEGED.
 *  - Shizuku: ejecutar la llamada con identidad "shell", que sí tiene TETHER_PRIVILEGED.
 *  - Device Owner (dispositivo dedicado): permite más control de red.
 * ============================================================================
 */
class TetheringController(context: Context) {

    sealed interface Outcome {
        data object Success : Outcome
        data class Failure(val reason: String) : Outcome
    }

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val shizuku = ShizukuNetwork(appContext)

    /** Android 11+ con Shizuku iniciado y autorizado: vía privilegiada (identidad "shell"). */
    private suspend fun shizukuUsable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ShizukuHelper.awaitReady()

    /** ¿Tiene la app algún permiso que el servicio de tethering acepte? */
    fun hasTetherPermission(): Boolean =
        Settings.System.canWrite(appContext) ||
            appContext.checkSelfPermission(TETHER_PRIVILEGED) == PackageManager.PERMISSION_GRANTED

    /** WifiManager.isWifiApEnabled() es oculto; si la reflexión falla devolvemos false. */
    fun isHotspotEnabled(): Boolean = runCatching {
        WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifiManager) as Boolean
    }.getOrDefault(false)

    suspend fun startWifiHotspot(): Outcome {
        if (isHotspotEnabled()) return Outcome.Success

        // 1) Shizuku: funciona aunque el fabricante rechace WRITE_SETTINGS (error code 14).
        if (shizukuUsable()) {
            val viaShizuku = shizuku.startTethering()
            if (viaShizuku == Outcome.Success || waitForHotspotState(true, 3_000)) {
                return Outcome.Success
            }
            Log.w(TAG, "Shizuku no pudo activar el hotspot: $viaShizuku; probando vía normal")
        }

        // 2) API oculta con WRITE_SETTINGS.
        if (!hasTetherPermission()) {
            return Outcome.Failure("Falta el permiso «Modificar ajustes del sistema» o Shizuku")
        }
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startViaTetheringManager()
            } else {
                startViaConnectivityManager()
            }
            // Algunos fabricantes no invocan el callback: confirmamos con el estado real.
            result ?: if (waitForHotspotState(true)) Outcome.Success
            else Outcome.Failure("El sistema no respondió a la petición")
        } catch (t: Throwable) {
            Log.e(TAG, "startTethering falló", t)
            Outcome.Failure(describe(t))
        }
    }

    suspend fun stopWifiHotspot(): Outcome {
        if (!isHotspotEnabled()) return Outcome.Success
        if (shizukuUsable()) {
            shizuku.stopTethering()
            if (waitForHotspotState(false)) return Outcome.Success
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val tm = tetheringManager()
                tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    .invoke(tm, TETHERING_WIFI)
            } else {
                ConnectivityManager::class.java
                    .getMethod("stopTethering", Int::class.javaPrimitiveType)
                    .invoke(connectivityManager, TETHERING_WIFI)
            }
            if (waitForHotspotState(false)) Outcome.Success
            else Outcome.Failure("El hotspot sigue encendido")
        } catch (t: Throwable) {
            Log.e(TAG, "stopTethering falló", t)
            Outcome.Failure(describe(t))
        }
    }

    // ------------------------------------------------------------------ Android 11+ (API 30+)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tetheringManager(): Any =
        appContext.getSystemService(TETHERING_SERVICE)
            ?: error("TetheringManager no disponible en este dispositivo")

    /**
     * TetheringManager.startTethering(TetheringRequest, Executor, StartTetheringCallback).
     * StartTetheringCallback es una *interfaz* en Android 11+, así que basta con un
     * java.lang.reflect.Proxy. Devuelve null si el callback no llega a tiempo.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startViaTetheringManager(): Outcome? {
        val tm = tetheringManager()
        val requestClass = Class.forName("android.net.TetheringManager\$TetheringRequest")
        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")

        val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
        val request = builderClass.getMethod("build").invoke(builder)

        return withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            suspendCancellableCoroutine<Outcome> { cont ->
                val callback = Proxy.newProxyInstance(
                    callbackClass.classLoader, arrayOf(callbackClass)
                ) { proxy, method, args ->
                    when (method.name) {
                        "onTetheringStarted" -> {
                            if (cont.isActive) cont.resume(Outcome.Success); null
                        }
                        "onTetheringFailed" -> {
                            val code = args?.firstOrNull() as? Int ?: -1
                            if (cont.isActive) cont.resume(Outcome.Failure(errorText(code))); null
                        }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        "toString" -> "AutoHotspotStartTetheringCallback"
                        else -> null
                    }
                }
                tm.javaClass
                    .getMethod("startTethering", requestClass, Executor::class.java, callbackClass)
                    .invoke(tm, request, Executor { it.run() }, callback)
            }
        }
    }

    // ------------------------------------------------------------- Android 8.0 – 10 (API 26–29)

    /**
     * ConnectivityManager.startTethering(int, boolean, OnStartTetheringCallback, Handler).
     * OnStartTetheringCallback es una *clase abstracta* oculta: java.lang.reflect.Proxy no
     * sirve, así que dexmaker genera una subclase en tiempo de ejecución.
     */
    private suspend fun startViaConnectivityManager(): Outcome? {
        val callbackClass =
            Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")

        return withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            suspendCancellableCoroutine<Outcome> { cont ->
                @Suppress("UNCHECKED_CAST")
                val callback = ProxyBuilder.forClass(callbackClass as Class<Any>)
                    .dexCache(appContext.codeCacheDir)
                    .handler { proxy, method, args ->
                        when (method.name) {
                            "onTetheringStarted" -> {
                                if (cont.isActive) cont.resume(Outcome.Success); null
                            }
                            "onTetheringFailed" -> {
                                if (cont.isActive) {
                                    cont.resume(Outcome.Failure("El sistema rechazó el tethering"))
                                }
                                null
                            }
                            else -> ProxyBuilder.callSuper(proxy, method, *(args ?: emptyArray()))
                        }
                    }
                    .build()

                ConnectivityManager::class.java.getMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    callbackClass,
                    Handler::class.java
                ).invoke(
                    connectivityManager,
                    TETHERING_WIFI,
                    false, // showProvisioningUi
                    callback,
                    Handler(Looper.getMainLooper())
                )
            }
        }
    }

    // ------------------------------------------------------------------------------ utilidades

    private suspend fun waitForHotspotState(enabled: Boolean, timeoutMs: Long = 8_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isHotspotEnabled() == enabled) return true
            delay(500)
        }
        return isHotspotEnabled() == enabled
    }

    private fun describe(t: Throwable): String {
        val root = (t as? InvocationTargetException)?.targetException ?: t
        return when (root) {
            is SecurityException -> "Permiso denegado por el sistema (${root.message})"
            is ClassNotFoundException, is NoSuchMethodException ->
                "API de tethering no encontrada en esta versión/ROM"
            else -> root.message ?: root.javaClass.simpleName
        }
    }

    /** Códigos de TetheringManager.TETHER_ERROR_* más habituales. */
    private fun errorText(code: Int): String = when (code) {
        11 -> "Aprovisionamiento del operador fallido (code 11)"
        12 -> "Comprobación del operador desconocida (code 12)"
        14 -> "Tu móvil rechaza «Modificar ajustes del sistema» (code 14). Usa Shizuku"
        else -> "Error de tethering (code $code)"
    }

    companion object {
        /** TetheringManager.TETHERING_WIFI == ConnectivityManager.TETHERING_WIFI == 0 */
        private const val TETHERING_WIFI = 0
        /** Context.TETHERING_SERVICE (oculto). */
        private const val TETHERING_SERVICE = "tethering"
        private const val TETHER_PRIVILEGED = "android.permission.TETHER_PRIVILEGED"
        private const val CALLBACK_TIMEOUT_MS = 10_000L

    }
}
