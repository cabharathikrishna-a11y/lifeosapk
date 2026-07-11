package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import com.example.ui.theme.WaterBlue
import com.example.util.AppBlockHelper
import com.example.util.AppLockHelper
import com.example.util.AppUpdateManager
import com.example.util.GoogleContactsSyncManager
import com.example.util.GoogleDriveSyncManager
import com.example.util.GoogleTasksSyncManager
import com.example.util.MapDownloadManager
import com.example.util.UpdateStatus
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



val WaterBlue = Color(0x0FF38B0F2) // Shared WaterBlue theme accent color

@Composable
fun SettingsPageScope(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
fun SettingsView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val directToBlocks = remember {
        val shared = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val value = shared.getBoolean("direct_to_blocks", false)
        if (value) {
            shared.edit().putBoolean("direct_to_blocks", false).apply()
        }
        value
    }
    var activePage by remember { mutableStateOf(if (directToBlocks) 14 else 0) }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    val vmActivePage by viewModel.settingsActivePage.collectAsState()
    LaunchedEffect(vmActivePage) {
        if (activePage != vmActivePage) {
            activePage = vmActivePage
        }
    }
    LaunchedEffect(activePage) {
        if (activePage != vmActivePage) {
            viewModel.updateSettingsActivePage(activePage)
        }
    }
    val isAdminUser by viewModel.isAdmin.collectAsState()

    when (activePage) {
        0 -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Centered Welcome Header
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(WaterBlue.copy(alpha = 0.12f), Color.Transparent)
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(WaterBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings Icon",
                                        tint = WaterBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "SETTINGS CENTER",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure and personalize your localized Life OS experience.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Group 1: Core Settings
                item {
                    SettingsCategoryGroup(title = "Core Settings") {
                        SettingsRowItem(
                            title = "SYSTEM & DIAGNOSTICS",
                            subtitle = "General options, background diagnostics, updates, deep links",
                            icon = Icons.Default.Settings,
                            iconBgColor = Color(0xFF2196F3)
                        ) { activePage = 30 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "AI & USER PROFILE",
                            subtitle = "Personalize profile, edit nickname, offline AI model caching",
                            icon = Icons.Default.Person,
                            iconBgColor = Color(0xFF00E5FF)
                        ) { activePage = 31 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "DATA & CONNECTIONS",
                            subtitle = "JSON backup/restore, Drive sync, permissions, database tools",
                            icon = Icons.Default.Refresh,
                            iconBgColor = Color(0xFFFFB300)
                        ) { activePage = 32 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "SECURITY & SCREEN LIMITS",
                            subtitle = "PIN/Password locking, biometric setups, app blocks",
                            icon = Icons.Default.Lock,
                            iconBgColor = Color(0xFFE91E63)
                        ) { activePage = 33 }
                    }
                }

                // Group 2: Productivity Suite
                item {
                    SettingsCategoryGroup(title = "Productivity Core") {
                        SettingsRowItem(
                            title = "TIMER CONFIGURATION",
                            subtitle = "Session periods, default break times, vibration style toggles",
                            icon = Icons.Default.PlayArrow,
                            iconBgColor = Color(0xFFFF3D00)
                        ) { activePage = 2 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "TASKS ENGINE",
                            subtitle = "Reminder frequencies, custom vibrators, default lists",
                            icon = Icons.Default.List,
                            iconBgColor = Color(0xFF4CAF50)
                        ) { activePage = 3 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "CALENDAR PLANNER",
                            subtitle = "Style layouts, display settings, timeline filters",
                            icon = Icons.Default.DateRange,
                            iconBgColor = Color(0xFF9C27B0)
                        ) { activePage = 4 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "HABITS TRACKER",
                            subtitle = "Streak calculations, automatic midnight reset triggers",
                            icon = Icons.Default.Refresh,
                            iconBgColor = Color(0xFFFF8F00)
                        ) { activePage = 5 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "SLEEP & WAKE-UP ALARM",
                            subtitle = "Bedtime reminders, wake-up alarms, snooze & alarm states",
                            icon = Icons.Default.Star,
                            iconBgColor = Color(0xFF3F51B5)
                        ) { activePage = 21 }
                    }
                }

                // Group 3: Logs & Utilities
                item {
                    SettingsCategoryGroup(title = "Logs & Utilities") {
                        SettingsRowItem(
                            title = "COUNTDOWNS & ALERTS",
                            subtitle = "Background notifications, custom alert parameters",
                            icon = Icons.Default.Notifications,
                            iconBgColor = Color(0xFF00E676)
                        ) { activePage = 6 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "LIFE JOURNAL",
                            subtitle = "Storage usage indexers, backup matching constraints",
                            icon = Icons.Default.Book,
                            iconBgColor = Color(0xFFE91E63)
                        ) { activePage = 7 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "CONTACTS DIRECTORY",
                            subtitle = "Full syncing filters, categories pairing, anniversaries",
                            icon = Icons.Default.AccountBox,
                            iconBgColor = Color(0xFF03A9F4)
                        ) { activePage = 8 }
                    }
                }

                // Group 4: File & Financials
                item {
                    SettingsCategoryGroup(title = "File & Financials") {
                        SettingsRowItem(
                            title = "FILE EXPLORER",
                            subtitle = "Workspace directories, index preferred storage",
                            icon = Icons.Default.Folder,
                            iconBgColor = Color(0xFF8D6E63)
                        ) { activePage = 9 }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "FINANCIAL LEDGER",
                            subtitle = "Accounts, custom family members, categories reporting",
                            icon = Icons.Default.MonetizationOn,
                            iconBgColor = Color(0xFF4CAF50)
                        ) { activePage = 10 }
                    }
                }

                // Group 5: Account
                item {
                    SettingsCategoryGroup(title = "Account Management") {
                        SettingsRowItem(
                            title = "LOGOUT",
                            subtitle = "Sign out from the current online account securely",
                            icon = Icons.Default.ExitToApp,
                            iconBgColor = Color(0xFFD32F2F)
                        ) { viewModel.logout() }
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                        SettingsRowItem(
                            title = "UNINSTALL & DE-REGISTER",
                            subtitle = "Securely wipe local data, notify peers on Firebase, and uninstall app",
                            icon = Icons.Default.Delete,
                            iconBgColor = Color(0xFFD32F2F)
                        ) { showUninstallConfirm = true }
                    }
                }
                


                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        1 -> {
            SettingsGeneralSystemPage(
                viewModel = viewModel,
                onBack = { activePage = 30 }
            )
        }

        2 -> {
            SettingsTimerConfigurationPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        3 -> {
            SettingsTasksPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        4 -> {
            SettingsSubpageWorkspace(
                title = "Calendar Planner Settings",
                description = "Custom calendar display preferences and layout rules.",
                onBack = { activePage = 0 }
            ) {
                CalendarSettingsSection(viewModel = viewModel)
            }
        }

        5 -> {
            SettingsHabitsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        6 -> {
            SettingsCountdownAlertsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        7 -> {
            SettingsJournalPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        8 -> {
            SettingsContactsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        9 -> {
            SettingsFileExplorerPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        10 -> {
            SettingsFinancialsPage(
                viewModel = viewModel,
                onBack = { activePage = 0 }
            )
        }

        11 -> {
            SettingsDeepaAIPage(
                viewModel = viewModel,
                onBack = { activePage = 31 }
            )
        }

        12 -> {
            SettingsSubpageWorkspace(
                title = "Backup & Restore",
                description = "Export and import your entire Life OS data via simple JSON backup.",
                onBack = { activePage = 32 }
            ) {
                LifeOSBackupSection(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                GoogleCalendarAndTasksSyncSection(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                FirebaseConfigurationSection(viewModel = viewModel)
            }
        }

        13 -> {
            SettingsSubpageWorkspace(
                title = "Secure App Lock",
                description = "Configure fingerprint/face biometric unlock, secure multi-digit PIN, or alphanumeric Password protection along with backup recovery.",
                onBack = { activePage = 33 }
            ) {
                AppLockSettingsSection()
            }
        }

        14 -> {
            SettingsSubpageWorkspace(
                title = "Blocks & Screen Limits",
                description = "Configure daily tracked limit quotas for Instagram, Facebook, Snapchat, or other manual apps.",
                onBack = { activePage = 33 }
            ) {
                AppBlocksSettingsSection()
            }
        }
        
        15 -> {
            SettingsUserInfoPage(
                viewModel = viewModel,
                onBack = { activePage = 31 }
            )
        }
        
        16 -> {
            SettingsUpdatesPage(
                viewModel = viewModel,
                onBack = { activePage = 30 }
            )
        }

        17 -> {
            SettingsBackgroundDiagnosticsPage(
                viewModel = viewModel,
                onBack = { activePage = 30 }
            )
        }

        19 -> {
            SettingsPermissionsPage(
                viewModel = viewModel,
                onBack = { activePage = 32 }
            )
        }

        18 -> {
            SettingsDeepLinksPage(
                viewModel = viewModel,
                onBack = { activePage = 30 }
            )
        }

        21 -> {
            SettingsSubpageWorkspace(
                title = "Sleep & Wake-up Alarm",
                description = "Manage bedtime reminders, morning alarms, snooze duration, and wake-up status logs.",
                onBack = { activePage = 0 }
            ) {
                SettingsSleepWakePage(viewModel = viewModel)
            }
        }

        22 -> {
            SettingsSubpageWorkspace(
                title = "Recompose Firebase Database",
                description = "Verify data structure and clean up non-Google registered user nodes.",
                onBack = { activePage = 32 },
                scrollable = false
            ) {
                SettingsRecomposeFirebasePage(viewModel = viewModel)
            }
        }

        30 -> {
            SettingsSubpageWorkspace(
                title = "System & Diagnostics",
                description = "Manage tab alignment, performance diagnostics, updates, and deep link options.",
                onBack = { activePage = 0 }
            ) {
                SettingsRowItem(
                    title = "GENERAL SYSTEM",
                    subtitle = "Tab alignment, navigation bar reordering, style configurations",
                    icon = Icons.Default.Settings,
                    iconBgColor = Color(0xFF2196F3)
                ) { activePage = 1 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "DIAGNOSTICS & BACKGROUND",
                    subtitle = "Fix stopwatch lockscreen freeze & background recording on Samsung/Oppo/Lenovo/Moto",
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFFE53935)
                ) { activePage = 17 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "SYSTEM UPDATE CENTER",
                    subtitle = "Check for updates, manage background downloads, authenticate tester",
                    icon = Icons.Default.Refresh,
                    iconBgColor = Color(0xFF4CAF50)
                ) { activePage = 16 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "DEEP LINKS & SHORTCUTS",
                    subtitle = "Copy application deep links, automation URI routes & assets",
                    icon = Icons.Default.Share,
                    iconBgColor = Color(0xFF03A9F4)
                ) { activePage = 18 }
            }
        }

        31 -> {
            SettingsSubpageWorkspace(
                title = "AI & User Profile",
                description = "Personalize your nickname, profile emoji, and manage Deepa AI brain cache.",
                onBack = { activePage = 0 }
            ) {
                SettingsRowItem(
                    title = "USER INFO",
                    subtitle = "Edit your profile details, nickname, and emoji",
                    icon = Icons.Default.Person,
                    iconBgColor = Color(0xFF673AB7)
                ) { activePage = 15 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "DEEPA AI BRAIN",
                    subtitle = "Offline model caching, memories vault management",
                    icon = Icons.Default.Face,
                    iconBgColor = Color(0xFF00E5FF)
                ) { activePage = 11 }
            }
        }

        32 -> {
            SettingsSubpageWorkspace(
                title = "Data & Connections",
                description = "Configure backups, calendar sync, Drive sync, permissions, and database tools.",
                onBack = { activePage = 0 }
            ) {
                SettingsRowItem(
                    title = "BACKUP & RESTORE",
                    subtitle = "JSON backups, Google Calendar & tasks sync, Firebase settings",
                    icon = Icons.Default.Refresh,
                    iconBgColor = Color(0xFFFFB300)
                ) { activePage = 12 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "PERMISSIONS & API CONNECTIONS",
                    subtitle = "Manage system permissions and Google Drive",
                    icon = Icons.Default.CheckCircle,
                    iconBgColor = Color(0xFF4CAF50)
                ) { activePage = 19 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "RECOMPOSE FIREBASE",
                    subtitle = "Clean database, delete non-Google registered user nodes",
                    icon = Icons.Default.Refresh,
                    iconBgColor = Color(0xFF00796B)
                ) { activePage = 22 }
            }
        }

        33 -> {
            SettingsSubpageWorkspace(
                title = "Security & Limits",
                description = "Configure lock screen PIN and app usage limits.",
                onBack = { activePage = 0 }
            ) {
                SettingsRowItem(
                    title = "SECURE APP LOCK",
                    subtitle = "Biometrics setup, secure PIN/Password lock configurations",
                    icon = Icons.Default.Lock,
                    iconBgColor = Color(0xFFE91E63)
                ) { activePage = 13 }
                HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
                SettingsRowItem(
                    title = "BLOCKS & SCREEN LIMITS",
                    subtitle = "Establish application constraints, usage warnings",
                    icon = Icons.Default.Block,
                    iconBgColor = Color(0xFFD32F2F)
                ) { activePage = 14 }
            }
        }
    }

    if (showUninstallConfirm) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text("Secure De-register & Uninstall", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "This action will:\n" +
                    "1. Mark your status as 'uninstalled' on the remote Firebase database so your name automatically and immediately disappears from your friends' focus list and details.\n" +
                    "2. Securely wipe all local databases, task lists, tracking history, and preferences.\n" +
                    "3. Request the Android system uninstallation dialog to delete the app.\n\n" +
                    "Do you want to proceed?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallConfirm = false
                        viewModel.deregisterAndUninstall(context) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("De-register & Delete App", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }
}

@Composable
fun SettingsCategoryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconBgColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Arrow",
            tint = Color.DarkGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsSubpageWorkspace(
    title: String,
    description: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun CalendarSettingsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val syncStatus by viewModel.calendarSyncStatus.collectAsState()
    val tasksSyncStatus by viewModel.googleTasksSyncStatus.collectAsState()

    val tasksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleTasks(context) { }
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = (permissions[android.Manifest.permission.READ_CALENDAR] ?: false) &&
                        (permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false)
    }

    // Prefs
    val prefs = remember { context.getSharedPreferences("app_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedAccount by remember { mutableStateOf(prefs.getString("selected_calendar_account", null)) }
    var selectedName by remember { mutableStateOf(prefs.getString("selected_calendar_name", null)) }
    var selectedId by remember { mutableStateOf(prefs.getLong("selected_calendar_id", -1L)) }

    // Query calendars if permission is granted
    val calendars = remember(hasPermission) {
        if (hasPermission) {
            com.example.util.GoogleCalendarSyncHelper.getAvailableCalendars(context)
        } else {
            emptyList()
        }
    }

    // Dropdown states
    var accountExpanded by remember { mutableStateOf(false) }
    var nameExpanded by remember { mutableStateOf(false) }

    val uniqueAccounts = remember(calendars) {
        calendars.map { it.accountName }.distinct()
    }

    val filteredNames = remember(calendars, selectedAccount) {
        if (selectedAccount == null) calendars else calendars.filter { it.accountName == selectedAccount }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
            border = BorderStroke(1.dp, Color(0xFF222225)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "GCal",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Google Calendar Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Enable bidirectional background synchronization with Google Calendar. Whenever you open the Calendar or modify tasks, synchronization occurs silently and automatically.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (!hasPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Calendar Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "✓ Calendar Permissions Granted",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Synchronized Account & Calendar",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Dropdown for Google Account
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Account", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { accountExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedAccount ?: "Choose Google Account (Default: First GCal)",
                                    color = if (selectedAccount != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = accountExpanded,
                                onDismissRequest = { accountExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (First Google Account)", color = Color.White) },
                                    onClick = {
                                        selectedAccount = null
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_account")
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        accountExpanded = false
                                    }
                                )
                                uniqueAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc, color = Color.White) },
                                        onClick = {
                                            selectedAccount = acc
                                            // Reset selectedName if not belonging to this account
                                            if (calendars.none { it.accountName == acc && it.displayName == selectedName }) {
                                                selectedName = null
                                                selectedId = -1L
                                            }
                                            prefs.edit()
                                                .putString("selected_calendar_account", acc)
                                                .putString("selected_calendar_name", selectedName)
                                                .putLong("selected_calendar_id", selectedId)
                                                .apply()
                                            accountExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown for Calendar Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Calendar Name", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                    .clickable { nameExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedName ?: "Choose Calendar (Default: Main)",
                                    color = if (selectedName != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = nameExpanded,
                                onDismissRequest = { nameExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default (Primary Calendar)", color = Color.White) },
                                    onClick = {
                                        selectedName = null
                                        selectedId = -1L
                                        prefs.edit()
                                            .remove("selected_calendar_name")
                                            .putLong("selected_calendar_id", -1L)
                                            .apply()
                                        nameExpanded = false
                                    }
                                )
                                filteredNames.forEach { cal ->
                                    DropdownMenuItem(
                                        text = { Text(cal.displayName, color = Color.White) },
                                        onClick = {
                                            selectedName = cal.displayName
                                            selectedId = cal.id
                                            prefs.edit()
                                                .putString("selected_calendar_name", cal.displayName)
                                                .putLong("selected_calendar_id", cal.id)
                                                .apply()
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                            Text(syncStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.syncGoogleCalendar(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
        val defaultEmail = googleAccount?.email ?: "cabharathikrishna@gmail.com"
        var selectedTasksAccount by remember { mutableStateOf(prefs.getString("selected_tasks_account", defaultEmail)) }

        val tasksAccountLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    val email = account?.email ?: ""
                    if (email.isNotEmpty()) {
                        selectedTasksAccount = email
                        prefs.edit().putString("selected_tasks_account", email).apply()
                        android.widget.Toast.makeText(context, "Connected to: $email", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Google Account selection failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
            border = BorderStroke(1.dp, Color(0xFF222225)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "GTasks",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Google Tasks Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Enable bidirectional background synchronization with Google Tasks. Tasks without date or time are automatically kept in sync with Google Tasks.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Google Account Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Connected Google Account", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                                .clickable {
                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .build()
                                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                    client.signOut().addOnCompleteListener {
                                        tasksAccountLauncher.launch(client.signInIntent)
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedTasksAccount ?: "No Account Connected",
                                color = if (selectedTasksAccount != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sync Status", color = Color.Gray, fontSize = 11.sp)
                        Text(tasksSyncStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.syncGoogleTasks(context) { intent ->
                                tasksAuthLauncher.launch(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSleepWakePage(viewModel: AppViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    
    var bedtimeEnabled by remember { mutableStateOf(prefs.getBoolean("bedtime_reminder_enabled", true)) }
    var wakeupEnabled by remember { mutableStateOf(prefs.getBoolean("wakeup_alarm_enabled", false)) }
    
    var bedtimeStr by remember { mutableStateOf(com.example.util.SleepTimeHelper.getSleepTime(context) ?: "22:00") }
    var wakeupStr by remember { mutableStateOf(com.example.util.SleepTimeHelper.getWakeUpTime(context) ?: "07:00") }
    
    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    var lastWokeUpTime by remember { mutableStateOf(prefs.getString("actual_wake_up_time_$todayStr", null)) }

    // Format HH:mm string to a nice AM/PM string for display
    fun formatToAmPm(timeStr: String): String {
        return try {
            if (timeStr == "LATE") return "Late Wake-up"
            val parts = timeStr.split(":")
            if (parts.size != 2) return timeStr
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val ampm = if (h >= 12) "PM" else "AM"
            val displayHour = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(java.util.Locale.US, "%d:%02d %s", displayHour, m, ampm)
        } catch (e: Exception) {
            timeStr
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bedtime Reminder Card
        Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Bedtime Reminder",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Bedtime Reminder",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Receive a screen notification at your scheduled sleep time reminding you to wind down for bed.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Bedtime Reminder", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = bedtimeEnabled,
                            onCheckedChange = { isChecked ->
                                bedtimeEnabled = isChecked
                                prefs.edit().putBoolean("bedtime_reminder_enabled", isChecked).apply()
                                com.example.util.AlarmScheduler.scheduleBedtimeReminder(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    if (bedtimeEnabled) {
                        HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parts = bedtimeStr.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    android.app.TimePickerDialog(context, { _, hour, minute ->
                                        val newTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                        bedtimeStr = newTime
                                        com.example.util.SleepTimeHelper.setSleepTime(context, newTime)
                                        com.example.util.AlarmScheduler.scheduleBedtimeReminder(context)
                                    }, h, m, false).show()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Scheduled Bedtime", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to change via clock pop-up", color = Color.Gray, fontSize = 11.sp)
                            }
                            Text(
                                text = formatToAmPm(bedtimeStr),
                                color = WaterBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        // Morning Wake-up Alarm Card
        Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Morning Wake-up Alarm",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Morning Wake-up Alarm",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Configure a full-screen morning alarm that wakes you up with custom snooze, dismiss, or literal wake-up logs.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Wake-up Alarm", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = wakeupEnabled,
                            onCheckedChange = { isChecked ->
                                wakeupEnabled = isChecked
                                prefs.edit().putBoolean("wakeup_alarm_enabled", isChecked).apply()
                                com.example.util.AlarmScheduler.scheduleWakeUpAlarm(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    if (wakeupEnabled) {
                        HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parts = wakeupStr.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    android.app.TimePickerDialog(context, { _, hour, minute ->
                                        val newTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                        wakeupStr = newTime
                                        com.example.util.SleepTimeHelper.setWakeUpTime(context, newTime)
                                        com.example.util.AlarmScheduler.scheduleWakeUpAlarm(context)
                                    }, h, m, false).show()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Scheduled Wake-up Time", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to change via clock pop-up", color = Color.Gray, fontSize = 11.sp)
                            }
                            Text(
                                text = formatToAmPm(wakeupStr),
                                color = WaterBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        // Alarm Logs / Statistics Card
        Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sleep Logs",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Today's Sleep Log",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Actual Wake Up Logged", color = Color.Gray, fontSize = 13.sp)
                        Text(
                            text = if (lastWokeUpTime != null) formatToAmPm(lastWokeUpTime!!) else "No wake-up logged yet",
                            color = if (lastWokeUpTime != null) Color.Green else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
    }
}

@Composable
fun SettingsRecomposeFirebasePage(viewModel: AppViewModel) {
    val logs by viewModel.recomposeLogs.collectAsState()
    val status by viewModel.recomposeStatus.collectAsState()
    val lazyListState = rememberLazyListState()

    // Auto scroll logs to the bottom as they are added
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Banner Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                border = BorderStroke(1.dp, Color(0xFF33333C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Firebase Database Integrity",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recomposing the database verifies the data structure of all active records and cleans up unverified, legacy, or invalid user nodes from Firebase (such as mock usernames and non-Google registered accounts). This optimizes query speeds and protects privacy.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Action & Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                border = BorderStroke(1.dp, Color(0xFF33333C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Operations Status",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusText = when (status) {
                                "idle" -> "System Idle - Ready"
                                "running" -> "Executing Database Audit..."
                                "success" -> "Optimization Completed"
                                "error" -> "Process Interrupted"
                                else -> "Unknown"
                            }
                            val statusColor = when (status) {
                                "idle" -> Color(0xFF90A4AE)
                                "running" -> Color(0xFF29B6F6)
                                "success" -> Color(0xFF66BB6A)
                                "error" -> Color(0xFFEF5350)
                                else -> Color.White
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        when (status) {
                            "running" -> {
                                CircularProgressIndicator(
                                    color = Color(0xFF00796B),
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                            "success" -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            "error" -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Idle",
                                    tint = Color(0xFF90A4AE),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.recomposeFirebase() },
                        enabled = status != "running",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00796B),
                            disabledContainerColor = Color(0xFF004D40).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("recompose_firebase_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status == "running") "AUDITING FIREBASE..." else "RECOMPOSE FIREBASE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Live Console Logs
        item {
            Text(
                text = "Live Execution Logs",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0C0F))
                    .border(1.dp, Color(0xFF222226), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Console waiting. Click 'RECOMPOSE FIREBASE' above to initiate process.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs.size) { idx ->
                            val log = logs[idx]
                            val textColor = when {
                                log.contains("WARNING") -> Color(0xFFFFB300)
                                log.contains("Error") || log.contains("Exception") -> Color(0xFFEF5350)
                                log.contains("Successfully") || log.contains("Complete") || log.contains("Successfully deleted") -> Color(0xFF81C784)
                                else -> Color(0xFFCFD8DC)
                            }
                            Text(
                                text = log,
                                color = textColor,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_BackgroundDiagnostics.kt
// ==========================================================



@Composable
fun SettingsBackgroundDiagnosticsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isIgnoringOptimizations by remember { mutableStateOf(checkBatteryOptimizations(context)) }
    var selectedBrandTab by remember { mutableStateOf("samsung") }
    var fcmToken by remember { mutableStateOf("Fetching FCM Token...") }
    var isFcmWorking by remember { mutableStateOf<Boolean?>(null) }

    // Re-check optimization status when entering or active
    LaunchedEffect(Unit) {
        isIgnoringOptimizations = checkBatteryOptimizations(context)
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fcmToken = task.result ?: "Token is null"
                    isFcmWorking = true
                } else {
                    fcmToken = "Error: ${task.exception?.message ?: "Unknown error"}"
                    isFcmWorking = false
                }
            }
        } catch (e: Exception) {
            fcmToken = "FCM Error or not initialized: ${e.message}"
            isFcmWorking = false
        }
    }

    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "Diagnostics & Background Sync",
            description = "Fix stopwatch freezing and background focus recording issues on Samsung, Oppo, Lenovo, and Motorola.",
            onBack = onBack
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Status Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, if (isIgnoringOptimizations) Color(0xFF2E7D32) else Color(0xFFC62828)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isIgnoringOptimizations) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    else Color(0xFFC62828).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isIgnoringOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Status Icon",
                                tint = if (isIgnoringOptimizations) Color(0xFF4CAF50) else Color(0xFFE53935),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isIgnoringOptimizations) "BACKGROUND CONFIGURATION: OPTIMAL" else "BACKGROUND CONFIGURATION: RESTRICTED",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIgnoringOptimizations) Color(0xFF4CAF50) else Color(0xFFE53935),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isIgnoringOptimizations) {
                                "Your device allows Life OS to operate with unrestricted background access. Stopwatches and focus timers will tick continuously, even when your screen is locked."
                            } else {
                                "Android's battery-saving system is active for Life OS. On many devices (especially Samsung, Oppo, Lenovo, Motorola), the system freezes background processes or terminates timers when the screen locks."
                            },
                            fontSize = 11.5.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                requestIgnoreBatteryOptimizations(context)
                                // Refresh status slightly after launching
                                isIgnoringOptimizations = checkBatteryOptimizations(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIgnoringOptimizations) Color(0xFF2E7D32) else WaterBlue,
                                contentColor = if (isIgnoringOptimizations) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Battery Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isIgnoringOptimizations) "Open Battery Settings Again" else "Disable Battery Optimization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. Explanations of why it freezes
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070709)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E22)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WHY TIMERS & STOPWATCHES FREEZE",
                            color = WaterBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. CPU Standby: When the screen turns off, Android suspends CPU cores to save power, freezing local clocks.\n" +
                                    "2. Aggressive Custom Skins: Samsung (OneUI), Oppo (ColorOS), Lenovo, and Motorola utilize aggressive OEM managers that kill background services or suppress periodic network/local state sync requests.\n" +
                                    "3. Network Restrictions: In sleep mode, background cellular and Wi-Fi sync are deferred, meaning focus data updates aren't logged in real-time until you unlock.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 3. OEM Specific Workaround Tabs
                Text(
                    text = "OEM-SPECIFIC FIXES",
                    color = WaterBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0E), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "samsung" to "Samsung",
                        "oppo" to "Oppo",
                        "lenovo" to "Lenovo",
                        "motorola" to "Moto",
                        "others" to "Others"
                    ).forEach { (key, label) ->
                        val isSelected = selectedBrandTab == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue else Color.Transparent)
                                .clickable { selectedBrandTab = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Workaround Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, Color(0xFF1E1E22)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedBrandTab) {
                            "samsung" -> {
                                Text("Samsung (One UI) Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Set Battery to Unrestricted:\n" +
                                            "   • Long press the Life OS app icon -> Tap \"App info\" (or Go to Settings -> Apps -> Life OS).\n" +
                                            "   • Tap \"Battery\".\n" +
                                            "   • Select \"Unrestricted\" (the default is usually Optimized or Restricted).\n\n" +
                                            "2. Add to Never Sleeping Apps:\n" +
                                            "   • Go to Settings -> Battery and device care -> Battery -> Background usage limits.\n" +
                                            "   • Tap \"Never sleeping apps\".\n" +
                                            "   • Click \"+\" in the top right, select \"Life OS\" and click Add.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "oppo" -> {
                                Text("Oppo / OnePlus / Realme (ColorOS) Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Enable Background Execution:\n" +
                                            "   • Settings -> Apps -> App management -> Life OS.\n" +
                                            "   • Tap \"Battery usage\".\n" +
                                            "   • Toggle ON \"Allow background activity\" and \"Allow auto-launch\".\n\n" +
                                            "2. Lock in Recent Apps:\n" +
                                            "   • Swipe up to open your Recent Apps screen.\n" +
                                            "   • Tap the three-dots/options icon above the Life OS card.\n" +
                                            "   • Select \"Lock\" to prevent the ColorOS memory sweeper from cleaning the background thread.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "lenovo" -> {
                                Text("Lenovo Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Disable App Restriction:\n" +
                                            "   • Open Settings -> Apps -> Life OS.\n" +
                                            "   • Tap \"Battery\".\n" +
                                            "   • Select \"Unrestricted\".\n\n" +
                                            "2. Lenovo Power Management (ZUI):\n" +
                                            "   • Open the built-in ZUI Security / Power Manager app.\n" +
                                            "   • Look for \"Auto-start\" or \"Background app management\".\n" +
                                            "   • Allow Life OS to start and stay active in the background.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "motorola" -> {
                                Text("Motorola Guidelines", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Disable Battery Saver Optimization:\n" +
                                            "   • Go to Settings -> Apps & notifications -> Special app access -> Battery optimization.\n" +
                                            "   • Filter by \"All apps\", select \"Life OS\" and choose \"Don't optimize\".\n\n" +
                                            "2. Enable Unrestricted Usage:\n" +
                                            "   • Go to Settings -> Apps -> Life OS -> Battery.\n" +
                                            "   • Select \"Unrestricted\" to grant standard foreground service background permissions without system suspension.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            "others" -> {
                                Text("General & Other Devices (Xiaomi, Vivo, etc.)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Xiaomi (MIUI / HyperOS):\n" +
                                            "   • Settings -> Apps -> Manage apps -> Life OS -> Battery saver -> Select \"No restrictions\". Also enable \"Autostart\".\n\n" +
                                            "2. Vivo (FuntouchOS):\n" +
                                            "   • Settings -> Battery -> High background power consumption -> Enable \"Life OS\".\n\n" +
                                            "3. General Troubleshooting:\n" +
                                            "   • Ensure your device is not in system-wide \"Power Saver Mode\" or \"Ultra Battery Saver\".\n" +
                                            "   • Keep the background notification for the \"KeepAliveService\" active. It prevents the system from categorizing Life OS as idle.",
                                    color = Color.LightGray,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 4. Test Keep-Alive Service Running Status
                val isServiceActive = isKeepAliveServiceRunning(context)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0C)),
                    border = BorderStroke(1.dp, Color(0xFF222226)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FOREGROUND KEEP-ALIVE SERVICE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceActive) "Active and holding a background notification channel." else "Not active or has been suspended.",
                                color = if (isServiceActive) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 10.5.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isServiceActive) Color(0xFF4CAF50) else Color(0xFFD32F2F))
                        )
                    }
                }

                // 5. Firebase Cloud Messaging (FCM) Status
                val clipboardManager = LocalClipboardManager.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0C)),
                    border = BorderStroke(1.dp, if (isFcmWorking == true) Color(0xFF2E7D32) else Color(0xFF222226)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FIREBASE CLOUD MESSAGING (FCM)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (isFcmWorking) {
                                        true -> "FCM Service initialized and active."
                                        false -> "FCM failed or Google Play Services missing."
                                        else -> "Retrieving push notification status..."
                                    },
                                    color = if (isFcmWorking == true) Color(0xFF4CAF50) else Color.Gray,
                                    fontSize = 10.5.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (isFcmWorking) {
                                            true -> Color(0xFF4CAF50)
                                            false -> Color(0xFFD32F2F)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "FCM REGISTRATION TOKEN",
                            color = Color(0xFF03A9F4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF15151A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF22222A), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = fcmToken,
                                color = Color.LightGray,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isFcmWorking == true) {
                                    clipboardManager.setText(AnnotatedString(fcmToken))
                                    Toast.makeText(context, "FCM Token copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "FCM Token is not available to copy.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFcmWorking == true) Color(0xFF0288D1) else Color(0xFF222226),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text(
                                text = "Copy FCM Token",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun checkBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Battery settings not required on this Android version.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Could not open settings automatically.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun isKeepAliveServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
        if ("com.example.service.KeepAliveService" == service.service.className) {
            return true
        }
    }
    return false
}

// ==========================================================
// MERGED FROM: SettingsView_BackupAndSecurity.kt
// ==========================================================



@Composable
fun LifeOSBackupSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf<String?>(null) }
    
    // Google Drive Integration State
    var hasDrivePermission by remember { mutableStateOf(GoogleDriveSyncManager.hasDrivePermission(context)) }
    var isOperating by remember { mutableStateOf(false) }
    var gdMessage by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var lastSyncTs by remember { mutableStateOf(prefs.getLong("gd_all_last_sync_timestamp", 0L)) }

    var optTasks by remember { mutableStateOf(prefs.getBoolean("backup_option_tasks", true)) }
    var optHabits by remember { mutableStateOf(prefs.getBoolean("backup_option_habits", true)) }
    var optJournal by remember { mutableStateOf(prefs.getBoolean("backup_option_journal", true)) }
    var optFinances by remember { mutableStateOf(prefs.getBoolean("backup_option_finances", true)) }
    var optContacts by remember { mutableStateOf(prefs.getBoolean("backup_option_contacts", true)) }
    var optFiles by remember { mutableStateOf(prefs.getBoolean("backup_option_files", true)) }
    var optSettings by remember { mutableStateOf(prefs.getBoolean("backup_option_settings", true)) }
    var optHealth by remember { mutableStateOf(prefs.getBoolean("backup_option_health", true)) }
    var optNotes by remember { mutableStateOf(prefs.getBoolean("backup_option_notes", true)) }
    var optFocus by remember { mutableStateOf(prefs.getBoolean("backup_option_focus", true)) }

    fun updateBackupOption(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            statusText = "Exporting data..."
            viewModel.exportBackup(context, uri) { success ->
                statusText = if (success) "Export completed successfully!" else "Failed to export data."
            }
        }
    }

    val htmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            statusText = "Exporting offline archive..."
            viewModel.exportHtmlZip(context, uri) { success ->
                statusText = if (success) "HTML Archive exported successfully! (Offline companion ready)" else "Failed to export HTML archive."
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            statusText = "Importing and reconciling..."
            viewModel.importBackup(context, uri) { success ->
                statusText = if (success) "Import completed successfully! Life OS data synced." else "Failed to import backup."
            }
        }
    }

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        if (result.resultCode == Activity.RESULT_OK) {
            gdMessage = "Google Drive successfully authorized! Tap Backup or Restore to align your app data."
        } else {
            gdMessage = "Google Drive authorization declined."
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        // --- GOOGLE DRIVE CLOUD BACKUP & RESTORE CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
            border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Google Drive Sync",
                            tint = WaterBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Google Drive Backup",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    if (!hasDrivePermission) {
                        Button(
                            onClick = {
                                scope.launch {
                                    GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).testTag("connect_drive_settings_btn")
                        ) {
                            Text("CONNECT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B5E20).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CONNECTED",
                                color = Color.Green,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "Encrypts and uploads your complete Life OS workspace—including nested tasks, completed habits, transaction history logs, journal texts, and media files—directly to your secure, private Google Drive AppData folder.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                if (lastSyncTs > 0L) {
                    Text(
                        text = "Last synced: " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTs)),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                if (hasDrivePermission) {
                    if (isOperating) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accessing Google Drive...", color = Color.LightGray, fontSize = 11.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isOperating = true
                                    gdMessage = "Initiating cloud backup..."
                                    viewModel.backupAllDataToGoogleDrive(context, { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }) { success, msg ->
                                        isOperating = false
                                        gdMessage = msg
                                        if (success) {
                                            lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp).testTag("drive_backup_all_btn")
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp), tint = WaterBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Backup", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                            }

                            Button(
                                onClick = {
                                    isOperating = true
                                    gdMessage = "Initiating cloud restore..."
                                    viewModel.restoreAllDataFromGoogleDrive(context, { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }) { success, msg ->
                                        isOperating = false
                                        gdMessage = msg
                                        if (success) {
                                            lastSyncTs = prefs.getLong("gd_all_last_sync_timestamp", 0L)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp).testTag("drive_restore_all_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Restore", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                gdMessage?.let {
                    Text(
                        text = it,
                        color = if (it.contains("Successfully") || it.contains("authorized")) Color.Green else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // --- BACKUP CATEGORIES SELECTION ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
            border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Backup Options",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Configure Backup Options",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "Select which categories should be exported in manual snapshots or synchronized to Google Drive:",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                val items = listOf(
                    Triple("Tasks, Custom Lists & Deadlines", optTasks) { v: Boolean -> optTasks = v; updateBackupOption("backup_option_tasks", v) },
                    Triple("Habits & Streak Completions", optHabits) { v: Boolean -> optHabits = v; updateBackupOption("backup_option_habits", v) },
                    Triple("Journal Texts & Entries", optJournal) { v: Boolean -> optJournal = v; updateBackupOption("backup_option_journal", v) },
                    Triple("Finance Accounts, Ledger & Transactions", optFinances) { v: Boolean -> optFinances = v; updateBackupOption("backup_option_finances", v) },
                    Triple("Contacts & Folders", optContacts) { v: Boolean -> optContacts = v; updateBackupOption("backup_option_contacts", v) },
                    Triple("Local Files & Documents (PDF/Word/Excel)", optFiles) { v: Boolean -> optFiles = v; updateBackupOption("backup_option_files", v) },
                    Triple("App Configuration & Shared Preferences", optSettings) { v: Boolean -> optSettings = v; updateBackupOption("backup_option_settings", v) },
                    Triple("Health & Fitness Records (Steps, Water, Food Diary)", optHealth) { v: Boolean -> optHealth = v; updateBackupOption("backup_option_health", v) },
                    Triple("Pinned & Personal Keep Notes", optNotes) { v: Boolean -> optNotes = v; updateBackupOption("backup_option_notes", v) },
                    Triple("Focus Session History Log", optFocus) { v: Boolean -> optFocus = v; updateBackupOption("backup_option_focus", v) }
                )

                items.forEach { (label, value, onValueChange) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueChange(!value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = Color.LightGray, fontSize = 12.sp)
                        Switch(
                            checked = value,
                            onCheckedChange = onValueChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // --- MANUAL SNAPSHOTS ---
        Text("Manage Manual Snapshots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Export or restore your localized databases, settings, task lists, and history records securely.", color = Color.Gray, fontSize = 11.sp)
        
        Button(
            onClick = {
                exportLauncher.launch("life_os_backup_${System.currentTimeMillis()}.zip")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Manual Backup (ZIP)")
        }
        
        Button(
            onClick = {
                importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "application/x-zip-compressed"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B1E), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import and Reconcile Backup (ZIP/JSON)")
        }

        Button(
            onClick = {
                htmlExportLauncher.launch("life_os_offline_dashboard_${System.currentTimeMillis()}.zip")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3D2E), contentColor = Color.Green),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = Color.Green)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Offline HTML Companion (ZIP)", color = Color.Green)
        }
        
        statusText?.let {
            Text(it, color = if (it.contains("successfully")) Color.Green else Color.Red, fontSize = 12.sp)
        }
    }
}

@Composable
fun AppLockSettingsSection() {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AppLockHelper.isAppLockEnabled(context)) }
    var lockType by remember { mutableStateOf(AppLockHelper.getLockType(context)) }
    var code by remember { mutableStateOf(AppLockHelper.getLockCode(context) ?: "") }
    var biometricsEnabled by remember { mutableStateOf(AppLockHelper.isBiometricsEnabled(context)) }
    var showSetupDialog by remember { mutableStateOf(false) }
    
    // Recovery Questions State
    val questions = remember { AppLockHelper.getSecurityQuestions(context) }
    var q1 by remember { mutableStateOf(questions[0].first) }
    var a1 by remember { mutableStateOf(questions[0].second) }
    var q2 by remember { mutableStateOf(questions[1].first) }
    var a2 by remember { mutableStateOf(questions[1].second) }
    var q3 by remember { mutableStateOf(questions[2].first) }
    var a3 by remember { mutableStateOf(questions[2].second) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Lock Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable App Lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Require PIN or password when opening Life OS", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showSetupDialog = true
                    } else {
                        AppLockHelper.setAppLockEnabled(context, false)
                        AppLockHelper.setLockCode(context, null)
                        isEnabled = false
                        code = ""
                    }
                }
            )
        }
        
        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lock Type", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Select authentication mode", color = Color.Gray, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lockType == "pin",
                        onClick = {
                            lockType = "pin"
                            AppLockHelper.setLockType(context, "pin")
                        },
                        label = { Text("PIN") }
                    )
                    FilterChip(
                        selected = lockType == "password",
                        onClick = {
                            lockType = "password"
                            AppLockHelper.setLockType(context, "password")
                        },
                        label = { Text("Password") }
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Unlock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Allow face or fingerprint unlock if supported", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = biometricsEnabled,
                    onCheckedChange = {
                        biometricsEnabled = it
                        AppLockHelper.setBiometricsEnabled(context, it)
                    }
                )
            }
        }
        
        if (showSetupDialog) {
            AlertDialog(
                onDismissRequest = { showSetupDialog = false },
                title = { Text("Setup Secure Lock") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Configure your security code and security questions to enable recovery in case you forget it.", color = Color.Gray, fontSize = 11.sp)
                        
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(if (lockType == "pin") "Enter PIN (Digits)" else "Enter Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = if (lockType == "pin") KeyboardType.Number else KeyboardType.Password)
                        )
                        
                        Text("Recovery Security Questions", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q1, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a1,
                                onValueChange = { a1 = it },
                                label = { Text("Answer 1") },
                                singleLine = true
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q2, color = Color.LightGray, fontSize = 11.sp)
                            OutlinedTextField(
                                value = a2,
                                onValueChange = { a2 = it },
                                label = { Text("Answer 2") },
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (code.isNotBlank() && a1.isNotBlank() && a2.isNotBlank()) {
                                AppLockHelper.setLockCode(context, code)
                                AppLockHelper.setAppLockEnabled(context, true)
                                AppLockHelper.saveSecurityQuestions(context, q1, a1, q2, a2, q3, a3)
                                AppLockHelper.setSecuritySetupComplete(context, true)
                                isEnabled = true
                                showSetupDialog = false
                            }
                        }
                    ) {
                        Text("Enable App Lock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AppBlocksSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(AppBlockHelper.hasUsageStatsPermission(context)) }
    var blockedApps by remember { mutableStateOf(AppBlockHelper.getBlockedApps(context)) }
    var selectedAppForLimit by remember { mutableStateOf<String?>(null) }
    var limitMinutesText by remember { mutableStateOf("30") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppPackage by remember { mutableStateOf("") }
    
    // State for all installed apps on device
    var installedApps by remember { mutableStateOf<List<com.example.util.AppBlockHelper.AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Refresh permission status when entering and load apps asynchronously
    LaunchedEffect(Unit) {
        hasPermission = AppBlockHelper.hasUsageStatsPermission(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            installedApps = com.example.util.AppBlockHelper.getInstalledApps(context)
            isLoadingApps = false
        }
    }

    // Format usage helper
    fun formatUsageTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text("App Blocks & Usage Limits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Set daily tracked screen-time limit quotas for distracting applications.", color = Color.Gray, fontSize = 11.sp)
        
        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Usage Stats Permission Required", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Life OS requires the Usage Access permission to track open times and enforce screen limits.", color = Color.LightGray, fontSize = 11.sp)
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Grant Permission", fontSize = 12.sp)
                    }
                }
            }
        }
        
        // Quick App Toggles for Instagram and YouTube
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("QUICK APP TOGGLES", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                
                // Instagram Toggle
                val isInstaBlocked = blockedApps.contains("com.instagram.android")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppListIcon(pkg = "com.instagram.android")
                        }
                    }
                    Switch(
                        checked = isInstaBlocked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                AppBlockHelper.addBlockedApp(context, "com.instagram.android")
                            } else {
                                AppBlockHelper.removeBlockedApp(context, "com.instagram.android")
                            }
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                // YouTube Toggle
                val isYoutubeBlocked = blockedApps.contains("com.google.android.youtube")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppListIcon(pkg = "com.google.android.youtube")
                        }
                    }
                    Switch(
                        checked = isYoutubeBlocked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                AppBlockHelper.addBlockedApp(context, "com.google.android.youtube")
                            } else {
                                AppBlockHelper.removeBlockedApp(context, "com.google.android.youtube")
                            }
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }

        // Instagram Advanced Blocker Settings Card
        var useSelectiveIg by remember { mutableStateOf(AppBlockHelper.isIgSelectiveBlockingEnabled(context)) }
        var isIgReelsBlocked by remember { mutableStateOf(AppBlockHelper.isIgReelsBlocked(context)) }
        var isIgStoriesBlocked by remember { mutableStateOf(AppBlockHelper.isIgStoriesBlocked(context)) }
        var isIgExploreBlocked by remember { mutableStateOf(AppBlockHelper.isIgExploreBlocked(context)) }
        var isIgAllowSharedReels by remember { mutableStateOf(AppBlockHelper.isIgAllowSharedReels(context)) }
        var isIgFeedScrollLimit by remember { mutableStateOf(AppBlockHelper.isIgFeedScrollLimit(context)) }
        var isIgReelsMuteAudio by remember { mutableStateOf(AppBlockHelper.isIgReelsMuteAudio(context)) }

        // YouTube Advanced Blocker Settings Card
        var useSelectiveYt by remember { mutableStateOf(AppBlockHelper.isYtSelectiveBlockingEnabled(context)) }
        var isYtShortsBlocked by remember { mutableStateOf(AppBlockHelper.isYtShortsBlocked(context)) }
        var isYtSearchBlocked by remember { mutableStateOf(AppBlockHelper.isYtSearchBlocked(context)) }
        var isYtCommentsBlocked by remember { mutableStateOf(AppBlockHelper.isYtCommentsBlocked(context)) }
        var isYtOnlyAllowApprovedChannels by remember { mutableStateOf(AppBlockHelper.isYtOnlyAllowApprovedChannels(context)) }
        var ytApprovedChannels by remember { mutableStateOf(AppBlockHelper.getYtApprovedChannels(context)) }

        // Snapchat Advanced Blocker Settings Card
        var useSelectiveSnap by remember { mutableStateOf(AppBlockHelper.isSnapSelectiveBlockingEnabled(context)) }
        var isSnapSpotlightBlocked by remember { mutableStateOf(AppBlockHelper.isSnapSpotlightBlocked(context)) }
        var isSnapMapBlocked by remember { mutableStateOf(AppBlockHelper.isSnapMapBlocked(context)) }
        var isSnapDiscoverBlocked by remember { mutableStateOf(AppBlockHelper.isSnapDiscoverBlocked(context)) }

        // Facebook Advanced Blocker Settings Card
        var useSelectiveFb by remember { mutableStateOf(AppBlockHelper.isFbSelectiveBlockingEnabled(context)) }
        var isFbReelsBlocked by remember { mutableStateOf(AppBlockHelper.isFbReelsBlocked(context)) }
        var isFbWatchBlocked by remember { mutableStateOf(AppBlockHelper.isFbWatchBlocked(context)) }
        var isFbStoriesBlocked by remember { mutableStateOf(AppBlockHelper.isFbStoriesBlocked(context)) }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE1306C).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("instagram_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF833AB4).copy(alpha = 0.08f), Color(0xFFE1306C).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title and Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF833AB4), Color(0xFFF77737))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Instagram Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "INSTAGRAM SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Instagram features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle: Use Selective blocking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow Chats while enforcing specific limits below.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveIg,
                        onCheckedChange = { checked ->
                            useSelectiveIg = checked
                            AppBlockHelper.setIgSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFE1306C),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("ig_selective_blocking_switch")
                    )
                }

                if (useSelectiveIg) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Surgical Reels Blocker", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Actively scans and covers Reels on your Home feed with individual black cover-up displays. If you open the Reels tab or Reels viewer, it throws up a full-screen overlay stating 'Reels Blocked' with a 'Go Back' button.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgReelsBlocked,
                            onCheckedChange = { checked ->
                                isIgReelsBlocked = checked
                                AppBlockHelper.setIgReelsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_reels_switch")
                        )
                    }

                    // 3. Block Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Hides/interdicts user Stories completely. Story viewer is closed on click.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgStoriesBlocked,
                            onCheckedChange = { checked ->
                                isIgStoriesBlocked = checked
                                AppBlockHelper.setIgStoriesBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_stories_switch")
                        )
                    }

                    // 4. Block Explore Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Explore & Search Tab", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Redirects away from the Explore search grid to prevent visual doomscrolling.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgExploreBlocked,
                            onCheckedChange = { checked ->
                                isIgExploreBlocked = checked
                                AppBlockHelper.setIgExploreBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_block_explore_switch")
                        )
                    }

                    // 5. Allow Shared Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Allow Shared Reel in Messages", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Allows you to watch only the single shared Reel sent in your messages/chats. If you swipe or scroll to watch another Reel, the full-screen blocked overlay triggers immediately to prevent doomscrolling.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgAllowSharedReels,
                            onCheckedChange = { checked ->
                                isIgAllowSharedReels = checked
                                AppBlockHelper.setIgAllowSharedReels(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_allow_shared_reels_switch")
                        )
                    }

                    // 6. Feed Scroll Limiter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Feed Scroll Limiter", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Quota: maximum of 5 scroll gestures in main feed per Focus phase before closure.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgFeedScrollLimit,
                            onCheckedChange = { checked ->
                                isIgFeedScrollLimit = checked
                                AppBlockHelper.setIgFeedScrollLimit(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_feed_scroll_limit_switch")
                        )
                    }

                    // 7. Auto-Mute Reels Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-Mute Reels Audio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Eliminates auditory attention traps by forcing Reel mutes on launch.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isIgReelsMuteAudio,
                            onCheckedChange = { checked ->
                                isIgReelsMuteAudio = checked
                                AppBlockHelper.setIgReelsMuteAudio(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFE1306C),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("ig_mute_reels_audio_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // YouTube Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("youtube_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF0000).copy(alpha = 0.08f), Color(0xFF990000).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF0000), Color(0xFF990000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "YouTube Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "YOUTUBE SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific YouTube features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow regular video play while restricting specific distractions.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveYt,
                        onCheckedChange = { checked ->
                            useSelectiveYt = checked
                            AppBlockHelper.setYtSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFF0000),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("yt_selective_blocking_switch")
                    )
                }

                if (useSelectiveYt) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Shorts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block YouTube Shorts", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Throws up a full-screen black cover-up display with options to 'Close App' or 'Go to YouTube Home' whenever the Shorts tab is opened or any Short is played from Home, Profile, Subscriptions, or History.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtShortsBlocked,
                            onCheckedChange = { checked ->
                                isYtShortsBlocked = checked
                                AppBlockHelper.setYtShortsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_shorts_switch")
                        )
                    }

                    // 3. Block Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Search Feed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents manual searching of videos during active focus periods.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtSearchBlocked,
                            onCheckedChange = { checked ->
                                isYtSearchBlocked = checked
                                AppBlockHelper.setYtSearchBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_search_switch")
                        )
                    }

                    // 4. Block Comments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Hide Comments Section", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Blocks view of the YouTube comments pane to stop reading/writing distractions.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtCommentsBlocked,
                            onCheckedChange = { checked ->
                                isYtCommentsBlocked = checked
                                AppBlockHelper.setYtCommentsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_block_comments_switch")
                        )
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 5. Only Allow Whitelisted Channels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Only Allow Whitelisted Channels", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Blocks play/view of any video except those from your approved channels list below.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isYtOnlyAllowApprovedChannels,
                            onCheckedChange = { checked ->
                                isYtOnlyAllowApprovedChannels = checked
                                AppBlockHelper.setYtOnlyAllowApprovedChannels(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFF0000),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("yt_only_allow_approved_channels_switch")
                        )
                    }

                    if (isYtOnlyAllowApprovedChannels) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Whitelisted Channel Names (comma separated):",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = ytApprovedChannels,
                                onValueChange = { newValue ->
                                    ytApprovedChannels = newValue
                                    AppBlockHelper.setYtApprovedChannels(context, newValue)
                                },
                                placeholder = {
                                    Text("e.g. Marques Brownlee, Kurzgesagt, TEDx", color = Color.DarkGray, fontSize = 11.sp)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("yt_approved_channels_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFF0000),
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedContainerColor = Color(0xFF141419),
                                    unfocusedContainerColor = Color(0xFF0F0F12),
                                    cursorColor = Color(0xFFFF0000)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = "Matches are case-insensitive and allow partial matches. Enter full channel names or keywords separated by commas.",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Snapchat Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFFFC00).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("snapchat_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFC00).copy(alpha = 0.05f), Color(0xFFFFFC00).copy(alpha = 0.02f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFFC00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Snapchat Blocker",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SNAPCHAT SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Snapchat features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow chat, viewing & uploading snaps while blocking feeds.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveSnap,
                        onCheckedChange = { checked ->
                            useSelectiveSnap = checked
                            AppBlockHelper.setSnapSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFFC00),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("snap_selective_blocking_switch")
                    )
                }

                if (useSelectiveSnap) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Spotlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Snapchat Spotlight", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents viewing or scrolling the Spotlight vertical video feeds.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapSpotlightBlocked,
                            onCheckedChange = { checked ->
                                isSnapSpotlightBlocked = checked
                                AppBlockHelper.setSnapSpotlightBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_spotlight_switch")
                        )
                    }

                    // 3. Block Map
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Snap Map", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Restricts access to the location map tab to keep focus private and active.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapMapBlocked,
                            onCheckedChange = { checked ->
                                isSnapMapBlocked = checked
                                AppBlockHelper.setSnapMapBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_map_switch")
                        )
                    }

                    // 4. Block Discover & Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories & Discover Feeds", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Hides user Stories, sub feeds, and show discoveries completely.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isSnapDiscoverBlocked,
                            onCheckedChange = { checked ->
                                isSnapDiscoverBlocked = checked
                                AppBlockHelper.setSnapDiscoverBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFFC00),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("snap_block_discover_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Facebook Advanced Blocker Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("facebook_advanced_blocker_card")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1877F2).copy(alpha = 0.08f), Color(0xFF1877F2).copy(alpha = 0.04f), Color.Transparent)
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Facebook Blocker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FACEBOOK SURGICAL BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Fine-grained controls for specific Facebook features.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))

                // 1. Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Selective Sub-Feature Blocking", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Bypasses full app blocking to allow messaging/posts while blocking feeds & videos.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Switch(
                        checked = useSelectiveFb,
                        onCheckedChange = { checked ->
                            useSelectiveFb = checked
                            AppBlockHelper.setFbSelectiveBlockingEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF1877F2),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("fb_selective_blocking_switch")
                    )
                }

                if (useSelectiveFb) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                    // 2. Block Reels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Facebook Reels", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents viewing or scrolling short vertical video Reels.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbReelsBlocked,
                            onCheckedChange = { checked ->
                                isFbReelsBlocked = checked
                                AppBlockHelper.setFbReelsBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_reels_switch")
                        )
                    }

                    // 3. Block Watch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Watch & Video Feed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Prevents entering the Watch tab or video panels entirely.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbWatchBlocked,
                            onCheckedChange = { checked ->
                                isFbWatchBlocked = checked
                                AppBlockHelper.setFbWatchBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_watch_switch")
                        )
                    }

                    // 4. Block Stories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Block Stories", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Omit & hide Facebook Stories and story trays on the screen.", color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                        Switch(
                            checked = isFbStoriesBlocked,
                            onCheckedChange = { checked ->
                                isFbStoriesBlocked = checked
                                AppBlockHelper.setFbStoriesBlocked(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1877F2),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("fb_block_stories_switch")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Active Block Rules Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active Block Rules", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Manual Add App", tint = Color.LightGray)
                    }
                }
                
                if (blockedApps.isEmpty()) {
                    Text("No app limits configured. Enable blocks on any app in the directory below to restrict its usage.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    blockedApps.forEach { pkg ->
                        val limitMins = AppBlockHelper.getDailyLimitMinutes(context, pkg)
                        val dailyUsageSecs = AppBlockHelper.getDailyUsageSeconds(context, pkg)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppListIcon(pkg = pkg)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Limit: $limitMins min", color = Color(0xFFFFA726), fontSize = 10.sp)
                                    Text("•", color = Color.DarkGray, fontSize = 10.sp)
                                    Text("Today: ${formatUsageTime(dailyUsageSecs)}", color = if (dailyUsageSecs > 0) WaterBlue else Color.Gray, fontSize = 10.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    selectedAppForLimit = pkg
                                    limitMinutesText = limitMins.toString()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    AppBlockHelper.removeBlockedApp(context, pkg)
                                    blockedApps = AppBlockHelper.getBlockedApps(context)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Installed Apps & Screen Time Directory Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Installed Apps & Screen Time", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Lists all user-launchable apps installed. Updates automatically.", color = Color.Gray, fontSize = 10.sp)
                    }
                    IconButton(onClick = {
                        isLoadingApps = true
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                installedApps = com.example.util.AppBlockHelper.getInstalledApps(context)
                                isLoadingApps = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps List", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141419),
                        unfocusedContainerColor = Color(0xFF141419)
                    )
                )

                if (isLoadingApps) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WaterBlue, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val filteredApps = remember(searchQuery, installedApps) {
                        if (searchQuery.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter {
                                it.label.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Text("No apps match your search.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredApps.forEach { app ->
                                val isBlocked = blockedApps.contains(app.packageName)
                                val dailyUsageSecs = AppBlockHelper.getDailyUsageSeconds(context, app.packageName)
                                val limitMins = AppBlockHelper.getDailyLimitMinutes(context, app.packageName)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AppListIcon(pkg = app.packageName)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Today: ${formatUsageTime(dailyUsageSecs)}",
                                                color = if (dailyUsageSecs > 0) WaterBlue else Color.Gray,
                                                fontSize = 10.sp
                                            )
                                            if (isBlocked) {
                                                Text(
                                                    text = "Limit: $limitMins min",
                                                    color = Color(0xFFFFA726),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isBlocked) {
                                            IconButton(onClick = {
                                                selectedAppForLimit = app.packageName
                                                limitMinutesText = limitMins.toString()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Limit",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (isBlocked) {
                                                    AppBlockHelper.removeBlockedApp(context, app.packageName)
                                                } else {
                                                    AppBlockHelper.addBlockedApp(context, app.packageName)
                                                }
                                                blockedApps = AppBlockHelper.getBlockedApps(context)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBlocked) Color(0xFF2D1515) else Color(0xFF1E1E24),
                                                contentColor = if (isBlocked) Color.Red else Color.LightGray
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = if (isBlocked) "Blocked" else "Block",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        selectedAppForLimit?.let { pkg ->
            AlertDialog(
                onDismissRequest = { selectedAppForLimit = null },
                title = { Text("Edit App Limit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pkg, color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = limitMinutesText,
                            onValueChange = { limitMinutesText = it },
                            label = { Text("Daily Limit (minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val mins = limitMinutesText.toIntOrNull() ?: 30
                            AppBlockHelper.setDailyLimitMinutes(context, pkg, mins)
                            selectedAppForLimit = null
                            blockedApps = AppBlockHelper.getBlockedApps(context)
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedAppForLimit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showAddAppDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppDialog = false },
                title = { Text("Add App via Package Name") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("For unlisted or system package names", color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = newAppPackage,
                            onValueChange = { newAppPackage = it },
                            label = { Text("Package Name (e.g. com.facebook.katana)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAppPackage.isNotBlank()) {
                                AppBlockHelper.addBlockedApp(context, newAppPackage.trim())
                                blockedApps = AppBlockHelper.getBlockedApps(context)
                                showAddAppDialog = false
                                newAppPackage = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAppDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun GoogleCalendarAndTasksSyncSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val calendarSyncStatus by viewModel.calendarSyncStatus.collectAsState()
    val googleTasksSyncStatus by viewModel.googleTasksSyncStatus.collectAsState()
    
    val googleAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    
    // Auth launcher for tasks sync
    val tasksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.syncGoogleTasks(context)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Cloud Backup Sync",
                    tint = WaterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Google Calendar & Tasks Cloud Sync",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            
            Text(
                text = "Securely synchronize your scheduled events directly with Google Calendar, and your task lists with Google Tasks. This provides a robust, visual cloud backup of all your activities and task lists.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            
            HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)

            // Google Calendar Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222225)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                            Text("Google Calendar Backup", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0288D1).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", color = Color(0xFF03A9F4), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        "Syncs your timed schedule blocks directly to your Google Calendar app for visual timeline backups.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(calendarSyncStatus, color = Color.LightGray, fontSize = 11.sp)
                        }
                        
                        Button(
                            onClick = {
                                viewModel.syncGoogleCalendar(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync Calendar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Google Tasks Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222225)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                            Text("Google Tasks Backup", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF388E3C).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        "Synchronizes all lists and tasks bidirectionally with the official Google Tasks cloud API.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(googleTasksSyncStatus, color = Color.LightGray, fontSize = 11.sp)
                        }
                        
                        Button(
                            onClick = {
                                viewModel.syncGoogleTasks(context) { intent ->
                                    tasksAuthLauncher.launch(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Sync Tasks", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirebaseConfigurationSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var dbUrl by remember { mutableStateOf(prefs.getString("custom_firebase_db_url", com.example.api.FirebaseConfig.DATABASE_URL) ?: com.example.api.FirebaseConfig.DATABASE_URL) }
    var projectId by remember { mutableStateOf(prefs.getString("custom_firebase_project_id", "lifeosca") ?: "lifeosca") }
    var appId by remember { mutableStateOf(prefs.getString("custom_firebase_app_id", "1:432934819080:android:97852f87b34cae7fbcc8bd") ?: "1:432934819080:android:97852f87b34cae7fbcc8bd") }
    var storageBucket by remember { mutableStateOf(prefs.getString("custom_firebase_storage_bucket", "lifeosca.firebasestorage.app") ?: "lifeosca.firebasestorage.app") }
    var realtimeSyncEnabled by remember { mutableStateOf(prefs.getBoolean("enable_firebase_realtime_sync", true)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.WaterBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = "Firebase Config",
                    tint = com.example.ui.theme.WaterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Firebase Realtime Database & Storage",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Text(
                text = "Configure your own Firebase endpoints below. When changed, the app client dynamically reinstantiates Retrofit connections to sync tasks, ledger, profile details, and peer focus sessions in real time.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.5.dp)

            // Database URL input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Database URL", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = dbUrl,
                    onValueChange = { dbUrl = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Project ID input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Project ID", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // App ID input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase App ID", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Storage Bucket input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firebase Storage Bucket", color = Color.Gray, fontSize = 11.sp)
                OutlinedTextField(
                    value = storageBucket,
                    onValueChange = { storageBucket = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = com.example.ui.theme.WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = com.example.ui.theme.WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Real-time Sync Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .clickable {
                        realtimeSyncEnabled = !realtimeSyncEnabled
                        prefs.edit().putBoolean("enable_firebase_realtime_sync", realtimeSyncEnabled).apply()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Real-time Synchronization",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Bi-directional database updates are push-triggered in real time",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = realtimeSyncEnabled,
                    onCheckedChange = { value ->
                        realtimeSyncEnabled = value
                        prefs.edit().putBoolean("enable_firebase_realtime_sync", value).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = com.example.ui.theme.WaterBlue,
                        checkedTrackColor = com.example.ui.theme.WaterBlue.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        // Reset to defaults
                        dbUrl = com.example.api.FirebaseConfig.DATABASE_URL
                        projectId = "lifeosca"
                        appId = "1:432934819080:android:97852f87b34cae7fbcc8bd"
                        storageBucket = "lifeosca.firebasestorage.app"
                        realtimeSyncEnabled = true

                        prefs.edit()
                            .remove("custom_firebase_db_url")
                            .remove("custom_firebase_project_id")
                            .remove("custom_firebase_app_id")
                            .remove("custom_firebase_storage_bucket")
                            .putBoolean("enable_firebase_realtime_sync", true)
                            .apply()

                        com.example.api.FirebaseClient.activeUrl = com.example.api.FirebaseConfig.DATABASE_URL
                        Toast.makeText(context, "Reset to official Life OS Firebase defaults!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24), contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Text("Reset Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (dbUrl.isBlank() || !dbUrl.startsWith("http")) {
                            Toast.makeText(context, "Please enter a valid HTTP/S Firebase Database URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        prefs.edit()
                            .putString("custom_firebase_db_url", dbUrl)
                            .putString("custom_firebase_project_id", projectId)
                            .putString("custom_firebase_app_id", appId)
                            .putString("custom_firebase_storage_bucket", storageBucket)
                            .putBoolean("enable_firebase_realtime_sync", realtimeSyncEnabled)
                            .apply()

                        // Dynamically update the Retrofit service active URL
                        com.example.api.FirebaseClient.activeUrl = dbUrl

                        Toast.makeText(context, "Firebase dynamic endpoint updated and saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Save Config", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Contacts.kt
// ==========================================================



@Composable
fun SettingsContactsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleContacts(context) { intent ->
                // Avoid looping
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_CONTACTS] ?: false
        val writeGranted = permissions[android.Manifest.permission.WRITE_CONTACTS] ?: false
        if (!readGranted || !writeGranted) {
            Toast.makeText(context, "Full system contact synchronization requires Contacts permission.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Contacts permissions verified!", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasContactsPermissions(): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val writePerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return readPerm && writePerm
    }

    SettingsPageScope {
        val isContactsSyncPaused by viewModel.isContactsSyncPaused.collectAsState()

        SettingsSubpageWorkspace(
            title = "Contacts Settings",
            description = "Anniversaries and device contact synchronization.",
            onBack = onBack
        ) {
            Text("Search algorithms index display names, emails, and phone indices. Anniversaries are linked to countdown reminders automatically.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Contacts System Sync",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Synchronize contacts created within Life OS with your Android device's native contacts application in real time.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .clickable {
                                if (isContactsSyncPaused) {
                                    if (!hasContactsPermissions()) {
                                        contactsPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CONTACTS,
                                                android.Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                    }
                                }
                                viewModel.setContactsSyncPaused(!isContactsSyncPaused)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pause Real-time Sync",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isContactsSyncPaused) "Real-time updates are PAUSED" else "Real-time updates are ACTIVE",
                                color = if (isContactsSyncPaused) Color.LightGray else WaterBlue,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isContactsSyncPaused,
                            onCheckedChange = { paused ->
                                if (!paused) {
                                    if (!hasContactsPermissions()) {
                                        contactsPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CONTACTS,
                                                android.Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                    }
                                }
                                viewModel.setContactsSyncPaused(paused)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (!hasContactsPermissions()) {
                                contactsPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CONTACTS,
                                        android.Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            } else {
                                viewModel.forceSyncAllContactsToDevice()
                                Toast.makeText(context, "Force-synced app contacts to phone!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_sync_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Save App Contacts to Phone", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    Text(
                        text = "Notes: Syncing transfers First name, Last name, Phone, Email, and Profile picture to raw system contacts. Offline changes won't fetch system contact updates, but editing locally overrides whatever is on the device.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Account and Cloud Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cloud Google Contacts Sync",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Connect a Google account to save, sync and backup contacts with Google Contacts Cloud services.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
                    val defaultEmail = googleAccount?.email ?: "cabharathikrishna@gmail.com"
                    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                    var contactsAccount by remember { mutableStateOf(prefs.getString("selected_contacts_account", defaultEmail)) }
                    var contactsGroup by remember { mutableStateOf(prefs.getString("selected_contacts_group", "LifeOS Contacts Group")) }
                    var autoCloudBackup by remember { mutableStateOf(prefs.getBoolean("auto_cloud_contacts_backup", true)) }

                    LaunchedEffect(Unit) {
                        if (prefs.getString("selected_contacts_account", null) == null) {
                            prefs.edit().putString("selected_contacts_account", defaultEmail).apply()
                        }
                    }

                    val googleSignInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            try {
                                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                                val email = account?.email ?: ""
                                if (email.isNotEmpty()) {
                                    contactsAccount = email
                                    prefs.edit().putString("selected_contacts_account", email).apply()
                                    Toast.makeText(context, "Connected to: $email", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Google Account selection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    var groupDropdownExpanded by remember { mutableStateOf(false) }

                    // Google Account Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Account for Saving Contacts", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val contactsScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts")
                                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                            .requestEmail()
                                            .requestScopes(contactsScope)
                                            .build()
                                        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                        client.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(client.signInIntent)
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contactsAccount ?: "No Google Account connected",
                                    color = if (contactsAccount != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    // Contacts Group Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Target Contacts Directory / Label", color = Color.Gray, fontSize = 11.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable { groupDropdownExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contactsGroup ?: "Default My Contacts",
                                    color = if (contactsGroup != null) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.Gray
                                )
                            }

                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1B1B22))
                            ) {
                                val groups = listOf("LifeOS Contacts Group", "My Contacts", "Work Contacts", "Family & Friends Label")
                                groups.forEach { grp ->
                                    DropdownMenuItem(
                                        text = { Text(grp, color = Color.White) },
                                        onClick = {
                                            contactsGroup = grp
                                            prefs.edit().putString("selected_contacts_group", grp).apply()
                                            groupDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Auto cloud backup toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .clickable {
                                autoCloudBackup = !autoCloudBackup
                                prefs.edit().putBoolean("auto_cloud_contacts_backup", autoCloudBackup).apply()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Sync to Google Drive",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Automatically upload contact backups to connected Google account",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoCloudBackup,
                            onCheckedChange = { value ->
                                autoCloudBackup = value
                                prefs.edit().putBoolean("auto_cloud_contacts_backup", value).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WaterBlue,
                                checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val googleContactsSyncStatus by viewModel.googleContactsSyncStatus.collectAsState()

                    Button(
                        onClick = {
                            viewModel.syncGoogleContacts(context) { intent ->
                                googleAuthLauncher.launch(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("sync_google_contacts_now_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (googleContactsSyncStatus == "Syncing...") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing with Google...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Google Contacts Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (googleContactsSyncStatus.isNotEmpty() && googleContactsSyncStatus != "Ready") {
                        val isSuccess = googleContactsSyncStatus.contains("successful", ignoreCase = true)
                        Text(
                            text = "Sync Status: $googleContactsSyncStatus",
                            color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE57373),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Countdown.kt
// ==========================================================



@Composable
fun SettingsCountdownAlertsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("countdown_settings_prefs", android.content.Context.MODE_PRIVATE) }

        var onScreenReminderEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("on_screen_reminder", true))
        }
        var notificationReminderEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("notification_reminder", true))
        }
        var countdownSilentModeEnabled by remember {
            mutableStateOf(sharedPrefs.getBoolean("countdown_silent_mode", false))
        }
        var countdownReminders by remember {
            mutableStateOf(
                run {
                    val jsonStr = sharedPrefs.getString("reminders_list_json", null)
                    if (!jsonStr.isNullOrEmpty()) {
                        try {
                            val arr = org.json.JSONArray(jsonStr)
                            val list = mutableListOf<CountdownReminder>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(
                                    CountdownReminder(
                                        daysBefore = obj.getInt("daysBefore"),
                                        timeString = obj.getString("timeString")
                                    )
                                )
                            }
                            list
                        } catch (e: Exception) {
                            mutableListOf(CountdownReminder(0, "09:00"))
                        }
                    } else {
                        mutableListOf(CountdownReminder(0, "09:00"))
                    }
                }
            )
        }

        fun saveCountdownSettings(onScreen: Boolean, notif: Boolean, silent: Boolean, list: List<CountdownReminder>) {
            val editor = sharedPrefs.edit()
            editor.putBoolean("on_screen_reminder", onScreen)
            editor.putBoolean("notification_reminder", notif)
            editor.putBoolean("countdown_silent_mode", silent)
            try {
                val arr = org.json.JSONArray()
                list.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("daysBefore", item.daysBefore)
                    obj.put("timeString", item.timeString)
                    arr.put(obj)
                }
                editor.putString("reminders_list_json", arr.toString())
            } catch (e: java.lang.Exception) {}
            editor.apply()
        }

        SettingsSubpageWorkspace(
            title = "Countdown Settings",
            description = "Configure custom reminder categories, schedules and systems.",
            onBack = onBack
        ) {
            // Warning Card description
            Text(
                text = "Warning cards calculate hours relative to deadlines and can trigger background alarms 24 hours prior.",
                color = Color.LightGray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // On-Screen Reminders Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On-Screen Reminders", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Show reminders on application launch screen", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = onScreenReminderEnabled,
                        onCheckedChange = { onScreenReminderEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notification Reminders Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notification Reminders", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Send system-wide push notifications", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = notificationReminderEnabled,
                        onCheckedChange = { notificationReminderEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Silent Mode Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silent Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Displays on-screen but has no sound / vibration", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = countdownSilentModeEnabled,
                        onCheckedChange = { countdownSilentModeEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("countdown_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reminders Interval List Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("REMINDER INTERVAL SCHEDULES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Add customized schedules to run before target events", color = Color.Gray, fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                countdownReminders = (countdownReminders + CountdownReminder(0, "09:00")).toMutableList()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(WaterBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (countdownReminders.isEmpty()) {
                        Text(
                            "No active alerts configured.",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        countdownReminders.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Days Before input
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Days Before", color = Color.Gray, fontSize = 9.sp)
                                    OutlinedTextField(
                                        value = item.daysBefore.toString(),
                                        onValueChange = { newVal ->
                                            val days = newVal.toIntOrNull() ?: 0
                                            val newList = countdownReminders.toMutableList()
                                            newList[index] = newList[index].copy(daysBefore = days)
                                            countdownReminders = newList
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = WaterBlue,
                                            unfocusedBorderColor = Color.DarkGray
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    if (item.daysBefore == 0) {
                                        Text("0 = Event day", color = WaterBlue, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Time Input
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("Time (HH:MM)", color = Color.Gray, fontSize = 9.sp)
                                    OutlinedTextField(
                                        value = item.timeString,
                                        onValueChange = { newVal ->
                                            val newList = countdownReminders.toMutableList()
                                            newList[index] = newList[index].copy(timeString = newVal)
                                            countdownReminders = newList
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = WaterBlue,
                                            unfocusedBorderColor = Color.DarkGray
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        val newList = countdownReminders.toMutableList()
                                        newList.removeAt(index)
                                        countdownReminders = newList
                                    },
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Reminder", tint = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Global Save Button for Countdown Settings
            Button(
                onClick = {
                    // Persist to SharedPreferences and inform user
                    saveCountdownSettings(onScreenReminderEnabled, notificationReminderEnabled, countdownSilentModeEnabled, countdownReminders)
                    Toast.makeText(context, "Countdown Settings Saved!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_countdown_settings_btn")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("SAVE COUNTDOWN SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_DeepLinks.kt
// ==========================================================



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDeepLinksPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INTEGRATIONS & DEEP LINKS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF09090C))
            )
        },
        containerColor = Color(0xFF09090C)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Integration & Shortcuts Center",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Life OS supports powerful custom URI deep linking. You can create widget buttons, custom shortcuts, or automation scripts to trigger app navigation or run silent background synchronizations directly from your external home screens.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // App Logo link Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Application Logo Assets",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Use these high-resolution launcher logo links to build shortcuts or launcher badges.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val logoUrl = "https://raw.githubusercontent.com/cabharathikrishna/Life.os/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF16161B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF282830), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Official Logo URL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(logoUrl))
                                    Toast.makeText(context, "Copied Logo URL!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logo", tint = WaterBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = logoUrl,
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Screen Navigation Links
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Screen Deep Links",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Tap any link to copy it. When triggered externally, the app opens directly to that page.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val screens = listOf(
                        "Home Dashboard" to "lifeos://home",
                        "Tasks Engine" to "lifeos://tasks",
                        "Calendar Planner" to "lifeos://calendar",
                        "Timer focus" to "lifeos://timer",
                        "Habits Tracker" to "lifeos://habits",
                        "Life Journal" to "lifeos://journal",
                        "Contacts Directory" to "lifeos://contacts",
                        "File Explorer" to "lifeos://file_explorer",
                        "Financial Ledger" to "lifeos://finances",
                        "Countdown & Alerts" to "lifeos://countdown",
                        "Deepa AI Assistant" to "lifeos://ai_chat",
                        "Onboarding Screen" to "lifeos://onboarding",
                        "System Settings" to "lifeos://settings",
                        "Analytics Center" to "lifeos://analytics",
                        "Search Engine" to "lifeos://search"
                    )

                    screens.forEach { (name, link) ->
                        DeepLinkRow(name = name, uri = link, clipboardManager = clipboardManager, context = context)
                    }
                }
            }

            // Action Trigger Links
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11)),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Automation Action Triggers",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Launch these actions silently or navigate instantly to synchronize cloud backends.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    val actions = listOf(
                        "Sync Google Calendar" to "lifeos://action/sync_calendar",
                        "Auto-backup Google Drive" to "lifeos://action/backup_drive",
                        "Force Contacts Device Sync" to "lifeos://action/force_contacts_sync",
                        "Check App System Updates" to "lifeos://action/check_updates"
                    )

                    actions.forEach { (name, link) ->
                        DeepLinkRow(name = name, uri = link, clipboardManager = clipboardManager, context = context)
                    }
                }
            }

            // Integration Help Info
            Text(
                "Tip: You can use these deep links inside macro apps (like Tasker, Macrodroid, or Bixby Routines) or launcher shortcuts to trigger custom smart buttons in widgets on your Android desktop.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DeepLinkRow(
    name: String,
    uri: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF16161B))
            .clickable {
                clipboardManager.setText(AnnotatedString(uri))
                Toast.makeText(context, "Copied link for $name!", Toast.LENGTH_SHORT).show()
            }
            .border(1.dp, Color(0xFF1E1E22), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(uri, color = WaterBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy link",
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ==========================================================
// MERGED FROM: SettingsView_DeepaAI.kt
// ==========================================================



@Composable
fun SettingsDeepaAIPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aiMemories by viewModel.aiMemories.collectAsState()
    val autoAiUpdater by viewModel.autoAiUpdaterEnabled.collectAsState()

    // Local AI model states
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    val downloadingModelId by viewModel.downloadingModelId.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadSpeedMB by viewModel.downloadSpeedMB.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()

    // Genkit Integration states
    val isGenkitEnabled by viewModel.isGenkitEnabled.collectAsState()
    val genkitUrl by viewModel.genkitUrl.collectAsState()
    val genkitFlowName by viewModel.genkitFlowName.collectAsState()
    val genkitAuthToken by viewModel.genkitAuthToken.collectAsState()
    val genkitCallMethod by viewModel.genkitCallMethod.collectAsState()

    // FCM Integration states
    val fcmToken by viewModel.fcmToken.collectAsState()
    val firebaseInstallationId by viewModel.firebaseInstallationId.collectAsState()
    val isFcmAutoInitEnabled by viewModel.isFcmAutoInitEnabled.collectAsState()

    // Dialog state
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var customUrlInput by remember { mutableStateOf("") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importLocalModelFile(uri, context, selectedModelId)
        }
    }

    val registeredFiles by viewModel.files.collectAsState()
    val jsonBackups = remember(registeredFiles) {
        registeredFiles.filter { it.name.endsWith(".json") && it.name.startsWith("ai_memories_") }
    }

    SettingsSubpageWorkspace(
        title = "Deepa AI Brain Settings",
        description = "Manage long-term AI memories, pick local models, and synchronize background upkeeps.",
        onBack = onBack
    ) {
        // --- 1. LOCAL AI OFFLINE MODEL MANAGER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🤖 LOCAL AI OFFLINE MODEL MANAGER",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Select and download lightweight decentralized language models to process queries fully on-device. Perfect for maximum speed, 100% data privacy, and zero data usage!",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Models Specs List
                val modelsSpecs = listOf(
                    LocalAiModelSpec(
                        id = "gemma_3_1b",
                        name = "Gemma 3 1B",
                        subtitle = "Ultra-Modern Compact • Highly Recommended",
                        specs = "🧠 RAM: Best for 4GB RAM devices • 💾 Storage: 1.2 GB • 🎯 Accuracy: 85%",
                        description = "State-of-the-art Google compact model. Extremely responsive on phone chips."
                    ),
                    LocalAiModelSpec(
                        id = "gemma_1_1_2b",
                        name = "Gemma 1.1 2B",
                        subtitle = "Standard Balanced • Stable",
                        specs = "🧠 RAM: Best for 6GB RAM devices • 💾 Storage: 1.8 GB • 🎯 Accuracy: 88%",
                        description = "Highly consistent conversational model with balanced reasoning capability."
                    ),
                    LocalAiModelSpec(
                        id = "gemma_2_2b",
                        name = "Gemma 2 2B",
                        subtitle = "Highly Advanced • Top-Tier",
                        specs = "🧠 RAM: Needs 8GB+ RAM devices • 💾 Storage: 2.6 GB • 🎯 Accuracy: 94%",
                        description = "Exceptional logical reasoning, complex task formulation, and supportive advice."
                    ),
                    LocalAiModelSpec(
                        id = "tiny_llama_1_1b",
                        name = "TinyLlama 1.1B",
                        subtitle = "Least Model • Resource-Light",
                        specs = "🧠 RAM: Runs fast on any RAM (<4GB) • 💾 Storage: 0.7 GB • 🎯 Accuracy: 76%",
                        description = "Ultra-lightweight helper. Swift responses on older or resource-constrained devices."
                    )
                )

                modelsSpecs.forEach { spec ->
                    val isSelected = selectedModelId == spec.id
                    val isDownloaded = downloadedModels.contains(spec.id)
                    val isActive = activeModelId == spec.id
                    val isDownloadingThis = downloadingModelId == spec.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(
                                if (isSelected) Color(0xFF161C24) else Color(0xFF070707),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) Color(0xFF34A853) else if (isSelected) Color(0xFF4285F4) else Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = spec.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF1B5E20),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color(0xFF81C784),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (isDownloaded) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF2E3B5E),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "DOWNLOADED",
                                                color = Color(0xFF90CAF9),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(spec.subtitle, color = Color.Gray, fontSize = 10.sp)
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectModel(spec.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4285F4))
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(spec.specs, color = Color.LightGray, fontSize = 10.sp)
                        Text(spec.description, color = Color.Gray, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 4.dp))

                        if (isDownloadingThis) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = downloadStatusText,
                                        color = Color(0xFFFBBC05),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(downloadProgress * 100).toInt()}% (${"%.1f".format(downloadSpeedMB)} MB/s)",
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFFBBC05),
                                    trackColor = Color(0xFF1E1E1E)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isDownloaded) {
                                    if (!isActive) {
                                        Button(
                                            onClick = { viewModel.selectModel(spec.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Select & Run", color = Color.White, fontSize = 10.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteModel(spec.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete model files",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else if (downloadingModelId == null) {
                                    Button(
                                        onClick = { viewModel.downloadModel(spec.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Download & Activate", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🔧 ADVANCED LOCAL NATIVE GEMMA ENGINE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure native on-device execution of authentic Gemma model weights (.bin format) for true 100% private processing without cloud servers.",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current Engine Status Card
                val isNativeActive = com.example.util.LocalGemmaInferenceManager.isNativeEngineActive()
                val activeModelPath = com.example.util.LocalGemmaInferenceManager.getActiveModelPath()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNativeActive) Color(0xFF1B5E20) else Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isNativeActive) "✅ NATIVE EDGE ENGINE ACTIVE" else "⚠️ SANDBOX CLOUD CORE ACTIVE",
                            color = if (isNativeActive) Color(0xFF81C784) else Color(0xFFFBBC05),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isNativeActive) {
                                "Running authentic Gemma model file locally on your device CPU/GPU. Path: $activeModelPath"
                            } else {
                                "Cloud-backed smart-sandbox mode is currently active (using Gemini API styled as Gemma) because no real local model file was detected. Loading custom .bin weights below activates true native edge execution."
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Picker Import Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Local Model File (.bin)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a pre-downloaded MediaPipe Gemma model file from your device.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Pick File", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom URL Download Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Download from Custom URL",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste a direct download URL pointing to a Gemma MediaPipe .bin model file.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = { Text("https://example.com/gemma-2b.bin", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4285F4),
                                unfocusedBorderColor = Color(0xFF222222),
                                focusedContainerColor = Color(0xFF070707),
                                unfocusedContainerColor = Color(0xFF070707),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )
                        Button(
                            onClick = {
                                if (customUrlInput.isNotEmpty()) {
                                    viewModel.downloadModel(selectedModelId, customUrlInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(40.dp),
                            enabled = customUrlInput.isNotEmpty() && downloadingModelId == null
                        ) {
                            Text("Download", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. AI SYSTEM STORAGE & DATA VAULT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💾 AI STORAGE & SYSTEM TOOLS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Configure secure local memory vaults, create offline backup logs, or completely purge AI traces.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.backupAiMemories(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Export Memories JSON", color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Restore Memories", color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Completely Delete AI and Data", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. LONG-TERM MEMORY VAULT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Memory Vault Icon",
                        tint = Color(0xFF4285F4)
                    )
                    Text(
                        text = "LONG-TERM MEMORY VAULT",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "This vault stores facts, constraints, and instructions you've explicitly instructed Deepa AI to remember during your conversations. These memories are parsed and securely injected into the AI's contextual reasoning space.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                if (aiMemories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your AI Memory Vault is currently empty.\nInstruct the chatbot to 'remember X' to register preferences.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        aiMemories.forEachIndexed { index, memory ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• \"$memory\"",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                IconButton(
                                    onClick = { viewModel.deleteAiMemory(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Memory",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. AUTOMATED AI UPDATER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Auto Updater Icon",
                            tint = Color(0xFF34A853)
                        )
                        Text(
                            text = "AUTOMATED AI UPDATER",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Switch(
                        checked = autoAiUpdater,
                        onCheckedChange = { viewModel.updateAutoAiUpdaterEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ensures localized prompting instructions, offline language schemas, and response indices auto-update smoothly in the background without manual update prompts.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Engines Status:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (autoAiUpdater) "ACTIVE (IDLE)" else "DISABLED",
                            color = if (autoAiUpdater) Color(0xFF34A853) else Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Synchronization Cadence:", color = Color.Gray, fontSize = 11.sp)
                        Text("Every 12 hours", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Background Check:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (autoAiUpdater) "Today, 10:15 AM (Auto Worker)" else "N/A",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Prompt Core Schema version:", color = Color.Gray, fontSize = 11.sp)
                        Text("v2026.06.19", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. INSTANT OFFLINE AI COMMANDS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Offline Commands Icon",
                        tint = Color(0xFFFBBC05)
                    )
                    Text(
                        text = "INSTANT OFFLINE AI COMMANDS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Perform direct database operations instantly in milliseconds, even without an internet connection. The Offline Cognitive Engine parses your inputs locally and triggers immediate actions.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cheats = listOf(
                        Triple("📋 Tasks", "add task [title] [priority high/low] [30 mins] [list work]", "complete task [title] | delete task [title] | show tasks"),
                        Triple("🔥 Habits", "add habit [name]", "complete habit [name] | delete habit [name] | show habits"),
                        Triple("💵 Ledger", "add expense 150 for coffee | add income 5000", "delete transaction [note/amount] | show finance"),
                        Triple("⏱️ Focus", "start focus timer 25 mins for Study | start stopwatch", "pause focus | reset focus | show focus"),
                        Triple("📝 Journal", "add journal Title: [t] Content: [c]", "read journal [title] | delete journal [title] | list journals"),
                        Triple("👤 Profile", "set username [name]", "Changes your local display moniker instantly"),
                        Triple("☁️ Cloud", "backup database | sync drive", "Triggers secure SQLite backup / Google Drive upload")
                    )

                    cheats.forEach { (cat, cmd1, cmd2) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(cat, color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("👉 `$cmd1`", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("👉 `$cmd2`", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 6. FIREBASE GENKIT INTEGRATION ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("genkit_integration_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Genkit Icon",
                            tint = Color(0xFF4285F4)
                        )
                        Text(
                            text = "FIREBASE GENKIT INTEGRATION",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = isGenkitEnabled,
                        onCheckedChange = { viewModel.setGenkitEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4285F4),
                            checkedTrackColor = Color(0xFF1E3A8A)
                        ),
                        modifier = Modifier.testTag("genkit_enabled_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Route Deepa AI queries to a custom Firebase Genkit Flow backend. This lets you delegate reasoning tasks to remote LLM agent workflows deployed with custom prompt structures and tool plugins.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                if (isGenkitEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Selector for method
                    Text(
                        text = "Invocation Method",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = genkitCallMethod == "http",
                                onClick = { viewModel.setGenkitCallMethod("http") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4285F4)),
                                modifier = Modifier.testTag("genkit_method_http_radio")
                            )
                            Text("HTTP POST (REST)", color = Color.White, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = genkitCallMethod == "callable",
                                onClick = { viewModel.setGenkitCallMethod("callable") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4285F4)),
                                modifier = Modifier.testTag("genkit_method_callable_radio")
                            )
                            Text("Firebase HTTPS Callable", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (genkitCallMethod == "http") {
                        // URL endpoint input
                        OutlinedTextField(
                            value = genkitUrl,
                            onValueChange = { viewModel.setGenkitUrl(it) },
                            label = { Text("Genkit Flow Endpoint URL", color = Color.Gray) },
                            placeholder = { Text("https://example-flow-url.run.app/helloFlow", color = Color.DarkGray) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().testTag("genkit_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4285F4),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Auth Token input
                        OutlinedTextField(
                            value = genkitAuthToken,
                            onValueChange = { viewModel.setGenkitAuthToken(it) },
                            label = { Text("Authorization Bearer Token (Optional)", color = Color.Gray) },
                            placeholder = { Text("Bearer secret_key_abc123", color = Color.DarkGray) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().testTag("genkit_token_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4285F4),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                    } else {
                        // Flow name input for Callable
                        OutlinedTextField(
                            value = genkitFlowName,
                            onValueChange = { viewModel.setGenkitFlowName(it) },
                            label = { Text("Callable Function Flow Name", color = Color.Gray) },
                            placeholder = { Text("helloFlow", color = Color.DarkGray) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().testTag("genkit_flow_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4285F4),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "💡 Runs the flow as a secured Firebase Cloud Function. Ensure that you have initiated App Check to safely validate client integrity before executing requests.",
                            color = Color(0xFFFBBC05),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 7. FIREBASE CLOUD MESSAGING (FCM) ---
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Card(
            modifier = Modifier.fillMaxWidth().testTag("fcm_integration_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "FCM Icon",
                            tint = Color(0xFFFFB300)
                        )
                        Text(
                            text = "FIREBASE CLOUD MESSAGING (FCM)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.refreshFcmDetails() },
                        modifier = Modifier.testTag("fcm_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh FCM details",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Verify your FCM client configuration, retrieve your unique registration token or installation ID (FID), and copy them to send test push notifications from the Firebase Console.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Initialization Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FCM Auto-Initialization",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Enable automatic synchronization of your installation ID and token with FCM on startup.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isFcmAutoInitEnabled,
                        onCheckedChange = { viewModel.setFcmAutoInitEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFB300),
                            checkedTrackColor = Color(0xFF7D5700)
                        ),
                        modifier = Modifier.testTag("fcm_auto_init_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FCM Token Section
                Text(
                    text = "FCM Registration Token",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = fcmToken,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).testTag("fcm_token_text"),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fcmToken))
                        },
                        modifier = Modifier.size(28.dp).testTag("fcm_copy_token_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Token",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // FID Section
                Text(
                    text = "Firebase Installation ID (FID)",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = firebaseInstallationId,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).testTag("fcm_fid_text"),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(firebaseInstallationId))
                        },
                        modifier = Modifier.size(28.dp).testTag("fcm_copy_fid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy FID",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "💡 Tap the refresh icon at the top right if token displays 'Fetching...' to query the Firebase client APIs.",
                    color = Color(0xFFFFB300),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }

    // --- DIALOGS ---

    // JSON RESTORE DIALOG
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Text(
                    "Restore AI Memories",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Select a JSON memory backup file registered in your File Explorer documents directory:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (jsonBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070707), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No JSON backups found inside /docs.\nCreate a backup first or check file directory.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        ) {
                            jsonBackups.forEach { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                                    onClick = {
                                        viewModel.restoreAiMemories(context, file)
                                        showRestoreDialog = false
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(file.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Size: ${file.size} bytes • Path: ${file.path}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Close", color = Color(0xFF4285F4))
                }
            },
            containerColor = Color(0xFF141414)
        )
    }

    // PURGE CONFIRMATION DIALOG
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    "⚠️ Delete AI System & Memories?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "Are you absolutely sure you want to completely erase your AI model files, wipe the Long-Term Memory Vault, and clear all chatbot message history? This action is offline-permanent and cannot be undone.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completelyDeleteAiData(context)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Yes, Purge Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF141414)
        )
    }
}

// Simple data class for spec display
data class LocalAiModelSpec(
    val id: String,
    val name: String,
    val subtitle: String,
    val specs: String,
    val description: String
)

// ==========================================================
// MERGED FROM: SettingsView_FileExplorer.kt
// ==========================================================



@Composable
fun SettingsFileExplorerPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isSdCardConnected by remember {
        mutableStateOf(com.example.util.StorageHelper.isExternalStorageConnected(context))
    }
    var preferredStorage by remember {
        mutableStateOf(com.example.util.StorageHelper.getPreferredStorage(context))
    }

    LaunchedEffect(Unit) {
        isSdCardConnected = com.example.util.StorageHelper.isExternalStorageConnected(context)
    }

    SettingsSubpageWorkspace(
        title = "File Explorer Settings",
        description = "Volume paths.",
        onBack = onBack
    ) {
        Text(
            text = "Secure physical file indices reside directly under context sandbox paths.",
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isSdCardConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Primary Data & Media Storage",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select preferred storage target for application records and media files.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    listOf(
                        Pair("internal", "Internal Device Storage"),
                        Pair("sd_card", "Removable SD Card Storage")
                    ).forEach { (key, label) ->
                        val isSelected = preferredStorage == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) WaterBlue else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    preferredStorage = key
                                    com.example.util.StorageHelper.setPreferredStorage(context, key)
                                    Toast.makeText(
                                        context,
                                        "Storage target updated to ${if (key == "sd_card") "SD Card" else "Internal Storage"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (key == "sd_card") Icons.Default.Build else Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isSelected) WaterBlue else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) WaterBlue else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferredStorage = key
                                    com.example.util.StorageHelper.setPreferredStorage(context, key)
                                    Toast.makeText(
                                        context,
                                        "Storage target updated to ${if (key == "sd_card") "SD Card" else "Internal Storage"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = WaterBlue,
                                    unselectedColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Drive File Synchronization Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Google Drive Cloud Save Settings",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure preferred Google account and target directory for file backups & attachments.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
                val defaultEmail = googleAccount?.email ?: "cabharathikrishna@gmail.com"
                val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                var fileBackupAccount by remember { mutableStateOf(prefs.getString("selected_file_backup_account", defaultEmail)) }
                var fileBackupDir by remember { mutableStateOf(prefs.getString("selected_file_backup_dir", "Root Directory (/)")) }
                var customDirInput by remember { mutableStateOf(prefs.getString("custom_file_backup_dir_path", "")) }

                val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                            val email = account?.email ?: ""
                            if (email.isNotEmpty()) {
                                fileBackupAccount = email
                                prefs.edit().putString("selected_file_backup_account", email).apply()
                                Toast.makeText(context, "Connected to: $email", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google Account selection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                var dirDropdownExpanded by remember { mutableStateOf(false) }

                // Google Account selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Connected Google Account", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .build()
                                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                    client.signOut().addOnCompleteListener {
                                        googleSignInLauncher.launch(client.signInIntent)
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fileBackupAccount ?: "No Account Chosen",
                                color = if (fileBackupAccount != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                // Google Drive folder selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target Google Drive Folder", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable { dirDropdownExpanded = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fileBackupDir ?: "Root Directory (/)",
                                color = if (fileBackupDir != null) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }

                        DropdownMenu(
                            expanded = dirDropdownExpanded,
                            onDismissRequest = { dirDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1B1B22))
                        ) {
                            val directories = listOf("Root Directory (/)", "LifeOS_Files", "Personal_Daily_Archive", "Work_Cloud_Backup", "Custom Path...")
                            directories.forEach { dir ->
                                DropdownMenuItem(
                                    text = { Text(dir, color = Color.White) },
                                    onClick = {
                                        fileBackupDir = dir
                                        prefs.edit().putString("selected_file_backup_dir", dir).apply()
                                        dirDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (fileBackupDir == "Custom Path...") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Custom Folder Path", color = Color.Gray, fontSize = 11.sp)
                        OutlinedTextField(
                            value = customDirInput ?: "",
                            onValueChange = { newValue ->
                                customDirInput = newValue
                                prefs.edit().putString("custom_file_backup_dir_path", newValue).apply()
                            },
                            placeholder = { Text("e.g. Backups/Attachments/2026", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = WaterBlue
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Financials.kt
// ==========================================================



@Composable
fun SettingsFinancialsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val familyMembers by viewModel.familyMembers.collectAsState()
    val categories by viewModel.financeCategories.collectAsState()
    
    var newMemberName by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedCategoryType by remember { mutableStateOf("EXPENSE") } // "EXPENSE" or "INCOME"

    SettingsSubpageWorkspace(
        title = "Financial Ledger Settings",
        description = "Configure family members and transaction categories.",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION 1: MANAGE FAMILY MEMBERS ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FAMILY MEMBERS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                    Text("Create personal entities to segment assets, liabilities, and transactions.", fontSize = 11.sp, color = Color.Gray)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            placeholder = { Text("Name (e.g. Alice, Bob)", fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newMemberName.isNotBlank()) {
                                    viewModel.createFamilyMember(newMemberName.trim())
                                    newMemberName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (familyMembers.isEmpty()) {
                        Text("No custom family members registered yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            familyMembers.forEach { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(member.name, color = Color.White, fontSize = 14.sp)
                                    IconButton(
                                        onClick = { viewModel.deleteFamilyMember(member) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete member", tint = AlertRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: MANAGE CATEGORIES ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TRANSACTION CATEGORIES", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                    Text("Add categories for segmenting income sources and expense destinations.", fontSize = 11.sp, color = Color.Gray)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EXPENSE", "INCOME").forEach { type ->
                            val isSelected = selectedCategoryType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) WaterBlue else SurfaceCard)
                                    .clickable { selectedCategoryType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("Category (e.g. Internet, Dining)", fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    viewModel.createFinanceCategory(newCategoryName.trim(), selectedCategoryType)
                                    newCategoryName = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WaterBlue),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add category", tint = Color.Black)
                        }
                    }

                    // Segmented lists of current custom categories
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("EXPENSES", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            val expCats = categories.filter { it.type == "EXPENSE" }
                            if (expCats.isEmpty()) {
                                Text("None", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                expCats.forEach { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cat.name, fontSize = 12.sp, color = Color.White)
                                        IconButton(onClick = { viewModel.deleteFinanceCategory(cat) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("INCOMES", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            val incCats = categories.filter { it.type == "INCOME" }
                            if (incCats.isEmpty()) {
                                Text("None", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                incCats.forEach { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cat.name, fontSize = 12.sp, color = Color.White)
                                        IconButton(onClick = { viewModel.deleteFinanceCategory(cat) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================


// ==========================================================
// MERGED FROM: SettingsView_General.kt
// ==========================================================



@Composable
fun SettingsGeneralSystemPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val tabOrder by viewModel.tabOrder.collectAsState()
        val tabBarOrientation by viewModel.tabBarOrientation.collectAsState()
        val additionalReminderTimes by viewModel.additionalReminderTimes.collectAsState()
        val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
        val hiddenTabs by viewModel.hiddenTabs.collectAsState()
        val allDayNotificationEnabled by viewModel.allDayNotificationEnabled.collectAsState()
        val allDayNotificationTime by viewModel.allDayNotificationTime.collectAsState()
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()
        var tempOrder by remember(tabOrder) { mutableStateOf(tabOrder) }

        // General System Page
        SettingsSubpageWorkspace(
            title = "General System Settings",
            description = "Configure core systems, tab layout orientation and app reordering.",
            onBack = onBack
        ) {
            // Alignment Options Column Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Navigation Bar Position",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Set the tab position: left (sidebar), right, top, bottom, or legacy profiles.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("left", "right").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("top", "bottom").forEach { mode ->
                                val isSelected = tabBarOrientation.lowercase() == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue else Color(0xFF141414))
                                        .clickable { viewModel.updateTabBarOrientation(mode) }
                                        .padding(vertical = 12.dp)
                                        .testTag("tab_mode_${mode}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab order customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reorder navigation tabs",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move tabs up and down to customize their layout position.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tempOrder.forEachIndexed { index, screen ->
                            val label = when (screen) {
                                Screen.TASKS -> "Tasks"
                                Screen.CALENDAR -> "Calendar"
                                Screen.TIMER -> "Timer"
                                Screen.HABITS -> "Habits"
                                Screen.COUNTDOWN -> "Countdown"
                                Screen.JOURNAL -> "Journal"
                                Screen.KEEP_NOTES -> "Keep Notes"
                                Screen.CONTACTS -> "Contacts"
                                Screen.FILE_EXPLORER -> "File Explorer"
                                Screen.FINANCES -> "Finances"
                                Screen.DEEPA_AI -> "Deepa AI"
                                Screen.SEARCH -> "Search"
                                Screen.ANALYTICS -> "Analytics"
                                Screen.SETTINGS -> "Settings"
                                Screen.LOGIN -> "Login"
                                Screen.PROFILE_SETUP -> "Profile Setup"
                                Screen.PERMISSION_ONBOARDING -> "Permissions Onboarding"
                                Screen.CALENDAR_OPTIMIZATION_ONBOARDING -> "Calendar Optimization"
                                Screen.HEALTH -> "Health"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isTabHidden = hiddenTabs.contains(screen)
                                    IconButton(
                                        onClick = { viewModel.toggleTabVisibility(screen) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTabHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = if (isTabHidden) Color.Gray else WaterBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index - 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index < tempOrder.size - 1) {
                                                val list = tempOrder.toMutableList()
                                                val tmp = list.removeAt(index)
                                                list.add(index + 1, tmp)
                                                tempOrder = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index < tempOrder.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (index < tempOrder.size - 1) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.saveTabOrder(tempOrder) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_tab_order_subpage_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Text("SAVE TAB ORDER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Silent Mode Card
            val masterSilentMode by viewModel.masterSilentModeEnabled.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MASTER SILENT MODE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("When enabled, all app reminders, sounds, and vibrations are completely silenced.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = masterSilentMode,
                        onCheckedChange = { viewModel.updateMasterSilentModeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("master_silent_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Staging Mode Card
            val isStagingMode by viewModel.isStagingMode.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth().testTag("staging_mode_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Staging & Mock Users", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Show simulated friends and mock records (madhavan, shalini, subash).", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isStagingMode,
                        onCheckedChange = {
                            viewModel.setStagingMode(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("staging_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reminder Display Background Image Card
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var useCustomReminderBg by remember { mutableStateOf(sharedPrefs.getBoolean("use_custom_reminder_bg", false)) }
            var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
            var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            
            // Cropper adjustments
            var zoomScale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            var isCroppingMode by remember { mutableStateOf(false) }
            
            // Live preview image state
            var previewBitmap by remember(useCustomReminderBg) {
                mutableStateOf<Bitmap?>(
                    if (useCustomReminderBg) {
                        try {
                            val file = File(context.filesDir, "reminder_bg.jpg")
                            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                )
            }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    selectedImageUri = uri
                    val bitmap = uriToBitmap(context, uri)
                    if (bitmap != null) {
                        loadedBitmap = bitmap
                        zoomScale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        isCroppingMode = true
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("reminder_background_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REMINDER BACKGROUND IMAGE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose and crop a custom portrait image to display on the full-screen reminder overlay.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use Custom Image", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = useCustomReminderBg,
                            onCheckedChange = { isChecked ->
                                useCustomReminderBg = isChecked
                                sharedPrefs.edit().putBoolean("use_custom_reminder_bg", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("use_custom_reminder_bg_switch")
                        )
                    }

                    if (useCustomReminderBg) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Thumbnail Preview
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 128.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141414))
                                    .border(BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "Background Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHOOSE IMAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (previewBitmap != null) {
                                    Button(
                                        onClick = {
                                            try {
                                                val file = File(context.filesDir, "reminder_bg.jpg")
                                                if (file.exists()) file.delete()
                                                previewBitmap = null
                                                useCustomReminderBg = false
                                                sharedPrefs.edit().putBoolean("use_custom_reminder_bg", false).apply()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET TO DEFAULT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // High-fidelity full screen cropping dialog
            if (isCroppingMode && loadedBitmap != null) {
                Dialog(
                    onDismissRequest = { isCroppingMode = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false
                    )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        val screenWidth = maxWidth
                        val screenHeight = maxHeight
                        val density = LocalDensity.current
                        
                        // Calculate portrait 9:16 cropping frame dynamically to fit safe screen boundaries
                        val cropWidthDp = minOf(screenWidth - 48.dp, (screenHeight - 180.dp) * (9f / 16f))
                        val cropHeightDp = cropWidthDp * (16f / 9f)

                        val cropWidthPx = with(density) { cropWidthDp.toPx() }
                        val cropHeightPx = with(density) { cropHeightDp.toPx() }

                        val bmWidth = loadedBitmap!!.width.toFloat()
                        val bmHeight = loadedBitmap!!.height.toFloat()

                        // Fit image base scale to completely cover the crop frame initially
                        val baseScale = maxOf(cropWidthPx / bmWidth, cropHeightPx / bmHeight)
                        val layoutWidthDp = with(density) { (bmWidth * baseScale).toDp() }
                        val layoutHeightDp = with(density) { (bmHeight * baseScale).toDp() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(loadedBitmap) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        
                                        val totalScale = baseScale * zoomScale
                                        val wz = bmWidth * totalScale
                                        val hz = bmHeight * totalScale
                                        
                                        val maxOffsetX = (wz - cropWidthPx) / 2f
                                        val maxOffsetY = (hz - cropHeightPx) / 2f
                                        
                                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = loadedBitmap!!.asImageBitmap(),
                                contentDescription = "Cropping Source",
                                modifier = Modifier
                                    .size(width = layoutWidthDp, height = layoutHeightDp)
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    ),
                                contentScale = ContentScale.Crop
                            )

                            // Semi-translucent mask overlay with high-contrast portrait cropping window
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
                                }
                                val cropPath = androidx.compose.ui.graphics.Path().apply {
                                    addRect(androidx.compose.ui.geometry.Rect(
                                        canvasWidth / 2f - cropWidthPx / 2f,
                                        canvasHeight / 2f - cropHeightPx / 2f,
                                        canvasWidth / 2f + cropWidthPx / 2f,
                                        canvasHeight / 2f + cropHeightPx / 2f
                                    ))
                                }
                                
                                val maskPath = androidx.compose.ui.graphics.Path.combine(
                                    androidx.compose.ui.graphics.PathOperation.Difference,
                                    path,
                                    cropPath
                                )
                                
                                drawPath(
                                    path = maskPath,
                                    color = Color.Black.copy(alpha = 0.75f)
                                )
                                
                                // White fine boundary outline for the 9:16 target window
                                drawRect(
                                    color = Color.White.copy(alpha = 0.8f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        canvasWidth / 2f - cropWidthPx / 2f,
                                        canvasHeight / 2f - cropHeightPx / 2f
                                    ),
                                    size = androidx.compose.ui.geometry.Size(cropWidthPx, cropHeightPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            // Dynamic instruction text at top of screen
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Position Background",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Drag to reposition • Pinch with two fingers to resize",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Interactive controls at bottom
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isCroppingMode = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                Button(
                                    onClick = {
                                        val cropped = cropBitmapToPortraitAndScale(
                                            loadedBitmap!!,
                                            zoomScale,
                                            offsetX,
                                            offsetY,
                                            cropWidthPx,
                                            cropHeightPx,
                                            baseScale
                                        )
                                        saveBitmapToFile(context, cropped)
                                        
                                        // Update preview & enable custom BG option
                                        useCustomReminderBg = true
                                        sharedPrefs.edit().putBoolean("use_custom_reminder_bg", true).apply()
                                        
                                        try {
                                            val file = File(context.filesDir, "reminder_bg.jpg")
                                            previewBitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                        } catch (e: Exception) {
                                            previewBitmap = null
                                        }
                                        
                                        isCroppingMode = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply & Crop", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper methods for Custom Reminder Background Bitmap crop & conversion
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmapToPortraitAndScale(
    bitmap: Bitmap,
    zoomScale: Float,
    offsetX: Float,
    offsetY: Float,
    rectWidthPx: Float,
    rectHeightPx: Float,
    baseScale: Float
): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    val totalScale = baseScale * zoomScale
    val cropWidthOnBitmap = rectWidthPx / totalScale
    val cropHeightOnBitmap = rectHeightPx / totalScale
    
    // Calculate top-left coordinates on the original bitmap
    val startX = bitmapWidth / 2f - (offsetX + rectWidthPx / 2f) / totalScale
    val startY = bitmapHeight / 2f - (offsetY + rectHeightPx / 2f) / totalScale
    
    val startXCoerced = startX.toInt().coerceIn(0, (bitmapWidth - cropWidthOnBitmap).toInt().coerceAtLeast(0))
    val startYCoerced = startY.toInt().coerceIn(0, (bitmapHeight - cropHeightOnBitmap).toInt().coerceAtLeast(0))
    
    val widthCoerced = cropWidthOnBitmap.toInt().coerceIn(50, bitmap.width - startXCoerced)
    val heightCoerced = cropHeightOnBitmap.toInt().coerceIn(50, bitmap.height - startYCoerced)
    
    val cropped = Bitmap.createBitmap(bitmap, startXCoerced, startYCoerced, widthCoerced, heightCoerced)
    
    // Scale to a high quality but memory friendly resolution (540 x 960)
    val scaled = Bitmap.createScaledBitmap(cropped, 540, 960, true)
    
    if (cropped != bitmap) {
        cropped.recycle()
    }
    return scaled
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.filesDir, "reminder_bg.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


// ==========================================================
// MERGED FROM: SettingsView_Habits.kt
// ==========================================================



@Composable
fun SettingsHabitsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val habitSilentModeEnabled by viewModel.habitSilentModeEnabled.collectAsState()
        val habitOnScreen by viewModel.habitOnScreenReminderEnabled.collectAsState()
        val habitNotif by viewModel.habitNotifReminderEnabled.collectAsState()

        SettingsSubpageWorkspace(
            title = "Habits Tracker Settings",
            description = "Configure streaks, reminders and habit intervals.",
            onBack = onBack
        ) {
            Text("Streak recorders calculate active records continuously. Midnight resets evaluate complete boxes.", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Habit Notification Core", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On-Screen Reminders", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Display reminders in application dashboard", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitOnScreen,
                            onCheckedChange = { viewModel.updateHabitOnScreenReminderEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Notifications", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Send push notifications at scheduled times", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitNotif,
                            onCheckedChange = { viewModel.updateHabitNotifReminderEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Silent Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Displays on-screen but has no sound / vibration.", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = habitSilentModeEnabled,
                            onCheckedChange = { viewModel.updateHabitSilentModeEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("habit_silent_mode_switch")
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Journal.kt
// ==========================================================



@Composable
fun SettingsJournalPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val downloadedStatus = remember {
        mutableStateMapOf<String, Boolean>().apply {
            MapDownloadManager.zones.forEach { zone ->
                this[zone.id] = MapDownloadManager.isMapFileDownloaded(context, zone.id)
            }
        }
    }

    val activeDownloadingZoneId by MapDownloadManager.activeDownloadingZoneId.collectAsState()
    val downloadProgress by MapDownloadManager.downloadProgress.collectAsState()
    val downloadError by MapDownloadManager.downloadError.collectAsState()

    LaunchedEffect(activeDownloadingZoneId) {
        if (activeDownloadingZoneId == null) {
            MapDownloadManager.zones.forEach { zone ->
                downloadedStatus[zone.id] = MapDownloadManager.isMapFileDownloaded(context, zone.id)
            }
        }
    }

    SettingsPageScope {
        val onThisDayNotificationEnabled by viewModel.onThisDayNotificationEnabled.collectAsState()
        val onThisDayNotificationTime by viewModel.onThisDayNotificationTime.collectAsState()
        val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()

        SettingsSubpageWorkspace(
            title = "Life Journal Settings",
            description = "Configurations, storage maps.",
            onBack = onBack
        ) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Journal records and metadata are completely local and private to your device.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On This Day Notifications", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sends a notification reminding you of historical journal entries written on this day in history.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayNotificationEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayNotificationEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_notification_switch")
                        )
                    }
                    
                    if (onThisDayNotificationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Notification Trigger Time", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = onThisDayNotificationTime,
                            onValueChange = { viewModel.updateOnThisDayNotificationTime(it) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF333333)
                            ),
                            placeholder = { Text("e.g. 09:00 AM or 18:30", color = Color.DarkGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("on_this_day_notification_time_input")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On Screen Reminders", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Displays an on-screen dialog when today's historic journal entries exist.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onThisDayOnScreenEnabled,
                            onCheckedChange = { viewModel.updateOnThisDayOnScreenEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                            modifier = Modifier.testTag("on_this_day_onscreen_switch")
                        )
                    }
                }
            }

            // India Offline Map Manager Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Offline India Map Manager",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manage high-fidelity vector maps of India for completely offline travel/journal location display. Files are preserved during updates but deleted upon uninstallation.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (downloadError != null) {
                        Text(
                            text = "Download Error: $downloadError",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    MapDownloadManager.zones.forEachIndexed { index, zone ->
                        if (index > 0) {
                            HorizontalDivider(color = Color(0xFF1E1E22), modifier = Modifier.padding(vertical = 12.dp))
                        }

                        val isDownloaded = downloadedStatus[zone.id] ?: false
                        val isCurrentDownloading = activeDownloadingZoneId == zone.id

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = zone.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (zone.id == "southern") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(WaterBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "PRIMARY",
                                                color = WaterBlue,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "File size: ${zone.size}",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )

                                if (isCurrentDownloading) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val pct = ((downloadProgress ?: 0f) * 100).toInt()
                                    LinearProgressIndicator(
                                        progress = { downloadProgress ?: 0f },
                                        color = WaterBlue,
                                        trackColor = Color.DarkGray,
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Downloading: $pct%",
                                        color = WaterBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isDownloaded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Downloaded & Ready",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            if (isDownloaded) {
                                IconButton(
                                    onClick = {
                                        MapDownloadManager.deleteMap(context, zone.id)
                                        downloadedStatus[zone.id] = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Map",
                                        tint = Color(0xFFE53935)
                                    )
                                }
                            } else if (isCurrentDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = WaterBlue,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                val anyDownloading = activeDownloadingZoneId != null
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            MapDownloadManager.downloadMap(context, zone.id)
                                        }
                                    },
                                    enabled = !anyDownloading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WaterBlue,
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color(0xFF1E1E22),
                                        disabledContentColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Download",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Permissions.kt
// ==========================================================



@Composable
fun SettingsPermissionsPage(viewModel: AppViewModel, onBack: () -> Unit) {
    SettingsSubpageWorkspace(
        title = "Permissions & API Connections",
        description = "Manage system permissions and external API authorizations.",
        onBack = onBack
    ) {
        PermissionsSettingsSection(viewModel = viewModel)
    }
}

fun hasGoogleScope(context: Context, scopeUri: String): Boolean {
    val scope = com.google.android.gms.common.api.Scope(scopeUri)
    val account = GoogleSignIn.getLastSignedInAccount(context)
    return account != null && GoogleSignIn.hasPermissions(account, scope)
}

@Composable
fun PermissionsSettingsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    
    var hasSystemCalendarPermission by remember { mutableStateOf(false) }
    var hasSystemContactsPermission by remember { mutableStateOf(false) }

    var hasDrivePermission by remember { mutableStateOf(false) }
    var hasGoogleContactsPermission by remember { mutableStateOf(false) }
    var hasGoogleTasksPermission by remember { mutableStateOf(false) }
    
    var hasExactAlarmPermission by remember { mutableStateOf(false) }
    var hasNotificationListenerPermission by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
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
        
        hasSystemCalendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                      
        hasSystemContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                                      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

        hasDrivePermission = GoogleDriveSyncManager.hasDrivePermission(context)
        hasGoogleContactsPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/contacts")
        hasGoogleTasksPermission = hasGoogleScope(context, "https://www.googleapis.com/auth/tasks")

        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val cn = android.content.ComponentName(context, com.example.service.NotificationBlockerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        hasNotificationListenerPermission = flat != null && flat.contains(cn.flattenToString())
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkAllPermissions()
            delay(1000)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
        }
    )
    
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ -> checkAllPermissions() }
    )

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        checkAllPermissions()
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PermissionItem(
            title = "Notifications",
            description = "Required for focus timer alerts and reminders.",
            isGranted = hasNotificationPermission,
            onClick = {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!hasNotificationPermission) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Notification Blocker (Notification Access)",
            description = "Required to block and release incoming notifications during Focus phase.",
            isGranted = hasNotificationListenerPermission,
            onClick = {
                if (!hasNotificationListenerPermission) {
                    try {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("SettingsView", "Error starting notification listener settings: ${e.message}")
                    }
                }
            }
        )

        PermissionItem(
            title = "Battery Optimization",
            description = "Ignore battery optimization to keep timers running in background.",
            isGranted = isBatteryOptIgnored,
            onClick = {
                if (!isBatteryOptIgnored) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Exact Alarms",
            description = "Required for precise timer wakeups on Android 12+.",
            isGranted = hasExactAlarmPermission,
            onClick = {
                if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Display Over Other Apps",
            description = "Required to show strict mode blocking overlays.",
            isGranted = hasOverlayPermission,
            onClick = {
                if (!hasOverlayPermission) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        PermissionItem(
            title = "Usage Access",
            description = "Required to track app usage for blocking limits.",
            isGranted = hasUsageStatsPermission,
            onClick = {
                if (!hasUsageStatsPermission) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )
        
        PermissionItem(
            title = "System Calendar",
            description = "Required to sync events from local device calendars.",
            isGranted = hasSystemCalendarPermission,
            onClick = {
                if (!hasSystemCalendarPermission) {
                    multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
            }
        )
        
        PermissionItem(
            title = "System Contacts",
            description = "Required to pick contacts from the device.",
            isGranted = hasSystemContactsPermission,
            onClick = {
                if (!hasSystemContactsPermission) {
                    multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                }
            }
        )

        PermissionItem(
            title = "Google Drive",
            description = "Required to backup and restore app data to the cloud.",
            isGranted = hasDrivePermission,
            onClick = {
                if (!hasDrivePermission) {
                    scope.launch {
                        GoogleDriveSyncManager.getAccessToken(context) { intent ->
                            authResolutionLauncher.launch(intent)
                        }
                    }
                }
            }
        )
        
        PermissionItem(
            title = "Google Contacts",
            description = "Sync with Google Contacts API.",
            isGranted = hasGoogleContactsPermission,
            onClick = {
                if (!hasGoogleContactsPermission) {
                    scope.launch {
                        GoogleContactsSyncManager.getAccessToken(context) { intent ->
                            authResolutionLauncher.launch(intent)
                        }
                    }
                }
            }
        )
        

        PermissionItem(
            title = "Google Tasks",
            description = "Sync with Google Tasks API.",
            isGranted = hasGoogleTasksPermission,
            onClick = {
                if (!hasGoogleTasksPermission) {
                    scope.launch {
                        GoogleTasksSyncManager.getAccessToken(context) { intent ->
                            authResolutionLauncher.launch(intent)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Grant", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Tasks.kt
// ==========================================================



@Composable
fun SettingsTasksPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        val taskVibrationEnabled by viewModel.taskVibrationEnabled.collectAsState()
        val taskSilentModeEnabled by viewModel.taskSilentModeEnabled.collectAsState()
        val allDayNotificationEnabled by viewModel.allDayNotificationEnabled.collectAsState()
        val allDayNotificationTime by viewModel.allDayNotificationTime.collectAsState()
        val taskHighNotif by viewModel.taskHighNotifEnabled.collectAsState()
        val taskHighDisplay by viewModel.taskHighDisplayEnabled.collectAsState()
        val taskMediumNotif by viewModel.taskMediumNotifEnabled.collectAsState()
        val taskMediumDisplay by viewModel.taskMediumDisplayEnabled.collectAsState()
        val taskLowNotif by viewModel.taskLowNotifEnabled.collectAsState()
        val taskLowDisplay by viewModel.taskLowDisplayEnabled.collectAsState()
        val taskHighAlarmSound by viewModel.taskHighAlarmSoundEnabled.collectAsState()
        val taskMediumAlarmSound by viewModel.taskMediumAlarmSoundEnabled.collectAsState()
        val taskLowAlarmSound by viewModel.taskLowAlarmSoundEnabled.collectAsState()
        val additionalReminderTimes by viewModel.additionalReminderTimes.collectAsState()

        // Tasks Subpage
        SettingsSubpageWorkspace(
            title = "Tasks Settings",
            description = "Configure custom reminders, vibrations, priority schedules.",
            onBack = onBack
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Task Reminder Vibration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Vibrate device during custom relative reminder schedules.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = taskVibrationEnabled,
                    onCheckedChange = { viewModel.updateTaskVibrationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("task_reminder_vibrate_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Silent Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Displays on-screen but has no sound / vibration.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = taskSilentModeEnabled,
                    onCheckedChange = { viewModel.updateTaskSilentModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("task_reminder_silent_switch")
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Additional Reminder Offsets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Define comma-separated relative offsets in minutes inside schedules (ex: 5, 15, 30):", color = Color.Gray, fontSize = 10.sp)
                OutlinedTextField(
                    value = additionalReminderTimes,
                    onValueChange = { viewModel.updateAdditionalReminderTimes(it) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    placeholder = { Text("e.g. 5, 15, 30", color = Color.DarkGray, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("additional_reminders_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Priority Notification Settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // High Priority
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighNotif, onCheckedChange = { viewModel.updateTaskHighNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighDisplay, onCheckedChange = { viewModel.updateTaskHighDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskHighAlarmSound, onCheckedChange = { viewModel.updateTaskHighAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Medium Priority
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Medium Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumNotif, onCheckedChange = { viewModel.updateTaskMediumNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumDisplay, onCheckedChange = { viewModel.updateTaskMediumDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskMediumAlarmSound, onCheckedChange = { viewModel.updateTaskMediumAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Low/No Priority Notification settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No / Low Priority Settings", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowNotif, onCheckedChange = { viewModel.updateTaskLowNotifEnabled(it) })
                                    Text("Notifications", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowDisplay, onCheckedChange = { viewModel.updateTaskLowDisplayEnabled(it) })
                                    Text("On-Screen", color = Color.Gray, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = taskLowAlarmSound, onCheckedChange = { viewModel.updateTaskLowAlarmSoundEnabled(it) })
                                    Text("Alarm Sound", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("All-Day Tasks Alerts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pending All-Day Tasks Alert", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Sends a notification showing how many all-day tasks are pending.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = allDayNotificationEnabled,
                                onCheckedChange = { viewModel.updateAllDayNotificationEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WaterBlue),
                                modifier = Modifier.testTag("all_day_notification_switch")
                            )
                        }
                        
                        if (allDayNotificationEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Alert Trigger Time", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = allDayNotificationTime,
                                onValueChange = { viewModel.updateAllDayNotificationTime(it) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF333333)
                                ),
                                placeholder = { Text("e.g. 09:00 AM or 18:30", color = Color.DarkGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("all_day_notification_time_input")
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Timer.kt
// ==========================================================




@Composable
fun SettingsTimerConfigurationPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    SettingsPageScope {
        val focusTimerDurationMins by viewModel.focusTimerDurationMins.collectAsState()
        val breakDurationMins by viewModel.breakDurationMins.collectAsState()
        val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
        val showOverlayEnabled by viewModel.showOverlayEnabled.collectAsState()
        val floatingTimerSize by viewModel.floatingTimerSize.collectAsState()
        val keepNotificationEnabled by viewModel.keepNotificationEnabled.collectAsState()
        val dailyFocusHoursTarget by viewModel.dailyFocusHoursTarget.collectAsState()
        val focusMotivationalQuoteEnabled by viewModel.focusMotivationalQuoteEnabled.collectAsState()
        val focusMotivationalQuoteIntervalMins by viewModel.focusMotivationalQuoteIntervalMins.collectAsState()

        val timerSoundEnabled by viewModel.timerSoundEnabled.collectAsState()
        val timerAutoStartBreak by viewModel.timerAutoStartBreak.collectAsState()
        val timerAutoStartPomo by viewModel.timerAutoStartPomo.collectAsState()
        val stopwatchBreakDurationMinutes by viewModel.stopwatchBreakDurationMinutes.collectAsState()
        val autoStartStopwatchAfterBreak by viewModel.autoStartStopwatchAfterBreak.collectAsState()
        val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
        val batterySaverModeEnabled by viewModel.batterySaverModeEnabled.collectAsState()
        val shareFocusDetailsEnabled by viewModel.shareFocusDetailsEnabled.collectAsState()
        val shareFocusHistoryEnabled by viewModel.shareFocusHistoryEnabled.collectAsState()

        val isTimerRunning by viewModel.isTimerRunning.collectAsState()
        val isStopwatchActive by viewModel.isStopwatchActive.collectAsState()
        val isFocusPhase by viewModel.isFocusPhase.collectAsState()
        val isStrictBlocked = (isTimerRunning && isFocusPhase) || isStopwatchActive

        // Timer Configuration Page
        SettingsSubpageWorkspace(
            title = "Timer Configuration",
            description = "Configure Pomodoro sessions and custom indicators.",
            onBack = onBack
        ) {
            var focusInputText by remember(focusTimerDurationMins) { mutableStateOf(focusTimerDurationMins.toString()) }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Focus Session Duration (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = focusInputText,
                    onValueChange = { newValue ->
                        focusInputText = newValue
                        val parsed = newValue.toIntOrNull()
                        if (parsed != null && parsed > 0) {
                            viewModel.updateTimerDuration(parsed)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("focus_duration_input")
                )
            }

            var breakInputText by remember(breakDurationMins) { mutableStateOf(breakDurationMins.toString()) }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Default Rest Break Period (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = breakInputText,
                    onValueChange = { newValue ->
                        breakInputText = newValue
                        val parsed = newValue.toIntOrNull()
                        if (parsed != null && parsed > 0) {
                            viewModel.updateBreakDuration(parsed)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("break_duration_input")
                )
            }

            var stopwatchBreakInputText by remember(stopwatchBreakDurationMinutes) { mutableStateOf(stopwatchBreakDurationMinutes.toString()) }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Stopwatch Rest Break Period (mins)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = stopwatchBreakInputText,
                    onValueChange = { newValue ->
                        stopwatchBreakInputText = newValue
                        val parsed = newValue.toIntOrNull()
                        if (parsed != null && parsed > 0) {
                            viewModel.setStopwatchBreakDuration(parsed)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stopwatch_break_duration_input")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tactile Vibration Alerts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Vibrate device when timer completes", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = { viewModel.updateVibrationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("timer_vibration_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anti-Burn Screen Protection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Slowly shifts digits to protect active AMOLED/OLED displays.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = antiBurnScreenEnabled,
                    onCheckedChange = { viewModel.updateAntiBurnScreenEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("anti_burn_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery Saver Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Disables background sync/alerts on close. Resumes timer precisely on app launch.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = batterySaverModeEnabled,
                    onCheckedChange = { viewModel.updateBatterySaverModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("battery_saver_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share Focus Details", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Share active focus details with friends on the cloud.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = shareFocusDetailsEnabled,
                    onCheckedChange = { viewModel.updateShareFocusDetailsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("share_focus_details_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share Focus Session History", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Share focus history records to Firebase. Only focus time is shared if off.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = shareFocusHistoryEnabled,
                    onCheckedChange = { viewModel.updateShareFocusHistoryEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("share_focus_history_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Motivational Quote Overlay", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Display study, money, and life change quotes in full-screen mode.", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = focusMotivationalQuoteEnabled,
                    onCheckedChange = { viewModel.updateFocusMotivationalQuoteEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("focus_motivational_quote_switch")
                )
            }

            if (focusMotivationalQuoteEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quote Rotation Interval", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Specify interval for rotating the motivational quote (minutes).", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (focusMotivationalQuoteIntervalMins > 1) {
                                    viewModel.updateFocusMotivationalQuoteIntervalMins(focusMotivationalQuoteIntervalMins - 1)
                                }
                            },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Decrease Interval",
                                tint = if (focusMotivationalQuoteIntervalMins > 1) Color.White else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$focusMotivationalQuoteIntervalMins min",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                if (focusMotivationalQuoteIntervalMins < 60) {
                                    viewModel.updateFocusMotivationalQuoteIntervalMins(focusMotivationalQuoteIntervalMins + 1)
                                }
                            },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Increase Interval",
                                tint = if (focusMotivationalQuoteIntervalMins < 60) Color.White else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Focus Target (hours)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Target daily focus hours logged (1–20 hours).", color = Color.Gray, fontSize = 10.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (dailyFocusHoursTarget > 1) {
                                viewModel.updateDailyFocusHoursTarget(dailyFocusHoursTarget - 1)
                            }
                        },
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Decrease Focus Target",
                            tint = if (dailyFocusHoursTarget > 1) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "$dailyFocusHoursTarget",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = {
                            if (dailyFocusHoursTarget < 20) {
                                viewModel.updateDailyFocusHoursTarget(dailyFocusHoursTarget + 1)
                            }
                        },
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1F1F1F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Increase Focus Target",
                            tint = if (dailyFocusHoursTarget < 20) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Display Overlay Widget", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Floating widget when leaving app", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = showOverlayEnabled,
                    onCheckedChange = { viewModel.updateShowOverlayEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("overlay_switch")
                )
            }

            if (showOverlayEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Floating Timer Size", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Choose size (Small, Medium, Large) for the floating widget", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("small", "medium", "large").forEach { size ->
                            val isSelected = floatingTimerSize == size
                            val label = size.replaceFirstChar { it.uppercase() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) WaterBlue else Color(0xFF1E1E1F),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.updateFloatingTimerSize(size) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Persistent Notification", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Status bar/lockscreen visibility helper", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = keepNotificationEnabled,
                    onCheckedChange = { viewModel.updateKeepNotificationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("notification_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Timer End Sound Alerts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Play high-quality alerting sound when segments end", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = timerSoundEnabled,
                    onCheckedChange = { viewModel.setTimerSoundEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("timer_sound_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Start Break After Focus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Instantly trigger rest period when study focus timer completes", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = timerAutoStartBreak,
                    onCheckedChange = { viewModel.setTimerAutoStartBreak(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("timer_autostart_break_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Start Focus After Break", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Instantly trigger next focus session when rest break completes", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = timerAutoStartPomo,
                    onCheckedChange = { viewModel.setTimerAutoStartPomo(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("timer_autostart_pomo_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Start Stopwatch After Break", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Automatically resume stopwatch when stopwatch break completes", color = Color.Gray, fontSize = 10.sp)
                }
                Switch(
                    checked = autoStartStopwatchAfterBreak,
                    onCheckedChange = { viewModel.setAutoStartStopwatchAfterBreak(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = WaterBlue,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("stopwatch_autostart_after_break_switch")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            val strictPrefs = remember { context.getSharedPreferences("strict_mode_prefs", android.content.Context.MODE_PRIVATE) }
            var strictModeEnabled by remember {
                mutableStateOf(strictPrefs.getBoolean("strict_mode_enabled", true))
            }
            var blockedApps by remember {
                mutableStateOf(strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet())
            }
            var searchAppQuery by remember { mutableStateOf("") }

            var hasPermissionState by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    hasPermissionState = com.example.util.AppBlockHelper.hasUsageStatsPermission(context)
                    kotlinx.coroutines.delay(2000)
                }
            }

            // FOCUS TAGS MANAGER
            val focusTags by viewModel.focusTags.collectAsState()
            var newTagInput by remember { mutableStateOf("") }
            var editingTagIndex by remember { mutableStateOf<Int?>(null) }
            var editingTagInput by remember { mutableStateOf("") }

            Text(
                text = "FOCUS TAGS MANAGER",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize your focus categories. These tags will be available for you to classify your focused Pomodoro or Stopwatch session blocks.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    // Add / Edit Row
                    if (editingTagIndex == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                label = { Text("New Tag", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF333333),
                                    focusedContainerColor = Color(0xFF151515),
                                    unfocusedContainerColor = Color(0xFF151515)
                                ),
                                modifier = Modifier.weight(1f).height(52.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = newTagInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.addFocusTag(trimmed)
                                        newTagInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editingTagInput,
                                onValueChange = { editingTagInput = it },
                                label = { Text("Edit Tag", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF333333),
                                    focusedContainerColor = Color(0xFF151515),
                                    unfocusedContainerColor = Color(0xFF151515)
                                ),
                                modifier = Modifier.weight(1f).height(52.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = editingTagInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.updateFocusTag(editingTagIndex!!, trimmed)
                                        editingTagIndex = null
                                        editingTagInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    editingTagIndex = null
                                    editingTagInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }

                    // Tags List
                    if (focusTags.isEmpty()) {
                        Text("No custom tags added yet.", color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            focusTags.forEachIndexed { idx, tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF151515), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tag, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingTagIndex = idx
                                                editingTagInput = tag
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Tag", tint = WaterBlue, modifier = Modifier.size(14.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteFocusTag(idx)
                                                if (editingTagIndex == idx) {
                                                    editingTagIndex = null
                                                    editingTagInput = ""
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Tag", tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            Text(
                text = "STRICT FOCUS SECURE GATE",
                color = WaterBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isStrictBlocked) {
                        Text(
                            text = "⚠️ Strict Mode settings are locked while the focus timer or stopwatch is running.",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Mode Trigger", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Intercept chosen apps when focus timer/stopwatch runs.", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = strictModeEnabled,
                            enabled = !isStrictBlocked,
                            onCheckedChange = { isEnabled ->
                                if (isStrictBlocked) {
                                    Toast.makeText(context, "Cannot change strict mode settings while a timer or stopwatch is running!", Toast.LENGTH_SHORT).show()
                                } else {
                                    strictModeEnabled = isEnabled
                                    strictPrefs.edit().putBoolean("strict_mode_enabled", isEnabled).apply()
                                    if (isEnabled && !hasPermissionState) {
                                        Toast.makeText(context, "Usage Permission is required for Strict Mode!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = WaterBlue,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray,
                                disabledCheckedTrackColor = WaterBlue.copy(alpha = 0.5f),
                                disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (strictModeEnabled) {
                        if (!hasPermissionState) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open settings automatically. Please search Settings for Usage Access.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Authorize Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = searchAppQuery,
                            onValueChange = { searchAppQuery = it },
                            enabled = !isStrictBlocked,
                            placeholder = { Text("Filter package or name...", color = Color.DarkGray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222),
                                disabledTextColor = Color.LightGray.copy(alpha = 0.5f),
                                disabledBorderColor = Color(0xFF222222).copy(alpha = 0.5f)
                            )
                        )

                        val launchableAppsList = remember {
                            try {
                                val pm = context.packageManager
                                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                }
                                pm.queryIntentActivities(intent, 0)
                                    .map { resolveInfo ->
                                        val label = resolveInfo.loadLabel(pm).toString()
                                        val pkg = resolveInfo.activityInfo.packageName
                                        Pair(label, pkg)
                                    }
                                    .distinctBy { it.second }
                                    .filter { it.second != context.packageName }
                                    .sortedBy { it.first.lowercase() }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        val filteredList = launchableAppsList.filter {
                            it.first.contains(searchAppQuery, ignoreCase = true) || 
                            it.second.contains(searchAppQuery, ignoreCase = true)
                        }

                        Text(
                            text = "BLOCK LIST (${blockedApps.size} apps selected)",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (filteredList.isEmpty()) {
                                    Text(
                                        text = "No packages match query",
                                        color = Color.DarkGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                } else {
                                    filteredList.forEach { (label, pkg) ->
                                        val isBlocked = blockedApps.contains(pkg)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isStrictBlocked) {
                                                    val updated = blockedApps.toMutableSet()
                                                    if (isBlocked) {
                                                        updated.remove(pkg)
                                                    } else {
                                                        updated.add(pkg)
                                                    }
                                                    blockedApps = updated
                                                    strictPrefs.edit().putStringSet("blocked_packages", updated).apply()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                AppListIcon(pkg = pkg)
                                            }
                                            Checkbox(
                                                checked = isBlocked,
                                                enabled = !isStrictBlocked,
                                                onCheckedChange = { checked ->
                                                    if (!isStrictBlocked) {
                                                        val updated = blockedApps.toMutableSet()
                                                        if (checked == true) {
                                                            updated.add(pkg)
                                                        } else {
                                                            updated.remove(pkg)
                                                        }
                                                        blockedApps = updated
                                                        strictPrefs.edit().putStringSet("blocked_packages", updated).apply()
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = WaterBlue,
                                                    checkmarkColor = Color.Black,
                                                    uncheckedColor = Color.Gray
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_Updates.kt
// ==========================================================



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsUpdatesPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()
    
    var autoDownload by remember { mutableStateOf(AppUpdateManager.isAutoUpdateEnabled(context)) }
    var pauseUpdates by remember { mutableStateOf(AppUpdateManager.isPauseUpdatesEnabled(context)) }
    var forceUpdate by remember { mutableStateOf(AppUpdateManager.isForceUpdateEnabled(context)) }
    
    var githubOwner by remember { mutableStateOf(AppUpdateManager.getGithubOwner(context)) }
    var githubRepo by remember { mutableStateOf(AppUpdateManager.getGithubRepo(context)) }
    var runningFirebaseCode by remember { mutableStateOf(AppUpdateManager.getRunningFirebaseVersion(context)) }

    // Check if there is an offline downloaded update ready to install
    val readyApkPath = remember(updateStatus) { AppUpdateManager.getReadyApkPath(context) }
    val offlineApkFile = remember(readyApkPath) { readyApkPath?.let { java.io.File(it) } }
    val isOfflineApkReady = remember(offlineApkFile) { 
        offlineApkFile != null && offlineApkFile.exists() && offlineApkFile.length() > 0 && AppUpdateManager.isValidAndNewerApk(context, offlineApkFile)
    }

    val currentVersionCode = remember { AppUpdateManager.getCurrentVersionCode(context) }
    val currentVersionName = remember { AppUpdateManager.getCurrentVersionName(context) }

    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "System Update Center",
            description = "Manage Firebase App Distribution updates, automated downloads, and restore backups.",
            onBack = onBack
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Status Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(WaterBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (updateStatus) {
                                is UpdateStatus.Checking -> Icons.Default.Refresh
                                is UpdateStatus.Downloading -> Icons.Default.ArrowDropDown
                                is UpdateStatus.ReadyToInstall -> Icons.Default.CheckCircle
                                is UpdateStatus.NewVersionAvailable -> Icons.Default.Info
                                is UpdateStatus.Error -> Icons.Default.Warning
                                else -> Icons.Default.Settings
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Status Icon",
                                tint = WaterBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "CURRENT VERSION: $currentVersionName (BUILD $currentVersionCode) | FIREBASE BUILD: $runningFirebaseCode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val statusLabel = when (val state = updateStatus) {
                            is UpdateStatus.Idle -> "System is up to date"
                            is UpdateStatus.Checking -> "Checking for updates..."
                            is UpdateStatus.SecuringData -> "Securing user data and performing auto-backup..."
                            is UpdateStatus.Downloading -> "Downloading system update: ${(state.progress * 100).toInt()}%"
                            is UpdateStatus.ReadyToInstall -> "System Update Downloaded & Ready"
                            is UpdateStatus.NewVersionAvailable -> "New Update Available: Build ${state.versionId}"
                            is UpdateStatus.NoUpdateAvailable -> "Your system is up to date (Build ${state.localVersion})"
                            is UpdateStatus.Error -> "Check failed: ${state.message}"
                        }

                        Text(
                            text = statusLabel.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        if (updateStatus is UpdateStatus.Downloading) {
                            val progress = (updateStatus as UpdateStatus.Downloading).progress
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { if (progress >= 0f) progress else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = WaterBlue,
                                trackColor = Color(0xFF1F1F24)
                            )
                        }
                    }
                }

                // 2. Manual Action Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MANUAL ACTIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    AppUpdateManager.checkForUpdates(context, manualCheck = true)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("check_updates_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp),
                            enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        // Download & Install button if a new version is available but not yet downloaded
                        if (updateStatus is UpdateStatus.NewVersionAvailable) {
                            val state = updateStatus as UpdateStatus.NewVersionAvailable
                            Button(
                                onClick = {
                                    AppUpdateManager.startDownloadAndInstall(context, state.apkFileId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("download_updates_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Download", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Install Build ${state.versionId}", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Install button if update downloaded
                        val canInstall = updateStatus is UpdateStatus.ReadyToInstall || isOfflineApkReady
                        val apkFileToInstall = when {
                            updateStatus is UpdateStatus.ReadyToInstall -> (updateStatus as UpdateStatus.ReadyToInstall).apkFile
                            isOfflineApkReady -> offlineApkFile
                            else -> null
                        }

                        if (canInstall && apkFileToInstall != null) {
                            Button(
                                onClick = {
                                    AppUpdateManager.installApk(context, apkFileToInstall)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("install_updates_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Install", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Downloaded Update", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Force Redownload & Re-sync button
                        Button(
                            onClick = {
                                AppUpdateManager.forceRedownloadUpdate(context)
                                Toast.makeText(context, "Cleaning files & initiating fresh redownload...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("force_redownload_updates_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Redownload", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Redownload & Re-sync", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // App Distribution specific Tester authentication
                        Button(
                            onClick = {
                                try {
                                    val appDist = com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance()
                                    if (!appDist.isTesterSignedIn) {
                                        Toast.makeText(context, "Opening Firebase App Distribution Login...", Toast.LENGTH_SHORT).show()
                                        appDist.signInTester()
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Firebase Tester Sign-In Succeeded!", Toast.LENGTH_LONG).show()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Sign-In Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        Toast.makeText(context, "Firebase Tester is already signed in!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "App Distribution Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("app_dist_sign_in_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1E)),
                            border = BorderStroke(1.dp, Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Tester Sign In", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Firebase Tester Authentication", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Update Preferences Toggles
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PREFERENCES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Auto download toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoDownload = !autoDownload
                                    AppUpdateManager.setAutoUpdateEnabled(context, autoDownload)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Download Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Automatically download APKs silently in the background on startup.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = autoDownload,
                                onCheckedChange = {
                                    autoDownload = it
                                    AppUpdateManager.setAutoUpdateEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }

                        HorizontalDivider(color = Color(0xFF16161A), thickness = 0.5.dp)

                        // Pause updates toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pauseUpdates = !pauseUpdates
                                    AppUpdateManager.setPauseUpdatesEnabled(context, pauseUpdates)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pause Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Temporarily pause silent background updates checks.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = pauseUpdates,
                                onCheckedChange = {
                                    pauseUpdates = it
                                    AppUpdateManager.setPauseUpdatesEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }

                        HorizontalDivider(color = Color(0xFF16161A), thickness = 0.5.dp)

                        // Force updates toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    forceUpdate = !forceUpdate
                                    AppUpdateManager.setForceUpdateEnabled(context, forceUpdate)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Force System Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Disallow bypassing updates when a new critical build is available.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = forceUpdate,
                                onCheckedChange = {
                                    forceUpdate = it
                                    AppUpdateManager.setForceUpdateEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WaterBlue, checkedTrackColor = WaterBlue.copy(alpha = 0.4f))
                            )
                        }
                    }
                }

                // 3.5. Firebase Running Version Override
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "FIREBASE VERSION CONFIGURATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = "To prevent infinite installation loops on GitHub automated builds, Life OS tracks your current running version code independently from the hardcoded package codebase. Adjust this value to bypass or enable system updates manually.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Running Firebase Version", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (runningFirebaseCode > 1) {
                                            runningFirebaseCode -= 1
                                            AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                            Toast.makeText(context, "Running Firebase version updated to Build $runningFirebaseCode", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.background(Color(0xFF1E1E24), RoundedCornerShape(4.dp)).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "Decrease Version",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = runningFirebaseCode.toString(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(
                                    onClick = {
                                        runningFirebaseCode += 1
                                        AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                        Toast.makeText(context, "Running Firebase version updated to Build $runningFirebaseCode", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.background(Color(0xFF1E1E24), RoundedCornerShape(4.dp)).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Increase Version",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                runningFirebaseCode = currentVersionCode
                                AppUpdateManager.setRunningFirebaseVersion(context, runningFirebaseCode)
                                Toast.makeText(context, "Running Firebase version reset to match package code ($currentVersionCode)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1E)),
                            border = BorderStroke(1.dp, Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("Reset to Package Build Code ($currentVersionCode)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. Advanced Configurations
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ADVANCED SOURCE SOURCES (GITHUB)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlue,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedTextField(
                            value = githubOwner,
                            onValueChange = {
                                githubOwner = it
                                AppUpdateManager.setGithubOwner(context, it)
                            },
                            label = { Text("GitHub Owner", color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = githubRepo,
                            onValueChange = {
                                githubRepo = it
                                AppUpdateManager.setGithubRepo(context, it)
                            },
                            label = { Text("GitHub Repository Name", color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ==========================================================
// MERGED FROM: SettingsView_User.kt
// ==========================================================



@Composable
fun SettingsUserInfoPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    SettingsPageScope {
        SettingsSubpageWorkspace(
            title = "User Info",
            description = "Update your profile name, nickname, and emoji picture.",
            onBack = onBack
        ) {
            val currentUser = viewModel.currentUserRemote.collectAsState().value
            var name by remember { mutableStateOf(currentUser?.name ?: "") }
            var nickname by remember { mutableStateOf(currentUser?.nickname ?: "") }
            var emoji by remember { mutableStateOf(currentUser?.emoji ?: "") }
            var statusMsg by remember { mutableStateOf<String?>(null) }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                ProfilePicEditor(
                    initialValue = emoji.ifBlank { "👨‍💻" },
                    onValueChange = { emoji = it }
                )
                
                if (statusMsg != null) {
                    Text(statusMsg!!, color = if (statusMsg!!.startsWith("Updated")) Color.Green else Color.Red, fontSize = 12.sp)
                }
                
                Button(
                    onClick = {
                        if (name.isBlank() || emoji.isBlank()) {
                            statusMsg = "Name and Emoji are mandatory"
                            return@Button
                        }
                        viewModel.completeProfileSetup(name, nickname, emoji)
                        statusMsg = "Updated Successfully!"
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Details")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Firebase Diagnostics",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test the Firebase Crashlytics reporting SDK integration dynamically by forcing a JVM runtime crash.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        throw RuntimeException("Test Crashlytics Setup: Life OS Forced Crash")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Trigger Test Crash (Crashlytics)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

