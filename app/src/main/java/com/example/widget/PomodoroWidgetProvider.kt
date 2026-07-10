package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.FocusTimerManager

class PomodoroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.updatePomodoroWidget(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        Log.d("PomodoroWidgetProvider", "Pomo widget received action: $action")

        FocusTimerManager.init(context)
        when (action) {
            "com.example.widget.ACTION_POMO_START_PAUSE" -> {
                if (FocusTimerManager.isTimerRunning.value) {
                    FocusTimerManager.pauseTimer(context)
                } else {
                    FocusTimerManager.startTimer(context)
                }
                WidgetUpdater.updatePomodoroWidget(context)
            }
            "com.example.widget.ACTION_POMO_RESET" -> {
                FocusTimerManager.resetTimer(context)
                WidgetUpdater.updatePomodoroWidget(context)
            }
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                WidgetUpdater.updatePomodoroWidget(context)
            }
        }
    }
}
