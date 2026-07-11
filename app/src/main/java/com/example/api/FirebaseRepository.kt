package com.example.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

object FirebaseRepository {
    private val _userProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val userProfiles: StateFlow<Map<String, UserProfile>> = _userProfiles.asStateFlow()

    private val _activeTimers = MutableStateFlow<Map<String, ActiveTimer>>(emptyMap())
    val activeTimers: StateFlow<Map<String, ActiveTimer>> = _activeTimers.asStateFlow()

    private val _todayStats = MutableStateFlow<Map<String, TodayStats>>(emptyMap())
    val todayStats: StateFlow<Map<String, TodayStats>> = _todayStats.asStateFlow()

    private val _emojis = MutableStateFlow<Map<String, String>>(emptyMap())
    val emojis: StateFlow<Map<String, String>> = _emojis.asStateFlow()

    private val lock = Any()

    val usersState: StateFlow<Map<String, UserRemote>> = combine(
        _userProfiles,
        _activeTimers,
        _todayStats,
        _emojis
    ) { profiles, timers, stats, emojis ->
        val usernames = profiles.keys + timers.keys + stats.keys + emojis.keys
        usernames.associateWith { username ->
            val profile = profiles[username]
            val timer = timers[username]
            val today = stats[username]
            val emojiVal = emojis[username] ?: "🎯"

            val isFocusingVal = timer?.status == "FOCUSING"
            val focusStatusVal = timer?.status?.lowercase() ?: "idle"
            val lastResumeTimeMsVal = if (isFocusingVal) timer?.startTimeMs else null
            val isStopwatchModeVal = timer?.mode == "STOPWATCH"

            UserRemote(
                name = profile?.name,
                nickname = profile?.nickname,
                profile = profile,
                activeTimer = timer,
                todayStats = today,
                emoji = emojiVal,
                isFocusing = isFocusingVal,
                focusStatus = focusStatusVal,
                lastResumeTimeMs = lastResumeTimeMsVal,
                isStopwatchMode = isStopwatchModeVal
            )
        }
    }.stateIn(
        scope = kotlinx.coroutines.GlobalScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    fun updateUserProfile(username: String, profile: UserProfile) {
        synchronized(lock) {
            val current = _userProfiles.value.toMutableMap()
            current[username] = profile
            _userProfiles.value = current
        }
    }

    fun updateActiveTimer(username: String, timer: ActiveTimer) {
        synchronized(lock) {
            val current = _activeTimers.value.toMutableMap()
            current[username] = timer
            _activeTimers.value = current
        }
    }

    fun updateTodayStats(username: String, stats: TodayStats) {
        synchronized(lock) {
            val current = _todayStats.value.toMutableMap()
            current[username] = stats
            _todayStats.value = current
        }
    }

    fun updateEmoji(username: String, emoji: String) {
        synchronized(lock) {
            val current = _emojis.value.toMutableMap()
            current[username] = emoji
            _emojis.value = current
        }
    }

    fun updateUsers(newUsers: Map<String, UserRemote>) {
        synchronized(lock) {
            newUsers.forEach { (username, user) ->
                user.profile?.let { updateUserProfile(username, it) }
                user.activeTimer?.let { updateActiveTimer(username, it) }
                user.todayStats?.let { updateTodayStats(username, it) }
                user.emoji?.let { updateEmoji(username, it) }
            }
        }
    }

    fun syncAllUsers(allRemoteUsers: Map<String, UserRemote>) {
        synchronized(lock) {
            val newProfiles = mutableMapOf<String, UserProfile>()
            val newTimers = mutableMapOf<String, ActiveTimer>()
            val newStats = mutableMapOf<String, TodayStats>()
            val newEmojis = mutableMapOf<String, String>()
            
            allRemoteUsers.forEach { (username, user) ->
                newProfiles[username] = user.profile ?: UserProfile(name = user.name ?: "", nickname = user.nickname ?: "")
                newTimers[username] = user.activeTimer ?: ActiveTimer()
                newStats[username] = user.todayStats ?: TodayStats()
                newEmojis[username] = user.emoji ?: "🎯"
            }
            _userProfiles.value = newProfiles
            _activeTimers.value = newTimers
            _todayStats.value = newStats
            _emojis.value = newEmojis
        }
    }

    fun getUsers(): Map<String, UserRemote> {
        return usersState.value
    }
}
