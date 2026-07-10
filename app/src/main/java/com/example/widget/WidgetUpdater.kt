package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.util.FocusTimerManager

object WidgetUpdater {

    private fun getPendingIntentFlags(isMutable: Boolean = false): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isMutable) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    /**
     * Updates the Friends Focus Widget ("Who is Focusing")
     */
    fun updateFriendsFocusWidget(context: Context, statusText: String? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, FriendsFocusWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val textToShow = statusText ?: context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            .getString("last_friends_focus_text", "No one is focusing") ?: "No one is focusing"

        // Cache it for subsequent onUpdate calls
        if (statusText != null) {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_friends_focus_text", statusText)
                .apply()
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val pendingIntent = PendingIntent.getActivity(context, 2001, intent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_friends_focus)
            views.setTextViewText(R.id.focus_status_text, textToShow)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    /**
     * Updates the Stopwatch Widget
     */
    fun updateStopwatchWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TimerStopwatchWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        FocusTimerManager.init(context)
        val seconds = FocusTimerManager.stopwatchSeconds.value
        val isRunning = FocusTimerManager.isStopwatchActive.value

        // Standard Chronometer counts up relative to its system elapsed base
        val baseTime = android.os.SystemClock.elapsedRealtime() - seconds * 1000L

        val startPauseIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 3001, startPauseIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 3002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 3003, rootIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_stopwatch)
            
            // Native smooth count-up mechanism
            views.setChronometer(R.id.stopwatch_time_display, baseTime, null, isRunning)
            
            if (isRunning) {
                views.setTextViewText(R.id.btn_stopwatch_start_pause, "⏸ PAUSE")
            } else {
                views.setTextViewText(R.id.btn_stopwatch_start_pause, "▶ START")
            }

            views.setOnClickPendingIntent(R.id.btn_stopwatch_start_pause, startPausePending)
            views.setOnClickPendingIntent(R.id.btn_stopwatch_reset, resetPending)
            views.setOnClickPendingIntent(R.id.stopwatch_title, rootPending)
            views.setOnClickPendingIntent(R.id.stopwatch_time_display, rootPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    /**
     * Updates the Pomodoro Widget
     */
    fun updatePomodoroWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, PomodoroWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        FocusTimerManager.init(context)
        val totalSecs = FocusTimerManager.timerSecondsLeft.value
        val isRunning = FocusTimerManager.isTimerRunning.value
        val isFocus = FocusTimerManager.isFocusPhase.value

        // Countdown base reaches target in exactly totalSecs from now
        val baseTime = android.os.SystemClock.elapsedRealtime() + totalSecs * 1000L

        val headerText = if (isFocus) "POMODORO FOCUS 🎯" else "REST BREAK ☕"
        val headerColor = if (isFocus) 0xFF30D158.toInt() else 0xFFFF9500.toInt()

        val startPauseIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 4001, startPauseIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 4002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 4003, rootIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_pomodoro)
            
            views.setTextViewText(R.id.pomo_title, headerText)
            views.setTextColor(R.id.pomo_title, headerColor)
            
            // Native smooth count-down mechanism (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                views.setChronometerCountDown(R.id.pomo_time_display, true)
            }
            views.setChronometer(R.id.pomo_time_display, baseTime, null, isRunning)

            if (isRunning) {
                views.setTextViewText(R.id.btn_pomo_start_pause, "⏸ PAUSE")
            } else {
                views.setTextViewText(R.id.btn_pomo_start_pause, "▶ START")
            }

            views.setOnClickPendingIntent(R.id.btn_pomo_start_pause, startPausePending)
            views.setOnClickPendingIntent(R.id.btn_pomo_reset, resetPending)
            views.setOnClickPendingIntent(R.id.pomo_title, rootPending)
            views.setOnClickPendingIntent(R.id.pomo_time_display, rootPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    /**
     * Forces full updates across all widgets
     */
    fun updateAllWidgets(context: Context) {
        try {
            updateFriendsFocusWidget(context)
            updateStopwatchWidget(context)
            updatePomodoroWidget(context)
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Error updating widgets: ${e.message}")
        }
    }
}
