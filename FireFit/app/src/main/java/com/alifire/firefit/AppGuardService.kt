package com.alifire.firefit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppGuardService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.alifire.firefit") return
        val prefs = getSharedPreferences("firefit", MODE_PRIVATE)
        val selected = prefs.getStringSet("selected", emptySet()) ?: emptySet()
        if (pkg !in selected || System.currentTimeMillis() < prefs.getLong("until", 0L)) return
        if (prefs.getString("pending_package", null) == pkg) return
        prefs.edit().putString("pending_package", pkg).apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("blocked_package", pkg)
        })
    }
    override fun onInterrupt() {}
}
