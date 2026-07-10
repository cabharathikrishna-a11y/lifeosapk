package com.example.util

import android.content.Context
import android.util.Log

object DeletedTaskLogHelper {
    private const val PREFS_NAME = "deleted_tasks_log_prefs"
    private const val TAG = "DeletedTaskLog"

    fun logDeletedTask(context: Context, title: String, dueDate: String, gCalEventId: String?) {
        if (title.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        editor.putBoolean(titleDateKey, true)
        Log.d(TAG, "Logged deleted task by Title/Date: $titleDateKey")

        if (!gCalEventId.isNullOrEmpty()) {
            val eventIdKey = "gcal_event_id:$gCalEventId"
            editor.putBoolean(eventIdKey, true)
            Log.d(TAG, "Logged deleted task by GCalEventId: $eventIdKey")
        }
        
        editor.apply()
    }

    fun isTaskDeletedLocally(context: Context, title: String, dueDate: String): Boolean {
        if (title.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        return prefs.getBoolean(titleDateKey, false)
    }

    fun isGCalEventDeletedLocally(context: Context, gCalEventId: String): Boolean {
        if (gCalEventId.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eventIdKey = "gcal_event_id:$gCalEventId"
        return prefs.getBoolean(eventIdKey, false)
    }
    
    fun removeDeletedTaskFromLog(context: Context, title: String, dueDate: String, gCalEventId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleDateKey = "title_date:${cleanTitle}_$dueDate"
        editor.remove(titleDateKey)
        
        if (!gCalEventId.isNullOrEmpty()) {
            val eventIdKey = "gcal_event_id:$gCalEventId"
            editor.remove(eventIdKey)
        }
        
        editor.apply()
    }

    // --- Google Tasks Deletion Logging Support ---
    fun logDeletedGoogleTask(context: Context, title: String, gTaskId: String?) {
        if (title.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        editor.putBoolean(titleKey, true)
        Log.d(TAG, "Logged deleted Google Task by Title: $titleKey")

        if (!gTaskId.isNullOrEmpty()) {
            val gTaskIdKey = "g_task_id:$gTaskId"
            editor.putBoolean(gTaskIdKey, true)
            Log.d(TAG, "Logged deleted Google Task by GTaskId: $gTaskIdKey")
        }
        
        editor.apply()
    }

    fun isGoogleTaskDeletedLocally(context: Context, title: String): Boolean {
        if (title.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        return prefs.getBoolean(titleKey, false)
    }

    fun isGTaskIdDeletedLocally(context: Context, gTaskId: String): Boolean {
        if (gTaskId.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gTaskIdKey = "g_task_id:$gTaskId"
        return prefs.getBoolean(gTaskIdKey, false)
    }

    fun removeDeletedGoogleTaskFromLog(context: Context, title: String, gTaskId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val cleanTitle = title.lowercase().trim()
        val titleKey = "g_task_title:${cleanTitle}"
        editor.remove(titleKey)
        
        if (!gTaskId.isNullOrEmpty()) {
            val gTaskIdKey = "g_task_id:$gTaskId"
            editor.remove(gTaskIdKey)
        }
        
        editor.apply()
    }
}
