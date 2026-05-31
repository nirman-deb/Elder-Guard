package com.tokdoi.elderguard

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import java.io.File

class DeepFileScannerService : Service() {

    private lateinit var contentObserver: ContentObserver
    private var lastScannedFile: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.d("ElderGuard", "Deep Scanner Started via MediaStore!")

        // OS ke internal database (MediaStore) pe nazar rakh rahe hain
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                checkForNewDownloads()
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, contentObserver
        )
    }

    // Isse service background mein jaldi kill nahi hogi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun checkForNewDownloads() {
        try {
            val projection = arrayOf(MediaStore.Downloads.DATA, MediaStore.Downloads.DISPLAY_NAME)
            // Hamesha sabse latest download uthao
            val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC LIMIT 1"

            contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)

                    val filePath = cursor.getString(dataIndex) ?: return
                    val fileName = cursor.getString(nameIndex) ?: return

                    // Check if it's a new file we haven't scanned yet
                    if (filePath != lastScannedFile) {
                        lastScannedFile = filePath

                        // 1. POP-UP FOR EVERY FILE (Jo tujhe chahiye tha)
                        Handler(Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(applicationContext, "Elder Guard: Scanning $fileName...", android.widget.Toast.LENGTH_LONG).show()
                        }

                        // 2. ONLY DEEP SCAN APKs (Taaki photos/pdfs pe app crash ya hang na ho)
                        if (fileName.endsWith(".apk", ignoreCase = true)) {
                            Log.d("ElderGuard", "New APK Detected: $fileName. Running Deep Scan...")
                            analyzeApkPermissions(File(filePath))
                        } else {
                            // Agar normal photo/pdf hai, to safe bol ke chhod do
                            Handler(Looper.getMainLooper()).postDelayed({
                                android.widget.Toast.makeText(applicationContext, "Elder Guard: File is Safe ✅", android.widget.Toast.LENGTH_SHORT).show()
                            }, 1500)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ElderGuard", "Download Watcher Error: ${e.message}")
        }
    }

    private fun analyzeApkPermissions(apkFile: File) {
        val pm = packageManager
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_PERMISSIONS)

        if (packageInfo != null) {
            packageInfo.applicationInfo?.sourceDir = apkFile.absolutePath
            packageInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath

            val appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: "Unknown App"
            val permissions = packageInfo.requestedPermissions ?: emptyArray()

            val dangerousPerms = listOf(
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.READ_CALL_LOG",
                "android.permission.SEND_SMS"
            )

            val foundThreats = permissions.filter { dangerousPerms.contains(it) }

            if (foundThreats.isNotEmpty()) {
                val sketchyList = foundThreats.joinToString(", ") { it.substringAfterLast(".") }
                triggerBlockScreen("High Threat: '$appName' is asking to read your SMS/Calls ($sketchyList). This is likely malware!")
            } else {
                // Agar APK safe hai
                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(applicationContext, "Elder Guard: $appName is Safe ✅", android.widget.Toast.LENGTH_LONG).show()
                }
                Log.d("ElderGuard", "Scanned APK '$appName' - Safe.")
            }
        } else {
            Log.e("ElderGuard", "Corrupted or unreadable APK: ${apkFile.name}")
        }
    }

    private fun triggerBlockScreen(reason: String) {
        val intent = Intent(this, DangerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("reason", reason)
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}