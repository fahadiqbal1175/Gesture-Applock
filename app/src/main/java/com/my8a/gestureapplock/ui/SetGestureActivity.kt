package com.my8a.gestureapplock.ui

import android.gesture.Gesture
import android.gesture.GestureOverlayView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.databinding.ActivitySetGestureBinding

class SetGestureActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetGestureBinding
    private var packageNameExtra: String? = null
    private var appLabel: String? = null

    // Minimum samples required
    private val MIN_SAMPLES = 3

    // If true -> automatically finish the activity after MIN_SAMPLES have been saved.
    // If false -> user must press Done.
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
                    if (AUTO_CLOSE_AFTER_MIN_SAMPLES) {
                        // user is done — finish and return to app list
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
            Toast.makeText(this, "Gesture setup complete", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClear.setOnClickListener {
            binding.gestureView.clear(false)
        }

        binding.btnRemoveAll.setOnClickListener {
            val pkg = packageNameExtra ?: return@setOnClickListener
            val ok = GestureStore.removeAllGesturesForApp(this, pkg)
            if (ok) {
                Toast.makeText(this, "All samples removed", Toast.LENGTH_SHORT).show()
                updateSampleCount()
            } else {
                // Even if library remove fails, mapping is cleared by function
                Toast.makeText(this, "Removed mapping (library removal may be limited on device).", Toast.LENGTH_SHORT).show()
                updateSampleCount()
            }
        }
    }

    private fun updateSampleCount() {
        val pkg = packageNameExtra
        val count = if (pkg != null) GestureStore.getSamplesCount(this, pkg) else 0
        binding.sampleCount.text = "Samples: $count"
    }
}
