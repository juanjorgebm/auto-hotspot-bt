package com.autohotspot.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autohotspot.R
import com.autohotspot.ui.MainActivity

object Notifications {

    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_ACTIONS = "actions"

    const val ID_SERVICE = 1
    const val ID_HOTSPOT_ACTION = 2
    const val ID_WIFI_ACTION = 3

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Servicio en segundo plano", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notificación permanente mientras se vigila el Bluetooth" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTIONS, "Acciones necesarias", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos cuando Android no permite cambiar la red automáticamente"
            }
        )
    }

    fun serviceNotification(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_hotspot)
            .setContentTitle("Auto Hotspot activo")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Fallback cuando Android impide el cambio automático: una notificación de alta prioridad
     * que, con un solo toque, abre la pantalla adecuada. Desde Android 10 un servicio en
     * segundo plano NO puede abrir actividades directamente, así que esta es la vía permitida.
     */
    @SuppressLint("MissingPermission") // comprobado con Permissions.hasNotifications
    fun showActionNeeded(context: Context, id: Int, title: String, text: String, intent: Intent) {
        if (!Permissions.hasNotifications(context)) return
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_hotspot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
