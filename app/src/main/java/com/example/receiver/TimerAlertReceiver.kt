package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.FocusTimerManager
import com.example.util.AlarmScheduler

class TimerAlertReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "timer_alert_channel"
    private val CHANNEL_NAME = "Timer Completed Alert"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("TimerAlertReceiver", "Timer end alarm received with action: $action")

        if (action == "com.example.action.TIMER_FINISHED") {
            // 1. Play the strong bell sound with vibration
            try {
                FocusTimerManager.playStrongBellSoundWithVibration(context)
            } catch (e: Exception) {
                Log.e("TimerAlertReceiver", "Failed to play bell sound: ${e.message}", e)
            }

            // 2. Show a high-priority notification to alert the user
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts when focus or break sessions finish"
                    enableLights(true)
                    enableVibration(true)
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Open application on click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                AlarmScheduler.TIMER_ALARM_REQUEST_CODE,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("LifeOS: Session Finished! 🎯")
                .setContentText("Congratulations! Your timer completed successfully.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500))

            notificationManager.notify(AlarmScheduler.TIMER_ALARM_REQUEST_CODE, builder.build())
        }
    }
}
