package com.example.receiver

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.LocalRepository

class HistorySyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("HistorySyncWorker", "Starting scheduled background history sync...")
        return try {
            val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val username = prefs.getString("current_username", null)
            if (username.isNullOrEmpty()) {
                Log.i("HistorySyncWorker", "No logged-in user found. Skipping sync.")
                return Result.success()
            }

            val syncPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val lastSync = syncPrefs.getLong("history_logs_last_sync_${username}", 0L)
            
            Log.i("HistorySyncWorker", "Performing delta background sync for user: $username since timestamp: $lastSync")
            
            val db = AppDatabase.getInstance(appContext)
            val repository = LocalRepository(db, appContext)
            
            repository.syncMissingHistoryLogs(username, lastSync)
            
            val newSyncTime = System.currentTimeMillis()
            syncPrefs.edit().putLong("history_logs_last_sync_${username}", newSyncTime).apply()
            
            Log.i("HistorySyncWorker", "Background history sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("HistorySyncWorker", "Error during background history sync: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            try {
                val syncRequest = androidx.work.PeriodicWorkRequestBuilder<HistorySyncWorker>(
                    12, java.util.concurrent.TimeUnit.HOURS
                ).setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                ).build()

                androidx.work.WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        "HistorySyncWorkerUnique",
                        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                        syncRequest
                    )
                Log.i("HistorySyncWorker", "Successfully scheduled periodic HistorySyncWorker to run twice a day (every 12h).")
            } catch (e: Exception) {
                Log.e("HistorySyncWorker", "Failed to schedule HistorySyncWorker: ${e.message}", e)
            }
        }
    }
}
