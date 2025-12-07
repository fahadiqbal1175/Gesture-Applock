package com.my8a.gestureapplock.data

import android.graphics.drawable.Drawable

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var locked: Boolean = false
)