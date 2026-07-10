package com.example.util

import android.content.Context
import java.util.Calendar

object SleepTimeHelper {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_WAKE_UP_TIME = "wake_up_time"
    private const val KEY_SLEEP_TIME = "sleep_time"

    fun isWakeUpAndSleepTimeSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wakeUp = prefs.getString(KEY_WAKE_UP_TIME, null)
        val sleep = prefs.getString(KEY_SLEEP_TIME, null)
        return !wakeUp.isNullOrBlank() && !sleep.isNullOrBlank()
    }

    fun getWakeUpTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WAKE_UP_TIME, null)
    }

    fun getSleepTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SLEEP_TIME, null)
    }

    fun setWakeUpTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WAKE_UP_TIME, time).apply()
    }

    fun setSleepTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SLEEP_TIME, time).apply()
    }

    fun isInSleepTime(context: Context): Boolean {
        if (!isWakeUpAndSleepTimeSet(context)) return false

        val wakeUp = getWakeUpTime(context) ?: return false
        val sleep = getSleepTime(context) ?: return false

        // Parse wakeUp "HH:mm"
        val wakeParts = wakeUp.split(":")
        if (wakeParts.size != 2) return false
        val wakeHour = wakeParts[0].toIntOrNull() ?: return false
        val wakeMin = wakeParts[1].toIntOrNull() ?: 0

        // Parse sleep "HH:mm"
        val sleepParts = sleep.split(":")
        if (sleepParts.size != 2) return false
        val sleepHour = sleepParts[0].toIntOrNull() ?: return false
        val sleepMin = sleepParts[1].toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)
        val curMin = cal.get(Calendar.MINUTE)

        val curMinutes = curHour * 60 + curMin
        val wakeMinutes = wakeHour * 60 + wakeMin
        val sleepMinutes = sleepHour * 60 + sleepMin

        return if (sleepMinutes < wakeMinutes) {
            // Sleep window is within the same calendar day (e.g. sleep 13:00 to 15:00)
            curMinutes in sleepMinutes until wakeMinutes
        } else {
            // Sleep window spans midnight (e.g. sleep 22:00 to 07:00)
            curMinutes >= sleepMinutes || curMinutes < wakeMinutes
        }
    }
}
