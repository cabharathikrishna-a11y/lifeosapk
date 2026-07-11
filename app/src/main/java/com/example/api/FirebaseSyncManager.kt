package com.example.api

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseSyncManager {
    
    // We delegate friends' live statuses to FirebaseRepository's combined usersState flow for thin-client compatibility
    val friendsLiveStatus = FirebaseRepository.usersState

    private var database: FirebaseDatabase? = null
    private val activeListeners = mutableMapOf<String, ValueEventListener>()

    private fun getDatabase(context: Context): FirebaseDatabase {
        return database ?: synchronized(this) {
            val db = database ?: run {
                val url = FirebaseConfig.getDatabaseUrl(context)
                val instance = FirebaseDatabase.getInstance(url)
                try {
                    instance.setPersistenceEnabled(true)
                } catch (e: Exception) {
                    // Can throw if initialized more than once, safe to ignore
                }
                instance
            }
            database = db
            db
        }
    }

    fun parseUserSnapshot(snapshot: DataSnapshot): UserRemote? {
        try {
            val profileSnap = snapshot.child("profile")
            val timerSnap = snapshot.child("active_timer")
            val statsSnap = snapshot.child("today_stats")
            val dashboardSnap = snapshot.child("stats_dashboard")

            val profile = if (profileSnap.exists()) {
                UserProfile(
                    name = profileSnap.child("name").getValue(String::class.java) ?: "",
                    nickname = profileSnap.child("nickname").getValue(String::class.java) ?: "",
                    photoUpdatedAt = profileSnap.child("photoUpdatedAt").getValue(Long::class.java) ?: 0L
                )
            } else null

            val timer = if (timerSnap.exists()) {
                ActiveTimer(
                    status = timerSnap.child("status").getValue(String::class.java) ?: "RELAXING",
                    mode = timerSnap.child("mode").getValue(String::class.java) ?: "POMODORO",
                    startTimeMs = timerSnap.child("startTimeMs").getValue(Long::class.java) ?: 0L,
                    targetEndTimeMs = timerSnap.child("targetEndTimeMs").getValue(Long::class.java) ?: 0L,
                    accumulatedFocusMs = timerSnap.child("accumulatedFocusMs").getValue(Long::class.java) ?: 0L,
                    accumulatedBreakMs = timerSnap.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L,
                    timezoneOffsetMinutes = timerSnap.child("timezoneOffsetMinutes").getValue(Int::class.java) ?: 0,
                    taskTitle = timerSnap.child("taskTitle").getValue(String::class.java),
                    tag = timerSnap.child("tag").getValue(String::class.java)
                )
            } else null

            val stats = if (statsSnap.exists()) {
                TodayStats(
                    todayFocusTimeMs = statsSnap.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L,
                    dateString = statsSnap.child("dateString").getValue(String::class.java) ?: ""
                )
            } else null

            val dashboard = if (dashboardSnap.exists()) {
                val todayFocusMs = dashboardSnap.child("todayFocusMs").getValue(Long::class.java) ?: 0L
                val lastSevenDaysMs = dashboardSnap.child("lastSevenDaysMs").getValue(Long::class.java) ?: 0L
                val lastThirtyDaysMs = dashboardSnap.child("lastThirtyDaysMs").getValue(Long::class.java) ?: 0L
                val allTimeMs = dashboardSnap.child("allTimeMs").getValue(Long::class.java) ?: 0L
                val dailyBuckets = mutableMapOf<String, Long>()
                dashboardSnap.child("dailyBuckets").children.forEach { bucket ->
                    val k = bucket.key
                    val v = bucket.getValue(Long::class.java)
                    if (k != null && v != null) {
                        dailyBuckets[k] = v
                    }
                }
                StatsDashboard(
                    todayFocusMs = todayFocusMs,
                    lastSevenDaysMs = lastSevenDaysMs,
                    lastThirtyDaysMs = lastThirtyDaysMs,
                    allTimeMs = allTimeMs,
                    dailyBuckets = dailyBuckets
                )
            } else null

            val isFocusingVal = timer?.status == "FOCUSING"
            val focusStatusVal = timer?.status?.lowercase() ?: "idle"
            val isStopwatchModeVal = timer?.mode == "STOPWATCH"

            return UserRemote(
                password = snapshot.child("password").getValue(String::class.java) ?: "",
                name = profile?.name,
                nickname = profile?.nickname,
                profile = profile,
                activeTimer = timer,
                todayStats = stats,
                stats_dashboard = dashboard,
                emoji = "🎯",
                isFocusing = isFocusingVal,
                focusStatus = focusStatusVal,
                isStopwatchMode = isStopwatchModeVal,
                lastUpdatedTimestamp = snapshot.child("lastUpdatedTimestamp").getValue(Long::class.java),
                lastUpdatedDeviceId = snapshot.child("lastUpdatedDeviceId").getValue(String::class.java)
            )
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Failed to parse UserRemote from snapshot", e)
            return null
        }
    }

    suspend fun fetchUserProfile(context: Context, username: String): UserProfile? {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("profile")
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            continuation.resume(null, onCancellation = null)
                            return
                        }
                        try {
                            val name = snapshot.child("name").getValue(String::class.java) ?: ""
                            val nickname = snapshot.child("nickname").getValue(String::class.java) ?: ""
                            val photoUpdatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java) ?: 0L
                            val profile = UserProfile(name, nickname, photoUpdatedAt)
                            FirebaseRepository.updateUserProfile(username, profile)
                            continuation.resume(profile, onCancellation = null)
                        } catch (e: Exception) {
                            Log.e("FirebaseSyncManager", "Error parsing profile for $username", e)
                            continuation.resume(null, onCancellation = null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null, onCancellation = null)
                    }
                })
            }
        }
    }

    suspend fun fetchActiveTimer(context: Context, username: String): ActiveTimer? {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("active_timer")
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            FirebaseRepository.updateActiveTimer(username, ActiveTimer())
                            continuation.resume(ActiveTimer(), onCancellation = null)
                            return
                        }
                        try {
                            val status = snapshot.child("status").getValue(String::class.java) ?: "RELAXING"
                            val mode = snapshot.child("mode").getValue(String::class.java) ?: "POMODORO"
                            val startTimeMs = snapshot.child("startTimeMs").getValue(Long::class.java) ?: 0L
                            val targetEndTimeMs = snapshot.child("targetEndTimeMs").getValue(Long::class.java) ?: 0L
                            val accumulatedFocusMs = snapshot.child("accumulatedFocusMs").getValue(Long::class.java) ?: 0L
                            val accumulatedBreakMs = snapshot.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L
                            val timezoneOffsetMinutes = snapshot.child("timezoneOffsetMinutes").getValue(Int::class.java) ?: 0
                            val taskTitle = snapshot.child("taskTitle").getValue(String::class.java)
                            val tag = snapshot.child("tag").getValue(String::class.java)
                            val isStopwatchModeVal = snapshot.child("isStopwatchMode").getValue(Boolean::class.java) ?: (mode == "STOPWATCH")
                            
                            val timer = ActiveTimer(status, mode, startTimeMs, targetEndTimeMs, accumulatedFocusMs, accumulatedBreakMs, timezoneOffsetMinutes, taskTitle, tag, isStopwatchModeVal)
                            FirebaseRepository.updateActiveTimer(username, timer)
                            continuation.resume(timer, onCancellation = null)
                        } catch (e: Exception) {
                            Log.e("FirebaseSyncManager", "Error parsing active_timer for $username", e)
                            continuation.resume(null, onCancellation = null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null, onCancellation = null)
                    }
                })
            }
        }
    }

    suspend fun fetchTodayStats(context: Context, username: String): TodayStats? {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("today_stats")
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            FirebaseRepository.updateTodayStats(username, TodayStats())
                            continuation.resume(TodayStats(), onCancellation = null)
                            return
                        }
                        try {
                            val todayFocusTimeMs = snapshot.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L
                            val dateString = snapshot.child("dateString").getValue(String::class.java) ?: ""
                            
                            val stats = TodayStats(todayFocusTimeMs, dateString)
                            FirebaseRepository.updateTodayStats(username, stats)
                            continuation.resume(stats, onCancellation = null)
                        } catch (e: Exception) {
                            Log.e("FirebaseSyncManager", "Error parsing today_stats for $username", e)
                            continuation.resume(null, onCancellation = null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null, onCancellation = null)
                    }
                })
            }
        }
    }

    fun listenToStatsDashboard(context: Context, username: String) {
        val db = getDatabase(context)
        
        // 1. Listen to active_timer
        if (!activeListeners.containsKey("active_timer_$username")) {
            val activeTimerRef = db.getReference("users").child(username).child("active_timer")
            val activeTimerListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        FirebaseRepository.updateActiveTimer(username, ActiveTimer())
                        return
                    }
                    try {
                        val status = snapshot.child("status").getValue(String::class.java) ?: "RELAXING"
                        val mode = snapshot.child("mode").getValue(String::class.java) ?: "POMODORO"
                        val startTimeMs = snapshot.child("startTimeMs").getValue(Long::class.java) ?: 0L
                        val targetEndTimeMs = snapshot.child("targetEndTimeMs").getValue(Long::class.java) ?: 0L
                        val accumulatedFocusMs = snapshot.child("accumulatedFocusMs").getValue(Long::class.java) ?: 0L
                        val accumulatedBreakMs = snapshot.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L
                        val timezoneOffsetMinutes = snapshot.child("timezoneOffsetMinutes").getValue(Int::class.java) ?: 0
                        val taskTitle = snapshot.child("taskTitle").getValue(String::class.java)
                        val tag = snapshot.child("tag").getValue(String::class.java)
                        val isStopwatchModeVal = snapshot.child("isStopwatchMode").getValue(Boolean::class.java) ?: (mode == "STOPWATCH")

                        val timer = ActiveTimer(status, mode, startTimeMs, targetEndTimeMs, accumulatedFocusMs, accumulatedBreakMs, timezoneOffsetMinutes, taskTitle, tag, isStopwatchModeVal)
                        
                        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val currentUsername = prefs.getString("current_username", null)
                        val isMe = (username == currentUsername)

                        if (isMe && com.example.util.FocusTimerManager.isRecentLocalInteraction()) {
                            Log.d("FirebaseSyncManager", "Skipping active_timer repository update for current user due to recent local interaction.")
                        } else {
                            FirebaseRepository.updateActiveTimer(username, timer)
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "Error parsing active_timer for $username", e)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSyncManager", "active_timer listener cancelled for $username", error.toException())
                }
            }
            activeTimerRef.addValueEventListener(activeTimerListener)
            activeListeners["active_timer_$username"] = activeTimerListener
        }

        // 2. Listen to today_stats
        if (!activeListeners.containsKey("today_stats_$username")) {
            val todayStatsRef = db.getReference("users").child(username).child("today_stats")
            val todayStatsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        FirebaseRepository.updateTodayStats(username, TodayStats())
                        return
                    }
                    try {
                        val todayFocusTimeMs = snapshot.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L
                        val dateString = snapshot.child("dateString").getValue(String::class.java) ?: ""

                        val stats = TodayStats(todayFocusTimeMs, dateString)
                        FirebaseRepository.updateTodayStats(username, stats)
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "Error parsing today_stats for $username", e)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSyncManager", "today_stats listener cancelled for $username", error.toException())
                }
            }
            todayStatsRef.addValueEventListener(todayStatsListener)
            activeListeners["today_stats_$username"] = todayStatsListener
        }

        // 3. Listen to stats_dashboard
        if (!activeListeners.containsKey("stats_dashboard_$username")) {
            val statsDashboardRef = db.getReference("users").child(username).child("stats_dashboard")
            val statsDashboardListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        FirebaseRepository.updateStatsDashboard(username, StatsDashboard())
                        return
                    }
                    try {
                        val todayFocusMs = snapshot.child("todayFocusMs").getValue(Long::class.java) ?: 0L
                        val lastSevenDaysMs = snapshot.child("lastSevenDaysMs").getValue(Long::class.java) ?: 0L
                        val lastThirtyDaysMs = snapshot.child("lastThirtyDaysMs").getValue(Long::class.java) ?: 0L
                        val allTimeMs = snapshot.child("allTimeMs").getValue(Long::class.java) ?: 0L
                        val dailyBuckets = mutableMapOf<String, Long>()
                        snapshot.child("dailyBuckets").children.forEach { bucket ->
                            val k = bucket.key
                            val v = bucket.getValue(Long::class.java)
                            if (k != null && v != null) {
                                dailyBuckets[k] = v
                             }
                        }
                        val dashboard = StatsDashboard(todayFocusMs, lastSevenDaysMs, lastThirtyDaysMs, allTimeMs, dailyBuckets)
                        FirebaseRepository.updateStatsDashboard(username, dashboard)
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "Error parsing stats_dashboard for $username", e)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSyncManager", "stats_dashboard listener cancelled for $username", error.toException())
                }
            }
            statsDashboardRef.addValueEventListener(statsDashboardListener)
            activeListeners["stats_dashboard_$username"] = statsDashboardListener
        }

        // 4. Listen to timer_settings
        if (!activeListeners.containsKey("timer_settings_$username")) {
            val timerSettingsRef = db.getReference("users").child(username).child("timer_settings")
            val timerSettingsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    try {
                        val duration = snapshot.child("timerDurationMinutes").getValue(Int::class.java)
                        val breakDuration = snapshot.child("stopwatchBreakDurationMinutes").getValue(Int::class.java)
                        val autoBreak = snapshot.child("autoStartBreak").getValue(Boolean::class.java)
                        val autoPomo = snapshot.child("autoStartPomo").getValue(Boolean::class.java)
                        val autoSwAfterBreak = snapshot.child("autoStartStopwatchAfterBreak").getValue(Boolean::class.java)

                        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        
                        duration?.let {
                            editor.putInt("timer_duration", it)
                            if (username == prefs.getString("current_username", null)) {
                                com.example.util.FocusTimerManager.updateTimerDurationFromCloud(it)
                            }
                        }
                        breakDuration?.let {
                            editor.putInt("stopwatch_break_duration", it)
                            editor.putInt("break_duration", it)
                            if (username == prefs.getString("current_username", null)) {
                                com.example.util.FocusTimerManager.updateStopwatchBreakDurationFromCloud(it)
                            }
                        }
                        autoBreak?.let {
                            editor.putBoolean("timer_autostart_break", it)
                            if (username == prefs.getString("current_username", null)) {
                                com.example.util.FocusTimerManager.updateAutoStartBreakFromCloud(it)
                            }
                        }
                        autoPomo?.let {
                            editor.putBoolean("timer_autostart_pomo", it)
                            if (username == prefs.getString("current_username", null)) {
                                com.example.util.FocusTimerManager.updateAutoStartPomoFromCloud(it)
                            }
                        }
                        autoSwAfterBreak?.let {
                            editor.putBoolean("stopwatch_autostart_after_break", it)
                            if (username == prefs.getString("current_username", null)) {
                                com.example.util.FocusTimerManager.updateAutoStartStopwatchAfterBreakFromCloud(it)
                            }
                        }
                        editor.apply()
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "Error parsing timer_settings for $username", e)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSyncManager", "timer_settings listener cancelled for $username", error.toException())
                }
            }
            timerSettingsRef.addValueEventListener(timerSettingsListener)
            activeListeners["timer_settings_$username"] = timerSettingsListener
        }
    }

    fun stopListening(context: Context) {
        val db = getDatabase(context)
        activeListeners.forEach { (key, listener) ->
            if (key.startsWith("active_timer_")) {
                val username = key.removePrefix("active_timer_")
                db.getReference("users").child(username).child("active_timer").removeEventListener(listener)
            } else if (key.startsWith("today_stats_")) {
                val username = key.removePrefix("today_stats_")
                db.getReference("users").child(username).child("today_stats").removeEventListener(listener)
            } else if (key.startsWith("stats_dashboard_")) {
                val username = key.removePrefix("stats_dashboard_")
                db.getReference("users").child(username).child("stats_dashboard").removeEventListener(listener)
            } else if (key.startsWith("timer_settings_")) {
                val username = key.removePrefix("timer_settings_")
                db.getReference("users").child(username).child("timer_settings").removeEventListener(listener)
            }
        }
        activeListeners.clear()
    }
}
