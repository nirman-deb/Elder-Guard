package com.tokdoi.elderguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            val className = event.className?.toString() ?: ""
            val eventText = event.text?.joinToString(" ")?.lowercase() ?: ""

            // Whitelist Google Play Store
            if (packageName == "com.android.vending") return

            // The Fix: Detect and allow uninstalls
            if (className.contains("uninstall", ignoreCase = true) || eventText.contains("uninstall")) {
                return
            }

            // Block raw APK installations
            if (packageName.contains("packageinstaller", ignoreCase = true)) {
                val intent = Intent(this, DangerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("reason", "Unauthorized APK installation attempt blocked")
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {}
}