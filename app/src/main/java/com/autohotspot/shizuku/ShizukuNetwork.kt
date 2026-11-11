package com.autohotspot.shizuku

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.dx.stock.ProxyBuilder
import com.autohotspot.AutoHotspotApp.Companion.TAG
import com.autohotspot.network.TetheringController.Outcome
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.resume

/**
 * Llamadas privilegiadas a través de Shizuku (identidad "shell", uid 2000).
 *
 * - Zona WiFi: se habla directamente con el servicio de tethering (ITetheringConnector), que es
 *   lo que hace TetheringManager por dentro. Como el llamante es "shell", el sistema comprueba
 *   TETHER_PRIVILEGED (que shell tiene) en lugar de WRITE_SETTINGS. Además se pide
 *   exemptFromEntitlementCheck para saltar la comprobación del operador.
 * - Wi-Fi: IWifiManager.setWifiEnabled("com.android.shell", ...) es exactamente lo que hace
 *   `adb shell svc wifi enable/disable`, por lo que funciona también en Android 10+.
 *
 * Las firmas AIDL cambian entre versiones (p. ej. Android 12 añadió callingFeatureId), por eso
 * los argumentos se construyen según los tipos de parámetro que encontramos en tiempo de
 * ejecución.
 */
class ShizukuNetwork(context: Context) {

    private val appContext = context.applicationContext

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun startTethering(): Outcome = callTethering("startTethering")

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun stopTethering(): Outcome = callTethering("stopTethering")

    fun setWifiEnabled(enabled: Boolean): Boolean = try {
        val binder = ShizukuHelper.systemServiceBinder("wifi") ?: error("servicio wifi no encontrado")
        val iface = Class.forName("android.net.wifi.IWifiManager")
        val wm = asInterface(iface, binder)
        val method = iface.methods.first {
            it.name == "setWifiEnabled" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java
        }
        method.invoke(wm, SHELL_PACKAGE, enabled) as Boolean
    } catch (t: Throwable) {
        Log.e(TAG, "Shizuku setWifiEnabled falló", t)
        false
    }

    // -------------------------------------------------------------------------------- tethering

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun callTethering(methodName: String): Outcome = try {
        val iface = Class.forName("android.net.ITetheringConnector")
        val connector = asInterface(iface, tetheringBinder())
        val method = iface.methods.first { it.name == methodName }
        val request = if (methodName == "startTethering") buildRequest() else null

        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Outcome> { cont ->
                val listener = resultListener { code ->
                    if (cont.isActive) {
                        cont.resume(if (code == 0) Outcome.Success else Outcome.Failure(errorText(code)))
                    }
                }
                method.invoke(connector, *buildArgs(method, request, listener))
            }
        } ?: Outcome.Failure("Shizuku: el sistema no respondió")
    } catch (t: Throwable) {
        val root = (t as? InvocationTargetException)?.targetException ?: t
        Log.e(TAG, "Shizuku $methodName falló", root)
        Outcome.Failure("Shizuku: ${root.message ?: root.javaClass.simpleName}")
    }

    /**
     * El servicio aparece como "tethering" en ServiceManager. Si una ROM no lo registra ahí,
     * usamos el binder que ya tiene el TetheringManager de la app (campo interno mConnector).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun tetheringBinder(): IBinder {
        ShizukuHelper.systemServiceBinder("tethering")?.let { return it }
        val tm = appContext.getSystemService("tethering") ?: error("TetheringManager no disponible")
        val field = tm.javaClass.getDeclaredField("mConnector").apply { isAccessible = true }
        val connector = field.get(tm) ?: error("Servicio de tethering no conectado todavía")
        val binder = connector.javaClass.getMethod("asBinder").invoke(connector) as IBinder
        return ShizukuHelper.wrap(binder)
    }

    /** TetheringRequest para Wi-Fi, sin comprobación del operador y sin UI. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildRequest(): Any {
        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(TETHERING_WIFI)
        val bool = Boolean::class.javaPrimitiveType
        runCatching { builderClass.getMethod("setExemptFromEntitlementCheck", bool).invoke(builder, true) }
        runCatching { builderClass.getMethod("setShouldShowEntitlementUi", bool).invoke(builder, false) }
        return builderClass.getMethod("build").invoke(builder)!!
    }

    private fun buildArgs(method: Method, request: Any?, listener: Any): Array<Any?> {
        var stringIndex = 0
        return method.parameterTypes.map { type ->
            when {
                type.name == "android.net.TetheringRequestParcel" ->
                    request!!.javaClass.getMethod("getParcel").invoke(request)
                request != null && type.isInstance(request) -> request
                type == String::class.java -> if (stringIndex++ == 0) SHELL_PACKAGE else null
                type.name == "android.net.IIntResultListener" -> listener
                type == Int::class.javaPrimitiveType -> TETHERING_WIFI
                type == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        }.toTypedArray()
    }

    /** Implementa la clase abstracta IIntResultListener.Stub (un Binder) con dexmaker. */
    private fun resultListener(onResult: (Int) -> Unit): Any {
        @Suppress("UNCHECKED_CAST")
        val stubClass = Class.forName("android.net.IIntResultListener\$Stub") as Class<Any>
        return ProxyBuilder.forClass(stubClass)
            .dexCache(appContext.codeCacheDir)
            .handler { proxy, method, args ->
                if (method.name == "onResult" && args?.size == 1) {
                    onResult(args[0] as Int)
                    null
                } else {
                    ProxyBuilder.callSuper(proxy, method, *(args ?: emptyArray()))
                }
            }
            .build()
    }

    private fun asInterface(iface: Class<*>, binder: IBinder): Any =
        Class.forName("${iface.name}\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!

    private fun errorText(code: Int): String = when (code) {
        11 -> "Aprovisionamiento del operador fallido (code 11)"
        12 -> "Comprobación del operador desconocida (code 12)"
        14 -> "Sin permiso para cambiar el tethering (code 14)"
        else -> "Error de tethering (code $code)"
    }

    companion object {
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val TETHERING_WIFI = 0
        private const val TIMEOUT_MS = 10_000L
    }
}
