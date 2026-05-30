package com.tokdoi.elderguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class DangerActivity : ComponentActivity() {

    private var threatReason: String = "Unknown threat detected"
    private var trustedPhoneNumber: String = ""
    private var deviceName: String = "This Device"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        threatReason = intent.getStringExtra("reason") ?: "Unknown threat detected"

        val prefs = getSharedPreferences("ElderGuardPrefs", Context.MODE_PRIVATE)
        trustedPhoneNumber = prefs.getString("trusted_phone", "") ?: ""
        deviceName = prefs.getString("device_name", "This Device") ?: "This Device"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            sendTechSos(trustedPhoneNumber, deviceName, threatReason)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 101)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "⚠️ THREAT BLOCKED!"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val reasonText = TextView(this).apply {
            text = "Reason: $threatReason"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(reasonText)

        setContentView(layout)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendTechSos(trustedPhoneNumber, deviceName, threatReason)
            } else {
                Toast.makeText(this, "SMS Permission Denied. SOS not sent.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendTechSos(phoneNumber: String, deviceName: String, threatDetail: String) {
        if (phoneNumber.isBlank()) return

        try {
            val smsManager: SmsManager = this.getSystemService(SmsManager::class.java)

            val message = "🚨 Elder Guard SOS from $deviceName:\n\nA severe threat was just blocked.\n\nDetails: $threatDetail \n\nPlease call them immediately to ensure they are safe."

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)

            Log.d("ElderGuard", "Tech SOS SMS sent successfully to $phoneNumber")
            Toast.makeText(this, "Alert sent to trusted contact", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ElderGuard", "Failed to send Tech SOS: ${e.message}")
        }
    }
}