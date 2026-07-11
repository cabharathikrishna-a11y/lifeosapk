package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.util.FocusTimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.example.ui.FocusRecord
import com.example.api.UserRemote
import com.example.api.BellSignal

class KeepAliveService : Service() {

    private val serviceJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("KeepAliveService", "Uncaught exception in background service scope: ${exception.message}", exception)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)
    private var combinedMonitoringJob: Job? = null
    private var friendsFocusJob: Job? = null
    private var waterReminderJob: Job? = null
    private var databaseAlignmentCheckJob: Job? = null
    private var lastKnownForegroundPackage: String? = null
    private var wasUsingInstaOnBreak = false
    private val lastFocusStatusMap = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val latestFetchedPeerStates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val debounceJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var usersValueEventListener: ValueEventListener? = null
    private var usersDatabaseReference: DatabaseReference? = null
    private var bellsValueEventListener: ValueEventListener? = null
    private var bellsDatabaseReference: DatabaseReference? = null

    override fun onCreate() {
        super.onCreate()
        
        // 1. INSTANTLY satisfy the Android OS requirement
        createNotificationChannel()
        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Initializing unbreakable scheduler...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        try {
            startForeground(NOTIFICATION_ID, initialNotification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to start service in foreground in onCreate: ${e.message}", e)
        }

        // 2. NOW it is safe to do heavy initialization
        com.example.util.StableTime.init()
        com.example.api.FirebaseClient.appContext = applicationContext
        com.example.util.FocusTimerManager.init(applicationContext)
        com.example.util.AppBlockHelper.initializeStrictAppsIfNeeded(applicationContext)
        
        startCombinedMonitoring()
        startFriendsFocusMonitoring()
        startWaterReminderMonitoring()
        startHourlyDatabaseAlignmentCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Guarantee that startForeground is called instantly using a safe, up-to-date notification
            createNotificationChannel()

            val action = intent?.action
            Log.d("KeepAliveService", "KeepAliveService started with action: $action")
            
            when (action) {
                ACTION_PAUSE_TIMER -> {
                    FocusTimerManager.pauseTimer(this)
                }
                ACTION_RESUME_TIMER -> {
                    FocusTimerManager.startTimer(this)
                }
                ACTION_RESET_TIMER -> {
                    FocusTimerManager.resetTimer(this)
                }
                ACTION_PAUSE_STOPWATCH -> {
                    FocusTimerManager.pauseStopwatch(this)
                }
                ACTION_RESUME_STOPWATCH -> {
                    FocusTimerManager.startStopwatch(this)
                }
                ACTION_RESET_STOPWATCH -> {
                    FocusTimerManager.resetStopwatch(this)
                }
            }

            // Immediately build the actual up-to-date notification and set it as foreground
            val notification = buildKeepAliveNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error in onStartCommand: ${e.message}", e)
            try {
                val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("LifeOS Active System")
                    .setContentText("Ensuring scheduler accuracy")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()
                startForeground(NOTIFICATION_ID, fallbackNotification)
            } catch (inner: Exception) {
                Log.e("KeepAliveService", "Fallback startForeground failed: ${inner.message}", inner)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateNotificationDirectly() {
        try {
            val notification = buildKeepAliveNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to update notification directly: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LifeOS Core Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps LifeOS system scheduling services active and accurate"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildFallbackNotification(): Notification {
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            9999,
            launchIntent,
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Ensuring scheduler accuracy")
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getActionPendingIntent(action: String): android.app.PendingIntent {
        val intent = Intent(this, KeepAliveService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        return android.app.PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            flags
        )
    }

    private fun buildKeepAliveNotification(): Notification {
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            9999,
            launchIntent,
            flags
        )

        val isTimerOn = FocusTimerManager.isTimerRunning.value
        val hasProgress = FocusTimerManager.timerSecondsLeft.value < FocusTimerManager.timerDurationMinutes.value * 60
        val isPaused = !isTimerOn && hasProgress

        val isStopwatchOn = FocusTimerManager.isStopwatchActive.value
        val hasStopwatchProgress = FocusTimerManager.stopwatchSeconds.value > 0
        val isStopwatchPaused = !isStopwatchOn && hasStopwatchProgress

        if (isTimerOn || isPaused) {
            val totalSecs = FocusTimerManager.timerSecondsLeft.value
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            val timeStr = if (hours > 0) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", totalSecs / 60, secs)
            }
            val phase = if (FocusTimerManager.isFocusPhase.value) "FOCUSING 🎯" else "BREAK ☕"
            val taskName = FocusTimerManager.attachedTask.value?.title ?: "Focus Session"

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true)

            if (isTimerOn) {
                builder.setContentTitle("Focus Timer ($phase)")
                builder.setContentText("Active - $taskName")
                builder.setUsesChronometer(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
                builder.setWhen(System.currentTimeMillis() + (totalSecs * 1000L))
            } else {
                builder.setContentTitle("Focus Timer: $timeStr ($phase)")
                builder.setContentText(if (isPaused) "Paused - $taskName" else "Active - $taskName")
                builder.setUsesChronometer(false)
            }

            if (isTimerOn) {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    getActionPendingIntent(ACTION_PAUSE_TIMER)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End",
                    getActionPendingIntent(ACTION_RESET_TIMER)
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    getActionPendingIntent(ACTION_RESUME_TIMER)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End",
                    getActionPendingIntent(ACTION_RESET_TIMER)
                )
            }

            return builder.build()
        } else if (isStopwatchOn || isStopwatchPaused) {
            val totalSecs = FocusTimerManager.stopwatchSeconds.value
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            val timeStr = if (hours > 0) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true)

            if (isStopwatchOn) {
                builder.setContentTitle("Stopwatch Active")
                builder.setContentText("Focus session in progress")
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() - (totalSecs * 1000L))
            } else {
                builder.setContentTitle("Stopwatch: $timeStr")
                builder.setContentText("Paused stopwatch")
                builder.setUsesChronometer(false)
            }

            if (isStopwatchOn) {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    getActionPendingIntent(ACTION_PAUSE_STOPWATCH)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End",
                    getActionPendingIntent(ACTION_RESET_STOPWATCH)
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    getActionPendingIntent(ACTION_RESUME_STOPWATCH)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End",
                    getActionPendingIntent(ACTION_RESET_STOPWATCH)
                )
            }

            return builder.build()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeOS Active System")
            .setContentText("Ensuring accurate backgrounds & task scheduling")
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "Start Stopwatch",
                getActionPendingIntent(ACTION_RESUME_STOPWATCH)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "Start Timer",
                getActionPendingIntent(ACTION_RESUME_TIMER)
            )
            .build()
    }

    private fun startCombinedMonitoring() {
        if (combinedMonitoringJob != null) return
        combinedMonitoringJob = serviceScope.launch {
            var lastCheckTime = android.os.SystemClock.elapsedRealtime()
            var prevPackage: String? = null
            var lastWasFocusing = false
            while (true) {
                // Adaptive delay: 1 second if active timer or strict mode/monitored apps are present, 5 seconds if idle
                val isTimerActive = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value
                val isFocusPhase = FocusTimerManager.isFocusPhase.value
                val isCurrentlyFocusing = isTimerActive && isFocusPhase

                if (lastWasFocusing && !isCurrentlyFocusing) {
                    Log.d("KeepAliveService", "Focusing ended/paused/break. Releasing blocked notifications.")
                    com.example.util.AppBlockHelper.releaseBlockedNotifications(applicationContext)
                }
                lastWasFocusing = isCurrentlyFocusing

                val strictPrefs = getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
                val strictEnabled = strictPrefs.getBoolean("strict_mode_enabled", true)
                val monitoredAppsCount = com.example.util.AppBlockHelper.getBlockedApps(applicationContext).size

                val delayMs = if (isTimerActive || strictEnabled || monitoredAppsCount > 0) 1000L else 5000L
                delay(delayMs)

                val now = android.os.SystemClock.elapsedRealtime()
                val actualElapsedMs = now - lastCheckTime
                lastCheckTime = now

                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isScreenOn = powerManager?.isInteractive ?: true
                    if (!isScreenOn) continue

                    val foregroundPackage = getForegroundPackageName() ?: continue

                    // Detect if foreground app changed to clear active screen limit bypass session
                    if (prevPackage != null && foregroundPackage != prevPackage) {
                        val monitoredApps = com.example.util.AppBlockHelper.getBlockedApps(applicationContext)
                        if (monitoredApps.contains(prevPackage)) {
                            val isLimitOver = com.example.util.AppBlockHelper.isDailyLimitExceeded(applicationContext, prevPackage)
                            if (isLimitOver) {
                                com.example.util.AppBlockHelper.clearSessionForPackage(applicationContext, prevPackage)
                                Log.d("KeepAliveService", "Cleared session for $prevPackage because user left the app (switched to $foregroundPackage)")
                            }
                        }
                    }
                    prevPackage = foregroundPackage

                    if (foregroundPackage == packageName) continue

                    val isBreakActive = !FocusTimerManager.isFocusPhase.value

                    // --- 1. STRICT MODE MONITORING ---
                    if (isTimerActive) {
                        // Track if using Instagram or Snapchat during break timer
                        if (isBreakActive && (foregroundPackage == "com.instagram.android" || foregroundPackage == "com.snapchat.android")) {
                            wasUsingInstaOnBreak = true
                        }

                        // Close immediately if break ended
                        if (wasUsingInstaOnBreak && !isBreakActive) {
                            wasUsingInstaOnBreak = false
                            if (foregroundPackage == "com.instagram.android" || foregroundPackage == "com.snapchat.android") {
                                Log.d("KeepAliveService", "Instagram/Snapchat break over! Returning to Focus Timer.")
                                launch(Dispatchers.Main) {
                                    Toast.makeText(applicationContext, "Break is over! Returning to Focus Timer... 🎯", Toast.LENGTH_LONG).show()
                                }
                                val launchIntent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("SHOW_FULL_SCREEN_TIMER", true)
                                }
                                startActivity(launchIntent)
                                continue
                            }
                        }
                    } else {
                        wasUsingInstaOnBreak = false
                    }

                    var intercepted = false
                    if (strictEnabled && !isBreakActive && isTimerActive) {
                        if (com.example.util.AppBlockHelper.isPackageBlockedInStrictMode(applicationContext, foregroundPackage)) {
                            Log.d("KeepAliveService", "Strict Mode Intercept triggered for package: $foregroundPackage")
                            
                            val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                putExtra("IS_STRICT_MODE_INTERCEPT", true)
                                putExtra("IS_LIMIT_BLOCK", false)
                            }
                            startActivity(launchIntent)
                            intercepted = true
                        }
                    }

                    // --- 2. SCREEN LIMITS & APP BLOCKS MONITORING ---
                    if (!intercepted) {
                        val monitoredApps = com.example.util.AppBlockHelper.getBlockedApps(applicationContext)
                        
                        // Check if the foreground app is a user-launchable app on the device
                        val isLaunchableApp = try {
                            foregroundPackage != applicationContext.packageName && 
                            applicationContext.packageManager.getLaunchIntentForPackage(foregroundPackage) != null
                        } catch (e: Exception) {
                            false
                        }

                        if (isLaunchableApp) {
                            com.example.util.AppBlockHelper.checkAndResetDailyUsageIfNeeded(applicationContext)
                            
                            // 1. Increment active usage counter by actual elapsed seconds (e.g. 1s or 5s)
                            val incrementSecs = (actualElapsedMs / 1000).toInt()
                            if (incrementSecs > 0) {
                                com.example.util.AppBlockHelper.incrementDailyUsageSeconds(applicationContext, foregroundPackage, incrementSecs)
                            }
                        }

                        if (monitoredApps.contains(foregroundPackage)) {
                            val blockedApps = strictPrefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
                            val isAppInAllowList = !blockedApps.contains(foregroundPackage)
                            val areLimitsActive = !isTimerActive || !strictEnabled || isAppInAllowList

                            if (areLimitsActive) {
                                val hasSession = com.example.util.AppBlockHelper.isSessionActive(applicationContext, foregroundPackage)
                                val isLimitOver = com.example.util.AppBlockHelper.isDailyLimitExceeded(applicationContext, foregroundPackage)
                                val isLimitBypassed = com.example.util.AppBlockHelper.isDailyBypassed(applicationContext, foregroundPackage)
                                
                                if (isLimitOver && !isLimitBypassed) {
                                    if (!hasSession) {
                                        Log.d("KeepAliveService", "Daily limit exceeded for $foregroundPackage! Redirecting to block countdown...")
                                        val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            putExtra("IS_LIMIT_BLOCK", true)
                                            putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                        }
                                        startActivity(launchIntent)
                                    }
                                    continue
                                }

                                // 3. If limit not over, check temporary session
                                if (!hasSession) {
                                    Log.d("KeepAliveService", "No active temporary session for $foregroundPackage. Pointing to picker.")
                                    val launchIntent = Intent(applicationContext, com.example.ui.AppBlockInterceptActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra("IS_LIMIT_BLOCK", false)
                                        putExtra("INTERCEPTED_PACKAGE", foregroundPackage)
                                    }
                                    startActivity(launchIntent)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Failed to run combined monitoring: ${e.message}", e)
                }
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        
        // If we don't have a last known package, do a wider query to initialize it
        val queryWindowMs = if (lastKnownForegroundPackage == null) {
            30 * 60 * 1000L // 30 minutes
        } else {
            15 * 1000L // 15 seconds
        }
        
        val eventsStartTime = endTime - queryWindowMs
        val events = usageStatsManager.queryEvents(eventsStartTime, endTime)
        
        val event = android.app.usage.UsageEvents.Event()
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                lastKnownForegroundPackage = event.packageName
            }
        }
        
        // Fallback: If still null, query daily usage stats for the last 24 hours
        if (lastKnownForegroundPackage == null) {
            val statsStartTime = endTime - (24 * 3600 * 1000L)
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                statsStartTime,
                endTime
            )
            if (stats != null && stats.isNotEmpty()) {
                var lastUsage: android.app.usage.UsageStats? = null
                for (stat in stats) {
                    if (lastUsage == null || stat.lastTimeUsed > lastUsage.lastTimeUsed) {
                        lastUsage = stat
                    }
                }
                if (lastUsage != null && (endTime - lastUsage.lastTimeUsed) < 15000) {
                    lastKnownForegroundPackage = lastUsage.packageName
                }
            }
        }
        
        return lastKnownForegroundPackage
    }

    private fun startFriendsFocusMonitoring() {
        if (friendsFocusJob != null) return

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val currentUsername = prefs.getString("current_username", null)

        if (isLoggedIn && currentUsername != null) {
            try {
                // 1. Get the list of friends we care about
                val myFriendsList = listOf("subash", "madhavan", "maddy", "shalini")
                
                // 2. Tell the Firebase WebSocket to listen to them via isolated sub-nodes
                myFriendsList.forEach { friend ->
                    com.example.api.FirebaseSyncManager.listenToStatsDashboard(applicationContext, friend)
                }
                
                // 3. Observe the flow safely for UI updates and notifications
                serviceScope.launch {
                    com.example.api.FirebaseSyncManager.friendsLiveStatus.collect { liveFriendsMap ->
                        com.example.api.FirebaseRepository.syncAllUsers(liveFriendsMap)
                        processFriendsFocusStateAndNotify(liveFriendsMap)

                        // Real-time alignment check for current user if modified by another device (e.g. ended by Web App)
                        val meRemote = liveFriendsMap[currentUsername]
                        if (meRemote != null) {
                            val myDeviceId = com.example.util.FocusTimerManager.getOrCreateDeviceId(applicationContext)
                            val localIsActive = com.example.util.FocusTimerManager.isTimerRunning.value || com.example.util.FocusTimerManager.isStopwatchActive.value
                            val remoteIsFocusing = meRemote.isFocusing == true
                            val remoteLastUpdatedDeviceId = meRemote.lastUpdatedDeviceId

                            if (localIsActive && !remoteIsFocusing && (remoteLastUpdatedDeviceId == null || remoteLastUpdatedDeviceId != myDeviceId)) {
                                Log.i("KeepAliveService", "Real-time check: Focus ended remotely by another device ($remoteLastUpdatedDeviceId). Resetting local session.")
                                com.example.util.FocusTimerManager.performCloudAlignmentCheck(applicationContext)
                            }
                        }
                    }
                }

                // 4. Setup real-time listener on "/bells/{currentUsername}"
                val url = com.example.api.FirebaseConfig.getDatabaseUrl(applicationContext)
                val database = FirebaseDatabase.getInstance(url)
                val bellsRef = database.getReference("bells").child(currentUsername)
                bellsDatabaseReference = bellsRef
                val bellsListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        serviceScope.launch(Dispatchers.Default) {
                            try {
                                if (snapshot.exists()) {
                                    val senderUsername = snapshot.child("senderUsername").getValue(String::class.java) ?: ""
                                    val senderDisplayName = snapshot.child("senderDisplayName").getValue(String::class.java) ?: ""
                                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                                    val isProcessed = snapshot.child("isProcessed").getValue(Boolean::class.java) ?: false

                                    val signal = BellSignal(
                                        senderUsername = senderUsername,
                                        senderDisplayName = senderDisplayName,
                                        timestamp = timestamp,
                                        isProcessed = isProcessed
                                    )

                                    processBellSignal(signal, currentUsername)
                                } else {
                                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    notificationManager.cancel(10003)
                                }
                            } catch (e: Exception) {
                                Log.e("KeepAliveService", "Failed to process real-time bell update snapshot: ${e.message}", e)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w("KeepAliveService", "Real-time bell listener cancelled: ${error.message}")
                    }
                }
                bellsRef.addValueEventListener(bellsListener)
                bellsValueEventListener = bellsListener
                Log.d("KeepAliveService", "Registered Real-time Firebase Database listener for reminder bells.")

            } catch (e: Exception) {
                Log.e("KeepAliveService", "Error configuring Real-time Firebase listeners: ${e.message}", e)
            }
        }

        // Keep a non-aggressive background sync loop for inactivity notifications and database alignments
        friendsFocusJob = serviceScope.launch {
            while (true) {
                checkInactivityAndNotify()
                delay(60000) // Poll sync every 60 seconds (no REST users querying!)
            }
        }
    }

    private fun checkInactivityAndNotify() {
        try {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            if (!isLoggedIn) return

            val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
            val now = System.currentTimeMillis()

            if (isLocalFocusing) {
                // User is focusing! Update last activity timestamp and reset notification flag
                prefs.edit()
                    .putLong("last_focus_activity_timestamp_ms", now)
                    .putBoolean("inactivity_6hr_notified", false)
                    .apply()
            } else if (com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
                // Sleep hours: continuously advance the last active timestamp so inactivity timer starts fresh when waking up
                prefs.edit()
                    .putLong("last_focus_activity_timestamp_ms", now)
                    .putBoolean("inactivity_6hr_notified", false)
                    .apply()
            } else {
                var lastTime = prefs.getLong("last_focus_activity_timestamp_ms", 0L)
                if (lastTime == 0L) {
                    // Initialize if never set
                    lastTime = now
                    prefs.edit().putLong("last_focus_activity_timestamp_ms", now).apply()
                }

                val elapsedMs = now - lastTime
                val sixHoursMs = 6 * 60 * 60 * 1000L // 6 hours straight
                
                if (elapsedMs >= sixHoursMs) {
                    val alreadyNotified = prefs.getBoolean("inactivity_6hr_notified", false)
                    if (!alreadyNotified) {
                        sendInactivityEncouragementNotification()
                        prefs.edit().putBoolean("inactivity_6hr_notified", true).apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error in checkInactivityAndNotify: ${e.message}", e)
        }
    }

    private fun sendInactivityEncouragementNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "inactivity_encouragement_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Study Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to start studying if you have been inactive"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 10010, launchIntent, flags)
        
        val messages = listOf(
            "It's been 6 hours since your last session. Let's start studying and make some progress! ��",
            "Ready to unlock your full potential? Click here to start your focus timer! 🎯",
            "A small step today leads to big achievements tomorrow. Let's do a quick focus session! 🚀",
            "Consistency is key! You haven't focused in a while. Let's study together now! 👨‍💻",
            "Your goals are waiting. Start a focus session now and cross off your tasks! 🏆"
        )
        val message = messages.random()
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Let's Start Studying! 📚")
            .setContentText(message)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
            
        notificationManager.notify(10010, notification)
    }

    private fun processFriendsFocusStateAndNotify(users: Map<String, UserRemote>) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val friendsChannelId = "friends_focus_channel"
            
            // Create notification channel for friends focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    friendsChannelId,
                    "Friends Focusing Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows notifications when your friends are focusing"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val friendsNotificationId = 10002
            
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            val currentUsername = prefs.getString("current_username", null)
            
            if (isLoggedIn && currentUsername != null) {
                val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value

                // Check for focus transitions and trigger sound alert if we are NOT focusing with 30s debounce
                users.forEach { (username, peer) ->
                    if (username != currentUsername && 
                        username != "admin"
                    ) {
                        val isPeerNowFocusing = peer.isFocusing == true
                        latestFetchedPeerStates[username] = isPeerNowFocusing
                        
                        val wasPeerFocusing = lastFocusStatusMap[username] == true
                        val hasKey = lastFocusStatusMap.containsKey(username)

                        if (!hasKey) {
                            // First time populating, just store current status, don't trigger notification
                            lastFocusStatusMap[username] = isPeerNowFocusing
                        } else if (isPeerNowFocusing != wasPeerFocusing) {
                            // Peer status changed! Start debounce job
                            val jobKey = username
                            val existingJob = debounceJobs[jobKey]
                            if (existingJob == null || !existingJob.isActive) {
                                debounceJobs[jobKey] = serviceScope.launch {
                                    kotlinx.coroutines.delay(30000L) // Debounce delay 30 seconds
                                    val currentFirebaseState = latestFetchedPeerStates[username] ?: isPeerNowFocusing
                                    if (currentFirebaseState == isPeerNowFocusing) {
                                        // The status has remained changed for at least 30 seconds! Update map and trigger notification.
                                        lastFocusStatusMap[username] = isPeerNowFocusing
                                        if (isPeerNowFocusing && !isLocalFocusing) {
                                            val peerName = cleanName(peer.nickname ?: peer.name ?: username)
                                            val peerEmoji = peer.emoji ?: "🎯"
                                            val taskText = peer.currentTaskTitle?.let { " on: $it" } ?: ""

                                            val alertChannelId = "peer_started_focus_channel"
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                val alertChannel = NotificationChannel(
                                                    alertChannelId,
                                                    "Friends Focus Alerts",
                                                    NotificationManager.IMPORTANCE_HIGH
                                                ).apply {
                                                    description = "Plays alert with sound when a friend starts focusing"
                                                    enableLights(true)
                                                    enableVibration(true)
                                                }
                                                notificationManager.createNotificationChannel(alertChannel)
                                            }

                                            val alertPendingIntent = android.app.PendingIntent.getActivity(
                                                this@KeepAliveService,
                                                20000 + username.hashCode(),
                                                Intent(this@KeepAliveService, com.example.MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                    putExtra("SHOW_TIMER_PAGE", true)
                                                },
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                                } else {
                                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                                }
                                            )

                                            val alertBuilder = NotificationCompat.Builder(this@KeepAliveService, alertChannelId)
                                                .setContentTitle("$peerName started focusing!")
                                                .setContentText("$peerName has started a focusing session$taskText. Join them and complete your goals!")
                                                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                .setCategory(NotificationCompat.CATEGORY_ALARM)
                                                .setDefaults(NotificationCompat.DEFAULT_ALL)
                                                .setContentIntent(alertPendingIntent)
                                                .setAutoCancel(true)

                                            if (!com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
                                                notificationManager.notify(20000 + username.hashCode(), alertBuilder.build())
                                            } else {
                                                Log.i("KeepAliveService", "Muting alert notification during sleep hours.")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // If they are back to/at their cached state, cancel any active debounce job for this user
                            debounceJobs[username]?.cancel()
                        }
                    }
                }

                val focusingPeers = users.filter { (username, user) ->
                    username != currentUsername && 
                    username != "admin" && 
                    user.isFocusing == true
                }.values.toList()
                
                if (focusingPeers.isNotEmpty()) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        9998,
                        Intent(this, com.example.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("SHOW_TIMER_PAGE", true)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                    
                    val title = if (focusingPeers.size == 1) {
                        val peer = focusingPeers.first()
                        val name = cleanName(peer.name ?: peer.nickname ?: "A friend")
                        "$name is focusing!"
                    } else {
                        "Friends are Focusing Live!"
                    }
                    
                    val desc = focusingPeers.joinToString(separator = "\n") { peer ->
                        val name = cleanName(peer.name ?: peer.nickname ?: "Friend")
                        val task = peer.currentTaskTitle?.let { " - $it" } ?: ""
                        "$name$task"
                    }
                    
                    val builder = NotificationCompat.Builder(this, friendsChannelId)
                        .setContentTitle(title)
                        .setContentText(desc)
                        .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                    
                    if (focusingPeers.size > 1) {
                        builder.setStyle(NotificationCompat.BigTextStyle().bigText(desc))
                    }
                    
                    notificationManager.notify(friendsNotificationId, builder.build())

                    // Update the widget
                    val widgetDesc = focusingPeers.joinToString(separator = "   ") { peer ->
                        val emoji = peer.emoji ?: "🎯"
                        val name = peer.name ?: peer.nickname ?: "Friend"
                        "$emoji $name"
                    }
                    com.example.widget.WidgetUpdater.updateFriendsFocusWidget(applicationContext, widgetDesc)
                } else {
                    notificationManager.cancel(friendsNotificationId)
                    com.example.widget.WidgetUpdater.updateFriendsFocusWidget(applicationContext, "No one is focusing")
                }
                
                checkRankChangesAndNotify(users, currentUsername)
            } else {
                notificationManager.cancel(friendsNotificationId)
                com.example.widget.WidgetUpdater.updateFriendsFocusWidget(applicationContext, "No one is focusing")
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Failed to process real-time friends focus status: ${e.message}", e)
        }
    }

    private suspend fun processBellSignal(signal: BellSignal, currentUsername: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!signal.isProcessed && signal.senderUsername != currentUsername) {
            val lastTimestamp = prefs.getLong("last_processed_bell_timestamp", 0L)
            if (signal.timestamp > lastTimestamp) {
                val isSilent = FocusTimerManager.isBellSilentModeEnabled.value
                val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value

                if (!isSilent && !isFocusing) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val bellChannelId = "friends_bell_channel"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val bellChannel = NotificationChannel(
                            bellChannelId,
                            "Friend Bell Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Plays alert when friends ring your focus bell"
                            enableLights(true)
                            enableVibration(true)
                        }
                        notificationManager.createNotificationChannel(bellChannel)
                    }

                    // Trigger corresponding 5-second vibration and tone generator bell alert
                    FocusTimerManager.playFriendReminderBellSound(this@KeepAliveService)

                    val bellPendingIntent = android.app.PendingIntent.getActivity(
                        this@KeepAliveService,
                        9995,
                        Intent(this@KeepAliveService, com.example.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("SHOW_TIMER_PAGE", true)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )

                    val senderName = cleanName(if (!signal.senderDisplayName.isNullOrEmpty()) signal.senderDisplayName else signal.senderUsername)
                    val bellNotificationId = 10003
                    val bellBuilder = NotificationCompat.Builder(this@KeepAliveService, bellChannelId)
                        .setContentTitle("Session Reminder!")
                        .setContentText("$senderName rang the bell to remind you to start your timer!")
                        .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setContentIntent(bellPendingIntent)
                        .setAutoCancel(true)

                    if (!com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
                        notificationManager.notify(bellNotificationId, bellBuilder.build())
                    } else {
                        Log.i("KeepAliveService", "Muting bell signal notification during sleep hours.")
                    }
                }

                // Mark as processed locally and update state on Firebase to prevent repeating
                prefs.edit().putLong("last_processed_bell_timestamp", signal.timestamp).apply()
                
                try {
                    com.example.api.FirebaseClient.api.putBellSignal(currentUsername, signal.copy(isProcessed = true))
                } catch (writeErr: Exception) {
                    Log.e("KeepAliveService", "Failed to mark Firebase bell signal processed: ${writeErr.message}")
                }
            }
        }
    }

    private fun startWaterReminderMonitoring() {
        if (waterReminderJob != null) return
        waterReminderJob = serviceScope.launch {
            while (true) {
                delay(30000) // check every 30 seconds
                try {
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val enabled = prefs.getBoolean("water_reminder_enabled", false)
                    if (enabled) {
                        val intervalMins = prefs.getFloat("water_reminder_interval_mins", 60f)
                        val startTimeStr = prefs.getString("water_reminder_start_time", "08:00") ?: "08:00"
                        val endTimeStr = prefs.getString("water_reminder_end_time", "22:00") ?: "22:00"
                        
                        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                        
                        var isWithinWakeupWindow = false
                        if (startTimeStr <= endTimeStr) {
                            isWithinWakeupWindow = currentTime >= startTimeStr && currentTime <= endTimeStr
                        } else {
                            // Overnight window e.g. 22:00 to 08:00
                            isWithinWakeupWindow = currentTime >= startTimeStr || currentTime <= endTimeStr
                        }
                        
                        if (isWithinWakeupWindow) {
                            val lastMs = prefs.getLong("last_water_reminder_time_ms", 0L)
                            val currentMs = System.currentTimeMillis()
                            val intervalMs = (intervalMins * 60 * 1000L).toLong()
                            
                            if (lastMs == 0L) {
                                prefs.edit().putLong("last_water_reminder_time_ms", currentMs).apply()
                            } else if (currentMs - lastMs >= intervalMs) {
                                triggerWaterReminderNotification()
                                prefs.edit().putLong("last_water_reminder_time_ms", currentMs).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Failed to check water reminder: ${e.message}", e)
                }
            }
        }
    }

    private fun triggerWaterReminderNotification() {
        if (com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
            Log.i("KeepAliveService", "Muting water reminder during sleep hours.")
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "water_reminder_channel",
                "Water Drinking Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to stay hydrated"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 10002, launchIntent, flags)
        
        val notification = NotificationCompat.Builder(this, "water_reminder_channel")
            .setContentTitle("Time to drink water! 💧")
            .setContentText("Keep focused and hydrated! Drink a glass of water now.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
            
        manager.notify(10002, notification)
    }

    private fun checkRankChangesAndNotify(
        users: Map<String, com.example.api.UserRemote>,
        currentUsername: String
    ) {
        try {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            
            val lastRankDate = prefs.getString("last_rank_date", "")
            val isNewDay = lastRankDate != todayStr
            
            val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
            val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
            val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
            val runningSecs = if (isLocalFocusing) {
                val cumSecs = FocusTimerManager.cumulativeSessionFocusSeconds.value
                val swSecs = FocusTimerManager.stopwatchSeconds.value
                if (cumSecs > 0) cumSecs else if (swSecs > 0) swSecs else 0
            } else {
                (FocusTimerManager.accumulatedSessionTimeMs.value / 1000).toInt()
            }
            val myTodaySeconds = completedTodaySecs + pendingSecs + runningSecs
            
            val meRemote = users[currentUsername]
            val myName = meRemote?.nickname ?: meRemote?.name ?: "Bharathikrishna M"
            val myEmoji = meRemote?.emoji ?: "👨‍💻"
            
            val allUsersList = mutableListOf<UserRankInfo>()
            allUsersList.add(UserRankInfo(currentUsername, myName, myEmoji, myTodaySeconds, true))
            
            val currentUnixTime = System.currentTimeMillis() / 1000
            users.forEach { (username, u) ->
                if (username != currentUsername && 
                    username != "admin"
                ) {
                    val nameToShow = u.nickname ?: u.name ?: username
                    val isFocusing = u.isFocusing == true
                    val liveSecs = if (isFocusing && u.lastResumeTimeMs != null) {
                        val currentChunkMs = (currentUnixTime * 1000) - u.lastResumeTimeMs
                        val totalMs = u.accumulatedTimeMs + maxOf(0L, currentChunkMs)
                        (totalMs / 1000).toInt()
                    } else {
                        (u.accumulatedTimeMs / 1000).toInt()
                    }
                    allUsersList.add(UserRankInfo(username, nameToShow, u.emoji ?: "🎯", liveSecs, false))
                }
            }
            
            val sortedList = allUsersList.sortedByDescending { it.focusedSeconds }
            
            var currentRank = 1
            val newRanks = mutableMapOf<String, Int?>()
            for (user in sortedList) {
                if (user.focusedSeconds > 0) {
                    newRanks[user.username] = currentRank
                    currentRank++
                } else {
                    newRanks[user.username] = null
                }
            }
            
            val previousRanks = mutableMapOf<String, Int?>()
            if (!isNewDay) {
                allUsersList.forEach { u ->
                    val rVal = prefs.getInt("rank_val_${u.username}", -1)
                    previousRanks[u.username] = if (rVal != -1) rVal else null
                }
            }
            
            val myPrevRank = previousRanks[currentUsername]
            val myNewRank = newRanks[currentUsername]
            
            var shouldNotify = false
            var crosserName = ""
            
            if (myPrevRank != null && myNewRank != null && myNewRank != myPrevRank) {
                shouldNotify = true
                if (myNewRank > myPrevRank) {
                    val crossers = sortedList.filter { other ->
                        other.username != currentUsername && 
                        newRanks[other.username] != null && 
                        newRanks[other.username]!! < myNewRank &&
                        (previousRanks[other.username] == null || previousRanks[other.username]!! > myPrevRank)
                    }
                    val crosserStr = if (crossers.isNotEmpty()) {
                        " (" + crossers.joinToString(", ") { cleanName(it.displayName) } + " crossed you)"
                    } else ""
                    crosserName = "Your rank dropped to #$myNewRank$crosserStr. Focus now to win back your rank!"
                } else {
                    crosserName = "Your rank improved to #$myNewRank! Great job!"
                }
            }
            
            val editor = prefs.edit()
            editor.putString("last_rank_date", todayStr)
            newRanks.forEach { (username, r) ->
                if (r != null) {
                    editor.putInt("rank_val_$username", r)
                } else {
                    editor.remove("rank_val_$username")
                }
            }
            editor.apply()
            
            if (shouldNotify && !isLocalFocusing) {
                sendRankChangedNotification(crosserName)
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error in checkRankChangesAndNotify: ${e.message}", e)
        }
    }

    private fun sendRankChangedNotification(message: String) {
        if (com.example.util.SleepTimeHelper.isInSleepTime(applicationContext)) {
            Log.i("KeepAliveService", "Muting rank changed notification during sleep hours.")
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val rankChannelId = "friends_rank_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                rankChannelId,
                "Rank Change Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when your leaderboard rank changes"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 10005, launchIntent, flags)
        
        val notification = NotificationCompat.Builder(this, rankChannelId)
            .setContentTitle("Rank Changed!")
            .setContentText(message)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
            
        notificationManager.notify(10005, notification)
    }

    data class UserRankInfo(
        val username: String,
        val displayName: String,
        val emoji: String,
        val focusedSeconds: Int,
        val isMe: Boolean
    )

    private fun startHourlyDatabaseAlignmentCheck() {
        if (databaseAlignmentCheckJob != null) return
        databaseAlignmentCheckJob = serviceScope.launch {
            while (true) {
                try {
                    com.example.util.FocusTimerManager.addSystemLog(
                        applicationContext,
                        "Periodic 30m State Sync Started",
                        "FIREBASE_SYNC",
                        "Initiating automated 1-hour local and online database integrity verification"
                    )
                    com.example.util.FocusTimerManager.performCloudAlignmentCheck(applicationContext)
                } catch (e: Exception) {
                    Log.e("KeepAliveService", "Hourly alignment check failed: ${e.message}", e)
                    com.example.util.FocusTimerManager.addSystemLog(
                        applicationContext,
                        "Periodic State Sync Error",
                        "FIREBASE_SYNC",
                        "Verification error: ${e.message}"
                    )
                }
                delay(1800000L) // 1 hour delay
            }
        }
    }

    private fun cleanName(rawName: String?): String {
        if (rawName == null) return "Friend"
        var clean = rawName
            .replace(Regex("""\[?photo:[^\]]+\]?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""photo:\S*""", RegexOption.IGNORE_CASE), "")
            .trim()
        
        val emojiRegex = Regex(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+|" +
            "[\\u2600-\\u27BF]+|" +
            "[\\uE000-\\uF8FF]+|" +
            "[\\uFE0F]+"
        )
        clean = emojiRegex.replace(clean, "").trim()
        
        if (clean.isBlank()) {
            return "Friend"
        }
        return clean
    }

    override fun onDestroy() {
        Log.d("KeepAliveService", "KeepAliveService destroyed")
        try {
            com.example.api.FirebaseSyncManager.stopListening(applicationContext)
            usersValueEventListener?.let {
                usersDatabaseReference?.removeEventListener(it)
            }
            bellsValueEventListener?.let {
                bellsDatabaseReference?.removeEventListener(it)
            }
        } catch (e: Exception) {
            Log.e("KeepAliveService", "Error removing Firebase listeners in onDestroy: ${e.message}")
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "lifeos_keepalive_service_channel"
        const val NOTIFICATION_ID = 10001

        const val ACTION_PAUSE_TIMER = "com.example.service.ACTION_PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.example.service.ACTION_RESUME_TIMER"
        const val ACTION_RESET_TIMER = "com.example.service.ACTION_RESET_TIMER"

        const val ACTION_PAUSE_STOPWATCH = "com.example.service.ACTION_PAUSE_STOPWATCH"
        const val ACTION_RESUME_STOPWATCH = "com.example.service.ACTION_RESUME_STOPWATCH"
        const val ACTION_RESET_STOPWATCH = "com.example.service.ACTION_RESET_STOPWATCH"
        
        fun start(context: Context) {
            try {
                val intent = Intent(context.applicationContext, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                Log.d("KeepAliveService", "KeepAliveService start command triggered successfully")
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to invoke startForegroundService: ${e.message}")
            }
        }

        fun updateNotification(context: Context) {
            try {
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val isTimerOn = FocusTimerManager.isTimerRunning.value
                val isStopwatchOn = FocusTimerManager.isStopwatchActive.value
                if (!prefs.getBoolean("keep_notification_enabled", true) && !isTimerOn && !isStopwatchOn) {
                    return
                }

                val intent = Intent(context.applicationContext, KeepAliveService::class.java).apply {
                    action = "com.example.service.UPDATE_NOTIFICATION"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("KeepAliveService", "Failed to update notification service: ${e.message}", e)
            }
        }
    }
}
