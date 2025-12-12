package com.my8a.gestureapplock.ui

import android.content.Intent
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.gesture.Gesture
import android.content.Context
import android.net.Uri
import android.gesture.GestureOverlayView
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.Prefs
import com.my8a.gestureapplock.databinding.ActivitySetGestureBinding
import com.my8a.gestureapplock.service.ForegroundAppMonitorService

class SetGestureActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetGestureBinding
    private var packageNameExtra: String? = null
    private var appLabel: String? = null

    private val MIN_SAMPLES = 3
    private val AUTO_CLOSE_AFTER_MIN_SAMPLES = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetGestureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageNameExtra = intent.getStringExtra("package")
        appLabel = intent.getStringExtra("appLabel")
        binding.titleText.text = "Set gesture for: ${appLabel ?: packageNameExtra}"

        updateSampleCount()

        binding.gestureView.addOnGestureListener(object : GestureOverlayView.OnGestureListener {
            override fun onGestureStarted(overlay: GestureOverlayView?, event: MotionEvent?) {}
            override fun onGesture(overlay: GestureOverlayView?, event: MotionEvent?) {}
            override fun onGestureEnded(overlay: GestureOverlayView?, event: MotionEvent?) {}
            override fun onGestureCancelled(overlay: GestureOverlayView?, event: MotionEvent?) {}
        })

        binding.btnSaveSample.setOnClickListener {
            val g: Gesture? = binding.gestureView.gesture
            if (g == null) {
                Toast.makeText(this, "Draw a gesture first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pkg = packageNameExtra ?: run {
                Toast.makeText(this, "Package missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = GestureStore.saveGestureForApp(this, pkg, g)
            if (ok) {
                val count = GestureStore.getSamplesCount(this, pkg)
                val need = (MIN_SAMPLES - count).coerceAtLeast(0)
                if (need > 0) {
                    Toast.makeText(this, "Sample saved. Draw $need more.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Enough samples saved.", Toast.LENGTH_SHORT).show()
                    ensurePermissionsAndStartMonitor()
                    if (AUTO_CLOSE_AFTER_MIN_SAMPLES) {
                        finish()
                        return@setOnClickListener
                    }
                }
                binding.gestureView.clear(false)
                updateSampleCount()
            } else {
                Toast.makeText(this, "Failed to save sample", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDone.setOnClickListener {
            val pkg = packageNameExtra ?: return@setOnClickListener
            val count = GestureStore.getSamplesCount(this, pkg)
            if (count < MIN_SAMPLES) {
                Toast.makeText(this, "Please save at least $MIN_SAMPLES samples. Current: $count", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensurePermissionsAndStartMonitor()
            Toast.makeText(this, "Gesture setup complete", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClear.setOnClickListener { binding.gestureView.clear(false) }

        binding.btnRemoveAll.setOnClickListener {
            val pkg = packageNameExtra ?: return@setOnClickListener
            val ok = GestureStore.removeAllGesturesForApp(this, pkg)
            if (ok) {
                Toast.makeText(this, "All samples removed", Toast.LENGTH_SHORT).show()
                updateSampleCount()
            } else {
                Toast.makeText(this, "Removed mapping (library removal may be limited on device).", Toast.LENGTH_SHORT).show()
                updateSampleCount()
            }
        }
    }

    private fun ensurePermissionsAndStartMonitor() {
        // Usage Access check (same as MainActivity)
        val hasUsage = try {
            val appOps = getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            try {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
                stats != null && stats.isNotEmpty()
            } catch (_: Throwable) { false }
        }

        if (!hasUsage) {
            AlertDialog.Builder(this)
                .setTitle("Usage Access required")
                .setMessage("Please grant Usage Access first. You will be taken to settings.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        } else {
            Prefs.setUsageGranted(this, true)
        }

        // Overlay check
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (!hasOverlay) {
            AlertDialog.Builder(this)
                .setTitle("Overlay permission required")
                .setMessage("Please allow the app to draw over other apps so the unlock screen can appear. You will be taken to settings.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        } else {
            Prefs.setOverlayGranted(this, true)
        }

        // Notification check: request or open settings
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasNotif) {
            // Friendly dialog to request notification permission (MainActivity shows the sequence too)
            AlertDialog.Builder(this)
                .setTitle("Notification permission required")
                .setMessage("Gesture AppLock needs notification permission so the monitoring notification can be shown. Grant it in the next screen.")
                .setPositiveButton("Open settings") { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // No launcher here; open app notification settings as fallback
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    } else {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        } else {
            Prefs.setNotificationGranted(this, true)
        }

        // both perms satisfied -> start monitor if not already started
        if (!Prefs.isMonitoringStarted(this)) {
            val svc = Intent(this, ForegroundAppMonitorService::class.java)
            ContextCompat.startForegroundService(this, svc)
            Prefs.setMonitoringStarted(this, true)
        }
    }

    private fun updateSampleCount() {
        val pkg = packageNameExtra
        val count = if (pkg != null) GestureStore.getSamplesCount(this, pkg) else 0
        binding.sampleCount.text = "Samples: $count"
    }
}