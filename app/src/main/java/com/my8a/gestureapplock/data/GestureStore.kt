package com.my8a.gestureapplock.data

import android.content.Context
import android.gesture.Gesture
import android.gesture.GestureLibraries
import android.gesture.GestureLibrary
import android.gesture.Prediction
import android.util.Log
import java.io.File

object GestureStore {
    private const val LIB_FILE = "gesturestore"
    private const val PREFS = "gesture_prefs"
    private const val KEY_PREFIX = "gesture_for_"
    private const val KEY_SESSION_UNLOCK_PREFIX = "session_unlocked_"

    private fun getLibFile(context: Context): File = File(context.filesDir, LIB_FILE)

    private fun openLibrary(context: Context): GestureLibrary {
        val file = getLibFile(context)
        if (!file.exists()) {
            try { file.createNewFile() } catch (t: Throwable) { Log.w("GestureStore", "create file failed: ${t.message}") }
        }
        val lib = GestureLibraries.fromFile(file)
        try { lib.load() } catch (t: Throwable) { Log.w("GestureStore", "load failed: ${t.message}") }
        return lib
    }

    fun saveGestureForApp(context: Context, packageName: String, gesture: Gesture): Boolean {
        val lib = openLibrary(context)
        lib.addGesture(packageName, gesture)
        val ok = try { lib.save() } catch (t: Throwable) { Log.w("GestureStore", "save failed: ${t.message}"); false }
        if (!ok) Log.w("GestureStore", "Failed to save gesture library")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + packageName, packageName)
            .apply()
        return ok
    }

    fun getGestureNameForApp(context: Context, packageName: String): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + packageName, null)
    }

    fun recognise(context: Context, gesture: Gesture): List<Prediction> {
        val lib = openLibrary(context)
        return try { lib.recognize(gesture) } catch (t: Throwable) { emptyList() }
    }

    fun getSamplesCount(context: Context, packageName: String): Int {
        val lib = openLibrary(context)
        return try {
            val entries = lib.gestureEntries
            if (entries.contains(packageName)) {
                lib.getGestures(packageName)?.size ?: 0
            } else 0
        } catch (t: Throwable) {
            0
        }
    }

    fun removeAllGesturesForApp(context: Context, packageName: String): Boolean {
        val lib = openLibrary(context)
        val removed = try {
            lib.removeEntry(packageName)
            true
        } catch (t: Throwable) {
            Log.w("GestureStore", "removeEntry failed: ${t.message}")
            false
        }
        val ok = try { lib.save() } catch (t: Throwable) { Log.w("GestureStore", "save after remove failed: ${t.message}"); false }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + packageName)
            .apply()
        return removed && ok
    }

    fun clearGestureForApp(context: Context, packageName: String) {
        removeAllGesturesForApp(context, packageName)
    }

    // ---- Session unlock helpers ----

    /** Mark session unlocked (true) or clear (false) */
    fun setSessionUnlocked(context: Context, packageName: String, unlocked: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (unlocked) prefs.putBoolean(KEY_SESSION_UNLOCK_PREFIX + packageName, true)
        else prefs.remove(KEY_SESSION_UNLOCK_PREFIX + packageName)
        prefs.apply()
    }

    /** Return true if session unlocked (i.e., user unlocked and hasn't left app yet) */
    fun isSessionUnlocked(context: Context, packageName: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SESSION_UNLOCK_PREFIX + packageName, false)
    }

    /** Clear session unlocked (helper) */
    fun clearSessionUnlocked(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_UNLOCK_PREFIX + packageName)
            .apply()
    }
}
