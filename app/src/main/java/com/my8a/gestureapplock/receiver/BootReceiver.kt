package com.my8a.gestureapplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.Prefs
import com.my8a.gestureapplock.service.ForegroundAppMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // If user had monitoring enabled previously OR there are saved gestures, start service
        val shouldStart = Prefs.isMonitoringStarted(context) || GestureStore.hasAnySavedGestures(context)
        if (!shouldStart) return

        // Start the foreground service (requires the app to be allowed to start on boot by the system user)
        try {
            val svc = Intent(context, ForegroundAppMonitorService::class.java)
            ContextCompat.startForegroundService(context, svc)
        } catch (_: Exception) {
            // best effort — some OEMs restrict background start on boot
        }
    }
}
