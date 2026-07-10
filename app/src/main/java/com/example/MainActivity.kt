package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.util.FocusTimerManager
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.data.AppDatabase
import com.example.data.LocalRepository
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WaterBlue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalRepository
    private lateinit var firebaseAnalytics: com.google.firebase.analytics.FirebaseAnalytics
    private val viewModel: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                    val db = AppDatabase.getInstance(applicationContext)
                    val repo = LocalRepository(db, applicationContext)
                    @Suppress("UNCHECKED_CAST")
                    return AppViewModel(application, repo) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    private var startupException: Throwable? = null
    private val isAppUnlockedState = androidx.compose.runtime.mutableStateOf(false)
    private val interceptedAppSessionQuery = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val isDbReady = androidx.compose.runtime.mutableStateOf(false)

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserInteraction(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Dismiss stale notifications to prevent SystemUI asset loading crashes on app updates/reinstalls
            try {
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancelAll()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to cancel old notifications on startup: ${e.message}")
            }

            // Initialize monotonic StableTime
            com.example.util.StableTime.init()

            // Initialize database and repository synchronously on the main thread to prevent UninitializedPropertyAccessException
            database = AppDatabase.getInstance(applicationContext)
            repository = LocalRepository(database, applicationContext)

            // Set the global app context for Retrofit intercepting wrapper
            com.example.api.FirebaseClient.appContext = applicationContext

            // Configure App Check Debug Token in SharedPreferences so the Debug Provider uses it
            try {
                val debugToken = "28D9431B-D767-40B7-AD1A-1F0A97161FA9"
                val files = listOf(
                    "com.google.firebase.appcheck.debug.store.DebugAppCheckProviderFactory",
                    "com.google.firebase.appcheck.store",
                    "com.google.firebase.appcheck"
                )
                val keys = listOf(
                    "com.google.firebase.appcheck.debug.store.DebugAppCheckProviderFactory.DEBUG_TOKEN_KEY",
                    "DEBUG_TOKEN_KEY",
                    "debug_token"
                )
                for (file in files) {
                    val prefs = getSharedPreferences(file, android.content.Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    for (key in keys) {
                        editor.putString(key, debugToken)
                    }
                    editor.apply()
                }
                android.util.Log.i("MainActivity", "Configured App Check debug secret in SharedPreferences successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to configure App Check debug secret in SharedPreferences", e)
            }

            // Initialize Firebase App Check with Debug provider
            try {
                com.google.firebase.FirebaseApp.initializeApp(this)
                val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                )
                android.util.Log.i("MainActivity", "Firebase App Check Debug provider installed successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to install Firebase App Check Debug provider", e)
            }

            // Initialize Firebase Analytics
            try {
                firebaseAnalytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(this)
                android.util.Log.i("MainActivity", "Firebase Analytics initialized successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to initialize Firebase Analytics", e)
            }

            // Schedule the background periodic history sync worker
            com.example.receiver.HistorySyncWorker.schedule(applicationContext)

            // Initialize local Room Database with destructive migration allowance to prevent upgrade crashes
            lifecycleScope.launch {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // Initialize default app blocks and strict mode lists
                        com.example.util.AppBlockHelper.initializeStrictAppsIfNeeded(applicationContext)
                        
                        // Start the persistent keep alive daemon service if enabled
                        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        val customDbUrl = prefs.getString("custom_firebase_db_url", null)
                        if (!customDbUrl.isNullOrBlank()) {
                            com.example.api.FirebaseClient.activeUrl = customDbUrl
                        }
                        if (prefs.getBoolean("keep_notification_enabled", true)) {
                            com.example.service.KeepAliveService.start(applicationContext)
                        }

                        // Schedule bedtime reminder and wake-up alarm on app startup
                        com.example.util.AlarmScheduler.scheduleBedtimeReminder(applicationContext)
                        com.example.util.AlarmScheduler.scheduleWakeUpAlarm(applicationContext)

                        // Initialize timer manager with context
                        FocusTimerManager.init(applicationContext)

                        // Check for previously exported backups on first start and auto-restore
                        try {
                            val restored = com.example.util.DatabaseBackupHelper.autoRestoreIfNeeded(applicationContext, database)
                            if (restored) {
                                android.util.Log.i("MainActivity", "Successfully verified and auto-restored database from public storage.")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Auto-restore logic failed", e)
                        }
                    }

                    // Track screen changes dynamically to trigger/hide floating overlay
                    lifecycleScope.launch {
                        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                            viewModel.currentScreen.collect { screen ->
                                FocusTimerManager.setTimerScreenActiveState(this@MainActivity, screen == Screen.TIMER)
                                try {
                                    val bundle = Bundle().apply {
                                        putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_ID, screen.name)
                                        putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_NAME, screen.name)
                                        putString(com.google.firebase.analytics.FirebaseAnalytics.Param.CONTENT_TYPE, "screen")
                                    }
                                    firebaseAnalytics.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SELECT_ITEM, bundle)
                                    android.util.Log.d("MainActivity", "Logged SELECT_ITEM event for screen: ${screen.name}")
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Failed to log screen select event", e)
                                }
                            }
                        }
                    }

                    // Handle auto-navigation if launched with SHOW_TIMER_PAGE parameter
                    checkTimerNavigation(intent)
                    checkAppBlockInterceptions(intent)
                    handleDeepLink(intent)

                    isDbReady.value = true
                } catch (e: Throwable) {
                    e.printStackTrace()
                    startupException = e
                    isDbReady.value = true
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            startupException = e
            isDbReady.value = true
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                
                var showSocialOnboarding by remember {
                    mutableStateOf(run {
                        val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        val currentVersion = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).let {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    it.longVersionCode.toInt()
                                } else {
                                    @Suppress("DEPRECATION")
                                    it.versionCode
                                }
                            }
                        } catch (e: Exception) {
                            1
                        }
                        val shownVersion = appPrefs.getInt("social_blocker_settings_shown_version", -1)
                        shownVersion != currentVersion
                    })
                }
                LaunchedEffect(Unit) {
                    // Request Notification Permission on Android 13+ (API 33)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS
                        )
                        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                this@MainActivity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101
                            )
                        }
                    }
                }
                
                // On-startup check for downloaded APK and silent background checking
                val readyApkPath = remember { com.example.util.AppUpdateManager.getReadyApkPath(context) }
                var showStartupUpdateDialog by remember {
                    mutableStateOf(
                        readyApkPath?.let { path ->
                            val file = java.io.File(path)
                            file.exists() && file.length() > 0 && com.example.util.AppUpdateManager.isValidAndNewerApk(context, file)
                        } ?: false
                    )
                }

                val scope = rememberCoroutineScope()
                val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                
                var hasDrivePermission by remember { mutableStateOf(com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) }
                var showDrivePostUpdatePrompt by remember { mutableStateOf(false) }

                val authResolutionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    hasDrivePermission = com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)
                    if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) {
                        android.widget.Toast.makeText(context, "Google Drive Authorized! Auto-backup active.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
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
                                    com.example.util.GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                        authResolutionLauncher.launch(intent)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Google Sign-In failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val triggerDriveAuth = {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (googleAccount == null) {
                        try {
                            val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                            val webClientId = try {
                                context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                            } catch (e: Exception) {
                                ""
                            }
                            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
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
                            com.example.util.GoogleDriveSyncManager.getAccessToken(context) { intent ->
                                authResolutionLauncher.launch(intent)
                            }
                        }
                    }
                }

                var showCelebrationDialog by remember { mutableStateOf(false) }
                var updatedVersionCode by remember { mutableStateOf(0) }

                LaunchedEffect(Unit) {
                    val upgradedTo = com.example.util.AppUpdateManager.checkAndNotifyUpgradeComplete(context)
                    if (upgradedTo != null) {
                        updatedVersionCode = upgradedTo
                        showCelebrationDialog = true
                        prefs.edit().putBoolean("has_asked_drive_permission_after_update", false).apply()
                    }
                    if (!com.example.util.AppUpdateManager.isPauseUpdatesEnabled(context)) {
                        com.example.util.AppUpdateManager.checkForUpdates(context, manualCheck = false)
                    }
                }

                if (showCelebrationDialog) {
                    AlertDialog(
                        onDismissRequest = { 
                            showCelebrationDialog = false 
                            if (!com.example.util.GoogleDriveSyncManager.hasDrivePermission(context) && !prefs.getBoolean("has_asked_drive_permission_after_update", false)) {
                                showDrivePostUpdatePrompt = true
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success Icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Update Successful! 🎉", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Text(
                                "Life OS has been successfully updated to Build $updatedVersionCode.\n\n" +
                                "All your study statistics, task lists, and local backups remain completely safe and intact. Keep up the amazing focus!",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { 
                                    showCelebrationDialog = false 
                                    if (!com.example.util.GoogleDriveSyncManager.hasDrivePermission(context) && !prefs.getBoolean("has_asked_drive_permission_after_update", false)) {
                                        showDrivePostUpdatePrompt = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Awesome!", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF101014),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (showDrivePostUpdatePrompt) {
                    AlertDialog(
                        onDismissRequest = { 
                            showDrivePostUpdatePrompt = false 
                            prefs.edit().putBoolean("has_asked_drive_permission_after_update", true).apply()
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = "Cloud Icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Secure Your App Data", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Text(
                                "To keep your habits, tasks, settings, and study sessions completely secure after this update, we highly recommend linking your Google Drive for automatic, private cloud backups.",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { 
                                    showDrivePostUpdatePrompt = false 
                                    prefs.edit().putBoolean("has_asked_drive_permission_after_update", true).apply()
                                    triggerDriveAuth()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Connect Google Drive", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { 
                                    showDrivePostUpdatePrompt = false 
                                    prefs.edit().putBoolean("has_asked_drive_permission_after_update", true).apply()
                                }
                            ) {
                                Text("Later", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF101014),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                if (showStartupUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { showStartupUpdateDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Update ready icon",
                                    tint = com.example.ui.theme.WaterBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("System Update Ready", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            }
                        },
                        text = {
                            Text(
                                "A new Life OS update is downloaded and ready to install. Your local database and settings will be securely backed up prior to the installation to guarantee zero data loss. Proceed with the update now?",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showStartupUpdateDialog = false
                                    readyApkPath?.let { path ->
                                        com.example.util.AppUpdateManager.installApk(context, java.io.File(path))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.WaterBlue)
                            ) {
                                Text("Install Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartupUpdateDialog = false }) {
                                Text("Later", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF101014),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                val error = startupException
                val dbReady by isDbReady
                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error Logo",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "STARTUP FAILURE DETECTED",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Life OS failed to initialize. Details are documented below:",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = android.util.Log.getStackTraceString(error),
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().clear().apply()
                                    try {
                                        deleteDatabase("life_os_database")
                                    } catch (ex: Exception) {}
                                    val restartIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    startActivity(restartIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00))
                            ) {
                                Text("Factory Reset & Recovery Start", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (!dbReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF06070D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = com.example.ui.theme.WaterBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Initializing Database...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val isAppUnlocked by isAppUnlockedState
                    if (isAppUnlocked) {
                        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                    val activeNagTask by viewModel.activeNagTask.collectAsStateWithLifecycle()
                    val isSidebarOpen by viewModel.isLocalSidebarOpen.collectAsStateWithLifecycle()
                    val tabOrder by viewModel.tabOrder.collectAsStateWithLifecycle()
                    val hiddenTabs by viewModel.hiddenTabs.collectAsStateWithLifecycle()
                    val isTimerImmersive by viewModel.isTimerImmersive.collectAsStateWithLifecycle()
                    val tabBarOrientation by viewModel.tabBarOrientation.collectAsStateWithLifecycle()
                    val showHistoryScreen by viewModel.showHistoryScreen.collectAsStateWithLifecycle()
                    val navItems = getNavigationItems(tabOrder.filterNot { hiddenTabs.contains(it) })

                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    @OptIn(ExperimentalLayoutApi::class)
                    val isKeyboardVisible = WindowInsets.isImeVisible

                    // Back Navigation Control
                    if (currentScreen == Screen.DEEPA_AI) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                finish()
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && isTimerImmersive) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setTimerImmersive(false)
                            }
                        }
                    } else if (currentScreen == Screen.TIMER && showHistoryScreen) {
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.setShowHistoryScreen(false)
                            }
                        }
                    } else if (currentScreen == Screen.SETTINGS) {
                        val settingsActivePage by viewModel.settingsActivePage.collectAsStateWithLifecycle()
                        val previousScreenBeforeSettings by viewModel.previousScreenBeforeSettings.collectAsStateWithLifecycle()
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else if (settingsActivePage != 0) {
                                viewModel.updateSettingsActivePage(0)
                            } else {
                                val previous = previousScreenBeforeSettings
                                if (previous != null && previous != Screen.SETTINGS && previous != Screen.LOGIN && previous != Screen.PROFILE_SETUP && previous != Screen.PERMISSION_ONBOARDING && previous != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                    viewModel.navigateTo(previous)
                                } else {
                                    viewModel.navigateTo(Screen.DEEPA_AI)
                                }
                            }
                        }
                    } else {
                        // Other main tabs navigate back to Screen.DEEPA_AI (AI page again)
                        BackHandler(enabled = true) {
                            if (isKeyboardVisible) {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            } else {
                                viewModel.navigateTo(Screen.DEEPA_AI)
                            }
                        }
                    }

                    @Composable
                    fun MainScaffoldContent(scaffoldModifier: Modifier) {
                    Scaffold(
                        modifier = scaffoldModifier,
                        containerColor = Color.Transparent,
                        topBar = {}
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // Render screen container with premium fluid transition animations
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        val isForward = targetState.ordinal > initialState.ordinal
                                        val duration = 350
                                        (fadeIn(animationSpec = tween(duration)) +
                                         slideInHorizontally(
                                             initialOffsetX = { if (isForward) it / 6 else -it / 6 },
                                             animationSpec = tween(duration, easing = FastOutSlowInEasing)
                                         ) +
                                         scaleIn(initialScale = 0.97f, animationSpec = tween(duration)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(200)) +
                                            slideOutHorizontally(
                                                targetOffsetX = { if (isForward) -it / 6 else it / 6 },
                                                animationSpec = tween(duration, easing = FastOutSlowInEasing)
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "tab_screen_transition"
                                ) { targetScreen ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        when (targetScreen) {
                                            Screen.LOGIN -> LoginView(viewModel = viewModel)
                                            Screen.PROFILE_SETUP -> ProfileSetupView(viewModel = viewModel)
                                            Screen.PERMISSION_ONBOARDING -> PermissionOnboardingView(viewModel = viewModel)
                                            Screen.CALENDAR_OPTIMIZATION_ONBOARDING -> CalendarOptimizationOnboardingView(viewModel = viewModel)
                                            Screen.TASKS -> TaskEngineView(viewModel = viewModel)
                                            Screen.CALENDAR -> CalendarView(viewModel = viewModel)
                                            Screen.TIMER -> TimerView(viewModel = viewModel)
                                            Screen.HABITS -> HabitsView(viewModel = viewModel)
                                            Screen.COUNTDOWN -> CountdownView(viewModel = viewModel)
                                            Screen.JOURNAL -> JournalBookView(viewModel = viewModel)
                                            Screen.KEEP_NOTES -> KeepNotesView(viewModel = viewModel)
                                            Screen.CONTACTS -> ContactsView(viewModel = viewModel)
                                            Screen.FILE_EXPLORER -> FileExplorerView(viewModel = viewModel)
                                            Screen.FINANCES -> FinancialLedgerView(viewModel = viewModel)
                                            Screen.DEEPA_AI -> SmartChatView(viewModel = viewModel)
                                            Screen.SEARCH -> GlobalSearchView(viewModel = viewModel)
                                            Screen.ANALYTICS -> AnalyticsView(viewModel = viewModel)
                                            Screen.SETTINGS -> SettingsView(viewModel = viewModel)
                                            Screen.HEALTH -> com.example.ui.components.HealthView(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentScreen == Screen.TIMER && isTimerImmersive) {
                    // Full Screen Immersive Mode: covers all the display, leaving absolutely no side navigation or safe drawer padding!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        TimerView(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTablet = maxWidth >= 600.dp
                        val outerModifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF06070D),
                                        Color(0xFF0F111B),
                                        Color(0xFF030305)
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)

                        val savedAlignMode = tabBarOrientation.lowercase(java.util.Locale.ROOT)
                        val alignMode = if (savedAlignMode == "vertical" || savedAlignMode == "left" || savedAlignMode.isEmpty()) {
                            if (isTablet) "left" else "bottom"
                        } else {
                            savedAlignMode
                        }
                        if (alignMode == "horizontal" || alignMode == "top") {
                        Column(modifier = outerModifier) {
                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                // Top Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_top"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_top"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_top"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(WaterBlue.copy(alpha = bgAlpha))
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = tint,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                    }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else if (alignMode == "bottom") {
                        Column(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxWidth())

                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                                // Bottom Horizontal pill-tab navigation bar (Floating Glass Dock)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(28.dp))
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_bottom"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_bottom"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_bottom"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(WaterBlue.copy(alpha = bgAlpha))
                                                .let { m ->
                                                    if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                    else m
                                                }
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = tint,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                    }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            }
                        }
                    } else if (alignMode == "right") {
                        Row(modifier = outerModifier) {
                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())

                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                            // Right-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_right"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_right"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_right"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 36.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(WaterBlue.copy(alpha = bgAlpha))
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }
                        }
                    } else { // "left" or "vertical" or any other fallback
                        Row(modifier = outerModifier) {
                            if (!isKeyboardVisible && currentScreen != Screen.LOGIN && currentScreen != Screen.PROFILE_SETUP && currentScreen != Screen.PERMISSION_ONBOARDING && currentScreen != Screen.CALENDAR_OPTIMIZATION_ONBOARDING) {
                            // Left-hand vertical tabs column (Floating Glass Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(width = 1.dp, color = Color(0x18FFFFFF), shape = RoundedCornerShape(32.dp))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "LIFE OS",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.25f else 1.0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "nav_item_scale_left"
                                        )
                                        val tint by animateColorAsState(
                                            targetValue = if (isSelected) WaterBlue else Color.LightGray.copy(alpha = 0.6f),
                                            animationSpec = tween(300),
                                            label = "nav_item_tint_left"
                                        )
                                        val bgAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 0.15f else 0.0f,
                                            animationSpec = tween(300),
                                            label = "nav_item_bg_alpha_left"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClick { viewModel.navigateTo(item.screen) }
                                                .padding(vertical = 8.dp)
                                                .testTag("nav_item_${item.label.lowercase()}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(height = 36.dp, width = 56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(WaterBlue.copy(alpha = bgAlpha))
                                                    .let { m ->
                                                        if (isSelected) m.border(width = 1.dp, color = WaterBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                                        else m
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label,
                                                    tint = tint,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            }

                            MainScaffoldContent(scaffoldModifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                }
                } else {
                    com.example.ui.components.AppLockOverlay(
                        onUnlocked = {
                            isAppUnlockedState.value = true
                        }
                    )
                }

                // App Interception Selector Prompt Overlay
                val interceptPkg by interceptedAppSessionQuery
                if (interceptPkg != null) {
                    AlertDialog(
                        onDismissRequest = {
                            // Non-dismissible by clicking outside to keep it a strict block
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Session Warning Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "LIFE OS APP MONITOR",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val appLabel = remember(interceptPkg) {
                                    try {
                                        val pm = packageManager
                                        val info = pm.getApplicationInfo(interceptPkg ?: "", 0)
                                        pm.getApplicationLabel(info).toString()
                                    } catch (e: Exception) {
                                        interceptPkg?.substringAfterLast('.') ?: "Social App"
                                    }
                                }
                                Text(
                                    text = "You are attempting to open $appLabel.",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Please allocate your session time usage below. Once the duration is over, Life OS will automatically block access.\n\nSelecting 'Close App' will safely exit and return to home.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 10, 15, 20).forEach { mins ->
                                    Button(
                                        onClick = {
                                            com.example.util.AppBlockHelper.startTemporarySession(applicationContext, interceptPkg ?: "", mins)
                                            interceptedAppSessionQuery.value = null
                                            // Relaunch the application safely
                                            try {
                                                val pm = packageManager
                                                val launchIntent = pm.getLaunchIntentForPackage(interceptPkg ?: "")
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(applicationContext, "Session set, but failed to launch app implicitly.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = WaterBlue,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Text(
                                            text = "Use for $mins minutes",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        interceptedAppSessionQuery.value = null
                                        // Minimize our app and route user to the home screen launcher
                                        try {
                                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_HOME)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(homeIntent)
                                        } catch (e: Exception) {
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF5252),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text(
                                        text = "Close App",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Global Focus Session Saved & Verified Confirmation Overlay
                val showGlobalVerification by FocusTimerManager.showGlobalVerificationDialog.collectAsStateWithLifecycle()
                val globalFocusedSecs by FocusTimerManager.globalVerificationFocusedTimeSeconds.collectAsStateWithLifecycle()
                val globalRevisedSecs by FocusTimerManager.globalVerificationRevisedTotalSeconds.collectAsStateWithLifecycle()
                val verifiedStartMs by FocusTimerManager.verifiedSessionStartMs.collectAsStateWithLifecycle()
                val verifiedPauseRanges by FocusTimerManager.verifiedSessionPauseRanges.collectAsStateWithLifecycle()

                if (showGlobalVerification) {
                    AlertDialog(
                        onDismissRequest = {
                            FocusTimerManager.setShowGlobalVerificationDialog(false)
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Icon",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "SYSTEM VERIFICATION",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                fun formatSecondsToReadable(seconds: Int): String {
                                    if (seconds >= 3600) {
                                        val h = seconds / 3600
                                        val m = (seconds % 3600) / 60
                                        val s = seconds % 60
                                        return "${h}h ${m}m ${s}s"
                                    } else if (seconds >= 60) {
                                        val m = seconds / 60
                                        val s = seconds % 60
                                        return "${m}m ${s}s"
                                    } else {
                                        return "${seconds}s"
                                    }
                                }

                                val formattedNow = formatSecondsToReadable(globalFocusedSecs)
                                val pastSeconds = maxOf(0, globalRevisedSecs - globalFocusedSecs)
                                val formattedPast = formatSecondsToReadable(pastSeconds)
                                val formattedRevised = formatSecondsToReadable(globalRevisedSecs)

                                val timeFormatter = remember { java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()) }
                                val startStr = verifiedStartMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"
                                
                                var totalBreakMs = 0L
                                verifiedPauseRanges.forEach { (pStart, pEnd) ->
                                    if (pEnd >= pStart) {
                                        totalBreakMs += (pEnd - pStart)
                                    }
                                }
                                val breakSeconds = (totalBreakMs / 1000).toInt()
                                val wallSeconds = globalFocusedSecs + breakSeconds
                                val computedEndMs = verifiedStartMs?.let { it + wallSeconds * 1000L }
                                val endStr = computedEndMs?.let { timeFormatter.format(java.util.Date(it)) } ?: "N/A"

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22222A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 1. Previously Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("PREVIOUSLY FOCUSED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedPast, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 2. Start Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("START TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(startStr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 3. End Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("END TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(endStr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 4. Current Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("CURRENT FOCUSED TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedNow, color = WaterBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22222A)))

                                        // 5. Revised Focused Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("REVISED FOCUSED TIME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formattedRevised, color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    FocusTimerManager.setShowGlobalVerificationDialog(false)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text(
                                    text = "Confirm & Close",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        containerColor = Color(0xFF0F0F12),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                
                if (showSocialOnboarding) {
                    SocialOnboardingView(
                        onDismiss = { showSocialOnboarding = false }
                    )
                }
            }
        }
    }
}

    override fun onStart() {
        super.onStart()
        FocusTimerManager.setAppBackgroundedState(this, false)
        if (!com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.trackSleepFromDeviceUsage(applicationContext)
        viewModel.onAppResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppPause()
        FocusTimerManager.setAppBackgroundedState(this, true)
        if (com.example.util.AppLockHelper.isAppLockEnabled(this)) {
            isAppUnlockedState.value = false
        }

        // Auto-reconcile and Auto-backup to public storage before potential uninstall/force-stop
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (::database.isInitialized) {
                    // Ensure state consistency and flush memoryWAL files cleanly before backup
                    com.example.util.StateReconciliationHelper.runUnifiedReconciliation(applicationContext, database)
                    com.example.util.DatabaseBackupHelper.autoBackup(applicationContext, database)
                }
                
                // If the user has signed in and granted Drive permissions, auto-sync backup before potential uninstallation
                if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext)) {
                    android.util.Log.i("MainActivity", "Auto-backing up focus records to Google Drive on stop...")
                    val (success, msg) = com.example.util.GoogleDriveSyncManager.backupFocusData(applicationContext)
                    android.util.Log.i("MainActivity", "Google Drive auto-backup on stop result: success=$success, msg=$msg")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "State reconciliation, auto-backup, or Google Drive backup failed on stop", e)
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (com.example.util.FocusTimerManager.isAlarmPlaying()) {
                    com.example.util.FocusTimerManager.stopAlarm()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkTimerNavigation(intent)
        checkAppBlockInterceptions(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data
        if (uri == null) return
        
        android.util.Log.i("MainActivity", "handleDeepLink: action=$action, uri=$uri")
        try {
            val scheme = uri.scheme
            val host = uri.host
            if (scheme == "lifeos" || (host == "lifeos.app" && (scheme == "http" || scheme == "https"))) {
                val path = uri.path
                val targetHost = if (scheme == "lifeos") host else uri.lastPathSegment
                
                android.util.Log.i("MainActivity", "Deep Link routing: targetHost=$targetHost, path=$path")
                
                when (targetHost) {
                    "login" -> viewModel.navigateTo(Screen.LOGIN)
                    "profile_setup" -> viewModel.navigateTo(Screen.PROFILE_SETUP)
                    "onboarding" -> viewModel.navigateTo(Screen.PERMISSION_ONBOARDING)
                    "ai_chat" -> viewModel.navigateTo(Screen.DEEPA_AI)
                    "search" -> viewModel.navigateTo(Screen.SEARCH)
                    "tasks" -> viewModel.navigateTo(Screen.TASKS)
                    "calendar" -> viewModel.navigateTo(Screen.CALENDAR)
                    "timer" -> viewModel.navigateTo(Screen.TIMER)
                    "habits" -> viewModel.navigateTo(Screen.HABITS)
                    "countdown" -> viewModel.navigateTo(Screen.COUNTDOWN)
                    "journal" -> viewModel.navigateTo(Screen.JOURNAL)
                    "keep_notes", "notes" -> viewModel.navigateTo(Screen.KEEP_NOTES)
                    "contacts" -> viewModel.navigateTo(Screen.CONTACTS)
                    "file_explorer" -> viewModel.navigateTo(Screen.FILE_EXPLORER)
                    "finances", "financials" -> viewModel.navigateTo(Screen.FINANCES)
                    "analytics" -> viewModel.navigateTo(Screen.ANALYTICS)
                    "settings" -> viewModel.navigateTo(Screen.SETTINGS)
                    "action" -> {
                        when (path) {
                            "/sync_calendar" -> viewModel.syncGoogleCalendar(applicationContext)
                            "/backup_drive" -> {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.util.GoogleDriveSyncManager.backupFocusData(applicationContext)
                                }
                            }
                            "/force_contacts_sync" -> viewModel.forceSyncAllContactsToDevice()
                            "/check_updates" -> {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.util.AppUpdateManager.checkForUpdates(applicationContext, manualCheck = true)
                                }
                            }
                        }
                    }
                    else -> {
                        // If path segments contain the screen name (e.g. /tasks or /calendar)
                        val segments = uri.pathSegments
                        if (segments.isNotEmpty()) {
                            when (segments.firstOrNull()?.lowercase()) {
                                "login" -> viewModel.navigateTo(Screen.LOGIN)
                                "profile_setup" -> viewModel.navigateTo(Screen.PROFILE_SETUP)
                                "onboarding" -> viewModel.navigateTo(Screen.PERMISSION_ONBOARDING)
                                "ai_chat" -> viewModel.navigateTo(Screen.DEEPA_AI)
                                "search" -> viewModel.navigateTo(Screen.SEARCH)
                                "tasks" -> viewModel.navigateTo(Screen.TASKS)
                                "calendar" -> viewModel.navigateTo(Screen.CALENDAR)
                                "timer" -> viewModel.navigateTo(Screen.TIMER)
                                "habits" -> viewModel.navigateTo(Screen.HABITS)
                                "countdown" -> viewModel.navigateTo(Screen.COUNTDOWN)
                                "journal" -> viewModel.navigateTo(Screen.JOURNAL)
                                "keep_notes", "notes" -> viewModel.navigateTo(Screen.KEEP_NOTES)
                                "contacts" -> viewModel.navigateTo(Screen.CONTACTS)
                                "file_explorer" -> viewModel.navigateTo(Screen.FILE_EXPLORER)
                                "finances", "financials" -> viewModel.navigateTo(Screen.FINANCES)
                                "analytics" -> viewModel.navigateTo(Screen.ANALYTICS)
                                "settings" -> viewModel.navigateTo(Screen.SETTINGS)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing deep link", e)
        }
    }

    private fun checkTimerNavigation(intent: Intent?) {
        try {
            if (intent?.getBooleanExtra("SHOW_TIMER_PAGE", false) == true || intent?.getBooleanExtra("SHOW_FULL_SCREEN_TIMER", false) == true) {
                viewModel.navigateTo(Screen.TIMER)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to navigate to timer screen", e)
        }
    }

    private fun checkAppBlockInterceptions(intent: Intent?) {
        if (intent == null) return
        
        if (intent.getBooleanExtra("SHOW_BLOCKS_PAGE", false)) {
            viewModel.navigateTo(Screen.SETTINGS)
            intent.removeExtra("SHOW_BLOCKS_PAGE")
            getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("direct_to_blocks", true)
                .apply()
        }

        if (intent.getBooleanExtra("SHOW_INTERCEPT_PROMPT", false)) {
            val pkg = intent.getStringExtra("INTERCEPTED_PACKAGE")
            if (!pkg.isNullOrEmpty()) {
                interceptedAppSessionQuery.value = pkg
            }
            intent.removeExtra("SHOW_INTERCEPT_PROMPT")
            intent.removeExtra("INTERCEPTED_PACKAGE")
        }
    }

    private fun getNavigationItems(order: List<Screen>): List<NavigationItem> {
        val mapping = mapOf(
            Screen.TASKS to NavigationItem(Screen.TASKS, Icons.Default.List, "Tasks"),
            Screen.CALENDAR to NavigationItem(Screen.CALENDAR, Icons.Default.DateRange, "Calendar"),
            Screen.TIMER to NavigationItem(Screen.TIMER, Icons.Default.PlayArrow, "Timer"),
            Screen.HABITS to NavigationItem(Screen.HABITS, Icons.Default.CheckCircle, "Habits"),
            Screen.COUNTDOWN to NavigationItem(Screen.COUNTDOWN, Icons.Default.Notifications, "Countdown"),
            Screen.JOURNAL to NavigationItem(Screen.JOURNAL, Icons.Default.Book, "Journal"),
            Screen.KEEP_NOTES to NavigationItem(Screen.KEEP_NOTES, Icons.Default.Note, "Keep Notes"),
            Screen.CONTACTS to NavigationItem(Screen.CONTACTS, Icons.Default.AccountBox, "Contacts"),
            Screen.FILE_EXPLORER to NavigationItem(Screen.FILE_EXPLORER, Icons.Default.Folder, "File Explorer"),
            Screen.FINANCES to NavigationItem(Screen.FINANCES, Icons.Default.MonetizationOn, "Finances"),
            Screen.DEEPA_AI to NavigationItem(Screen.DEEPA_AI, Icons.Default.Face, "Deepa AI"),
            Screen.SEARCH to NavigationItem(Screen.SEARCH, Icons.Default.Search, "Search"),
            Screen.ANALYTICS to NavigationItem(Screen.ANALYTICS, Icons.Default.Star, "Analytics"),
            Screen.SETTINGS to NavigationItem(Screen.SETTINGS, Icons.Default.Settings, "Settings"),
            Screen.HEALTH to NavigationItem(Screen.HEALTH, Icons.Default.Favorite, "Health")
        )
        return order.mapNotNull { mapping[it] }
    }
}

data class NavigationItem(val screen: Screen, val icon: ImageVector, val label: String)
