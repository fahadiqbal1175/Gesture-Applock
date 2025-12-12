package com.my8a.gestureapplock.data

import android.content.Context

object Prefs {
    private const val PREFS = "gesture_prefs"
    private const val KEY_MONITORING_STARTED = "monitoring_started"
    private const val KEY_USAGE_GRANTED = "usage_granted"
    private const val KEY_OVERLAY_GRANTED = "overlay_granted"
    private const val KEY_NOTIF_GRANTED = "notification_granted"

    fun setMonitoringStarted(ctx: Context, started: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITORING_STARTED, started)
            .apply()
    }

    fun isMonitoringStarted(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITORING_STARTED, false)
    }

    fun setUsageGranted(ctx: Context, granted: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USAGE_GRANTED, granted)
            .apply()
    }

    fun isUsageGranted(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USAGE_GRANTED, false)
    }

    fun setOverlayGranted(ctx: Context, granted: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OVERLAY_GRANTED, granted)
            .apply()
    }

    fun isOverlayGranted(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_GRANTED, false)
    }

    fun setNotificationGranted(ctx: Context, granted: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_GRANTED, granted)
            .apply()
    }

    fun isNotificationGranted(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_GRANTED, false)
    }
}
