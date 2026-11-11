package com.autohotspot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autohotspot.data.SettingsRepository
import com.autohotspot.service.HotspotForegroundService

/** Reanuda la vigilancia tras reiniciar el teléfono o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (SettingsRepository.get(context).serviceEnabled) {
            HotspotForegroundService.send(context, HotspotForegroundService.ACTION_START)
        }
    }
}
