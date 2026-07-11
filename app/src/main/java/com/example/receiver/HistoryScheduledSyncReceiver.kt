package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryScheduledSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("HistoryScheduledSyncReceiver", "Alarm triggered for scheduled history sync!")
        
        val goAsyncPendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val username = prefs.getString("current_username", null)
                if (!username.isNullOrEmpty()) {
                    Log.i("HistoryScheduledSyncReceiver", "Syncing history logs from Firebase for user $username...")
                    val db = AppDatabase.getInstance(context)
                    val repository = LocalRepository(db, context)
                    
                    val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val lastSync = syncPrefs.getLong("history_logs_last_sync_${username}", 0L)
                    
                    repository.syncMissingHistoryLogs(username, lastSync)
                    
                    val newSyncTime = System.currentTimeMillis()
                    syncPrefs.edit().putLong("history_logs_last_sync_${username}", newSyncTime).apply()
                    Log.i("HistoryScheduledSyncReceiver", "Scheduled history sync completed successfully.")
                } else {
                    Log.i("HistoryScheduledSyncReceiver", "No logged-in user found. Skipping sync.")
                }
            } catch (e: Exception) {
                Log.e("HistoryScheduledSyncReceiver", "Error during scheduled history sync", e)
            } finally {
                goAsyncPendingResult.finish()
                // Re-schedule alarms to ensure they keep running
                scheduleSyncAlarms(context)
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_9AM = 9001
        private const val REQUEST_CODE_9PM = 9002

        fun scheduleSyncAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            scheduleAlarmAtHour(context, alarmManager, 9, REQUEST_CODE_9AM)  // 9:00 AM
            scheduleAlarmAtHour(context, alarmManager, 21, REQUEST_CODE_9PM) // 9:00 PM
            
            Log.i("HistoryScheduledSyncReceiver", "History sync alarms scheduled for 9:00 AM and 9:00 PM.")
        }

        private fun scheduleAlarmAtHour(context: Context, alarmManager: AlarmManager, hour: Int, requestCode: Int) {
            val intent = Intent(context, HistoryScheduledSyncReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // If the scheduled time is in the past, set it for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}
