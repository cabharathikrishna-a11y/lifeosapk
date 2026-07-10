package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileImageCacheManager {
    private const val TAG = "ProfileImageCache"
    private const val PREFS_NAME = "profile_image_cache_prefs"

    suspend fun getProfileImage(
        context: Context,
        username: String,
        photoUpdatedAt: Long,
        emojiOrBase64: String?
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (emojiOrBase64.isNullOrEmpty()) return@withContext null

        val cacheFile = File(context.cacheDir, "profile_${username}.jpg")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUpdatedAt = prefs.getLong("profile_${username}_updated_at", -1L)

        // 1. Check if cache exists and is fresh
        if (cacheFile.exists() && storedUpdatedAt == photoUpdatedAt) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    Log.i(TAG, "Loaded profile image directly from cache for $username")
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode cached file for $username", e)
            }
        }

        // 2. Timestamps don't match, or file missing -> Download / Rebuild!
        Log.i(TAG, "Cache miss or outdated for $username. Stored=$storedUpdatedAt, Remote=$photoUpdatedAt")
        var bitmapToSave: Bitmap? = null

        // Pattern A: If it's a base64 string, we decode it directly (instant offline sync/reflection)
        if (emojiOrBase64.startsWith("base64:")) {
            try {
                val base64Data = emojiOrBase64.substringAfter("base64:")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                bitmapToSave = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding base64 avatar for $username", e)
            }
        } else {
            // Pattern B: Download from Firebase Storage via REST API
            try {
                val storageUrl = "https://firebasestorage.googleapis.com/v0/b/lifeosca.firebasestorage.app/o/profiles%2F${username}%2Fprofile_photo.jpg?alt=media"
                val url = URL(storageUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.connect()
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = conn.inputStream
                    bitmapToSave = BitmapFactory.decodeStream(stream)
                } else {
                    Log.w(TAG, "No photo found on Firebase Storage for $username (HTTP ${conn.responseCode})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading photo from Firebase Storage for $username", e)
            }
        }

        // 3. Save to Cache and Update SharedPreferences
        if (bitmapToSave != null) {
            try {
                FileOutputStream(cacheFile).use { out ->
                    bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                prefs.edit().putLong("profile_${username}_updated_at", photoUpdatedAt).apply()
                Log.i(TAG, "Successfully cached and updated profile image for $username")
                return@withContext bitmapToSave
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile photo to cache for $username", e)
            }
        }

        return@withContext null
    }
}
