package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.theme.WaterBlue
import com.example.util.GoogleDriveSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AccentOrange
import com.example.util.AppBlockHelper
import kotlinx.coroutines.delay

@Composable
fun PermissionOnboardingView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Permission States
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var hasDrivePermission by remember { mutableStateOf(false) }
    var hasPackageInstallPermission by remember { mutableStateOf(false) }
    var hasExactAlarmPermission by remember { mutableStateOf(false) }
    
    var showDriveOnboardingPrompt by remember { mutableStateOf(false) }
    var hasAskedDriveOnboardingPrompt by remember { mutableStateOf(false) }

    // Drive automatic backup check & restore states
    var isCheckingDriveData by remember { mutableStateOf(false) }
    var driveCheckMessage by remember { mutableStateOf("") }
    var hasCheckedDriveData by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Check permissions helper
    val checkAllPermissions = {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasUsageStatsPermission = AppBlockHelper.hasUsageStatsPermission(context)
        hasAccessibilityPermission = AppBlockHelper.isAccessibilityServiceEnabled(context)
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        hasPackageInstallPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // Polling loop to dynamically detect system changes (e.g. when returning from Settings screen)
    LaunchedEffect(Unit) {
        while (true) {
            checkAllPermissions()
            delay(1000)
        }
    }

    // Auto-check Drive and retrieve if permission is acquired during installation
    LaunchedEffect(hasDrivePermission) {
        if (hasDrivePermission && !hasCheckedDriveData) {
            hasCheckedDriveData = true
            isCheckingDriveData = true
            driveCheckMessage = "🔍 Connecting to Google Drive to check for existing focus/db data..."
            try {
                val hasBackup = kotlinx.coroutines.withTimeoutOrNull(15000L) { GoogleDriveSyncManager.hasExistingBackupData(context) } ?: false
                if (hasBackup) {
                    driveCheckMessage = "📦 Existing backup data found on Google Drive! Retrieving and restoring data..."
                    delay(1500)
                    val (success, msg) = kotlinx.coroutines.withTimeoutOrNull(30000L) { GoogleDriveSyncManager.checkAndRetrieveDriveData(context, viewModel.appDatabase) } ?: Pair(false, "Timeout restoring data")
                    if (success) {
                        driveCheckMessage = "✅ Successfully restored backup data! Taking you to the user interface..."
                        delay(2000)
                        viewModel.navigateTo(viewModel.getDefaultScreen())
                    } else {
                        driveCheckMessage = "⚠️ Found backup but failed to restore: $msg. Proceeding..."
                        delay(2000)
                        isCheckingDriveData = false
                    }
                } else {
                    driveCheckMessage = "ℹ️ No existing backup found on Google Drive. Ready to start fresh!"
                    delay(1500)
                    isCheckingDriveData = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                driveCheckMessage = "⚠️ Error communicating with Google Drive: ${e.message}"
                delay(2000)
                isCheckingDriveData = false
            }
        }
    }

    // Launchers for permissions
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
        }
    )

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val email = account?.email ?: ""
                val displayName = account?.displayName ?: ""
                val idToken = account?.idToken
                if (email.isNotEmpty()) {
                    val username = email.substringBefore("@").replace(".", "_")
                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                    scope.launch {
                        GoogleDriveSyncManager.getAccessToken(context) { intent ->
                            authResolutionLauncher.launch(intent)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Google Sign-In failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSlate,
                        Color(0xFF0F111A),
                        Color(0xFF030305)
                    )
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header Section
            Icon(
                imageVector = Icons.Default.SettingsSuggest,
                contentDescription = "System Setup Required",
                tint = WaterBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "System Integration",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Life OS requires specific integrations to ensure offline synchronization, background timers, and app usage monitoring work reliably.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Mandatory Battery Optimization Section
            PermissionCard(
                title = "Battery Optimization (Mandatory)",
                description = "Android restricts apps running background daemons to save power. To keep focus timers and widget syncing active, this must be disabled.",
                isGranted = isBatteryOptIgnored,
                icon = Icons.Default.BatteryAlert,
                accentColor = if (isBatteryOptIgnored) SuccessGreen else AlertRed,
                buttonText = if (isBatteryOptIgnored) "Disabled (Optimal)" else "Request Disable",
                onButtonClick = {
                    if (!isBatteryOptIgnored) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                android.widget.Toast.makeText(context, "Please open Settings and disable battery optimization manually.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Notification Permission Section
            PermissionCard(
                title = "Push Notifications (Highly Recommended)",
                description = "Used to play alarm sounds, alert you when your Focus Session ends, and display daily reminders.",
                isGranted = hasNotificationPermission,
                icon = Icons.Default.NotificationsActive,
                accentColor = if (hasNotificationPermission) SuccessGreen else AccentOrange,
                buttonText = if (hasNotificationPermission) "Granted" else "Request Permission",
                onButtonClick = {
                    if (!hasNotificationPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Draw Over Other Apps Overlay Section
            PermissionCard(
                title = "System Overlay (Mandatory)",
                description = "Required to show the full-screen 'Focus Intercept' overlay when you attempt to open a blocked app (e.g. social media). Without this, Android background launch restrictions will silent-fail the app blocker.",
                isGranted = hasOverlayPermission,
                icon = Icons.Default.FlipToFront,
                accentColor = if (hasOverlayPermission) SuccessGreen else AlertRed,
                buttonText = if (hasOverlayPermission) "Enabled" else "Configure Overlay",
                onButtonClick = {
                    if (!hasOverlayPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Usage Statistics Permission Section
            PermissionCard(
                title = "App Usage Tracking (Mandatory)",
                description = "Required to monitor active foreground apps, enforce daily time limits, and gather real-time habit analytics.",
                isGranted = hasUsageStatsPermission,
                icon = Icons.Default.Timeline,
                accentColor = if (hasUsageStatsPermission) SuccessGreen else AlertRed,
                buttonText = if (hasUsageStatsPermission) "Enabled" else "Grant Usage Stats",
                onButtonClick = {
                    if (!hasUsageStatsPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4b. Accessibility Blocker Service Section (Mandatory)
            PermissionCard(
                title = "Accessibility Blocker Service (Mandatory)",
                description = "Required to block Reels, Shorts, Spotlight, and specific app sections inside social media. Without this, selective app blocking is not possible.",
                isGranted = hasAccessibilityPermission,
                icon = Icons.Default.Accessibility,
                accentColor = if (hasAccessibilityPermission) SuccessGreen else AlertRed,
                buttonText = if (hasAccessibilityPermission) "Enabled" else "Enable Accessibility",
                onButtonClick = {
                    if (!hasAccessibilityPermission) {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            isCheckingDriveData = false
                            throw e
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open settings automatically. Please go to Accessibility Settings and enable Life OS.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Google Drive Sync Authorization Section
            PermissionCard(
                title = "Google Drive Sync (Recommended)",
                description = "Securely backup and restore your focus sessions, metrics, and configurations in your personal Google Drive account.",
                isGranted = hasDrivePermission,
                icon = Icons.Default.CloudQueue,
                accentColor = if (hasDrivePermission) SuccessGreen else AccentOrange,
                buttonText = if (hasDrivePermission) "Authorized" else "Authorize Sync",
                onButtonClick = {
                    if (!hasDrivePermission) {
                        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                        if (googleAccount == null) {
                            try {
                                val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                val webClientId = try {
                                    context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                                } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                    ""
                                }
                                val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(driveScope)
                                if (webClientId.isNotEmpty()) {
                                    gsoBuilder.requestIdToken(webClientId)
                                }
                                val gso = gsoBuilder.build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            scope.launch {
                                GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                    authResolutionLauncher.launch(intent)
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Install Unknown Apps Permission Section (Mandatory)
            PermissionCard(
                title = "Install App Updates (Mandatory)",
                description = "Required to automatically install downloaded Life OS system updates. Without this, the app cannot update itself in-place.",
                isGranted = hasPackageInstallPermission,
                icon = Icons.Default.SystemUpdate,
                accentColor = if (hasPackageInstallPermission) SuccessGreen else AlertRed,
                buttonText = if (hasPackageInstallPermission) "Enabled" else "Configure Updates",
                onButtonClick = {
                    if (!hasPackageInstallPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(context, "Please open Settings -> Special App Access -> Install Unknown Apps to grant access.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Exact Alarm Permission Section (Mandatory)
            PermissionCard(
                title = "Exact Alarms Scheduling (Mandatory)",
                description = "Required to guarantee that focus timers and alarm notifications fire precisely when expected. Without this, Android 14+ will block exact scheduling and crash the app.",
                isGranted = hasExactAlarmPermission,
                icon = Icons.Default.Alarm,
                accentColor = if (hasExactAlarmPermission) SuccessGreen else AlertRed,
                buttonText = if (hasExactAlarmPermission) "Granted" else "Grant Exact Alarms",
                onButtonClick = {
                    if (!hasExactAlarmPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                isCheckingDriveData = false
                throw e
            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Please open Settings and grant Schedule Exact Alarm permission manually.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Mandatory requirement warning card
            val mandatoryGranted = isBatteryOptIgnored && hasNotificationPermission && hasOverlayPermission && hasUsageStatsPermission && hasAccessibilityPermission && hasPackageInstallPermission && hasExactAlarmPermission
            if (!mandatoryGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Mandatory Warning",
                            tint = AlertRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "All system permissions (Battery Optimization, Notifications, Overlay, Usage Tracking, Accessibility Service, Update Installation, and Exact Alarms) are strictly mandatory to run Life OS. Please configure all of them above to proceed.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom Action Button
            Button(
                onClick = {
                    if (mandatoryGranted) {
                        if (!hasDrivePermission && !hasAskedDriveOnboardingPrompt) {
                            showDriveOnboardingPrompt = true
                        } else {
                            viewModel.navigateTo(viewModel.getDefaultScreen())
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Please configure all mandatory options to proceed.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mandatoryGranted) WaterBlue else Charcoal,
                    contentColor = if (mandatoryGranted) Color.Black else Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = mandatoryGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_proceed_button")
            ) {
                Text(
                    text = "Proceed to Dashboard",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (isCheckingDriveData) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss during critical sync/restore */ },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isCheckingDriveData = false }) {
                    Text("Skip for Now", color = WaterBlue)
                }
            },
            title = {
                Text(
                    text = "Google Drive Backup Sync",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = WaterBlue)
                    Text(
                        text = driveCheckMessage,
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            },
            containerColor = DeepSlate,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDriveOnboardingPrompt) {
        AlertDialog(
            onDismissRequest = {
                showDriveOnboardingPrompt = false
                hasAskedDriveOnboardingPrompt = true
                viewModel.navigateTo(viewModel.getDefaultScreen())
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Your App Data", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    "Link your personal Google Drive account to enable seamless, automatic cloud backups of your study logs, settings, and habits.\n\nYou can skip this if you'd like to proceed offline.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriveOnboardingPrompt = false
                        hasAskedDriveOnboardingPrompt = true
                        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                        if (googleAccount == null) {
                            try {
                                val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                val webClientId = try {
                                    context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                                } catch (e: Exception) {
                                    ""
                                }
                                val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(driveScope)
                                if (webClientId.isNotEmpty()) {
                                    gsoBuilder.requestIdToken(webClientId)
                                }
                                val gso = gsoBuilder.build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            scope.launch {
                                GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                    authResolutionLauncher.launch(intent)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                ) {
                    Text("Connect Drive", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDriveOnboardingPrompt = false
                        hasAskedDriveOnboardingPrompt = true
                        viewModel.navigateTo(viewModel.getDefaultScreen())
                    }
                ) {
                    Text("Proceed Offline", color = Color.Gray)
                }
            },
            containerColor = DeepSlate,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isGranted) "Status: Enabled" else "Status: Restricted / Pending",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Pending",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) accentColor.copy(alpha = 0.15f) else accentColor,
                    contentColor = if (isGranted) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
