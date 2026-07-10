package com.example.api

import android.content.Context

/**
 * Configuration for the Firebase Realtime Database.
 * Contains URL constants for future online synchronization and integration.
 */
object FirebaseConfig {
    const val DATABASE_URL = "https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/"

    fun getDatabaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var url = prefs.getString("custom_firebase_db_url", DATABASE_URL) ?: DATABASE_URL
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    fun setDatabaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_firebase_db_url", url).commit()
    }
}
