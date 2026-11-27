package com.alifire.firefit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppGuardService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val prefs = getSharedPreferences("firefit", MODE_PRIVATE)
        if (prefs.getBoolean("challenge_active", false)) return
        if (System.currentTimeMillis() < prefs.getLong("until", 0L)) return

        val selected = prefs.getStringSet("selected", emptySet()) ?: emptySet()
        if (pkg !in selected) return

        // Set the guard before launching: window-state events can arrive several times.
        prefs.edit()
            .putString("pending_package", pkg)
            .putBoolean("challenge_active", true)
            .apply()

        try {
            startActivity(Intent(this, PlankActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", pkg)
            })
        } catch (_: Exception) {
            prefs.edit()
                .putBoolean("challenge_active", false)
                .remove("pending_package")
                .apply()
        }
    }

    override fun onInterrupt() {
        // No ongoing gesture stream to stop.
    }
}
