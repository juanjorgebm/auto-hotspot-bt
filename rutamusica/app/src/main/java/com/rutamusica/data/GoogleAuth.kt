package com.rutamusica.data

import android.content.Context
import com.rutamusica.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Inicio de sesión con Google para "TV y dispositivos de entrada limitada" (OAuth 2.0 device flow).
 *
 * Google no permite iniciar sesión dentro de un WebView y la pantalla del carro no tiene Google Play,
 * así que la app muestra un código y la persona lo escribe en google.com/device desde su celular.
 * Solo se pide permiso de LECTURA de YouTube (youtube.readonly).
 */
class GoogleAuth(context: Context, private val prefs: Prefs) {
    private val http = Http.client(context)
    private val mutex = Mutex()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Int,
        val expiresAt: Long,
    )

    class AuthException(message: String) : IOException(message)

    sealed class PollResult {
        object Pending : PollResult()
        object SlowDown : PollResult()
        object Done : PollResult()
    }

    val isSignedIn get() = prefs.refreshToken.isNotBlank()
    val hasClient get() = prefs.clientId.isNotBlank() && prefs.clientSecret.isNotBlank()

    suspend fun start(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", prefs.clientId)
            .add("scope", SCOPE)
            .build()
        val req = Request.Builder().url("https://oauth2.googleapis.com/device/code").post(body).build()
        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            val o = runCatching { JSONObject(txt) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) throw AuthException(explain(o, resp.code))
            DeviceCode(
                deviceCode = o.getString("device_code"),
                userCode = o.getString("user_code"),
                verificationUrl = o.optString("verification_url", "https://www.google.com/device"),
                intervalSec = o.optInt("interval", 5).coerceAtLeast(2),
                expiresAt = System.currentTimeMillis() + o.optInt("expires_in", 1800) * 1000L,
            )
        }
    }

    suspend fun poll(dc: DeviceCode): PollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", prefs.clientId)
            .add("client_secret", prefs.clientSecret)
            .add("device_code", dc.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        http.newCall(req).execute().use { resp ->
            val o = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrElse { JSONObject() }
            if (resp.isSuccessful) {
                saveTokens(o)
                return@withContext PollResult.Done
            }
            when (o.optString("error")) {
                "authorization_pending" -> PollResult.Pending
                "slow_down" -> PollResult.SlowDown
                "access_denied" -> throw AuthException("Se rechazó el permiso en el celular. Vuelve a intentarlo y toca \"Permitir\".")
                "expired_token" -> throw AuthException("El código venció. Toca \"Conectar\" otra vez para pedir uno nuevo.")
                else -> throw AuthException(explain(o, resp.code))
            }
        }
    }

    /** Devuelve un token válido, renovándolo si venció (dura 1 hora). */
    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        if (!forceRefresh && prefs.accessToken.isNotBlank() && System.currentTimeMillis() < prefs.accessExpiry - 60_000) {
            return prefs.accessToken
        }
        if (prefs.refreshToken.isBlank()) throw AuthException("No hay sesión iniciada.")
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", prefs.clientId)
                .add("client_secret", prefs.clientSecret)
                .add("refresh_token", prefs.refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val o = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrElse { JSONObject() }
                if (!resp.isSuccessful) {
                    if (o.optString("error") == "invalid_grant") {
                        prefs.signOut()
                        throw AuthException("La sesión de Google venció o se quitó el permiso. Vuelve a conectar tu cuenta.")
                    }
                    throw AuthException(explain(o, resp.code))
                }
                saveTokens(o)
            }
        }
        prefs.accessToken
    }

    private fun saveTokens(o: JSONObject) {
        prefs.accessToken = o.getString("access_token")
        prefs.accessExpiry = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000L
        o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { prefs.refreshToken = it }
    }

    private fun explain(o: JSONObject, code: Int): String {
        val err = o.optString("error")
        val desc = o.optString("error_description")
        return when (err) {
            "invalid_client" -> "Google no reconoce la llave (ID de cliente o secreto). Revísalos en Ajustes."
            "unauthorized_client" -> "La llave no es del tipo \"TV y dispositivos de entrada limitada\". Crea una de ese tipo."
            "invalid_scope" -> "La llave no tiene permiso para YouTube. Activa \"YouTube Data API v3\" en Google Cloud."
            "" -> "Error de Google (HTTP $code)"
            else -> "Error de Google: $err ${desc.ifBlank { "" }}".trim()
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }
}
