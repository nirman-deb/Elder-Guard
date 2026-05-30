package com.tokdoi.elderguard

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.ai.client.generativeai.GenerativeModel

class NotificationScannerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val originalMessage = "$title: $text"
        val content = originalMessage.lowercase()

        if (content.trim().isEmpty()) return

        val safeBankDomains = listOf("kotak.com", "hdfcbank.com", "icicibank.com", "sbi.co.in", "onlinesbi.sbi", "axisbank.com", "pnbindia.in")
        if (safeBankDomains.any { content.contains(it) }) {
            return
        }

        val hasLink = content.contains("http") ||
                content.contains("www.") ||
                content.contains(".com") ||
                content.contains("bit.ly") ||
                content.contains(".apk")

        if (!hasLink) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val prompt = "Analyze this message containing a link. Is it a malicious phishing attempt, a scam, or a rogue APK download? Do not flag normal informational links or real bank transaction alerts. Reply strictly with the exact word 'YES' if it is a severe threat, or 'NO' if it is safe. Message: $originalMessage"

                val response = generativeModel.generateContent(prompt)
                val resultText = response.text?.trim()?.uppercase()?.replace(Regex("[^A-Z]"), "") ?: ""

                Log.d("ElderGuard", "Gemini Result: $resultText")

                if (resultText == "YES") {
                    triggerBlockScreen("Malicious activity detected in the sms \"$originalMessage\"")
                }

            } catch (e: Exception) {
                Log.e("ElderGuard", "Gemini API Error: ${e.message}")

                val sketchyWords = listOf("lottery", "electricity disconnect", "kyc suspended", "pan blocked", "free recharge")
                if (sketchyWords.any { content.contains(it) }) {
                    triggerBlockScreen("Malicious activity detected (Offline scan) in the sms \"$originalMessage\"")
                }
            }
        }
    }

    private fun triggerBlockScreen(reason: String) {
        val intent = Intent(this@NotificationScannerService, DangerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("reason", reason)
        }
        startActivity(intent)
    }
}