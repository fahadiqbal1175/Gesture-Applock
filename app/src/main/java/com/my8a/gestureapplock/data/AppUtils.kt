package com.my8a.gestureapplock.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppUtils {
    /**
     * Return list of apps that are launchable (appear in launcher).
     * This includes user-installed apps and preinstalled launchable apps but
     * avoids internal-only system packages.
     */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val me = context.packageName
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // query the system for activities that respond to the launcher intent
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        // use a set to avoid duplicates by package
        val seen = LinkedHashSet<String>()
        val apps = mutableListOf<InstalledApp>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            if (pkg == me) continue // skip ourselves
            if (seen.contains(pkg)) continue
            seen.add(pkg)
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo)?.toString() ?: pkg
                val icon = pm.getApplicationIcon(appInfo)
                apps.add(InstalledApp(label, pkg, icon, locked = false))
            } catch (_: Exception) {
                // skip problematic package
            }
        }

        apps.sortBy { it.label.lowercase() }
        return apps
    }
}
