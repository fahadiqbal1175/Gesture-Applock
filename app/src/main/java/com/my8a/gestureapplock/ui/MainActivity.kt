package com.my8a.gestureapplock.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.my8a.gestureapplock.data.AppUtils
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.InstalledApp
import com.my8a.gestureapplock.data.Prefs
import com.my8a.gestureapplock.databinding.ActivityMainBinding
import com.my8a.gestureapplock.service.ForegroundAppMonitorService

class MainActivity : AppCompatActivity(), AppListAdapter.OnAppActionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private val apps = mutableListOf<InstalledApp>()

    // runtime launcher for POST_NOTIFICATIONS on Android 13+
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Prefs.setNotificationGranted(this, granted)
            if (!granted) {
                Toast.makeText(this, "Notification permission denied. Monitoring notification may be limited.", Toast.LENGTH_SHORT).show()
            }
            // After the notification result, update status text and try to start monitor if possible
            updateStatusText()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(apps, this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.searchView?.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText)
                return true
            }
        })


        // Start the permission sequence:
        // 1) Usage -> 2) Overlay -> 3) Notification
        if (!Prefs.isUsageGranted(this) && !hasUsageStatsPermission()) {
            promptForUsageAccessIfNeeded()
        } else {
            Prefs.setUsageGranted(this, true)
            if (!Prefs.isOverlayGranted(this) && !hasOverlayPermission()) {
                promptForOverlayIfNeeded()
            } else {
                Prefs.setOverlayGranted(this, true)
                if (!Prefs.isNotificationGranted(this) && !hasNotificationPermission()) {
                    promptForNotificationIfNeeded()
                } else {
                    Prefs.setNotificationGranted(this, true)
                }
            }
        }

        loadApps()
    }

    // ---------------- permission helpers ----------------

    private fun hasUsageStatsPermission(): Boolean {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            return try {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
                stats != null && stats.isNotEmpty()
            } catch (t: Throwable) {
                false
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            // before Android 13 permission not required
            true
        }
    }

    private fun promptForUsageAccessIfNeeded() {
        if (hasUsageStatsPermission()) {
            Prefs.setUsageGranted(this, true)
            // continue chain
            if (!Prefs.isOverlayGranted(this) && !hasOverlayPermission()) promptForOverlayIfNeeded()
            return
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Enable Usage Access")
            .setMessage("Gesture AppLock needs Usage Access to detect which app is in front. Please enable it in the next screen.")
            .setPositiveButton("Open settings") { _: DialogInterface, _: Int ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }

    private fun promptForOverlayIfNeeded() {
        if (hasOverlayPermission()) {
            Prefs.setOverlayGranted(this, true)
            // continue chain
            if (!Prefs.isNotificationGranted(this) && !hasNotificationPermission()) promptForNotificationIfNeeded()
            return
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Enable Overlay Permission")
            .setMessage("Gesture AppLock needs permission to draw on top of apps so it can show the unlock screen. Please enable it in the next screen.")
            .setPositiveButton("Open settings") { _: DialogInterface, _: Int ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }

    private fun promptForNotificationIfNeeded() {
        // If already granted, set flag and return
        if (hasNotificationPermission()) {
            Prefs.setNotificationGranted(this, true)
            return
        }

        // On Android 13+, request runtime permission; on older devices, open notifications settings
        val dlg = AlertDialog.Builder(this)
            .setTitle("Enable Notifications")
            .setMessage("Gesture AppLock needs notification permission so the monitoring notification can be shown. Please grant it in the next screen.")
            .setPositiveButton("Grant") { _: DialogInterface, _: Int ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // launcher will update Prefs in callback
                    requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // open the app notification settings page
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }

    // ---------------- start monitor ----------------

    /**
     * Start the foreground monitor service safely (checks flags). This method now
     * verifies Usage, Overlay and Notification permission before starting.
     */
    fun startMonitorServiceIfNotRunning() {
        if (Prefs.isMonitoringStarted(this)) return

        if (!hasUsageStatsPermission()) {
            promptForUsageAccessIfNeeded()
            return
        } else {
            Prefs.setUsageGranted(this, true)
        }

        if (!hasOverlayPermission()) {
            promptForOverlayIfNeeded()
            return
        } else {
            Prefs.setOverlayGranted(this, true)
        }

        if (!hasNotificationPermission()) {
            promptForNotificationIfNeeded()
            return
        } else {
            Prefs.setNotificationGranted(this, true)
        }

        // start service
        val intent = Intent(this, ForegroundAppMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Prefs.setMonitoringStarted(this, true)
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    // ---------------- UI / apps ----------------

    private fun loadApps() {
        apps.clear()
        apps.addAll(
            AppUtils.getInstalledApps(this).map { app ->
                val gestureName = GestureStore.getGestureNameForApp(this, app.packageName)
                app.locked = gestureName != null
                app
            }
                .sortedWith(
                    compareByDescending<InstalledApp> { it.locked }
                        .thenBy { it.label.lowercase() }
                )
        )
        adapter.updateApps(apps)

        if (apps.isEmpty()) {
            binding.instruction.visibility = View.VISIBLE
            binding.instruction.text = "No apps found"
        } else {
            binding.instruction.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()

        // Re-check permissions and update Prefs flags (user might have returned from settings)
        if (hasUsageStatsPermission()) Prefs.setUsageGranted(this, true)
        if (hasOverlayPermission()) Prefs.setOverlayGranted(this, true)
        if (hasNotificationPermission()) Prefs.setNotificationGranted(this, true)

        updateStatusText()
    }

    private fun updateStatusText() {
        when {
            !hasUsageStatsPermission() -> {
                binding.statusText.text = "Usage Access required — tap to enable"
                binding.statusText.setOnClickListener { promptForUsageAccessIfNeeded() }
            }
            !hasOverlayPermission() -> {
                binding.statusText.text = "Overlay permission required — tap to enable"
                binding.statusText.setOnClickListener { promptForOverlayIfNeeded() }
            }
            !hasNotificationPermission() -> {
                binding.statusText.text = "Notification permission required — tap to enable"
                binding.statusText.setOnClickListener { promptForNotificationIfNeeded() }
            }
            Prefs.isMonitoringStarted(this) -> {
                binding.statusText.text = "Monitoring is active"
                binding.statusText.setOnClickListener(null)
            }
            else -> {
                binding.statusText.text = "Set a gesture for any app to start monitoring automatically"
                binding.statusText.setOnClickListener(null)
            }
        }
    }

    // ---------------- adapter callbacks ----------------

    override fun onSetGesture(app: InstalledApp) {
        val i = Intent(this, SetGestureActivity::class.java)
        i.putExtra("package", app.packageName)
        i.putExtra("appLabel", app.label)
        startActivity(i)
    }

    override fun onRemoveGesture(app: InstalledApp) {
        GestureStore.clearGestureForApp(this, app.packageName)
        loadApps()
    }

    override fun onToggleLock(app: InstalledApp, enable: Boolean) {
        if (enable) onSetGesture(app) else onRemoveGesture(app)
    }
}
