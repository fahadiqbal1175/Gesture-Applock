package com.my8a.gestureapplock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.my8a.gestureapplock.data.AppUtils
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.InstalledApp
import com.my8a.gestureapplock.databinding.ActivityMainBinding
import com.my8a.gestureapplock.service.ForegroundAppMonitorService

class MainActivity : AppCompatActivity(), AppListAdapter.OnAppActionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private val apps = mutableListOf<InstalledApp>()

    // Prevent starting the monitor service repeatedly
    private var monitoringStarted = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notification permission denied. Monitoring may be limited.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(apps, this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // wire search view to adapter filtering
        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText)
                return true
            }
        })


        // Overlay permission check for MIUI/Xiaomi
        checkOverlayPermission()

        binding.btnUsageAccess.setOnClickListener {
            // open Usage Access settings so user can grant permission manually
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.btnStartMonitoring.setOnClickListener {
            ensureNotificationPermission()
            startMonitorService()
        }

        loadApps()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please allow overlay permission for unlock screen to work.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startMonitorService() {
        if (monitoringStarted) {
            Toast.makeText(this, "Monitoring already started", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ForegroundAppMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        monitoringStarted = true
        Toast.makeText(this, "Monitoring started. Grant Usage Access if required.", Toast.LENGTH_LONG).show()
    }

    private fun loadApps() {
        apps.clear()
        apps.addAll(AppUtils.getInstalledApps(this).map { app ->
            val gestureName = GestureStore.getGestureNameForApp(this, app.packageName)
            app.locked = gestureName != null
            app
        })
        adapter.updateApps(apps)
        // Use the instruction TextView as our "empty" indicator
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
        // clear search text so user sees full list when returning
        // comment out if you prefer search state to persist
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
    }


    // AppListAdapter callbacks
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
