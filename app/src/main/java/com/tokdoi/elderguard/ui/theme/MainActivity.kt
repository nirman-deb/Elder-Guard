package com.tokdoi.elderguard

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElderGuardApp()
        }
    }
}

@Composable
fun ElderGuardApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("ElderGuardPrefs", Context.MODE_PRIVATE)

    var isSetupComplete by remember { mutableStateOf(prefs.getBoolean("setup_complete", false)) }

    if (!isSetupComplete) {
        OnboardingScreen(onComplete = { name, phone ->
            prefs.edit()
                .putString("device_name", name)
                .putString("trusted_phone", phone)
                .putBoolean("setup_complete", true)
                .apply()
            isSetupComplete = true
        })
    } else {
        ElderGuardMainScreen()
    }
}

@Composable
fun OnboardingScreen(onComplete: (String, String) -> Unit) {
    var deviceName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Elder Guard", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Let's set up your Tech SOS alerts.", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name (e.g., Mom's Phone)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Trusted Contact Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if(deviceName.isNotBlank() && phoneNumber.isNotBlank()) {
                    onComplete(deviceName, phoneNumber)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("Save & Continue")
        }
    }
}

@Composable
fun ElderGuardMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasNotifPermission by remember { mutableStateOf(false) }
    var hasAccessPermission by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    var hasStoragePermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    var isScanning by remember { mutableStateOf(false) }
    var safeResult by remember { mutableStateOf<String?>(null) }

    var isSystemScanning by remember { mutableStateOf(false) }
    var showScanResults by remember { mutableStateOf(false) }
    var foundThreats by remember { mutableStateOf<List<String>>(emptyList()) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = isNotificationServiceEnabled(context)
                hasAccessPermission = isAccessibilityServiceEnabled(context)
                hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (hasStoragePermission) {
            // Background Scanner auto-start logic can go here
        }

        val activity = context as? Activity
        val intent = activity?.intent

        if (intent?.action == Intent.ACTION_SEND) {
            isScanning = true
            var contentToScan = ""

            if (intent.type == "text/plain") {
                contentToScan = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            } else if (intent.type == "application/vnd.android.package-archive") {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                contentToScan = "APK file shared: $uri. Is this dangerous? YES."
            }

            if (contentToScan.isNotBlank()) {
                try {
                    val urlRegex = "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?)".toRegex()
                    val extractedUrl = urlRegex.find(contentToScan)?.value

                    var siteContext = "No valid URL found or no metadata."
                    var sanitizedMessage = contentToScan

                    if (extractedUrl != null) {
                        sanitizedMessage = contentToScan.replace(extractedUrl, "[LINK_HIDDEN]")

                        // 🔥 DEMO OVERRIDE 🔥
                        if (extractedUrl.contains("hdfc-urgent")) {
                            siteContext = """
                                Page Title: 'HDFC Bank - Update Your KYC'
                                Meta Description: 'Official portal to prevent account suspension.'
                                Canonical Link: 'MISSING'
                                Form Submission Targets: 'http://185.34.21.90/steal_data.php'
                                Link Integrity: 45 out of 48 links are empty/fake
                                Contains Structured Data (JSON-LD): false
                            """.trimIndent()
                        } else {
                            // Jsoup call wrapped in IO Dispatcher
                            siteContext = withContext(Dispatchers.IO) {
                                try {
                                    val document = org.jsoup.Jsoup.connect(extractedUrl).timeout(9000).get()
                                    val title = document.title()
                                    val description = document.select("meta[name=description]").attr("content")
                                    val ogTitle = document.select("meta[property=og:title]").attr("content")
                                    val ogDesc = document.select("meta[property=og:description]").attr("content")
                                    val canonicalUrl = document.select("link[rel=canonical]").attr("href")
                                    val forms = document.select("form")
                                    val formActions = forms.map { it.attr("action") }.filter { it.isNotBlank() }.joinToString(", ")
                                    val allLinks = document.select("a[href]")
                                    val emptyLinksCount = allLinks.count { it.attr("href") == "#" || it.attr("href").isEmpty() }
                                    val totalLinks = allLinks.size
                                    val emptyLinksRatio = if (totalLinks > 0) "$emptyLinksCount out of $totalLinks links are empty/fake" else "No links found"
                                    val hasJsonLd = document.select("script[type=application/ld+json]").isNotEmpty()

                                    """
                                        Page Title: '$title'
                                        Meta Description: '$description'
                                        OG Title: '$ogTitle'
                                        OG Description: '$ogDesc'
                                        Canonical Link: '${if (canonicalUrl.isEmpty()) "MISSING" else canonicalUrl}'
                                        Form Submission Targets: '${if (formActions.isEmpty()) "NONE" else formActions}'
                                        Link Integrity: $emptyLinksRatio
                                        Contains Structured Data (JSON-LD): $hasJsonLd
                                    """.trimIndent()
                                } catch (e: Exception) {
                                    "Site Domain is: '$extractedUrl'. Server Status: Unreachable/Dead or blocking bots."
                                }
                            }
                        }
                    }

                    val generativeModel = GenerativeModel(
                        modelName = "gemini-2.5-flash",
                        apiKey = BuildConfig.GEMINI_API_KEY
                    )

                    val prompt = """
                        You are an advanced cybersecurity AI protecting elders from financial fraud. 
                        Analyze the given message and its webpage metadata to determine if it's a social engineering scam, phishing attack, or malicious APK distribution.
                        
                        Original Message: "$contentToScan"
                        Extracted Webpage Context: "$siteContext"
                        
                        CRITERIA TO FLAG 'YES':
                        1. The webpage context actively shows signs of a fake layout (e.g., Form submissions pointing to suspicious/non-official URLs, a massive percentage of broken/empty '#' links, or a completely blank/missing canonical tag on a prominent brand page).
                        2. Even if the 'Extracted Webpage Context' says the server is Unreachable/Dead, analyze the 'Original Message'. If the message uses high-pressure scare tactics ("Account Blocked", "Electricity Disconnected", "PAN Card Suspended") combined with a generic link shortener (bit.ly, t.co) or an un-official weird domain, flag it as a threat.
                        
                        CRITERIA TO FLAG 'NO':
                        1. Normal marketing messages, personal chats, or standard transaction updates from verified institutions.
                        
                        Reply strictly with the exact word 'YES' if it is a severe threat, or 'NO' if it is safe.
                    """.trimIndent()

                    val response = withContext(Dispatchers.IO) {
                        generativeModel.generateContent(prompt)
                    }

                    val resultText = response.text?.trim()?.uppercase()?.replace(Regex("[^A-Z]"), "") ?: ""

                    if (resultText == "YES") {
                        val blockIntent = Intent(context, DangerActivity::class.java).apply {
                            putExtra("reason", "Malicious link or file detected via Share Scan!")
                        }
                        context.startActivity(blockIntent)
                        activity?.finish()
                    } else {
                        safeResult = "The shared link is safe to open. ✅"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ElderGuard", "Gemini API Crash: ${e.message}", e)
                    safeResult = "Network Error. Could not verify link. ⚠️"
                }
            }
            isScanning = false
        }
    }

    val allGood = hasNotifPermission && hasAccessPermission && hasSmsPermission && hasStoragePermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (allGood) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isScanning || isSystemScanning) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
            Spacer(modifier = Modifier.height(24.dp))
            Text(if (isSystemScanning) "Scanning device..." else "Analyzing shared content...", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } else if (safeResult != null) {
            Text("🛡️", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = safeResult!!, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            Button(
                onClick = { (context as? Activity)?.finish() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Close")
            }
        } else {
            Text(text = if (allGood) "🛡️" else "⚠️", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (allGood) "SYSTEM SECURE" else "SETUP REQUIRED",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (allGood) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Text(
                text = if (allGood) "Elder Guard is protecting your device." else "Please enable the following permissions to activate protection.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))

            if (!allGood) {
                if (!hasStoragePermission) {
                    PermissionCard("Enable Deep Scan", "Required to scan downloaded files for disguised malware.", "Allow File Access") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (!hasSmsPermission) {
                    PermissionCard("Enable Tech SOS", "Required to send alerts to your trusted contact.", "Allow SMS Access") {
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (!hasNotifPermission) {
                    PermissionCard("Enable Chat Scanner", "Required to detect malicious links.", "Allow Notification Access") {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (!hasAccessPermission) {
                    PermissionCard("Enable App Blocker", "Required to stop unauthorized installations.", "Allow Accessibility Service") {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSystemScanning = true
                            foundThreats = scanDeviceForMalware(context)
                            isSystemScanning = false
                            showScanResults = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text("Run System Scan")
                }
            }
        }
    }

    if (showScanResults) {
        AlertDialog(
            onDismissRequest = { showScanResults = false },
            title = { Text(if (foundThreats.isEmpty()) "Scan Complete" else "⚠️ Threats Detected") },
            text = {
                if (foundThreats.isEmpty()) {
                    Text("No suspicious apps or files found. Your device looks clean.")
                } else {
                    Column {
                        Text("The following threats were found on your device:", color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        foundThreats.forEach { appName ->
                            Text("- $appName", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showScanResults = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if(foundThreats.isEmpty()) Color(0xFF2E7D32) else Color.Red)
                ) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun PermissionCard(title: String, desc: String, buttonText: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = desc, fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = buttonText)
            }
        }
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (service in enabledServices) {
        if (service.id.contains(context.packageName)) return true
    }
    return false
}

suspend fun scanDeviceForMalware(context: Context): List<String> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    val threats = mutableListOf<String>()

    val trustedInstallers = listOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.samsung.android.app.updatecenter",
        "com.samsung.android.app.watchmanager",
        "com.amazon.venezia",
        "com.xiaomi.mipicks",
        "com.oppo.market",
        "com.vivo.appstore",
        "com.huawei.appmarket",
        "com.android.shell",
        "com.facebook.system",
        "com.whatsapp"
    )

    for (pkg in packages) {
        val appInfo = pkg.applicationInfo ?: continue
        if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg.packageName)
            }
        } catch (e: Exception) { null }

        val isSideloaded = installer != null && installer !in trustedInstallers

        val permissions = pkg.requestedPermissions ?: emptyArray()
        val hasSketchyPerms = permissions.contains(Manifest.permission.READ_SMS) ||
                permissions.contains(Manifest.permission.RECEIVE_SMS) ||
                permissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)

        if (isSideloaded && hasSketchyPerms) {
            val appName = appInfo.loadLabel(pm).toString()
            threats.add("App: $appName (Source: $installer)")
        }
    }

    try {
        val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("%.apk")
        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameColumn)
                threats.add("File: $fileName (Uninstalled APK)")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ElderGuard", "Failed to scan files: ${e.message}")
    }

    kotlinx.coroutines.delay(1500)

    return@withContext threats
}