@file:Suppress("DEPRECATION")
package com.example.util

import okhttp3.RequestBody.Companion.asRequestBody
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

object GoogleDriveSyncManager {
    private val driveMutex = Mutex()
    private const val TAG = "GoogleDriveSync"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file"
    private const val BACKUP_FILE_NAME = "focus_backup.json"
    private const val ALL_DATA_BACKUP_FILE_NAME = "app_data_backup.zip"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Obtains the OAuth2 access token for the signed-in Google account.
     * If authentication resolution is required (e.g. user needs to approve permission),
     * [onAuthResolutionRequired] is invoked with the required Intent.
     */
    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_file_backup_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token: ${e.message}", e)
            null
        }
    }

    /**
     * Checks whether the user has signed in and granted the Drive AppData scope.
     */
    fun hasDrivePermission(context: Context): Boolean {
        val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, driveScope)
    }

    /**
     * Performs a backup of focus-related data (focus records list, total focus minutes, today's pomos count)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Load localized focus data from SharedPreferences
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val focusRecordsSerialized = prefs.getString("focus_records_list", "") ?: ""
                val totalMinutes = prefs.getInt("total_focus_minutes", 0)
                val pomosCount = prefs.getInt("today_pomos_count", 0)

                // 2. Build beautiful focus backup JSON payload
                val backupJson = JSONObject().apply {
                    put("focus_records_list", focusRecordsSerialized)
                    put("total_focus_minutes", totalMinutes)
                    put("today_pomos_count", pomosCount)
                    put("last_sync_timestamp", System.currentTimeMillis())
                    put("device_model", android.os.Build.MODEL)
                }
                val contentStr = backupJson.toString()

                // 3. Find if the file already exists in AppData
                var fileId = findBackupFileId(token)
                if (fileId == null) {
                    // Not found, create new file metadata first
                    Log.i(TAG, "Backup file not found in Google Drive. Creating a new one...")
                    fileId = createBackupFileMetadata(token)
                    if (fileId == null) {
                        return@withLock Pair(false, "Failed to initialize backup space in Google Drive.")
                    }
                }

                // 4. Upload/Patch the content to Google Drive
                val uploadSuccess = uploadBackupFileContent(token, fileId, contentStr)
                if (uploadSuccess) {
                    // Save last synced timestamp locally
                    prefs.edit().putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully backed up focus history to Google Drive.")
                } else {
                    Pair(false, "Failed to upload focus data to Google Drive.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error backing up focus data: ${e.message}", e)
                Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Retrieves focus-related data from Google Drive AppData folder,
     * reconciles and merges it with current local focus history (avoiding duplicates),
     * and restores the state.
     */
    suspend fun restoreFocusData(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Find the backup file in AppData folder
                val fileId = findBackupFileId(token)
                    ?: return@withLock Pair(false, "No backup file found on your Google Drive. Save a backup first.")

                // 2. Download backup content
                val contentStr = downloadBackupFileContent(token, fileId)
                    ?: return@withLock Pair(false, "Failed to read backup from Google Drive.")

                val backupJson = JSONObject(contentStr)
                val remoteSerializedRecords = backupJson.optString("focus_records_list", "")
                val remoteTotalMinutes = backupJson.optInt("total_focus_minutes", 0)
                val remotePomosCount = backupJson.optInt("today_pomos_count", 0)

                // 3. Load local focus records
                val localRecords = FocusTimerManager.loadFocusRecords(context)
                
                // Parse remote records from the serialized string
                val remoteRecords = parseSerializedFocusRecords(remoteSerializedRecords)

                // 4. Reconciliation: Merge lists and keep unique records (using unique key combination)
                val mergedRecords = (localRecords + remoteRecords).distinctBy { record ->
                    "${record.startTime}_${record.endTime}_${record.taskTitle}_${record.durationSeconds}"
                }

                // 5. Update local storage
                FocusTimerManager.saveFocusRecords(context, mergedRecords)
                
                // Overwrite total stats with max values or merged values to preserve progress
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val finalTotalMinutes = maxOf(prefs.getInt("total_focus_minutes", 0), remoteTotalMinutes, mergedRecords.sumOf { it.durationMinutes })
                val finalPomosCount = maxOf(prefs.getInt("today_pomos_count", 0), remotePomosCount)

                prefs.edit().apply {
                    putInt("total_focus_minutes", finalTotalMinutes)
                    putInt("today_pomos_count", finalPomosCount)
                    putLong("gd_focus_last_sync_timestamp", System.currentTimeMillis())
                    apply()
                }

                // 6. Update FocusTimerManager live states on main thread
                withContext(Dispatchers.Main) {
                    FocusTimerManager.setFocusRecords(mergedRecords)
                    FocusTimerManager.setTotalFocusMinutes(finalTotalMinutes)
                    FocusTimerManager.setTodayPomosCount(finalPomosCount)
                }

                Pair(true, "Successfully restored and merged ${remoteRecords.size} focus records from Google Drive!")
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error restoring focus data: ${e.message}", e)
                Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Performs a backup of the entire app database and attachment files (ZIP package)
     * to the user's hidden Google Drive AppData folder.
     */
    suspend fun backupAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Create a temp file to hold our zip backup
                val tempFile = java.io.File(context.cacheDir, "temp_app_data_backup.zip")
                if (tempFile.exists()) tempFile.delete()

                // 2. Export database and files to the temp zip file
                val exportSuccess = tempFile.outputStream().use { fos ->
                    DatabaseBackupHelper.exportDataToStream(context, database, fos)
                }

                if (!exportSuccess) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withLock Pair(false, "Failed to compile backup package locally.")
                }

                // 3. Find if the file already exists in AppData
                var fileId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
                if (fileId == null) {
                    fileId = createFileMetadata(token, ALL_DATA_BACKUP_FILE_NAME)
                    if (fileId == null) {
                        tempFile.delete()
                        return@withLock Pair(false, "Failed to initialize backup slot in Google Drive.")
                    }
                }

                // 4. Upload the zip binary
                val requestBody = tempFile.asRequestBody("application/zip".toMediaType())
                val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/zip")
                    .patch(requestBody)
                    .build()

                var uploadSuccess = false
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        uploadSuccess = true
                    } else {
                        Log.e(TAG, "Error uploading zip: code=${response.code} body=${response.body?.string()}")
                    }
                }

                // Clean up
                tempFile.delete()

                if (uploadSuccess) {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully backed up all app data and files to Google Drive.")
                } else {
                    Pair(false, "Failed to upload backup package to Google Drive.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error backing up all app data", e)
                Pair(false, "Backup Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Downloads and restores the entire app database and attachment files (ZIP package)
     * from the user's hidden Google Drive AppData folder.
     */
    suspend fun restoreAllAppData(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                // 1. Find the file ID in Google Drive
                val fileId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
                    ?: return@withLock Pair(false, "No full app data backup found on Google Drive. Save a backup first.")

                // 2. Download zip content
                val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val tempFile = java.io.File(context.cacheDir, "temp_app_data_restore.zip")
                if (tempFile.exists()) tempFile.delete()

                var downloadSuccess = false
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        downloadSuccess = true
                    } else {
                        Log.e(TAG, "Failed downloading zip backup: code=${response.code}")
                    }
                }

                if (!downloadSuccess) {
                    tempFile.delete()
                    return@withLock Pair(false, "Failed to download backup package from Google Drive.")
                }

                // 3. Import data from temp zip file
                val importSuccess = tempFile.inputStream().use { fis ->
                    DatabaseBackupHelper.importDataFromStream(context, database, fis)
                }

                tempFile.delete()

                if (importSuccess) {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("gd_all_last_sync_timestamp", System.currentTimeMillis()).apply()
                    Pair(true, "Successfully restored all app data and files from Google Drive!")
                } else {
                    Pair(false, "Failed to restore downloaded backup package.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error restoring all app data", e)
                Pair(false, "Restore Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Queries Google Drive to find the size of the uploaded backups.
     * Returns a map of file name to size in bytes.
     */
    suspend fun getBackupSizes(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context) ?: return@withContext emptyMap()
        val result = mutableMapOf<String, Long>()
        try {
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val filesArray = JSONObject(bodyStr).optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val fileObj = filesArray.getJSONObject(i)
                            val name = fileObj.optString("name", "")
                            val size = fileObj.optLong("size", 0L)
                            if (name.isNotEmpty()) {
                                result[name] = size
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching backup sizes from Google Drive: ${e.message}", e)
        }
        result
    }

    /**
     * Checks if any backup data exists in Google Drive.
     */
    suspend fun hasExistingBackupData(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken(context) ?: return@withContext false
        try {
            val focusId = findFileId(token, BACKUP_FILE_NAME)
            val dbId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
            focusId != null || dbId != null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing backup data", e)
            false
        }
    }

    /**
     * Reconciles/Retrieves whichever backup files exist in Google Drive.
     */
    suspend fun checkAndRetrieveDriveData(context: Context, database: com.example.data.AppDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
            ?: return@withContext Pair(false, "Authentication required.")
        
        try {
            val focusId = findFileId(token, BACKUP_FILE_NAME)
            val dbId = findFileId(token, ALL_DATA_BACKUP_FILE_NAME)
            
            if (focusId == null && dbId == null) {
                return@withContext Pair(false, "No existing backup files found.")
            }
            
            val results = mutableListOf<String>()
            var anySuccess = false
            
            if (dbId != null) {
                val (success, msg) = restoreAllAppData(context, database)
                if (success) {
                    anySuccess = true
                    results.add("App database restored.")
                } else {
                    results.add("App database restore failed: $msg")
                }
            }
            
            if (focusId != null) {
                val (success, msg) = restoreFocusData(context)
                if (success) {
                    anySuccess = true
                    results.add("Focus data restored.")
                } else {
                    results.add("Focus data restore failed: $msg")
                }
            }
            
            Pair(anySuccess, results.joinToString("\n"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndRetrieveDriveData", e)
            Pair(false, e.localizedMessage ?: "Unknown restore error.")
        }
    }

    /**
     * Searches for 'focus_backup.json' in the AppData folder.
     * Returns its fileId or null if not found.
     */
    private fun findBackupFileId(accessToken: String): String? {
        return findFileId(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Creates empty file metadata for 'focus_backup.json' in 'appDataFolder'.
     * Returns the created fileId or null.
     */
    private fun createBackupFileMetadata(accessToken: String): String? {
        return createFileMetadata(accessToken, BACKUP_FILE_NAME)
    }

    /**
     * Generic file finder inside Google Drive appDataFolder.
     */
    private fun findFileId(accessToken: String, fileName: String): String? {
        val query = "name = '$fileName'"
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "URLEncode failed", e)
            return null
        }
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error listing files: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive API Error (HTTP ${response.code}): $bodyStr")
            }
            val filesArray = JSONObject(bodyStr).getJSONArray("files")
            if (filesArray.length() > 0) {
                return filesArray.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    /**
     * Generic file metadata creator inside Google Drive appDataFolder.
     */
    private fun createFileMetadata(accessToken: String, fileName: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val bodyJson = JSONObject().apply {
            put("name", fileName)
            val parentsArray = org.json.JSONArray().apply {
                put("appDataFolder")
            }
            put("parents", parentsArray)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Error creating metadata: code=${response.code} body=$bodyStr")
                throw Exception("Google Drive Creation Error (HTTP ${response.code}): $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Uploads/Overwrites the file content using PATCH.
     */
    private fun uploadBackupFileContent(accessToken: String, fileId: String, content: String): Boolean {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .patch(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true
            }
            Log.e(TAG, "Error uploading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Upload Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Downloads file content from Google Drive.
     */
    private fun downloadBackupFileContent(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return bodyStr
            }
            Log.e(TAG, "Error downloading content: code=${response.code} body=$bodyStr")
            throw Exception("Google Drive Download Error (HTTP ${response.code}): $bodyStr")
        }
    }

    /**
     * Parsed serialized string back to FocusRecord list.
     */
    private fun parseSerializedFocusRecords(serialized: String): List<com.example.ui.FocusRecord> {
        if (serialized.isBlank()) return emptyList()
        return try {
            serialized.split("\n").mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val dateValue = if (parts.size >= 5) parts[4] else ""
                    val notesValue = if (parts.size >= 6) {
                        try {
                            String(android.util.Base64.decode(parts[5], android.util.Base64.NO_WRAP))
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { "" }
                    } else ""
                    val originalMins = parts[3].toInt()
                    val originalSecs = if (parts.size >= 7) parts[6].toIntOrNull() ?: (originalMins * 60) else (originalMins * 60)
                    
                    val durationMins = if (originalMins > 720) 720 else originalMins
                    val durationSecs = if (originalSecs > 43200) 43200 else originalSecs

                    com.example.ui.FocusRecord(parts[0], parts[1], parts[2], durationMins, dateValue, notesValue, durationSecs)
                } else null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing serialized focus records: ${e.message}")
            emptyList()
        }
    }

    /**
     * Uploads a physical media file to standard Google Drive (outside appDataFolder, under "LifeOS_Shared_Media" folder),
     * sets public read permissions on it, and returns its public sharing link.
     */
    suspend fun uploadPublicMediaFileDirect(
        context: Context,
        accessToken: String,
        file: java.io.File
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Check if the "LifeOS_Shared_Media" folder exists, if not create it
            val folderId = findOrCreateSharedFolder(accessToken, "LifeOS_Shared_Media") ?: return@withContext null

            // 2. Check if file already exists in that folder to avoid duplicates
            val existingFileId = findFileInFolder(accessToken, file.name, folderId)
            val fileId = if (existingFileId != null) {
                existingFileId
            } else {
                // Create file metadata in that folder
                val createdId = createFileMetadataInFolder(accessToken, file.name, folderId) ?: return@withContext null
                // Upload content
                val mimeType = getMimeType(file)
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", mimeType)
                    .patch(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed uploading public file content for ${file.name}: code=${response.code}")
                        return@withContext null
                    }
                }
                createdId
            }

            // 3. Make file public (accessible to anyone with link as reader)
            makeFilePublic(accessToken, fileId)

            // 4. Return sharing direct download link
            "https://drive.google.com/uc?export=download&id=$fileId"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading public media file ${file.name}", e)
            null
        }
    }

    private fun findOrCreateSharedFolder(token: String, folderName: String): String? {
        try {
            // Find folder
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    if (files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }

            // Create folder
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }
            val createRequest = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return JSONObject(response.body?.string() ?: "").getString("id")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in findOrCreateSharedFolder", e)
        }
        return null
    }

    private fun findFileInFolder(token: String, name: String, folderId: String): String? {
        try {
            val query = "name = '$name' and '$folderId' in parents and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val files = JSONObject(body).getJSONArray("files")
                    if (files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in findFileInFolder", e)
        }
        return null
    }

    private fun createFileMetadataInFolder(token: String, name: String, folderId: String): String? {
        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", name)
                val parents = org.json.JSONArray().apply { put(folderId) }
                put("parents", parents)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return JSONObject(response.body?.string() ?: "").getString("id")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in createFileMetadataInFolder", e)
        }
        return null
    }

    private fun makeFilePublic(token: String, fileId: String) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions"
            val body = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to make file $fileId public: code=${response.code}")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in makeFilePublic", e)
        }
    }

    private fun getMimeType(file: java.io.File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    data class GoogleSheetFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val webViewLink: String,
        val size: Long
    )

    suspend fun listGoogleSheets(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, List<GoogleSheetFile>> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, emptyList())

        try {
            val encodedQuery = java.net.URLEncoder.encode("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,modifiedTime,webViewLink,size)&orderBy=modifiedTime%20desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to list Google Sheets: code=${response.code}")
                    return@withContext Pair(false, emptyList())
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()
                val sheetsList = mutableListOf<GoogleSheetFile>()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val id = fileObj.optString("id", "")
                    val name = fileObj.optString("name", "Untitled Spreadsheet")
                    val modifiedTime = fileObj.optString("modifiedTime", "")
                    val webViewLink = fileObj.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$id/edit")
                    val size = fileObj.optLong("size", 0L)
                    sheetsList.add(GoogleSheetFile(id, name, modifiedTime, webViewLink, size))
                }
                Pair(true, sheetsList)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Google Sheets: ${e.message}", e)
            Pair(false, emptyList())
        }
    }

    suspend fun createGoogleSheet(
        context: Context,
        title: String,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val body = JSONObject().apply {
                put("name", title)
                put("mimeType", "application/vnd.google-apps.spreadsheet")
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to create Google Sheet: code=${response.code} body=$errBody")
                    return@withContext Pair(false, "Failed to create Google Sheet: ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val createdId = json.optString("id", "")
                val webLink = json.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$createdId/edit")
                
                // Set the permission so anyone can view it or keep it private but shareable
                makeFilePublic(token, createdId)
                
                Pair(true, webLink)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Sheet: ${e.message}", e)
            Pair(false, "Error: ${e.localizedMessage}")
        }
    }

    /**
     * Performs a full 2-way sync for Google Keep Notes.
     */
    suspend fun syncKeepNotes(
        context: Context,
        database: com.example.data.AppDatabase,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        driveMutex.withLock {
            val token = getAccessToken(context, onAuthResolutionRequired)
                ?: return@withLock Pair(false, "Authorization required. Please connect your Google Drive.")

            try {
                val keepNoteDao = database.keepNoteDao()
                val localNotes = keepNoteDao.getAllKeepNotesDirect()

                // 1. Find file named "google_keep_notes.json" in AppData
                var fileId = findFileId(token, "google_keep_notes.json")
                val remoteNotes = mutableListOf<com.example.data.KeepNote>()

                if (fileId != null) {
                    // Download cloud content
                    val cloudContent = downloadBackupFileContent(token, fileId)
                    if (!cloudContent.isNullOrBlank()) {
                        val jsonArray = JSONArray(cloudContent)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            remoteNotes.add(
                                com.example.data.KeepNote(
                                    title = obj.optString("title", ""),
                                    content = obj.optString("content", ""),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                    isPinned = obj.optBoolean("isPinned", false),
                                    colorHex = obj.optString("colorHex", "#202124"),
                                    isSynced = true,
                                    websiteUrl = if (obj.isNull("websiteUrl")) null else obj.optString("websiteUrl"),
                                    customLogoUrl = if (obj.isNull("customLogoUrl")) null else obj.optString("customLogoUrl")
                                )
                            )
                        }
                    }
                } else {
                    // Create metadata for the new file
                    fileId = createFileMetadata(token, "google_keep_notes.json")
                    if (fileId == null) {
                        return@withLock Pair(false, "Failed to initialize Google Keep Notes space in Google Drive.")
                    }
                }

                // 2. Reconciliation: Merge local & remote notes
                val mergedMap = mutableMapOf<String, com.example.data.KeepNote>()
                for (note in localNotes) {
                    val signature = "${note.title.trim()}|${note.content.trim()}"
                    mergedMap[signature] = note
                }
                for (remote in remoteNotes) {
                    val signature = "${remote.title.trim()}|${remote.content.trim()}"
                    val existing = mergedMap[signature]
                    if (existing == null || remote.timestamp > existing.timestamp) {
                        mergedMap[signature] = remote
                    }
                }

                val mergedList = mergedMap.values.toList()

                // 3. Serialize merged list back to JSON
                val uploadArray = JSONArray()
                for (note in mergedList) {
                    val obj = JSONObject().apply {
                        put("title", note.title)
                        put("content", note.content)
                        put("timestamp", note.timestamp)
                        put("isPinned", note.isPinned)
                        put("colorHex", note.colorHex)
                        put("websiteUrl", note.websiteUrl ?: JSONObject.NULL)
                        put("customLogoUrl", note.customLogoUrl ?: JSONObject.NULL)
                    }
                    uploadArray.put(obj)
                }

                val contentStr = uploadArray.toString()

                // 4. Upload merged back to Google Drive AppData
                val uploadSuccess = uploadBackupFileContent(token, fileId, contentStr)
                if (!uploadSuccess) {
                    return@withLock Pair(false, "Failed to write synchronized notes back to Google Drive.")
                }

                // 5. Update local database
                keepNoteDao.clearAllKeepNotes()
                for (note in mergedList) {
                    keepNoteDao.insertKeepNote(note.copy(isSynced = true))
                }

                Pair(true, "Successfully merged and synchronized ${mergedList.size} notes!")
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error syncing Google Keep notes: ${e.message}", e)
                Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }
}
