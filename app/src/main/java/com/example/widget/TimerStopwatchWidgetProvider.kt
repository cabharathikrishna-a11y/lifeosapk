package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.FocusTimerManager

class TimerStopwatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.updateStopwatchWidget(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        Log.d("TimerStopwatchWidget", "Widget received broadcast action: $action")
        
        FocusTimerManager.init(context)
        when (action) {
            "com.example.widget.ACTION_STOPWATCH_START_PAUSE" -> {
                if (FocusTimerManager.isStopwatchActive.value) {
                    FocusTimerManager.pauseStopwatch(context)
                } else {
                    FocusTimerManager.startStopwatch(context)
                }
                WidgetUpdater.updateStopwatchWidget(context)
            }
            "com.example.widget.ACTION_STOPWATCH_RESET" -> {
                FocusTimerManager.resetStopwatch(context)
                WidgetUpdater.updateStopwatchWidget(context)
            }
        }
    }
}
