package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.service.KeepAliveService
import com.example.ui.FocusRecord
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineExceptionHandler

object FocusTimerManager {
    private val firebaseSyncMutex = Mutex()
    private val logLock = Any()
    private val recordLock = Any()
    private val initLock = Any()
    private val ONE_HOUR_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(1)
    private val TWELVE_HOURS_SECONDS = java.util.concurrent.TimeUnit.HOURS.toSeconds(12).toInt()

    // System Audit Log definitions
    data class SystemLogEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val event: String,
        val category: String, // e.g. "BUTTON_PRESS", "AUTO_SAVE", "FIREBASE_SYNC", "STATE_RESTORE", "CALCULATION", "ALARM"
        val details: String
    )

    val systemLogs = MutableStateFlow<List<SystemLogEntry>>(emptyList())

    fun addSystemLog(context: Context?, event: String, category: String, details: String) {
        val log = SystemLogEntry(
            event = event,
            category = category,
            details = details
        )
        systemLogs.update { current ->
            val updated = current.toMutableList()
            updated.add(0, log)
            if (updated.size > 200) updated.take(200) else updated
        }

        context?.let { ctx ->
            scope.launch(Dispatchers.IO) {
                synchronized(logLock) {
                    try {
                        val prefs = ctx.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val serialized = systemLogs.value.joinToString("\n") { entry ->
                            val encodedEvent = android.util.Base64.encodeToString(entry.event.toByteArray(), android.util.Base64.NO_WRAP)
                            val encodedDetails = android.util.Base64.encodeToString(entry.details.toByteArray(), android.util.Base64.NO_WRAP)
                            "${entry.id}|${entry.timestamp}|${encodedEvent}|${entry.category}|${encodedDetails}"
                        }
                        prefs.edit().putString("system_logs_serialized2", serialized).apply()
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to save system logs", e)
                    }
                }
            }
        }
    }

    fun getRecentLogsSerialized(context: Context): String {
        return systemLogs.value.take(10).joinToString("\n") { entry ->
            "${entry.timestamp}|${entry.event}|${entry.category}|${entry.details}"
        }
    }

    fun clearSystemLogs(context: Context) {
        systemLogs.value = emptyList()
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("system_logs_serialized2").apply()
    }

    fun loadSystemLogs(context: Context): List<SystemLogEntry> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("system_logs_serialized2", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val id = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                    val event = try {
                        String(android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Event]" }
                    val category = parts[3]
                    val details = try {
                        String(android.util.Base64.decode(parts[4], android.util.Base64.NO_WRAP))
                    } catch (e: Exception) { "[Corrupted Details]" }
                    SystemLogEntry(id, timestamp, event, category, details)
                } else null
            }
        } catch (e: Exception) {
            Log.e("FocusTimerManager", "Failed to load system logs", e)
            emptyList()
        }
    }

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("local_device_id", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("local_device_id", deviceId).apply()
        }
        return deviceId
    }

    fun syncStateToFirebase(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        
        if (isLoggedIn && !isAdmin && currentUsername != null) {
            _isTimerSyncInProgress.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    firebaseSyncMutex.withLock {
                        val isTimerActive = isTimerRunning.value
                        val isSwActive = isStopwatchActive.value
                        val isFocus = isFocusPhase.value
                        val cumSecs = cumulativeSessionFocusSeconds.value
                        val swSecs = stopwatchSeconds.value
                        val attachedTaskTitle = attachedTask.value?.title

                        try {
                            addSystemLog(context, "Firebase Sync Started", "FIREBASE_SYNC", "TimerActive=$isTimerActive, StopwatchActive=$isSwActive, Focus=$isFocus, CumSecs=$cumSecs, SwSecs=$swSecs")
                            val response = com.example.api.FirebaseClient.api.getUser(currentUsername)
                            if (response.isSuccessful) {
                                val baseUser = response.body()
                                if (baseUser != null) {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    val todayStr = sdf.format(java.util.Date())
                                    
                                    val todayRecords = focusRecords.value.filter { r -> r.dateString == todayStr || r.dateString.isEmpty() }

                                    val isRunning = isTimerRunning.value || isStopwatchActive.value
                                    val focusStatus = if (!isFocus) {
                                        "break"
                                    } else if (isRunning) {
                                        "focusing"
                                    } else if (accumulatedSessionTimeMs.value > 0) {
                                        "paused"
                                    } else {
                                        "idle"
                                    }

                                    val activeTimerState = com.example.api.ActiveTimer(
                                        status = if (!isFocus) "BREAK" else if (isTimerRunning.value || isStopwatchActive.value) "FOCUSING" else if (accumulatedSessionTimeMs.value > 0) "PAUSED" else "RELAXING",
                                        mode = if (isSwActive) "STOPWATCH" else "POMODORO",
                                        startTimeMs = if (isRunning) lastResumeTimeMs.value ?: System.currentTimeMillis() else 0L,
                                        targetEndTimeMs = if (isTimerRunning.value && !isSwActive) (lastResumeTimeMs.value ?: System.currentTimeMillis()) + (timerSecondsLeft.value * 1000L) else 0L,
                                        accumulatedFocusMs = if (isFocus) accumulatedSessionTimeMs.value else 0L,
                                        accumulatedBreakMs = if (!isFocus) accumulatedSessionTimeMs.value else 0L,
                                        timezoneOffsetMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 1000)
                                    )

                                    val updatedUser = baseUser.copy(
                                        isFocusing = isRunning,
                                        accumulatedTimeMs = accumulatedSessionTimeMs.value,
                                        lastResumeTimeMs = if (isRunning) lastResumeTimeMs.value else null,
                                        focusStatus = focusStatus,
                                        currentTaskTitle = if (isFocus) attachedTaskTitle else null,
                                        todaysFocusRecords = null,
                                        isStopwatchMode = isSwActive,
                                        lastUpdatedTimestamp = System.currentTimeMillis(),
                                        lastUpdatedDeviceId = getOrCreateDeviceId(context),
                                        lastButtonClicked = null,
                                        lastButtonClickedTimestamp = null,
                                        deviceLogs = getRecentLogsSerialized(context),
                                        activeTimer = activeTimerState
                                    )
                                    com.example.api.FirebaseClient.api.putUser(currentUsername, updatedUser)
                                    addSystemLog(context, "Firebase Sync Success", "FIREBASE_SYNC", "User state updated: status=$focusStatus, accumulatedTime=${accumulatedSessionTimeMs.value}")
                                    try {
                                        com.example.widget.WidgetUpdater.updateAllWidgets(context)
                                    } catch (we: Exception) {
                                        Log.e("FocusTimerManager", "Widget update failed during firebase sync", we)
                                    }
                                }
                            } else {
                                addSystemLog(context, "Firebase Sync Failed", "FIREBASE_SYNC", "Server returned error code: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            addSystemLog(context, "Firebase Sync Error", "FIREBASE_SYNC", "Error: ${e.message}")
                        }
                    }
                } finally {
                    _isTimerSyncInProgress.value = false
                }
            }
        }
    }

    fun performCloudAlignmentCheck(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        
        if (!isLoggedIn || isAdmin || currentUsername == null) {
            return
        }

        if (isRecentLocalInteraction()) {
            Log.d("FocusTimerManager", "Skipping performCloudAlignmentCheck due to very recent local user interaction.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val response = com.example.api.FirebaseClient.api.getUser(currentUsername)
                if (response.isSuccessful) {
                    val baseUser = response.body()
                    if (baseUser != null) {
                        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                        
                        // 1. Align activeTimer state
                        withContext(Dispatchers.Main) {
                            val activeTimer = baseUser.activeTimer
                            if (activeTimer == null || activeTimer.status == "RELAXING") {
                                if (_isTimerRunning.value) {
                                    resetTimer(context, saveSession = false)
                                }
                                if (_isStopwatchActive.value) {
                                    resetStopwatch(context, saveSession = false)
                                }
                            } else {
                                val isStopwatch = activeTimer.mode == "STOPWATCH"
                                val elapsedSeconds = ((activeTimer.accumulatedFocusMs + (System.currentTimeMillis() - activeTimer.startTimeMs)) / 1000).toInt()
                                
                                when (activeTimer.status) {
                                    "FOCUSING" -> {
                                        _isFocusPhase.value = true
                                        if (isStopwatch) {
                                            if (_isTimerRunning.value) {
                                                pauseTimer(context, updateButton = false)
                                            }
                                            _accumulatedSessionTimeMs.value = activeTimer.accumulatedFocusMs
                                            _lastResumeTimeMs.value = activeTimer.startTimeMs
                                            
                                            val localSecs = _stopwatchSeconds.value
                                            if (Math.abs(localSecs - elapsedSeconds) > 3) {
                                                _stopwatchSeconds.value = elapsedSeconds
                                            }
                                            if (!_isStopwatchActive.value) {
                                                startStopwatch(context, stopActiveAlarm = false)
                                            }
                                        } else {
                                            if (_isStopwatchActive.value) {
                                                pauseStopwatch(context, stopActiveAlarm = false, updateButton = false)
                                            }
                                            _accumulatedSessionTimeMs.value = activeTimer.accumulatedFocusMs
                                            _lastResumeTimeMs.value = activeTimer.startTimeMs
                                            
                                            val timerDurationSecs = _timerDurationMinutes.value * 60
                                            val secondsLeft = (timerDurationSecs - elapsedSeconds).coerceAtLeast(0)
                                            
                                            val localLeft = _timerSecondsLeft.value
                                            if (Math.abs(localLeft - secondsLeft) > 3) {
                                                _timerSecondsLeft.value = secondsLeft
                                            }
                                            if (!_isTimerRunning.value) {
                                                startTimer(context, stopActiveAlarm = false, updateButton = false, forceFocusTab = false)
                                            }
                                        }
                                    }
                                    "BREAK" -> {
                                        _isFocusPhase.value = false
                                        val breakDurationMins = prefs.getInt("break_duration", 5)
                                        val breakSecsTotal = breakDurationMins * 60
                                        val breakSecondsLeft = (breakSecsTotal - elapsedSeconds).coerceAtLeast(0)
                                        
                                        _accumulatedSessionTimeMs.value = activeTimer.accumulatedBreakMs
                                        _lastResumeTimeMs.value = activeTimer.startTimeMs
                                        
                                        val localLeft = _timerSecondsLeft.value
                                        if (Math.abs(localLeft - breakSecondsLeft) > 3) {
                                            _timerSecondsLeft.value = breakSecondsLeft
                                        }
                                        if (!_isTimerRunning.value) {
                                            startTimer(context, stopActiveAlarm = false, updateButton = false, forceFocusTab = false)
                                        }
                                    }
                                    "PAUSED" -> {
                                        _accumulatedSessionTimeMs.value = activeTimer.accumulatedFocusMs
                                        _lastResumeTimeMs.value = null
                                        if (isStopwatch) {
                                            _stopwatchSeconds.value = (activeTimer.accumulatedFocusMs / 1000).toInt()
                                            if (_isStopwatchActive.value) {
                                                pauseStopwatch(context, stopActiveAlarm = false, updateButton = false)
                                            }
                                        } else {
                                            val timerDurationSecs = _timerDurationMinutes.value * 60
                                            val elapsedSecs = (activeTimer.accumulatedFocusMs / 1000).toInt()
                                            _timerSecondsLeft.value = (timerDurationSecs - elapsedSecs).coerceAtLeast(0)
                                            if (_isTimerRunning.value) {
                                                pauseTimer(context, updateButton = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Overwrite today's focus records from Firebase directly
                        baseUser.todaysFocusRecords?.let { remoteRecords ->
                            val sortedRecords = remoteRecords.sortedByDescending { it.startTime }
                            
                            // Update StateFlow
                            withContext(Dispatchers.Main) {
                                _focusRecords.value = sortedRecords
                            }
                            
                            // Save to SharedPreferences
                            val serialized = sortedRecords.joinToString("\n") { 
                                val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
                                "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}" 
                            }
                            prefs.edit().putString("focus_records_list", serialized).apply()
                            
                            // Save to Room Database safely
                            val db = com.example.data.AppDatabase.getInstance(context)
                            db.focusRecordDao().deleteRecordsForDate(todayStr)
                            for (rec in sortedRecords) {
                                val entity = com.example.data.FocusRecordEntity(
                                    taskTitle = rec.taskTitle,
                                    tag = rec.tag ?: "",
                                    notes = rec.notes,
                                    durationSeconds = rec.durationSeconds,
                                    durationMinutes = rec.durationMinutes,
                                    dateString = rec.dateString,
                                    startTime = rec.startTime,
                                    endTime = rec.endTime,
                                    timestamp = System.currentTimeMillis()
                                )
                                db.focusRecordDao().insertRecord(entity)
                            }
                            
                            val todayRecsNum = sortedRecords.filter { it.dateString == todayStr }.size
                            val totalMins = sortedRecords.sumOf { it.durationMinutes }
                            
                            withContext(Dispatchers.Main) {
                                _todayPomosCount.value = todayRecsNum
                                _totalFocusMinutes.value = totalMins
                            }
                            
                            prefs.edit()
                                .putInt("today_pomos_count", todayRecsNum)
                                .putInt("total_focus_minutes", totalMins)
                                .apply()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusTimerManager", "Error in performCloudAlignmentCheck", e)
            }
        }
    }

    // High-precision tracking variable for Doze-mode immune relative elapsed time
    private var lastResumeElapsedRealtime: Long? = null

    // Unbreakable anchors for hardware uptime, matching the requested new FocusTimerManager format
    var activeSessionStartRealtimeMs: Long
        get() = lastResumeElapsedRealtime ?: 0L
        set(value) {
            lastResumeElapsedRealtime = if (value == 0L) null else value
        }

    var baseAccumulatedSeconds: Int
        get() = (_accumulatedSessionTimeMs.value / 1000).toInt()
        set(value) {
            _accumulatedSessionTimeMs.value = value * 1000L
        }

    private var uiTickJob: android.os.AsyncTask<Void, Void, Void>? = null // Dummy or keep Job
    private var actualUiTickJob: kotlinx.coroutines.Job? = null
    private val stateMutex = kotlinx.coroutines.sync.Mutex()

    // Current Active States (Encapsulated backing fields)
    private val _accumulatedSessionTimeMs = MutableStateFlow(0L)
    val accumulatedSessionTimeMs: StateFlow<Long> = _accumulatedSessionTimeMs.asStateFlow()

    private val _lastResumeTimeMs = MutableStateFlow<Long?>(null)
    val lastResumeTimeMs: StateFlow<Long?> = _lastResumeTimeMs.asStateFlow()

    private val _timerSecondsLeft = MutableStateFlow(25 * 60)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft.asStateFlow()

    private val _timerDurationMinutes = MutableStateFlow(25)
    val timerDurationMinutes: StateFlow<Int> = _timerDurationMinutes.asStateFlow()
    
    private val _pendingFocusReview = MutableStateFlow<FocusRecord?>(null)
    val pendingFocusReview: StateFlow<FocusRecord?> = _pendingFocusReview.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _isFocusPhase = MutableStateFlow(true)
    val isFocusPhase: StateFlow<Boolean> = _isFocusPhase.asStateFlow()

    private val _attachedTask = MutableStateFlow<Task?>(null)
    val attachedTask: StateFlow<Task?> = _attachedTask.asStateFlow()

    private val _attachedTag = MutableStateFlow<String>("")
    val attachedTag: StateFlow<String> = _attachedTag.asStateFlow()

    private val _focusTags = MutableStateFlow<List<String>>(emptyList())
    val focusTags: StateFlow<List<String>> = _focusTags.asStateFlow()

    private val _cumulativeSessionFocusSeconds = MutableStateFlow(0)
    val cumulativeSessionFocusSeconds: StateFlow<Int> = _cumulativeSessionFocusSeconds.asStateFlow()

    // Global verification/completion dialog states
    private val _showGlobalVerificationDialog = MutableStateFlow(false)
    val showGlobalVerificationDialog: StateFlow<Boolean> = _showGlobalVerificationDialog.asStateFlow()

    private val _globalVerificationFocusedTimeSeconds = MutableStateFlow(0)
    val globalVerificationFocusedTimeSeconds: StateFlow<Int> = _globalVerificationFocusedTimeSeconds.asStateFlow()

    private val _globalVerificationRevisedTotalMinutes = MutableStateFlow(0)
    val globalVerificationRevisedTotalMinutes: StateFlow<Int> = _globalVerificationRevisedTotalMinutes.asStateFlow()

    private val _globalVerificationRevisedTotalSeconds = MutableStateFlow(0)
    val globalVerificationRevisedTotalSeconds: StateFlow<Int> = _globalVerificationRevisedTotalSeconds.asStateFlow()

    // Session Verification & Break tracking variables
    private val _currentSessionStartMs = MutableStateFlow<Long?>(null)
    val currentSessionStartMs: StateFlow<Long?> = _currentSessionStartMs.asStateFlow()

    private val _currentSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    val currentSessionPauseRanges: StateFlow<List<Pair<Long, Long>>> = _currentSessionPauseRanges.asStateFlow()

    var tempPauseStartMs: Long? = null

    private val _verifiedSessionStartMs = MutableStateFlow<Long?>(null)
    val verifiedSessionStartMs: StateFlow<Long?> = _verifiedSessionStartMs.asStateFlow()

    private val _verifiedSessionPauseRanges = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    val verifiedSessionPauseRanges: StateFlow<List<Pair<Long, Long>>> = _verifiedSessionPauseRanges.asStateFlow()

    fun recordSessionStart() {
        if (_currentSessionStartMs.value == null) {
            _currentSessionStartMs.value = StableTime.currentTimeMillis()
        }
        if (tempPauseStartMs != null) {
            val pauseStart = tempPauseStartMs!!
            val pauseEnd = StableTime.currentTimeMillis()
            _currentSessionPauseRanges.value = _currentSessionPauseRanges.value + Pair(pauseStart, pauseEnd)
            tempPauseStartMs = null
        }
    }

    fun recordSessionPause() {
        if (tempPauseStartMs == null) {
            tempPauseStartMs = StableTime.currentTimeMillis()
        }
    }

    fun recordSessionCompleteOrReset(isSaving: Boolean) {
        if (isSaving) {
            _verifiedSessionStartMs.value = _currentSessionStartMs.value
            if (tempPauseStartMs != null) {
                val finalPauseRange = Pair(tempPauseStartMs!!, StableTime.currentTimeMillis())
                _verifiedSessionPauseRanges.value = _currentSessionPauseRanges.value + finalPauseRange
            } else {
                _verifiedSessionPauseRanges.value = _currentSessionPauseRanges.value
            }
        }
        // Always reset current session tracking after transferring (or if not saving)
        _currentSessionStartMs.value = null
        _currentSessionPauseRanges.value = emptyList()
        tempPauseStartMs = null
    }

    // Stopwatch Active States (Encapsulated)
    private val _lastLocalInteractionTimestamp = MutableStateFlow(0L)
    val lastLocalInteractionTimestamp: StateFlow<Long> = _lastLocalInteractionTimestamp.asStateFlow()

    private val _lastButtonClicked = MutableStateFlow<String?>(null)
    val lastButtonClicked: StateFlow<String?> = _lastButtonClicked.asStateFlow()

    private val _lastButtonClickedTimestamp = MutableStateFlow<Long>(0L)
    val lastButtonClickedTimestamp: StateFlow<Long> = _lastButtonClickedTimestamp.asStateFlow()

    fun updateLocalInteractionTimestamp() {
        val now = StableTime.currentTimeMillis()
        _lastLocalInteractionTimestamp.value = now
    }

    fun isRecentLocalInteraction(thresholdMs: Long = 4000L): Boolean {
        val lastInteract = _lastLocalInteractionTimestamp.value
        return (StableTime.currentTimeMillis() - lastInteract) < thresholdMs
    }

    fun updateLastButtonClicked(action: String, timestamp: Long = StableTime.currentTimeMillis()) {
        _lastButtonClicked.value = action
        _lastButtonClickedTimestamp.value = timestamp
        _lastLocalInteractionTimestamp.value = timestamp
    }

    private val _isTimerSyncInProgress = MutableStateFlow(false)
    val isTimerSyncInProgress: StateFlow<Boolean> = _isTimerSyncInProgress.asStateFlow()

    private val _stopwatchSeconds = MutableStateFlow(0)
    val stopwatchSeconds: StateFlow<Int> = _stopwatchSeconds.asStateFlow()

    private val _isStopwatchActive = MutableStateFlow(false)
    val isStopwatchActive: StateFlow<Boolean> = _isStopwatchActive.asStateFlow()

    private val _stopwatchLimitReached = MutableStateFlow(false)
    val stopwatchLimitReached: StateFlow<Boolean> = _stopwatchLimitReached.asStateFlow()

    private val _isTabFocusTimerSelected = MutableStateFlow(false)
    val isTabFocusTimerSelected: StateFlow<Boolean> = _isTabFocusTimerSelected.asStateFlow()

    private val _stopwatchBreakDurationMinutes = MutableStateFlow(5)
    val stopwatchBreakDurationMinutes: StateFlow<Int> = _stopwatchBreakDurationMinutes.asStateFlow()

    private val _autoStartStopwatchAfterBreak = MutableStateFlow(true)
    val autoStartStopwatchAfterBreak: StateFlow<Boolean> = _autoStartStopwatchAfterBreak.asStateFlow()

    private val _wasStartedFromStopwatch = MutableStateFlow(false)
    val wasStartedFromStopwatch: StateFlow<Boolean> = _wasStartedFromStopwatch.asStateFlow()

    // User Stats States (Encapsulated)
    private val _todayPomosCount = MutableStateFlow(0)
    val todayPomosCount: StateFlow<Int> = _todayPomosCount.asStateFlow()

    private val _totalFocusMinutes = MutableStateFlow(0)
    val totalFocusMinutes: StateFlow<Int> = _totalFocusMinutes.asStateFlow()

    private val _focusRecords = MutableStateFlow<List<FocusRecord>>(emptyList())
    val focusRecords: StateFlow<List<FocusRecord>> = _focusRecords.asStateFlow()

    private val _liveGrandTotalSeconds = MutableStateFlow(0)
    val liveGrandTotalSeconds: StateFlow<Int> = _liveGrandTotalSeconds.asStateFlow()

    // Option toggles (Encapsulated)
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _isBellSilentModeEnabled = MutableStateFlow(false)
    val isBellSilentModeEnabled: StateFlow<Boolean> = _isBellSilentModeEnabled.asStateFlow()

    private val _autoStartBreak = MutableStateFlow(true)
    val autoStartBreak: StateFlow<Boolean> = _autoStartBreak.asStateFlow()

    private val _autoStartPomo = MutableStateFlow(true)
    val autoStartPomo: StateFlow<Boolean> = _autoStartPomo.asStateFlow()

    // Controlled Mutator Functions for Encapsulation
    fun setAccumulatedSessionTimeMs(value: Long) {
        _accumulatedSessionTimeMs.value = value
    }

    fun setLastResumeTimeMs(value: Long?) {
        _lastResumeTimeMs.value = value
        if (value != null) {
            val diff = StableTime.currentTimeMillis() - value
            lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime() - diff
        } else {
            lastResumeElapsedRealtime = null
        }
    }

    fun getCurrentChunkMs(): Long {
        return lastResumeElapsedRealtime?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
    }

    fun setTimerSecondsLeft(value: Int) {
        _timerSecondsLeft.value = value
    }

    fun setTimerDurationMinutes(value: Int) {
        _timerDurationMinutes.value = value
    }

    fun setPendingFocusReview(value: FocusRecord?) {
        _pendingFocusReview.value = value
    }

    fun setFocusPhase(value: Boolean) {
        _isFocusPhase.value = value
    }

    fun setAttachedTask(value: Task?) {
        _attachedTask.value = value
    }

    fun setAttachedTag(value: String) {
        _attachedTag.value = value
    }

    fun setFocusTags(value: List<String>) {
        _focusTags.value = value
    }

    fun setCumulativeSessionFocusSeconds(value: Int) {
        _cumulativeSessionFocusSeconds.value = value
    }

    fun setShowGlobalVerificationDialog(value: Boolean) {
        _showGlobalVerificationDialog.value = value
    }

    fun setGlobalVerificationFocusedTimeSeconds(value: Int) {
        _globalVerificationFocusedTimeSeconds.value = value
    }

    fun setGlobalVerificationRevisedTotalMinutes(value: Int) {
        _globalVerificationRevisedTotalMinutes.value = value
    }

    fun setGlobalVerificationRevisedTotalSeconds(value: Int) {
        _globalVerificationRevisedTotalSeconds.value = value
    }

    fun setCurrentSessionStartMs(value: Long?) {
        _currentSessionStartMs.value = value
    }

    fun setCurrentSessionPauseRanges(value: List<Pair<Long, Long>>) {
        _currentSessionPauseRanges.value = value
    }

    fun setVerifiedSessionStartMs(value: Long?) {
        _verifiedSessionStartMs.value = value
    }

    fun setVerifiedSessionPauseRanges(value: List<Pair<Long, Long>>) {
        _verifiedSessionPauseRanges.value = value
    }

    fun setStopwatchSeconds(value: Int) {
        _stopwatchSeconds.value = value
    }

    fun setStopwatchActive(value: Boolean) {
        _isStopwatchActive.value = value
    }

    fun setStopwatchLimitReached(value: Boolean) {
        _stopwatchLimitReached.value = value
    }

    fun setTabFocusTimerSelected(value: Boolean) {
        _isTabFocusTimerSelected.value = value
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("timer_is_tab_focus_selected", value).apply()
        }
    }

    fun setStopwatchBreakDurationMinutes(value: Int) {
        _stopwatchBreakDurationMinutes.value = value
    }

    fun setAutoStartStopwatchAfterBreak(value: Boolean) {
        _autoStartStopwatchAfterBreak.value = value
    }

    fun setWasStartedFromStopwatch(value: Boolean) {
        _wasStartedFromStopwatch.value = value
    }

    fun setTodayPomosCount(value: Int) {
        _todayPomosCount.value = value
    }

    fun setTotalFocusMinutes(value: Int) {
        _totalFocusMinutes.value = value
    }

    fun setFocusRecords(value: List<FocusRecord>) {
        _focusRecords.value = value
    }

    fun setSoundEnabled(value: Boolean) {
        _soundEnabled.value = value
    }

    fun setIsBellSilentModeEnabled(value: Boolean) {
        _isBellSilentModeEnabled.value = value
    }

    fun setAutoStartBreak(value: Boolean) {
        _autoStartBreak.value = value
    }

    fun setAutoStartPomo(value: Boolean) {
        _autoStartPomo.value = value
    }

    // UI context flags
    var isTimerScreenActive = false
    var appIsBackgrounded = false

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("FocusTimerManager", "Uncaught exception in timer scope: ${exception.message}", exception)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    private var timerJob: Job? = null
    private var stopwatchJob: Job? = null
    private var alarmJob: Job? = null

    // Window overlay objects
    private var overlayView: View? = null
    private var tvTimerText: TextView? = null
    private var tvCollapsedArrow: TextView? = null
    private var tvEndBtn: TextView? = null
    private var tvPauseBtn: TextView? = null
    private var windowManager: WindowManager? = null

    private var isOverlayCollapsed = false
    private var overlayCollapsedSide = "none" // "none", "left", "right"
    private var areOverlayControlsVisible = false
    private var overlayAutoHideJob: Job? = null
    private var lastOverlayX = 150
    private var lastOverlayY = 150

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null

    fun saveActiveSessionState(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_is_running", isTimerRunning.value)
            .putLong("accumulated_time_ms", accumulatedSessionTimeMs.value)
            .putLong("last_resume_time_ms", lastResumeTimeMs.value ?: -1L)
            .putLong("timer_session_start_ms", currentSessionStartMs.value ?: -1L)
            .putInt("timer_cumulative_seconds", cumulativeSessionFocusSeconds.value)
            .putBoolean("timer_is_focus_phase", isFocusPhase.value)
            .putBoolean("timer_is_stopwatch_active", isStopwatchActive.value)
            .putBoolean("timer_was_started_from_stopwatch", wasStartedFromStopwatch.value)
            .putInt("timer_attached_task_id", attachedTask.value?.id ?: -1)
            .putString("timer_attached_tag", attachedTag.value)
            .putLong("timer_last_active_timestamp", StableTime.currentTimeMillis())
            .putInt("timer_seconds_left", timerSecondsLeft.value)
            .putBoolean("timer_is_tab_focus_selected", isTabFocusTimerSelected.value)
            .apply()
    }

    fun persistStateToDisk(context: Context) {
        saveActiveSessionState(context)
    }

    private fun android.content.SharedPreferences.getSafeLong(key: String, defValue: Long): Long {
        try {
            val allMap = all
            if (allMap == null || !allMap.containsKey(key)) return defValue
            val raw = allMap[key] ?: return defValue
            if (raw is Long) return raw
            if (raw is Int) return raw.toLong()
            if (raw is Float) return raw.toLong()
            if (raw is Double) return raw.toLong()
            if (raw is Number) return raw.toLong()
            if (raw is String) return raw.toLongOrNull() ?: defValue
            return defValue
        } catch (e: Exception) {
            return defValue
        }
    }

    fun restoreStateFromDisk(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _isTimerRunning.value = prefs.getBoolean("timer_is_running", false)
        _isStopwatchActive.value = prefs.getBoolean("timer_is_stopwatch_active", false)
        lastResumeElapsedRealtime = prefs.getSafeLong("last_resume_time_ms", -1L).let { if (it == -1L) null else it }
        _accumulatedSessionTimeMs.value = prefs.getSafeLong("accumulated_time_ms", 0L)
        _isFocusPhase.value = prefs.getBoolean("timer_is_focus_phase", true)
        _isTabFocusTimerSelected.value = prefs.getBoolean("timer_is_tab_focus_selected", true)
    }

    fun recoverAndResumeActiveSession(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        val savedIsRunning = prefs.getBoolean("timer_is_running", false)
        val savedIsFocusPhase = prefs.getBoolean("timer_is_focus_phase", true)
        val savedIsStopwatchActive = prefs.getBoolean("timer_is_stopwatch_active", false)
        val savedWasStartedFromStopwatch = prefs.getBoolean("timer_was_started_from_stopwatch", false)
        val savedAttachedTaskId = prefs.getInt("timer_attached_task_id", -1)
        _attachedTag.value = prefs.getString("timer_attached_tag", "") ?: ""
        val savedIsTabFocusTimerSelected = prefs.getBoolean("timer_is_tab_focus_selected", true)
        _isTabFocusTimerSelected.value = savedIsTabFocusTimerSelected
        
        val savedAccumulated = prefs.getSafeLong("accumulated_time_ms", 0L)
        val savedLastResume = prefs.getSafeLong("last_resume_time_ms", -1L)
        val savedSessionStart = prefs.getSafeLong("timer_session_start_ms", -1L)
        val savedLastActiveTimestamp = prefs.getSafeLong("timer_last_active_timestamp", -1L)
        
        _accumulatedSessionTimeMs.value = savedAccumulated
        setLastResumeTimeMs(if (savedLastResume != -1L) savedLastResume else null)
        _currentSessionStartMs.value = if (savedSessionStart != -1L) savedSessionStart else null
        
        _isFocusPhase.value = savedIsFocusPhase
        _wasStartedFromStopwatch.value = savedWasStartedFromStopwatch
        
        if (savedIsRunning) {
            if (savedIsFocusPhase) {
                // Pomodoro Focus Phase Recovery
                if (savedLastResume != -1L) {
                    val elapsedBackgroundMs = StableTime.currentTimeMillis() - savedLastResume
                    val totalElapsedMs = savedAccumulated + elapsedBackgroundMs
                    val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L
                    
                    if (totalElapsedMs >= totalDurationMs) {
                        // Completed in background
                        _accumulatedSessionTimeMs.value = totalDurationMs
                        _timerSecondsLeft.value = 0
                        _isTimerRunning.value = false
                        handlePhaseCompletion(appContext, completedFocusPhase = true)
                    } else {
                        // Still running
                        _accumulatedSessionTimeMs.value = totalElapsedMs
                        _timerSecondsLeft.value = ((totalDurationMs - totalElapsedMs) / 1000).toInt()
                        _isTimerRunning.value = false
                        // Set last resume to now so current chunk starts from 0 again
                        setLastResumeTimeMs(StableTime.currentTimeMillis())
                        startTimer(appContext, stopActiveAlarm = false)
                    }
                } else {
                    _isTimerRunning.value = false
                    startTimer(appContext, stopActiveAlarm = false)
                }
            } else {
                // Pomodoro Break Phase Recovery
                val savedSecondsLeft = prefs.getInt("timer_seconds_left", -1)
                val elapsedSeconds = if (savedLastActiveTimestamp != -1L) {
                    ((StableTime.currentTimeMillis() - savedLastActiveTimestamp) / 1000).toInt()
                } else {
                    0
                }
                val actualSecondsLeft = if (savedSecondsLeft != -1) {
                    maxOf(0, savedSecondsLeft - elapsedSeconds)
                } else {
                    val bMins = prefs.getInt("break_duration", 5)
                    maxOf(0, (bMins * 60) - elapsedSeconds)
                }
                
                if (actualSecondsLeft <= 0) {
                    _timerSecondsLeft.value = 0
                    _isTimerRunning.value = false
                    handlePhaseCompletion(appContext, completedFocusPhase = false)
                } else {
                    _timerSecondsLeft.value = actualSecondsLeft
                    _isTimerRunning.value = false
                    startTimer(appContext, stopActiveAlarm = false)
                }
            }
        } else {
            _isTimerRunning.value = false
            val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L
            _timerSecondsLeft.value = maxOf(0, ((totalDurationMs - _accumulatedSessionTimeMs.value) / 1000).toInt())
        }
        
        if (savedIsStopwatchActive) {
            if (savedLastResume != -1L) {
                val elapsedBackgroundMs = StableTime.currentTimeMillis() - savedLastResume
                _accumulatedSessionTimeMs.value = savedAccumulated + elapsedBackgroundMs
                _stopwatchSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
                _isStopwatchActive.value = false
                // Set last resume to now so current chunk starts from 0 again
                setLastResumeTimeMs(StableTime.currentTimeMillis())
                startStopwatch(appContext, stopActiveAlarm = false)
            } else {
                _isStopwatchActive.value = false
                startStopwatch(appContext, stopActiveAlarm = false)
            }
        } else {
            _isStopwatchActive.value = false
            _stopwatchSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
        }
        
        if (savedAttachedTaskId != -1) {
            scope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(appContext)
                    val task = db.taskDao().getTaskById(savedAttachedTaskId)
                    launch(Dispatchers.Main) {
                        _attachedTask.value = task
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            _attachedTask.value = null
        }
        
        addSystemLog(appContext, "Session State Recovered Dynamically", "STATE_RESTORE", "TimerRunning=$savedIsRunning, StopwatchActive=$savedIsStopwatchActive, AccumulatedTimeMs=${accumulatedSessionTimeMs.value}")
    }

    fun init(context: Context) {
        if (isInitialized) {
            if (appContext == null) appContext = context.applicationContext
            return
        }
        synchronized(initLock) {
            if (isInitialized) {
                if (appContext == null) appContext = context.applicationContext
                return
            }
            isInitialized = true
            appContext = context.applicationContext
            systemLogs.value = loadSystemLogs(context)
            addSystemLog(context, "System Core Initialized", "SYSTEM", "Loaded ${systemLogs.value.size} persisted audit logs")
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            _timerDurationMinutes.value = prefs.getInt("timer_duration", 25)
            
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val lastResetDate = prefs.getString("last_midnight_reset_date", "")
            if (lastResetDate != todayStr) {
                _todayPomosCount.value = 0
                prefs.edit()
                    .putInt("today_pomos_count", 0)
                    .putString("last_midnight_reset_date", todayStr)
                    .putBoolean("needs_firebase_midnight_reset", true)
                    .apply()
            } else {
                _todayPomosCount.value = prefs.getInt("today_pomos_count", 0)
            }
            _totalFocusMinutes.value = prefs.getInt("total_focus_minutes", 0)
            _focusRecords.value = loadFocusRecords(context)
            _soundEnabled.value = prefs.getBoolean("timer_sound_enabled", true)
            _isBellSilentModeEnabled.value = prefs.getBoolean("bell_silent_mode_enabled", false)
            _autoStartBreak.value = prefs.getBoolean("timer_autostart_break", true)
            _autoStartPomo.value = prefs.getBoolean("timer_autostart_pomo", true)
            _stopwatchBreakDurationMinutes.value = prefs.getInt("stopwatch_break_duration", 5)
            _autoStartStopwatchAfterBreak.value = prefs.getBoolean("stopwatch_autostart_after_break", true)
            
            // Recover Active Session State Dynamically
            _focusTags.value = loadFocusTags(context)
            recoverAndResumeActiveSession(context)

        // Hourly Google Drive Sync Job
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    // Wait for 1 hour
                    delay(ONE_HOUR_MS)
                    
                    if (GoogleDriveSyncManager.hasDrivePermission(context)) {
                        Log.d("FocusTimerManager", "Starting hourly automatic Google Drive sync...")
                        val (success, msg) = GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Hourly Google Drive backup outcome: success=$success, msg=$msg")
                        addSystemLog(context, "Hourly Google Drive Sync", "AUTO_SAVE", "Outcome: success=$success, msg=$msg")
                    } else {
                        Log.d("FocusTimerManager", "Skipping hourly Google Drive sync: Permission not granted yet.")
                    }
                } catch (e: Exception) {
                    Log.e("FocusTimerManager", "Error in hourly Google Drive sync job: ${e.message}", e)
                }
            }
        }
        startUiTickLoop()
    }
}

    private fun startUiTickLoop() {
        scope.launch(Dispatchers.Main) {
            val context = appContext ?: return@launch
            while (true) {
                try {
                    recalculateGrandTotalSeconds(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000L)
            }
        }
    }

    fun getLiveActiveSessionSeconds(): Int {
        val isSwActive = _isStopwatchActive.value
        val isTimerActive = _isTimerRunning.value
        val isFocus = _isFocusPhase.value
        
        if (!isFocus) return 0 // Breaks don't count towards focus time
        
        return if (isSwActive) {
            val currentChunk = getCurrentChunkMs()
            val totalMs = _accumulatedSessionTimeMs.value + currentChunk
            (totalMs / 1000).toInt()
        } else if (isTimerActive) {
            val currentChunk = getCurrentChunkMs()
            val totalMs = _accumulatedSessionTimeMs.value + currentChunk
            (totalMs / 1000).toInt()
        } else {
            val accumulatedSecs = (_accumulatedSessionTimeMs.value / 1000).toInt()
            accumulatedSecs
        }
    }

    fun recalculateGrandTotalSeconds(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", null)
        val firebaseTodaySeconds = if (!currentUsername.isNullOrEmpty()) {
            val userRemote = com.example.api.FirebaseRepository.usersState.value[currentUsername]
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            if (userRemote?.todayStats?.dateString == todayStr || userRemote?.todayStats?.dateString.isNullOrEmpty()) {
                (userRemote?.todayStats?.todayFocusTimeMs ?: 0L) / 1000
            } else {
                0L
            }
        } else {
            0L
        }

        val activeSessionSeconds = getLiveActiveSessionSeconds()
        _liveGrandTotalSeconds.value = firebaseTodaySeconds.toInt() + activeSessionSeconds
    }

    fun forceRecalculateAndSync(context: Context) {
        init(context)
        // Instantly load from local SharedPreferences to update UI immediately
        val localRecords = loadFocusRecords(context)
        _focusRecords.value = localRecords
        recalculateGrandTotalSeconds(context)
        
        // Trigger cloud alignment check in the background
        performCloudAlignmentCheck(context)
    }

    fun setStopwatchBreakDuration(context: Context, mins: Int) {
        init(context)
        _stopwatchBreakDurationMinutes.value = mins
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("stopwatch_break_duration", mins).apply()
    }

    fun setAutoStartStopwatchAfterBreak(context: Context, enabled: Boolean) {
        init(context)
        _autoStartStopwatchAfterBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("stopwatch_autostart_after_break", enabled).apply()
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
    }

    fun isAlarmPlaying(): Boolean {
        return alarmJob != null && alarmJob?.isActive == true
    }

    fun playStrongBellSoundWithVibration(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            // Check if any bluetooth devices are connected to keep volume nominal (safe)
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100 // Nominal volume of 35 when bluetooth connected, else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                // Distinct, strong single bell-like alarm tone
                tg?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 1000) // 1 second duration
                
                try {
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(800, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(800)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(1200L) // Wait for the single sound to complete
            } finally {
                tg?.release()
            }
        }
    }

    fun playFriendReminderBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100 // Nominal volume of 35 when bluetooth connected, else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                // Quick distinct single ringing bell tone
                tg?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                
                try {
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(500)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(800L) // Wait for single sound to complete
            } finally {
                tg?.release()
            }
        }
    }

    fun playStopwatchBreakEndBellSound(context: Context) {
        stopAlarm()
        alarmJob = scope.launch {
            val isBT = isBluetoothAudioConnected(context)
            val volume = if (isBT) 35 else 100
            
            val tg = try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, volume)
            } catch (e: Exception) {
                null
            }
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

            try {
                tg?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 800)
                
                try {
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(600, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(600)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(1000L) // Wait for single sound to complete
            } finally {
                tg?.release()
            }
        }
    }

    private fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (i in devices.indices) {
                    val device = devices[i]
                    val type = device.type
                    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        return true
                    }
                }
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openAppWithTimerPageInFront(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.MainActivity::class.java)
            intent.apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("SHOW_TIMER_PAGE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        _soundEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_sound_enabled", enabled).apply()
    }

    fun setBellSilentModeEnabled(context: Context, enabled: Boolean) {
        init(context)
        _isBellSilentModeEnabled.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bell_silent_mode_enabled", enabled).apply()
    }

    fun setAutoStartBreak(context: Context, enabled: Boolean) {
        _autoStartBreak.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_autostart_break", enabled).apply()
    }

    fun setAutoStartPomo(context: Context, enabled: Boolean) {
        _autoStartPomo.value = enabled
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("timer_autostart_pomo", enabled).apply()
    }

    fun setTimerDuration(context: Context, mins: Int) {
        init(context)
        _timerDurationMinutes.value = mins
        if (!_isTimerRunning.value) {
            _timerSecondsLeft.value = mins * 60
        }
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("timer_duration", mins).apply()
    }

    fun attachTaskToTimer(context: Context, task: Task?) {
        init(context)
        _attachedTask.value = task
    }

    fun startTimer(context: Context, stopActiveAlarm: Boolean = true, updateButton: Boolean = true, forceFocusTab: Boolean = true) {
        init(context)
        if (updateButton) {
            updateLastButtonClicked("start_timer")
        }
        if (forceFocusTab) {
            setTabFocusTimerSelected(true)
        }
        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        if (_isTimerRunning.value) return
        val appContext = context.applicationContext

        if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            _isTimerRunning.value = true
            // --- POMODORO FOCUS MODE (Timestamp Engine) ---
            recordSessionStart()
            addSystemLog(appContext, "Start Timer", "BUTTON_PRESS", "Duration=${_timerDurationMinutes.value}m")
            
            KeepAliveService.start(appContext)

            _lastResumeTimeMs.value = StableTime.currentTimeMillis()
            lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime()
            saveActiveSessionState(appContext)

            timerJob?.cancel()
            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                val totalDurationMs = _timerDurationMinutes.value * 60 * 1000L
                var lastRecordedMinutes = ((_accumulatedSessionTimeMs.value / 1000) / 60).toInt()

                while (_isTimerRunning.value && _isFocusPhase.value) {
                    delay(200) // UI refresh rate
                    val currentChunkMs = getCurrentChunkMs()
                    val totalElapsedMs = _accumulatedSessionTimeMs.value + currentChunkMs
                    
                    val remainingMs = totalDurationMs - totalElapsedMs
                    _timerSecondsLeft.value = maxOf(0, (remainingMs / 1000).toInt())
                    _cumulativeSessionFocusSeconds.value = (totalElapsedMs / 1000).toInt()

                    val currentSessionSecs = _cumulativeSessionFocusSeconds.value
                    if (currentSessionSecs >= 21600) { // 6 hours
                        launch(Dispatchers.Main) {
                            Toast.makeText(appContext, "⚠️ Session focus limit of 6 hours reached! Timer paused.", Toast.LENGTH_LONG).show()
                        }
                        pauseTimer(appContext)
                        break
                    }

                    val todayTotalSecs = getTodayFocusSeconds() + currentSessionSecs
                    if (todayTotalSecs >= 72000) { // 20 hours
                        launch(Dispatchers.Main) {
                            Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Timer paused.", Toast.LENGTH_LONG).show()
                        }
                        pauseTimer(appContext)
                        break
                    }

                    val currentMinutes = ((totalElapsedMs / 1000) / 60).toInt()
                    val diffMinutes = currentMinutes - lastRecordedMinutes
                    if (diffMinutes > 0) {
                        lastRecordedMinutes = currentMinutes
                        _attachedTask.value?.let { task ->
                            val updatedTask = task.copy(actualMinutes = task.actualMinutes + diffMinutes)
                            updateTaskInDatabase(appContext, updatedTask)
                            _attachedTask.value = updatedTask
                        }
                    }
                    
                    updateOverlayTextAndState()
                    
                    if (remainingMs <= 0) break // Phase finished
                }

                if (_timerSecondsLeft.value <= 0) {
                    handlePhaseCompletion(appContext, completedFocusPhase = true)
                }
            }
        } else {
            _isTimerRunning.value = true
            // --- BREAK MODE (Simple Countdown) ---
            addSystemLog(appContext, "Start Break", "BUTTON_PRESS", "Left=${_timerSecondsLeft.value}s")
            KeepAliveService.start(appContext)
            
            saveActiveSessionState(appContext)

            timerJob?.cancel()
            timerJob = scope.launch {
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                var lastTickTime = android.os.SystemClock.elapsedRealtime()
                while (_isTimerRunning.value && !_isFocusPhase.value && _timerSecondsLeft.value > 0) {
                    delay(1000) // Simple 1-second tick for breaks
                    val now = android.os.SystemClock.elapsedRealtime()
                    val actualElapsedSecs = ((now - lastTickTime) / 1000).toInt()
                    lastTickTime = now

                    if (actualElapsedSecs > 0) {
                        _timerSecondsLeft.value = maxOf(0, _timerSecondsLeft.value - actualElapsedSecs)
                        updateOverlayTextAndState()
                    }
                }

                if (_timerSecondsLeft.value <= 0) {
                    handlePhaseCompletion(appContext, completedFocusPhase = false)
                }
            }
        }
        AlarmScheduler.scheduleTimerEndAlarm(appContext, _timerSecondsLeft.value)
    }

    private fun handlePhaseCompletion(context: Context, completedFocusPhase: Boolean) {
        val appContext = context.applicationContext
        _isTimerRunning.value = false
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        saveActiveSessionState(appContext)

        // Sound prompt alerting phase change (10s ring bell sound with vibration)
        if (_soundEnabled.value) {
            playStrongBellSoundWithVibration(appContext)
        }

        if (completedFocusPhase && !_wasStartedFromStopwatch.value) {
            val duration = _timerDurationMinutes.value

            // Save focus records history item -> Instead of saving directly, we queue a pending review
            val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
            val startStr = formatter.format(java.util.Date(System.currentTimeMillis() - duration * 60 * 1000L))
            val endStr = formatter.format(java.util.Date())
            val taskName = _attachedTask.value?.title ?: "Focus Session"
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Trigger immediate, robust data persistence before resetting the active session tracking values
            val elapsedSecs = if (_cumulativeSessionFocusSeconds.value > 0) _cumulativeSessionFocusSeconds.value else duration * 60
            val savedRecord = persistFocusSession(appContext, elapsedSecs, isTimer = true)

            _pendingFocusReview.value = savedRecord ?: FocusRecord(startStr, endStr, taskName, duration, todayStr, "", duration * 60)
            _cumulativeSessionFocusSeconds.value = 0
            _accumulatedSessionTimeMs.value = 0L
            setLastResumeTimeMs(null)

            // Switch to Break Mode
            _isFocusPhase.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val bMins = prefs.getInt("break_duration", 5)
            _timerSecondsLeft.value = bMins * 60

            saveActiveSessionState(appContext)
            KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            // Auto-start break depends on autoStartBreak preference
            if (_autoStartBreak.value) {
                updateLastButtonClicked("take_break_pomo")
                startTimer(appContext, stopActiveAlarm = false)
            }
        } else {
            // Break Finished!
            openAppWithTimerPageInFront(appContext)

            if (_wasStartedFromStopwatch.value) {
                _isFocusPhase.value = true
                _wasStartedFromStopwatch.value = false
                setTabFocusTimerSelected(false)
                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()

                // Reset Timer back to pomo duration
                _timerSecondsLeft.value = _timerDurationMinutes.value * 60

                saveActiveSessionState(appContext)
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                // Play bell sound for 3 seconds after stopwatch break is over
                if (_soundEnabled.value) {
                    playStopwatchBreakEndBellSound(appContext)
                }

                // Auto-start stopwatch if specified in settings
                if (_autoStartStopwatchAfterBreak.value) {
                    updateLastButtonClicked("start_stopwatch")
                    startStopwatch(appContext, stopActiveAlarm = false)
                } else {
                    updateLastButtonClicked("pause_stopwatch")
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                }
            } else {
                // Normal Pomo Break End: Reset to Work Phase
                _isFocusPhase.value = true
                _timerSecondsLeft.value = _timerDurationMinutes.value * 60

                _accumulatedSessionTimeMs.value = 0L
                setLastResumeTimeMs(null)
                _cumulativeSessionFocusSeconds.value = 0

                saveActiveSessionState(appContext)
                KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
                updateOverlayVisibility(appContext)

                // Auto-start next focus session depends on autoStartPomo preference
                if (_autoStartPomo.value) {
                    updateLastButtonClicked("start_timer")
                    startTimer(appContext, stopActiveAlarm = false)
                }
            }
        }
    }

    fun pauseTimer(context: Context, updateButton: Boolean = true, syncFirebase: Boolean = true) {
        init(context)
        if (updateButton) {
            updateLastButtonClicked("pause_timer")
        }
        
        // ONLY bank time if we are actively focusing
        if (_isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            val chunkMs = getCurrentChunkMs()
            _accumulatedSessionTimeMs.value += chunkMs
            _cumulativeSessionFocusSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
        }
        setLastResumeTimeMs(null) // Wipes out active live-tracking
 
        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        _isTimerRunning.value = false
        recordSessionPause()
        val appContext = context.applicationContext
        AlarmScheduler.cancelTimerEndAlarm(appContext)
        addSystemLog(appContext, "Pause Timer", "BUTTON_PRESS", "SecondsLeft=${_timerSecondsLeft.value}s")
        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
        if (syncFirebase) {
            syncStateToFirebase(appContext)
        }
        updateOverlayVisibility(appContext)
    }

    fun persistFocusSession(context: Context, elapsedSecs: Int, isTimer: Boolean): FocusRecord? {
        if (elapsedSecs <= 0) return null
        
        recordSessionCompleteOrReset(true)
        if (_verifiedSessionStartMs.value == null) {
            _verifiedSessionStartMs.value = StableTime.currentTimeMillis() - elapsedSecs * 1000L
        }
        
        val finalMinutes = elapsedSecs / 60
        val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val startStr = formatter.format(java.util.Date(StableTime.currentTimeMillis() - elapsedSecs * 1000L))
        val endStr = formatter.format(java.util.Date())
        val taskName = _attachedTask.value?.title ?: "Focus Session"
        val tagValue = _attachedTag.value
        
        // 1. Save Focus Record locally
        val record = addFocusRecord(context, startStr, endStr, taskName, finalMinutes, "", elapsedSecs, tagValue)

        // 2. Update Stats (Pomos count and total focus minutes)
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val focusTimerDurationMins = prefs.getInt("timer_duration", 25)
        if (isTimer && finalMinutes >= focusTimerDurationMins && focusTimerDurationMins > 0) {
            val currentPomos = _todayPomosCount.value
            _todayPomosCount.value = currentPomos + 1
            prefs.edit().putInt("today_pomos_count", currentPomos + 1).apply()
        }

        val currentMins = _totalFocusMinutes.value
        _totalFocusMinutes.value = currentMins + finalMinutes
        prefs.edit().putInt("total_focus_minutes", currentMins + finalMinutes).apply()

        // Trigger global verification dialog for background/immediate completion and auto-saves
        _globalVerificationFocusedTimeSeconds.value = elapsedSecs
        _globalVerificationRevisedTotalMinutes.value = getTodayFocusMinutes()
        _globalVerificationRevisedTotalSeconds.value = getTodayFocusSeconds()
        _showGlobalVerificationDialog.value = true

        // 3. Update task progress in database
        _attachedTask.value?.let { task ->
            val updatedTask = task.copy(actualMinutes = task.actualMinutes + finalMinutes)
            updateTaskInDatabase(context, updatedTask)
            _attachedTask.value = updatedTask
        }
        
        // 4. Remote Firebase Synchronization
        val currentUsername = prefs.getString("current_username", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isAdmin = prefs.getBoolean("is_admin", false)
        if (isLoggedIn && !isAdmin && currentUsername != null) {
            scope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    firebaseSyncMutex.withLock {
                        try {
                            val response = com.example.api.FirebaseClient.api.getUser(currentUsername)
                            if (response.isSuccessful) {
                                val baseUser = response.body()
                                if (baseUser != null) {
                                    val activeTimerState = com.example.api.ActiveTimer(
                                        status = "RELAXING",
                                        mode = if (_wasStartedFromStopwatch.value) "STOPWATCH" else "POMODORO",
                                        startTimeMs = 0L,
                                        targetEndTimeMs = 0L,
                                        accumulatedFocusMs = elapsedSecs * 1000L,
                                        accumulatedBreakMs = 0L,
                                        timezoneOffsetMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 1000)
                                    )
                                    val updatedUser = baseUser.copy(
                                        isFocusing = false,
                                        accumulatedTimeMs = 0L,
                                        lastResumeTimeMs = null,
                                        todaysFocusRecords = null,
                                        lastUpdatedTimestamp = System.currentTimeMillis(),
                                        focusStatus = "idle",
                                        activeTimer = activeTimerState
                                    )
                                    com.example.api.FirebaseClient.api.putUser(currentUsername, updatedUser)
                                    Log.d("FocusTimerManager", "Successfully synced end-event data persistence to Firebase.")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FocusTimerManager", "Failed to sync end-event data to Firebase", e)
                        }
                    }
                }
            }
        }

        // 5. Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    try {
                        GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                    }
                }
            }
        }

        return record
    }

    fun resetTimer(context: Context, saveSession: Boolean = true) {
        init(context)
        updateLastButtonClicked("reset_timer")
        updateLocalInteractionTimestamp()
        stopAlarm()
        AlarmScheduler.cancelTimerEndAlarm(context.applicationContext)
        timerJob?.cancel()
        _isTimerRunning.value = false

        val elapsedSecs = _cumulativeSessionFocusSeconds.value
        val appContext = context.applicationContext
        addSystemLog(appContext, "Reset Timer", "BUTTON_PRESS", "SaveSession=$saveSession, ElapsedSecs=${elapsedSecs}s")
        
        if (saveSession && elapsedSecs > 0 && _isFocusPhase.value && !_wasStartedFromStopwatch.value) {
            persistFocusSession(context, elapsedSecs, isTimer = true)
        }

        _isFocusPhase.value = true
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        setLastResumeTimeMs(null)
        _wasStartedFromStopwatch.value = false
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
    }

    fun takeBreakFromStopwatch(context: Context) {
        init(context)
        stopAlarm()
        pauseStopwatch(context, stopActiveAlarm = false, updateButton = false, syncFirebase = false)
        
        updateLastButtonClicked("pause_stopwatch")
        updateLocalInteractionTimestamp()
        
        _isFocusPhase.value = false
        _wasStartedFromStopwatch.value = true
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", true).apply()
        
        _timerSecondsLeft.value = _stopwatchBreakDurationMinutes.value * 60
        
        KeepAliveService.updateNotification(context)
        startTimer(context, stopActiveAlarm = false, updateButton = false, forceFocusTab = false)
        
        syncStateToFirebase(context)
    }

    fun takeBreakFromPomodoro(context: Context) {
        init(context)
        updateLastButtonClicked("take_break_pomo")
        updateLocalInteractionTimestamp()
        stopAlarm()
        pauseTimer(context)
        
        _isFocusPhase.value = false
        _wasStartedFromStopwatch.value = false
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        
        val bMins = prefs.getInt("break_duration", 5)
        _timerSecondsLeft.value = bMins * 60
        
        KeepAliveService.updateNotification(context)
        startTimer(context)
    }

    fun skipOrEndBreak(context: Context) {
        init(context)
        updateLastButtonClicked("skip_or_end_break")
        updateLocalInteractionTimestamp()
        stopAlarm()
        timerJob?.cancel()
        _isTimerRunning.value = false

        val appContext = context.applicationContext
        if (_wasStartedFromStopwatch.value) {
            _isFocusPhase.value = true
            _wasStartedFromStopwatch.value = false
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()

            _timerSecondsLeft.value = _timerDurationMinutes.value * 60
            KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            if (_autoStartStopwatchAfterBreak.value) {
                startStopwatch(appContext)
            } else {
                pauseStopwatch(appContext)
            }
        } else {
            _isFocusPhase.value = true
            _timerSecondsLeft.value = _timerDurationMinutes.value * 60
            KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
            updateOverlayVisibility(appContext)

            if (_autoStartPomo.value) {
                startTimer(appContext)
            }
        }
    }

    fun startStopwatch(context: Context, stopActiveAlarm: Boolean = true) {
        init(context)
        val isResume = _stopwatchSeconds.value > 0
        if (isResume) {
            updateLastButtonClicked("resume_stopwatch")
        } else {
            updateLastButtonClicked("start_stopwatch")
        }
        setTabFocusTimerSelected(false)
        if (stopActiveAlarm) {
            stopAlarm()
        }
        val appContext = context.applicationContext

        // If we are currently in break mode, stop the break timer and go back to stopwatch mode
        if (!_isFocusPhase.value) {
            timerJob?.cancel()
            _isTimerRunning.value = false
            _isFocusPhase.value = true
            _wasStartedFromStopwatch.value = false
            setTabFocusTimerSelected(false)
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
            
            // Reset break timer seconds left back to pomo duration for clean state
            _timerSecondsLeft.value = _timerDurationMinutes.value * 60
        }

        if (_isStopwatchActive.value) return
        updateLocalInteractionTimestamp()
        _isStopwatchActive.value = true
        recordSessionStart()
        addSystemLog(appContext, "Start Stopwatch", "BUTTON_PRESS", "Seconds=${_stopwatchSeconds.value}s")
        
        KeepAliveService.start(appContext)
        updateOverlayVisibility(appContext)

        _lastResumeTimeMs.value = StableTime.currentTimeMillis()
        lastResumeElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        saveActiveSessionState(appContext)

        stopwatchJob?.cancel()
        stopwatchJob = scope.launch {
            KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
            while (_isStopwatchActive.value) {
                delay(200) // UI refresh rate
                val currentChunkMs = getCurrentChunkMs()
                val totalMs = _accumulatedSessionTimeMs.value + currentChunkMs
                
                _stopwatchSeconds.value = (totalMs / 1000).toInt()

                val currentSessionSecs = _stopwatchSeconds.value
                if (currentSessionSecs >= 21600) { // 6 hours limit
                    launch(Dispatchers.Main) {
                        Toast.makeText(appContext, "⚠️ Session focus limit of 6 hours reached! Stopwatch paused.", Toast.LENGTH_LONG).show()
                    }
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                    break
                }

                val todayTotalSecs = getTodayFocusSeconds() + currentSessionSecs
                if (todayTotalSecs >= 72000) { // 20 hours limit
                    launch(Dispatchers.Main) {
                        Toast.makeText(appContext, "⚠️ Daily focus limit of 20 hours reached! Stopwatch paused.", Toast.LENGTH_LONG).show()
                    }
                    pauseStopwatch(appContext, stopActiveAlarm = false)
                    break
                }
                
                updateOverlayTextAndState()
            }
        }
    }

    fun pauseStopwatch(context: Context, stopActiveAlarm: Boolean = true, updateButton: Boolean = true, syncFirebase: Boolean = true) {
        init(context)
        if (updateButton) {
            updateLastButtonClicked("pause_stopwatch")
        }
        val chunkMs = getCurrentChunkMs()
        _accumulatedSessionTimeMs.value += chunkMs
        _stopwatchSeconds.value = (_accumulatedSessionTimeMs.value / 1000).toInt()
        setLastResumeTimeMs(null) // Wipes out active live-tracking

        updateLocalInteractionTimestamp()
        if (stopActiveAlarm) {
            stopAlarm()
        }
        stopwatchJob?.cancel()
        _isStopwatchActive.value = false
        recordSessionPause()
        val appContext = context.applicationContext
        addSystemLog(appContext, "Pause Stopwatch", "BUTTON_PRESS", "Seconds=${_stopwatchSeconds.value}s")
        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
        if (syncFirebase) {
            syncStateToFirebase(appContext)
        }
        updateOverlayVisibility(appContext)
    }

    fun resetStopwatch(context: Context, saveSession: Boolean = true) {
        init(context)
        updateLastButtonClicked("reset_stopwatch")
        updateLocalInteractionTimestamp()
        stopAlarm()
        stopwatchJob?.cancel()
        _isStopwatchActive.value = false

        val elapsedSecs = _stopwatchSeconds.value
        val appContext = context.applicationContext
        addSystemLog(appContext, "Reset Stopwatch", "BUTTON_PRESS", "SaveSession=$saveSession, Seconds=${elapsedSecs}s")
        
        if (saveSession && elapsedSecs > 0) {
            persistFocusSession(context, elapsedSecs, isTimer = false)
        }

        _stopwatchSeconds.value = 0
        _cumulativeSessionFocusSeconds.value = 0
        _accumulatedSessionTimeMs.value = 0L
        setLastResumeTimeMs(null)

        // Reset phase and wasStartedFromStopwatch flags so they don't get stuck in break mode
        _isFocusPhase.value = true
        _wasStartedFromStopwatch.value = false
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_started_from_stopwatch", false).apply()
        _timerSecondsLeft.value = _timerDurationMinutes.value * 60

        saveActiveSessionState(appContext)
        KeepAliveService.updateNotification(appContext)
                syncStateToFirebase(appContext)
        updateOverlayVisibility(appContext)
    }

    fun setAppBackgroundedState(context: Context, backgrounded: Boolean) {
        init(context)
        appIsBackgrounded = backgrounded
        
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val batterySaverEnabled = prefs.getBoolean("battery_saver_mode", false)
        
        if (batterySaverEnabled) {
            val appContext = context.applicationContext
            if (backgrounded) {
                Log.d("FocusTimerManager", "Battery Saver Mode: App backgrounded, disabling background loops and alarms.")
                
                // Save state to disk first so recovery is flawless
                saveActiveSessionState(appContext)
                
                // Stop KeepAliveService completely
                try {
                    val serviceIntent = Intent(appContext, KeepAliveService::class.java)
                    appContext.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Cancel active ticking jobs
                timerJob?.cancel()
                timerJob = null
                stopwatchJob?.cancel()
                stopwatchJob = null
                actualUiTickJob?.cancel()
                actualUiTickJob = null
                
                // Cancel the exact alarm scheduled for the end of the session
                AlarmScheduler.cancelTimerEndAlarm(appContext)
            } else {
                Log.d("FocusTimerManager", "Battery Saver Mode: App foregrounded, recovering and resuming state.")
                recoverAndResumeActiveSession(appContext)
            }
        }
        
        updateOverlayVisibility(context.applicationContext)
    }

    fun setTimerScreenActiveState(context: Context, active: Boolean) {
        init(context)
        isTimerScreenActive = active
        updateOverlayVisibility(context.applicationContext)
    }

    private fun updateOverlayVisibility(context: Context) {
        scope.launch(Dispatchers.Main) {
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val showOverlayPref = prefs.getBoolean("show_overlay_on_exit", true)

            val isPromoSessionActive = !isFocusPhase.value || timerSecondsLeft.value < timerDurationMinutes.value * 60
            val isStopwatchSessionActive = stopwatchSeconds.value > 0
            val hasAnySession = isTimerRunning.value || isStopwatchActive.value || isPromoSessionActive || isStopwatchSessionActive

            val shouldShow = hasAnySession && (!isTimerScreenActive || appIsBackgrounded) && showOverlayPref
            if (shouldShow) {
                showOverlay(context)
            } else {
                hideOverlay()
            }
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
        }
    }

    fun recreateOverlayIfExists(context: Context) {
        scope.launch(Dispatchers.Main) {
            if (overlayView != null) {
                hideOverlay()
                showOverlay(context)
            }
        }
    }

    private fun showOverlay(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.d("FocusTimerManager", "Overlay permission not granted")
            return
        }

        if (overlayView != null) {
            updateOverlayTextAndState()
            return
        }

        try {
            val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("floating_timer_size", "large") ?: "large"

            val textSizeVal: Float
            val padH: Float
            val padV: Float
            val fixedWidthDp: Float
            when (sizePref) {
                "small" -> {
                    textSizeVal = 14f
                    padH = 10f
                    padV = 6f
                    fixedWidthDp = 140f
                }
                "medium" -> {
                    textSizeVal = 19f
                    padH = 16f
                    padV = 10f
                    fixedWidthDp = 180f
                }
                else -> {
                    textSizeVal = 25f
                    padH = 22f
                    padV = 14f
                    fixedWidthDp = 220f
                }
            }

            val initialWidthDp = if (isOverlayCollapsed) 32f else fixedWidthDp
            val wmLayoutParams = WindowManager.LayoutParams(
                dpToPx(context, initialWidthDp),
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                     @Suppress("DEPRECATION")
                     WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = lastOverlayX
                y = lastOverlayY
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipToOutline = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF111111.toInt())
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
            }

            // End button on the left
            val endBtn = TextView(context).apply {
                text = "■"
                setTextColor(0xFFFF5252.toInt())
                textSize = textSizeVal + 8f
                gravity = Gravity.CENTER
                setPadding(dpToPx(context, 14f), dpToPx(context, padV), dpToPx(context, 6f), dpToPx(context, padV))
                setOnClickListener {
                    Toast.makeText(context, "Command Executed: [End Session] - Hiding Controls", Toast.LENGTH_SHORT).show()
                    hideOverlayControls(context)

                    val isStopwatch = stopwatchSeconds.value > 0 || isStopwatchActive.value
                    val elapsedSecs = if (isStopwatch) stopwatchSeconds.value else cumulativeSessionFocusSeconds.value

                    if (elapsedSecs > 0) {
                        if (isStopwatch) {
                            resetStopwatch(context, saveSession = true)
                        } else {
                            resetTimer(context, saveSession = true)
                        }
                        showOverlay3StepVerification(context, elapsedSecs, !isStopwatch)
                    } else {
                        if (isStopwatch) {
                            resetStopwatch(context, saveSession = false)
                        } else {
                            resetTimer(context, saveSession = false)
                        }
                        Toast.makeText(context, "Session cancelled. No focus time to save.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            tvEndBtn = endBtn
            container.addView(endBtn)

            // Timer display text view (middle)
            val textView = TextView(context).apply {
                text = formatTime(if (isStopwatchActive.value) stopwatchSeconds.value else timerSecondsLeft.value)
                setTextColor(android.graphics.Color.WHITE)
                textSize = textSizeVal
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, dpToPx(context, padV), 0, dpToPx(context, padV))
                this.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    gravity = Gravity.CENTER
                }
            }
            tvTimerText = textView
            container.addView(textView)

            // Pause button on the right
            val pauseBtn = TextView(context).apply {
                val isRunning = isTimerRunning.value || isStopwatchActive.value
                text = if (isRunning) "❙❙" else "▶"
                setTextColor(0xFF03A9F4.toInt())
                textSize = textSizeVal + 8f
                gravity = Gravity.CENTER
                setPadding(dpToPx(context, 6f), dpToPx(context, padV), dpToPx(context, 14f), dpToPx(context, padV))
                setOnClickListener {
                    val isRunningBefore = isTimerRunning.value || isStopwatchActive.value
                    val actionName = if (isRunningBefore) "Pause" else "Resume"
                    Toast.makeText(context, "Command Executed: [$actionName] - Hiding Controls", Toast.LENGTH_SHORT).show()
                    hideOverlayControls(context)

                    if (isRunningBefore) {
                        if (isStopwatchActive.value) {
                            pauseStopwatch(context)
                        } else if (isTimerRunning.value) {
                            pauseTimer(context)
                        }
                    } else {
                        if (isTabFocusTimerSelected.value) {
                            startTimer(context)
                        } else {
                            startStopwatch(context)
                        }
                    }
                    updateOverlayTextAndState()
                }
            }
            tvPauseBtn = pauseBtn
            container.addView(pauseBtn)

            // Handle collapsed arrow layout
            val arrowText = TextView(context).apply {
                text = "❯"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                visibility = View.GONE
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            tvCollapsedArrow = arrowText
            container.addView(arrowText)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("SHOW_TIMER_PAGE", true)
                    }
                    context.startActivity(intent)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (isOverlayCollapsed) {
                        expandOverlay(context)
                        return true
                    } else {
                        showOverlayControls(context)
                        return true
                    }
                }
            })

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            val onTouchHandler = View.OnTouchListener { _, event ->
                if (gestureDetector.onTouchEvent(event)) {
                    return@OnTouchListener true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = wmLayoutParams.x
                        initialY = wmLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        showOverlayControls(context)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        wmLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        wmLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        lastOverlayX = wmLayoutParams.x
                        lastOverlayY = wmLayoutParams.y
                        try {
                            wm.updateViewLayout(container, wmLayoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showOverlayControls(context)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)
                        
                        // Dock to edge collapse checks only if dragged
                        if (dx > 10 || dy > 10) {
                            val displayMetrics = android.util.DisplayMetrics()
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getMetrics(displayMetrics)
                            val screenWidth = displayMetrics.widthPixels
                            val containerWidth = container.width

                            val triggerThreshold = 0 // only minimize if forcefully pushed to edge

                            if (wmLayoutParams.x <= triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "left"
                                wmLayoutParams.x = 0
                                updateCollapsedStateViews(context)
                            } else if (wmLayoutParams.x >= screenWidth - containerWidth - triggerThreshold) {
                                isOverlayCollapsed = true
                                overlayCollapsedSide = "right"
                                wmLayoutParams.x = screenWidth - dpToPx(context, 32f) // keep mini handle visible
                                updateCollapsedStateViews(context)
                            } else {
                                isOverlayCollapsed = false
                                overlayCollapsedSide = "none"
                                showOverlayControls(context)
                            }
                        } else {
                            showOverlayControls(context)
                        }
                        lastOverlayX = wmLayoutParams.x
                        lastOverlayY = wmLayoutParams.y

                        try {
                            wm.updateViewLayout(container, wmLayoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                    else -> true
                }
            }

            textView.setOnTouchListener(onTouchHandler)
            arrowText.setOnTouchListener(onTouchHandler)

            wm.addView(container, wmLayoutParams)
            overlayView = container
            updateOverlayTextAndState()
            updateCollapsedStateViews(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverlay3StepVerification(context: Context, elapsedSeconds: Int, isTimer: Boolean) {
        val dp16 = dpToPx(context, 16f)
        val dp12 = dpToPx(context, 12f)
        val dp8 = dpToPx(context, 8f)
        val dp4 = dpToPx(context, 4f)

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF0F0F12.toInt())
                cornerRadius = dpToPx(context, 16f).toFloat()
            }
        }

        // Title
        val titleTv = TextView(context).apply {
            text = "SYSTEM VERIFICATION"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp12)
        }
        dialogView.addView(titleTv)

        // Container Card
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF15151A.toInt())
                setStroke(dpToPx(context, 1f), 0xFF22222A.toInt())
                cornerRadius = dpToPx(context, 12f).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp16)
            }
            layoutParams = lp
        }

        // Format helper
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

        // Calculations
        val currentSecs = elapsedSeconds
        val revisedSecs = getTodayFocusSeconds()
        val prevSecs = maxOf(0, revisedSecs - currentSecs)

        val formattedPast = formatSecondsToReadable(prevSecs)
        val formattedNow = formatSecondsToReadable(currentSecs)
        val formattedRevised = formatSecondsToReadable(revisedSecs)

        // Start / End
        val startMs = _verifiedSessionStartMs.value ?: (StableTime.currentTimeMillis() - currentSecs * 1000L)
        val endMs = StableTime.currentTimeMillis()
        val timeFormatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val startStr = timeFormatter.format(java.util.Date(startMs))
        val endStr = timeFormatter.format(java.util.Date(endMs))

        // Helper to add a row programmatically
        fun addMetricsRow(container: LinearLayout, labelText: String, valueText: String, valueColor: Int, isBold: Boolean = false, isHeavyBold: Boolean = false) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val label = TextView(context).apply {
                text = labelText
                setTextColor(0xFF888888.toInt())
                textSize = 10f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(context).apply {
                text = valueText
                setTextColor(valueColor)
                textSize = if (isHeavyBold) 14f else if (isBold) 12f else 11f
                val style = if (isHeavyBold || isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                setTypeface(android.graphics.Typeface.DEFAULT, style)
            }
            row.addView(label)
            row.addView(value)
            container.addView(row)
        }

        fun addDivider(container: LinearLayout) {
            val divider = View(context).apply {
                background = android.graphics.drawable.ColorDrawable(0xFF22222A.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 1f)).apply {
                    setMargins(0, dp8, 0, dp8)
                }
            }
            container.addView(divider)
        }

        // Add the 5 rows
        addMetricsRow(cardLayout, "PREVIOUSLY FOCUSED", formattedPast, 0xFFD3D3D3.toInt(), isBold = true)
        addDivider(cardLayout)
        addMetricsRow(cardLayout, "START TIME", startStr, android.graphics.Color.WHITE)
        addDivider(cardLayout)
        addMetricsRow(cardLayout, "END TIME", endStr, android.graphics.Color.WHITE)
        addDivider(cardLayout)
        addMetricsRow(cardLayout, "CURRENT FOCUSED TIME", formattedNow, 0xFF38B6FF.toInt(), isBold = true)
        addDivider(cardLayout)
        addMetricsRow(cardLayout, "REVISED FOCUSED TIME", formattedRevised, 0xFF4CAF50.toInt(), isHeavyBold = true)

        dialogView.addView(cardLayout)

        // Alert dialog setup
        val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(dialogView)
            .create()

        // Confirm Button
        val confirmBtn = TextView(context).apply {
            text = "Confirm & Close"
            setTextColor(android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF4CAF50.toInt())
                cornerRadius = dpToPx(context, 8f).toFloat()
            }
            setPadding(0, dpToPx(context, 10f), 0, dpToPx(context, 10f))
            setOnClickListener {
                dialog.dismiss()
            }
        }
        val btnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 42f)
        )
        confirmBtn.layoutParams = btnLp
        dialogView.addView(confirmBtn)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun expandOverlay(context: Context) {
        isOverlayCollapsed = false
        overlayCollapsedSide = "none"

        val wm = windowManager ?: return
        val container = overlayView as? LinearLayout ?: return

        val displayMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return

        if (lp.x < screenWidth / 2) {
            lp.x = 40
        } else {
            lp.x = screenWidth - container.width - 40
        }
        
        lastOverlayX = lp.x
        lastOverlayY = lp.y

        showOverlayControls(context)

        try {
            wm.updateViewLayout(container, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCollapsedStateViews(context: Context) {
        val timerText = tvTimerText ?: return
        val arrowText = tvCollapsedArrow ?: return
        val container = overlayView as? LinearLayout ?: return
        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return
        val wm = windowManager ?: return

        scope.launch(Dispatchers.Main) {
            if (isOverlayCollapsed) {
                timerText.visibility = View.GONE
                tvEndBtn?.visibility = View.GONE
                tvPauseBtn?.visibility = View.GONE
                arrowText.visibility = View.VISIBLE
                if (overlayCollapsedSide == "left") {
                    arrowText.text = "❯"
                    arrowText.setPadding(dpToPx(context, 10f), dpToPx(context, 12f), dpToPx(context, 6f), dpToPx(context, 12f))
                } else {
                    arrowText.text = "❮"
                    arrowText.setPadding(dpToPx(context, 6f), dpToPx(context, 12f), dpToPx(context, 10f), dpToPx(context, 12f))
                }
                lp.width = dpToPx(context, 32f)
            } else {
                timerText.visibility = View.VISIBLE
                arrowText.visibility = View.GONE

                if (areOverlayControlsVisible) {
                    tvEndBtn?.visibility = View.VISIBLE
                    tvPauseBtn?.visibility = View.VISIBLE

                    val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .getString("floating_timer_size", "large") ?: "large"
                    val fixedWidthDp = when (sizePref) {
                        "small" -> 140f
                        "medium" -> 180f
                        "large" -> 220f
                        else -> 220f
                    }
                    lp.width = dpToPx(context, fixedWidthDp)
                } else {
                    tvEndBtn?.visibility = View.GONE
                    tvPauseBtn?.visibility = View.GONE

                    val sizePref = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .getString("floating_timer_size", "large") ?: "large"
                    val compactWidthDp = when (sizePref) {
                        "small" -> 70f
                        "medium" -> 90f
                        "large" -> 110f
                        else -> 110f
                    }
                    lp.width = dpToPx(context, compactWidthDp)
                }
            }
            try {
                wm.updateViewLayout(container, lp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showOverlayControls(context: Context) {
        if (isOverlayCollapsed) return
        areOverlayControlsVisible = true
        updateCollapsedStateViews(context)
        
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = scope.launch(Dispatchers.Main) {
            delay(5000)
            hideOverlayControls(context)
        }
    }

    private fun hideOverlayControls(context: Context) {
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = null
        areOverlayControlsVisible = false
        updateCollapsedStateViews(context)
    }

    private fun hideOverlay() {
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = null
        areOverlayControlsVisible = false
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            tvTimerText = null
            tvCollapsedArrow = null
            tvEndBtn = null
            tvPauseBtn = null
            windowManager = null
        }
    }

    private fun updateOverlayTextAndState() {
        appContext?.let {
            com.example.widget.WidgetUpdater.updateStopwatchWidget(it)
            com.example.widget.WidgetUpdater.updatePomodoroWidget(it)
        }
        scope.launch(Dispatchers.Main) {
            val displaySeconds = if (!isFocusPhase.value) {
                timerSecondsLeft.value // Show break countdown
            } else if (isTimerRunning.value) {
                timerSecondsLeft.value // Show work countdown
            } else if (isStopwatchActive.value) {
                stopwatchSeconds.value // Show stopwatch active count up
            } else if (wasStartedFromStopwatch.value) {
                timerSecondsLeft.value // Break countdown
            } else {
                if (!isTabFocusTimerSelected.value) stopwatchSeconds.value else timerSecondsLeft.value
            }
            tvTimerText?.let { textView ->
                textView.text = formatTime(displaySeconds)
                val isBreakActive = !isFocusPhase.value
                val hasAnimation = textView.animation != null
                if (isBreakActive) {
                    if (!hasAnimation) {
                        val anim = android.view.animation.AlphaAnimation(1.0f, 0.15f).apply {
                            duration = 600
                            repeatMode = android.view.animation.Animation.REVERSE
                            repeatCount = android.view.animation.Animation.INFINITE
                        }
                        textView.startAnimation(anim)
                    }
                } else {
                    if (hasAnimation) {
                        textView.clearAnimation()
                    }
                }
            }
            tvPauseBtn?.let { btn ->
                val isRunning = isTimerRunning.value || isStopwatchActive.value
                btn.text = if (isRunning) "❙❙" else "▶"
            }
        }
    }

    private fun updateTaskInDatabase(context: Context, task: Task) {
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.taskDao().updateTask(task)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun incrementTodayPomos(context: Context) {
        val next = _todayPomosCount.value + 1
        _todayPomosCount.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("today_pomos_count", next).apply()
    }

    fun decrementTodayPomos(context: Context) {
        val next = maxOf(0, _todayPomosCount.value - 1)
        _todayPomosCount.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("today_pomos_count", next).apply()
    }

    fun addFocusMinutes(context: Context, mins: Int) {
        val next = _totalFocusMinutes.value + mins
        _totalFocusMinutes.value = next
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_focus_minutes", next).apply()
    }

    fun clearPendingFocusReview() {
        _pendingFocusReview.value = null
    }

    fun addFocusRecord(context: Context, startTime: String, endTime: String, taskTitle: String, durationMinutes: Int, notes: String = "", durationSeconds: Int = durationMinutes * 60, tag: String = "", id: String = java.util.UUID.randomUUID().toString()): FocusRecord {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val cappedMinutes = if (durationMinutes > 360) 360 else durationMinutes
        val cappedSeconds = if (durationSeconds > 21600) 21600 else durationSeconds
        
        val myDeviceId = getOrCreateDeviceId(context)
        val separator = if (notes.isEmpty()) "" else " "
        val markedNotes = if (!notes.contains("[logged_by_device:$myDeviceId]")) {
            notes + separator + "[logged_by_device:$myDeviceId]"
        } else {
            notes
        }
        
        val record = FocusRecord(startTime, endTime, taskTitle, cappedMinutes, todayStr, markedNotes, cappedSeconds, tag, id = id)
        return record
    }

    fun updateFocusRecordById(context: Context, id: String, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 360) 360 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 21600) 21600 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = sanitizeRecordsList(currentList)
                updatedList!!
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun deleteFocusRecordById(context: Context, id: String) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                currentList.removeAt(index)
                updatedList = sanitizeRecordsList(currentList)
                updatedList!!
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun updateFocusRecord(context: Context, index: Int, updatedRecord: FocusRecord) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                val cappedMinutes = if (updatedRecord.durationMinutes > 720) 720 else updatedRecord.durationMinutes
                val cappedSeconds = if (updatedRecord.durationSeconds > 43200) 43200 else updatedRecord.durationSeconds
                val record = updatedRecord.copy(durationMinutes = cappedMinutes, durationSeconds = cappedSeconds)
                currentList[index] = record
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun deleteFocusRecord(context: Context, index: Int) {
        var updatedList: List<FocusRecord>? = null
        _focusRecords.update { current ->
            val currentList = current.toMutableList()
            if (index in currentList.indices) {
                currentList.removeAt(index)
                updatedList = currentList
                currentList
            } else {
                current
            }
        }
        updatedList?.let {
            saveFocusRecords(context, it)
        }
    }

    fun loadPeerFocusRecords(context: Context, username: String): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("peer_focus_records_$username", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    FocusRecord(parts[0], parts[1], parts[2], originalMins, dateValue, notesValue, originalSecs, tagValue, idValue)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePeerFocusRecords(context: Context, username: String, list: List<FocusRecord>) {
        val serialized = list.joinToString("\n") { 
            val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
            "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}" 
        }
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("peer_focus_records_$username", serialized).apply()
    }

    fun saveFocusRecords(context: Context, list: List<FocusRecord>) {
        synchronized(recordLock) {
            val serialized = list.joinToString("\n") { 
                val b64Notes = android.util.Base64.encodeToString(it.notes.toByteArray(), android.util.Base64.NO_WRAP)
                "${it.startTime}|${it.endTime}|${it.taskTitle}|${it.durationMinutes}|${it.dateString}|$b64Notes|${it.durationSeconds}|${it.tag}|${it.id}" 
            }
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("focus_records_list", serialized).apply()
        }

        // Automatic Google Drive Backup
        if (GoogleDriveSyncManager.hasDrivePermission(context)) {
            scope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    try {
                        GoogleDriveSyncManager.backupFocusData(context)
                        Log.d("FocusTimerManager", "Successfully auto-backed up focus records to Google Drive.")
                    } catch (e: Exception) {
                        Log.e("FocusTimerManager", "Failed to auto-backup to Google Drive", e)
                    }
                }
            }
        }
    }

    fun sanitizeRecordsList(list: List<FocusRecord>): List<FocusRecord> {
        // 1. Cap each record to per-session limit of 6 hours (360 minutes, 21600 seconds)
        val sessionCapped = list.map {
            var changed = false
            var mins = it.durationMinutes
            var secs = it.durationSeconds
            if (mins > 360) {
                mins = 360
                changed = true
            }
            if (secs > 21600) {
                secs = 21600
                changed = true
            }
            if (changed) {
                it.copy(durationMinutes = mins, durationSeconds = secs)
            } else {
                it
            }
        }

        // 2. Cap per-day total to daily limit of 20 hours (1200 minutes, 72000 seconds)
        val groupedByDate = sessionCapped.groupBy { it.dateString }
        val finalSanitizedList = mutableListOf<FocusRecord>()

        for ((date, records) in groupedByDate) {
            val totalSecs = records.sumOf { it.durationSeconds }
            if (totalSecs > 72000) {
                var accumulatedSecs = 0
                var accumulatedMins = 0
                records.forEachIndexed { index, record ->
                    if (index == records.lastIndex) {
                        val remainingSecs = 72000 - accumulatedSecs
                        val remainingMins = 1200 - accumulatedMins
                        if (remainingSecs > 0) {
                            finalSanitizedList.add(record.copy(
                                durationMinutes = maxOf(1, remainingMins),
                                durationSeconds = maxOf(1, remainingSecs)
                            ))
                        }
                    } else {
                        val fraction = record.durationSeconds.toDouble() / totalSecs
                        val targetSecs = maxOf(1, (fraction * 72000).toInt())
                        val targetMins = maxOf(1, (fraction * 1200).toInt())
                        accumulatedSecs += targetSecs
                        accumulatedMins += targetMins
                        finalSanitizedList.add(record.copy(
                            durationMinutes = targetMins,
                            durationSeconds = targetSecs
                        ))
                    }
                }
            } else {
                finalSanitizedList.addAll(records)
            }
        }

        val orderMap = list.withIndex().associate { it.value.id to it.index }
        return finalSanitizedList.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    fun loadFocusRecords(context: Context): List<FocusRecord> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("focus_records_list", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            val list = serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    val tagValue = if (parts.size >= 8) parts[7] else ""
                    val idValue = if (parts.size >= 9) parts[8] else java.util.UUID.randomUUID().toString()
                    FocusRecord(parts[0], parts[1], parts[2], originalMins, dateValue, notesValue, originalSecs, tagValue, idValue)
                } else null
            }

            val sanitized = sanitizeRecordsList(list)
            val totalOriginalMins = list.sumOf { it.durationMinutes }
            val totalSanitizedMins = sanitized.sumOf { it.durationMinutes }
            val diffMins = totalOriginalMins - totalSanitizedMins

            if (sanitized != list || diffMins > 0) {
                saveFocusRecords(context, sanitized)
                if (diffMins > 0) {
                    val currentTotal = _totalFocusMinutes.value
                    val newTotal = maxOf(0, currentTotal - diffMins)
                    _totalFocusMinutes.value = newTotal
                    prefs.edit().putInt("total_focus_minutes", newTotal).apply()
                }
                sanitized
            } else {
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun getOverlapSecondsForDate(record: FocusRecord, targetDateStr: String): Int {
        try {
            val dateStr = if (record.dateString.isNotEmpty()) record.dateString else targetDateStr
            val fullStr = "$dateStr ${record.endTime}"
            val formats = listOf(
                "yyyy-MM-dd hh:mm:ss a",
                "yyyy-MM-dd hh:mm a",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
            )
            var endDate: java.util.Date? = null
            for (fmt in formats) {
                try {
                    val parser = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                    endDate = parser.parse(fullStr)
                    if (endDate != null) break
                } catch (e: Exception) {
                    // Try next format
                }
            }
            val resolvedEndDate = endDate ?: return 0
            val endMs = resolvedEndDate.time
            val startMs = endMs - (record.durationSeconds * 1000L)
            
            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return 0
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis
            
            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)
            
            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            if (record.dateString == targetDateStr || record.dateString.isEmpty()) {
                return record.durationSeconds
            }
            return 0
        }
    }

    fun getActiveSessionOverlapSeconds(startMs: Long, targetDateStr: String): Int {
        try {
            val endMs = StableTime.currentTimeMillis()
            val dateParser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val targetDate = dateParser.parse(targetDateStr) ?: return 0
            val calendar = java.util.Calendar.getInstance()
            calendar.time = targetDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val targetStartMs = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val targetEndMs = calendar.timeInMillis
            
            val overlapStart = maxOf(startMs, targetStartMs)
            val overlapEnd = minOf(endMs, targetEndMs)
            
            return if (overlapEnd > overlapStart) {
                ((overlapEnd - overlapStart) / 1000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            return ((StableTime.currentTimeMillis() - startMs) / 1000).toInt()
        }
    }

    fun getTodayFocusMinutes(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val completedTodaySeconds = focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
        return (completedTodaySeconds + 30) / 60
    }

    fun getTodayFocusSeconds(): Int {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return focusRecords.value.sumOf { r ->
            getOverlapSecondsForDate(r, todayStr)
        }
    }

    fun loadFocusTags(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val tagsString = prefs.getString("focus_tags_list", "")
        return if (tagsString.isNullOrBlank()) {
            listOf("Work", "Study", "Exercise", "Reading", "Relaxation", "Coding")
        } else {
            tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun saveFocusTags(context: Context, tags: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("focus_tags_list", tags.joinToString(",")).apply()
        _focusTags.value = tags
    }
}
