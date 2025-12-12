package com.my8a.gestureapplock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.my8a.gestureapplock.R
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.Prefs
import com.my8a.gestureapplock.ui.MainActivity
import com.my8a.gestureapplock.ui.UnlockActivity
import java.util.concurrent.TimeUnit

class ForegroundAppMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "gesture_applock_channel"
        private const val NOTIFICATION_ID = 1001
        private val POLL_INTERVAL_MS = 1000L
        private val EVENT_WINDOW_MS = 4000L
        private val REOPEN_WINDOW_MS = TimeUnit.SECONDS.toMillis(2)
        private const val TAG = "AppMonitor"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastHandledForegroundPackage: String? = null
    private var lastHandledAt: Long = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            try { pollUsageEvents() } catch (t: Throwable) { Log.w(TAG, "poll error: ${t.message}") }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannelIfNeeded()

        // Intent to open the main activity when user taps the notification
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMain = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification: small icon uses mipmap launcher to avoid missing resource.
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture AppLock")
            .setContentText("Monitoring protected apps")
            .setSmallIcon(R.mipmap.ic_light) // fallback to launcher icon if you haven't added a notification-specific icon
            .setContentIntent(pendingMain)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)               // Make it persistent / not swipeable in most UIs
            .setOnlyAlertOnce(true)

        val notification = builder.build()
        startForeground(NOTIFICATION_ID, notification)

        // Persist monitoring flag so app knows a monitor was started
        Prefs.setMonitoringStarted(this, true)

        // Start periodic polling
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "AppLock Monitor", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    private fun pollUsageEvents() {
        val now = System.currentTimeMillis()
        val usage = try {
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (t: Throwable) {
            Log.w(TAG, "UsageStats not available: ${t.message}")
            return
        }

        val events = usage.queryEvents(now - EVENT_WINDOW_MS, now)
        val ev = UsageEvents.Event()

        val sawBackground = mutableSetOf<String>()
        val sawForegroundAfterBackground = mutableSetOf<String>()
        var latestForegroundPackage: String? = null
        val packageEventList = mutableListOf<Triple<String, Int, Long>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            val ts = ev.timeStamp
            val type = ev.eventType
            packageEventList.add(Triple(pkg, type, ts))

            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestForegroundPackage = pkg
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                sawBackground.add(pkg)
            }
        }

        val byPkgEvents = packageEventList.groupBy { it.first }
        for ((pkg, list) in byPkgEvents) {
            val sorted = list.sortedBy { it.third }
            var sawBg = false
            for ((_, type, _) in sorted) {
                if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) sawBg = true
                if (sawBg && type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    sawForegroundAfterBackground.add(pkg)
                }
            }
        }

        for (pkg in sawBackground) {
            if (!sawForegroundAfterBackground.contains(pkg)) {
                try {
                    GestureStore.clearSessionUnlocked(this, pkg)
                    Log.d(TAG, "Cleared session-unlock for $pkg (real background)")
                } catch (t: Throwable) {
                    Log.w(TAG, "clearSessionUnlocked error for $pkg: ${t.message}")
                }
            } else {
                Log.d(TAG, "Keeping session-unlock for $pkg (internal nav)")
            }
        }

        if (latestForegroundPackage != null) {
            handleForegroundChange(latestForegroundPackage)
        }
    }

    private fun handleForegroundChange(pkg: String) {
        val myPkg = packageName
        val homePkg = getHomePackageName()

        if (pkg == myPkg || pkg == homePkg) {
            Log.d(TAG, "Ignoring package: $pkg")
            return
        }

        if (GestureStore.isSessionUnlocked(this, pkg)) {
            lastHandledForegroundPackage = pkg
            lastHandledAt = System.currentTimeMillis()
            Log.d(TAG, "Skipping $pkg because session-unlocked")
            return
        }

        if (lastHandledForegroundPackage == pkg) {
            val since = System.currentTimeMillis() - lastHandledAt
            if (since < REOPEN_WINDOW_MS) {
                Log.d(TAG, "Throttling re-open for $pkg ($since ms since last)")
                return
            }
        }

        val gestureName = GestureStore.getGestureNameForApp(this, pkg)
        Log.d(TAG, "Foreground detected: $pkg, gesture mapping: $gestureName")
        if (gestureName == null) {
            lastHandledForegroundPackage = null
            lastHandledAt = 0L
            return
        }

        val i = Intent(this, UnlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("package", pkg)

        try {
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            i.putExtra("appLabel", label)
        } catch (_: Exception) { /* ignore */ }

        try {
            startActivity(i)
            lastHandledForegroundPackage = pkg
            lastHandledAt = System.currentTimeMillis()
            Log.d(TAG, "Started UnlockActivity for $pkg")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start UnlockActivity for $pkg: ${t.message}")
        }
    }

    private fun getHomePackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
