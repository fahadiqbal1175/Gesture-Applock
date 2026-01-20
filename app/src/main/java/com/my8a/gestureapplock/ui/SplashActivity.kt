package com.my8a.gestureapplock.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.my8a.gestureapplock.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set splash layout
        setContentView(R.layout.activity_splash)

        // Delay only to show logo briefly (not blocking real loading)
        Handler(Looper.getMainLooper()).postDelayed({

            // Move to MainActivity
            startActivity(Intent(this, MainActivity::class.java))

            // Close splash so user can’t return to it
            finish()

        }, 1500) // 1.5 seconds (you can change this)
    }
}
