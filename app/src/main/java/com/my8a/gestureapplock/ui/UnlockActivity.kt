package com.my8a.gestureapplock.ui

import android.content.Intent
import android.gesture.Gesture
import android.gesture.Prediction
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.databinding.ActivityUnlockBinding
import android.window.OnBackInvokedCallback

class UnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockBinding
    private var targetPackage: String? = null
    private val TAG = "UnlockActivity"

    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var onBackPressedCallback: OnBackPressedCallback? = null

    // threshold as before
    private val THRESHOLD = 2.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Important flags so the activity appears above other apps
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // handle initial intent (non-nullable)
        intent?.let { applyIntent(it) }

        binding.gestureOverlay.addOnGesturePerformedListener { _, gesture ->
            onGesturePerformed(gesture)
        }

        binding.btnCancel.setOnClickListener { finish() }
        registerBackHandlers()
    }

    // Correct signature: non-null Intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent received")
        applyIntent(intent)
    }

    private fun applyIntent(i: Intent) {
        targetPackage = i.getStringExtra("package")
        val label = i.getStringExtra("appLabel") ?: targetPackage
        binding.titleText.text = "Unlock ${label ?: ""}"
        // reset UI for new package
        binding.gestureOverlay.clear(false)
    }

    private fun registerBackHandlers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = OnBackInvokedCallback { /* consume back */ }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0, backInvokedCallback!!)
        } else {
            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* consume back */ }
            }
            onBackPressedDispatcher.addCallback(this, onBackPressedCallback!!)
        }
    }

    private fun unregisterBackHandlers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backInvokedCallback = null
        } else {
            onBackPressedCallback?.remove()
            onBackPressedCallback = null
        }
    }

    private fun onGesturePerformed(gesture: Gesture) {
        val pkg = targetPackage
        if (pkg == null) {
            showFail()
            return
        }

        val preds: List<Prediction> = GestureStore.recognise(this, gesture)
        Log.d(TAG, "Preds: ${preds.joinToString { "${it.name}:${it.score}" }}")
        if (preds.isEmpty()) { showFail(); return }

        // matching predictions only for this package
        val matching = preds.filter { it.name == pkg }
        if (matching.isEmpty()) { showFail(); return }

        val topMatch = matching.maxByOrNull { it.score } ?: run { showFail(); return }

        if (topMatch.score >= THRESHOLD) {
            // set session-unlocked for this package
            GestureStore.setSessionUnlocked(this, pkg, true)
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            showFail()
        }
    }

    private fun showFail() {
        Toast.makeText(this, "Gesture not recognized — try again", Toast.LENGTH_SHORT).show()
        binding.gestureOverlay.clear(false)
    }

    override fun onDestroy() {
        unregisterBackHandlers()
        super.onDestroy()
    }
}
