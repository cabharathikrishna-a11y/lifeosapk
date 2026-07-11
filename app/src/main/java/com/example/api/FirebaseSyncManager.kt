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
        // Return a dummy/empty user or decode from the new segmented profile if needed
        try {
            val profileSnap = snapshot.child("profile")
            val timerSnap = snapshot.child("active_timer")
            val statsSnap = snapshot.child("today_stats")

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
                    accumulatedBreakMs = timerSnap.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L
                )
            } else null

            val stats = if (statsSnap.exists()) {
                TodayStats(
                    todayFocusTimeMs = statsSnap.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L,
                    dateString = statsSnap.child("dateString").getValue(String::class.java) ?: ""
                )
            } else null

            val emoji = snapshot.child("emoji").getValue(String::class.java) ?: "🎯"

            return UserRemote(
                name = profile?.name,
                nickname = profile?.nickname,
                profile = profile,
                activeTimer = timer,
                todayStats = stats,
                emoji = emoji
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
                            val mode = snapshot.child("mode").getValue(String::class.java) ?: "FOCUS"
                            val startTimeMs = snapshot.child("startTimeMs").getValue(Long::class.java) ?: 0L
                            val targetEndTimeMs = snapshot.child("targetEndTimeMs").getValue(Long::class.java) ?: 0L
                            val accumulatedFocusMs = snapshot.child("accumulatedFocusMs").getValue(Long::class.java) ?: 0L
                            val accumulatedBreakMs = snapshot.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L
                            val timezoneOffsetMinutes = snapshot.child("timezoneOffsetMinutes").getValue(Int::class.java) ?: 0
                            
                            val timer = ActiveTimer(status, mode, startTimeMs, targetEndTimeMs, accumulatedFocusMs, accumulatedBreakMs, timezoneOffsetMinutes)
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

    fun listenToActiveTimer(context: Context, username: String) {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("active_timer")
        
        if (activeListeners.containsKey("active_timer_$username")) return

        val listener = object : ValueEventListener {
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
                    
                    val timer = ActiveTimer(status, mode, startTimeMs, targetEndTimeMs, accumulatedFocusMs, accumulatedBreakMs, timezoneOffsetMinutes)
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
                Log.e("FirebaseSyncManager", "ActiveTimer listener cancelled for $username", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        activeListeners["active_timer_$username"] = listener
    }

    fun listenToTodayStats(context: Context, username: String) {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("today_stats")
        
        if (activeListeners.containsKey("today_stats_$username")) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    FirebaseRepository.updateTodayStats(username, TodayStats())
                    return
                }
                try {
                    val todayFocusTimeMs = snapshot.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L
                    val dateString = snapshot.child("dateString").getValue(String::class.java) ?: ""
                    
                    val stats = TodayStats(todayFocusTimeMs, dateString)
                    val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val currentUsername = prefs.getString("current_username", null)
                    val isMe = (username == currentUsername)

                    if (isMe && com.example.util.FocusTimerManager.isRecentLocalInteraction()) {
                        Log.d("FirebaseSyncManager", "Skipping today_stats repository update for current user due to recent local interaction.")
                    } else {
                        FirebaseRepository.updateTodayStats(username, stats)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseSyncManager", "Error parsing today_stats for $username", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSyncManager", "TodayStats listener cancelled for $username", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        activeListeners["today_stats_$username"] = listener
    }

    fun listenToStatsDashboard(context: Context, username: String) {
        val db = getDatabase(context)
        val ref = db.getReference("users").child(username).child("stats_dashboard")
        
        if (activeListeners.containsKey("stats_dashboard_$username")) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    FirebaseRepository.updateActiveTimer(username, ActiveTimer())
                    FirebaseRepository.updateTodayStats(username, TodayStats())
                    return
                }
                try {
                    val status = snapshot.child("status").getValue(String::class.java) ?: "RELAXING"
                    val mode = snapshot.child("mode").getValue(String::class.java) ?: "POMODORO"
                    val startTimeMs = snapshot.child("startTimeMs").getValue(Long::class.java) ?: 0L
                    val targetEndTimeMs = snapshot.child("targetEndTimeMs").getValue(Long::class.java) ?: 0L
                    val accumulatedFocusMs = snapshot.child("accumulatedFocusMs").getValue(Long::class.java) ?: 0L
                    val accumulatedBreakMs = snapshot.child("accumulatedBreakMs").getValue(Long::class.java) ?: 0L
                    
                    val todayFocusTimeMs = snapshot.child("todayFocusTimeMs").getValue(Long::class.java) ?: 0L
                    val dateString = snapshot.child("dateString").getValue(String::class.java) ?: ""

                    val timer = ActiveTimer(status, mode, startTimeMs, targetEndTimeMs, accumulatedFocusMs, accumulatedBreakMs)
                    val stats = TodayStats(todayFocusTimeMs, dateString)

                    val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val currentUsername = prefs.getString("current_username", null)
                    val isMe = (username == currentUsername)

                    if (isMe && com.example.util.FocusTimerManager.isRecentLocalInteraction()) {
                        Log.d("FirebaseSyncManager", "Skipping stats_dashboard repository update for current user due to recent local interaction.")
                    } else {
                        FirebaseRepository.updateActiveTimer(username, timer)
                        FirebaseRepository.updateTodayStats(username, stats)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseSyncManager", "Error parsing stats_dashboard for $username", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSyncManager", "StatsDashboard listener cancelled for $username", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        activeListeners["stats_dashboard_$username"] = listener
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
            }
        }
        activeListeners.clear()
    }
}
