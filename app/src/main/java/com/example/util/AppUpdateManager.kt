package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.api.FirebaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    object SecuringData : UpdateStatus()
    data class NewVersionAvailable(val versionId: Int, val currentVersionCode: Int, val apkFileId: String?) : UpdateStatus()
    data class NoUpdateAvailable(val cloudVersion: Int, val localVersion: Int) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val DEFAULT_FOLDER_ID = "1c8hXKg8YfX3cG8JOiHDr4he75eYPm17N"
    
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus

    @Volatile
    private var isDownloadingActive = false

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Kicks off update check asynchronously in a global, non-cancellable scope.
     */
    fun triggerCheckForUpdates(context: Context, manualCheck: Boolean = false) {
        updateScope.launch {
            try {
                checkForUpdates(context, manualCheck)
            } catch (e: Exception) {
                Log.e(TAG, "Global update check failed", e)
                _updateStatus.value = UpdateStatus.Error("Failed to check for updates: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Kicks off the download and install flow asynchronously in a global, non-cancellable scope.
     */
    fun startDownloadAndInstall(context: Context, providedFileId: String?) {
        updateScope.launch {
            try {
                downloadAndInstallUpdate(context, providedFileId)
            } catch (e: Exception) {
                Log.e(TAG, "Global download & install failed", e)
                _updateStatus.value = UpdateStatus.Error("Failed to download or install update: ${e.localizedMessage}")
            }
        }
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_auto_update_enabled", true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_auto_update_enabled", enabled).apply()
    }

    fun isForceUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_force_update_enabled", false)
    }

    fun setForceUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_force_update_enabled", enabled).apply()
    }

    fun isPauseUpdatesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_pause_updates", false)
    }

    fun setPauseUpdatesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_pause_updates", enabled).apply()
    }

    fun getGithubOwner(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pref_github_owner", "cabharathikrishna") ?: "cabharathikrishna"
    }

    fun setGithubOwner(context: Context, owner: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_github_owner", owner).apply()
    }

    fun getGithubRepo(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pref_github_repo", "Life.os") ?: "Life.os"
    }

    fun setGithubRepo(context: Context, repo: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_github_repo", repo).apply()
    }

    fun getPendingUpdateVersion(context: Context): Int {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("pref_pending_update_version_code", -1)
    }

    fun setPendingUpdateVersion(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("pref_pending_update_version_code", versionCode).apply()
    }

    fun clearPendingUpdateVersion(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("pref_pending_update_version_code").apply()
    }

    fun getReadyApkPath(context: Context): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pref_ready_apk_path", null)
    }

    fun setReadyApkPath(context: Context, path: String?) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (path == null) {
            prefs.edit().remove("pref_ready_apk_path").commit()
        } else {
            prefs.edit().putString("pref_ready_apk_path", path).commit()
        }
    }

    fun getRunningFirebaseVersion(context: Context): Int {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getInt("pref_running_firebase_version", -1)
        if (saved == -1) {
            val currentCode = getCurrentVersionCode(context)
            prefs.edit().putInt("pref_running_firebase_version", currentCode).apply()
            return currentCode
        }
        return saved
    }

    fun setRunningFirebaseVersion(context: Context, version: Int) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("pref_running_firebase_version", version).apply()
    }

    /**
     * Checks if the app was recently updated (current version code is higher than stored version code,
     * or the app package has been reinstalled/rebuilt with a newer timestamp).
     * If so, clears pending update data, cleans up old downloaded APKs, and returns the new version code.
     */
    fun checkAndNotifyUpgradeComplete(context: Context): Int? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastKnown = prefs.getInt("pref_last_known_version_code", -1)
        val current = getCurrentVersionCode(context)
        
        // Retrieve last known update time of the package to detect fresh compilation/reinstall in AI Studio or store
        val lastKnownUpdateTime = prefs.getLong("pref_last_known_update_time", 0L)
        var packageReinstalled = false
        var currentUpdateTime = 0L
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            currentUpdateTime = packageInfo.lastUpdateTime
            if (lastKnownUpdateTime > 0L && currentUpdateTime > lastKnownUpdateTime) {
                packageReinstalled = true
                Log.i(TAG, "Reinstall/Rebuild detected via package lastUpdateTime: $currentUpdateTime (previously: $lastKnownUpdateTime)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get package lastUpdateTime", e)
        }
        
        val installTarget = prefs.getInt("pref_install_target_version", -1)
        val runningFirebase = getRunningFirebaseVersion(context)
        
        var upgradeDetected = false
        var upgradedToVersion = current
        
        if (installTarget != -1 && installTarget > runningFirebase) {
            setRunningFirebaseVersion(context, installTarget)
            prefs.edit().remove("pref_install_target_version").apply()
            upgradeDetected = true
            upgradedToVersion = installTarget
            Log.i(TAG, "Upgrade detected via Firebase install target! Running Firebase version is now Build $installTarget")
        }
        
        if (lastKnown == -1) {
            prefs.edit().putInt("pref_last_known_version_code", current).apply()
            if (currentUpdateTime > 0L) {
                prefs.edit().putLong("pref_last_known_update_time", currentUpdateTime).apply()
            }
            if (upgradeDetected) {
                clearPendingUpdateVersion(context)
                sendUpgradeNotification(context, upgradedToVersion)
                return upgradedToVersion
            }
            return null
        }
        
        if (current > lastKnown || upgradeDetected || packageReinstalled) {
            Log.i(TAG, "Upgrade or reinstall condition met: current > lastKnown=${current > lastKnown}, upgradeDetected=$upgradeDetected, packageReinstalled=$packageReinstalled")
            
            prefs.edit().putInt("pref_last_known_version_code", current).apply()
            if (currentUpdateTime > 0L) {
                prefs.edit().putLong("pref_last_known_update_time", currentUpdateTime).apply()
            }
            
            if (current > lastKnown) {
                Log.i(TAG, "Upgrade detected via Package Version Code! Upgraded from Build $lastKnown to Build $current")
                setRunningFirebaseVersion(context, current)
                upgradedToVersion = current
            } else if (packageReinstalled) {
                Log.i(TAG, "Upgrade/Reinstall synchronized via Package Last Update Time. Running Firebase version is updated.")
                setRunningFirebaseVersion(context, maxOf(current, installTarget, runningFirebase))
            }
            
            clearPendingUpdateVersion(context)
            
            // Delete all downloaded APKs and clean up updates cache folder
            try {
                val updateDir = File(context.cacheDir, "updates")
                if (updateDir.exists()) {
                    updateDir.listFiles()?.forEach { file ->
                        file.delete()
                        Log.i(TAG, "Deleted old update file during clean up: ${file.name}")
                    }
                    updateDir.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean updates folder", e)
            }
            setReadyApkPath(context, null)
            
            sendUpgradeNotification(context, upgradedToVersion)
            return upgradedToVersion
        }
        
        // Ensure we persist the current update time if it was never recorded
        if (currentUpdateTime > 0L && lastKnownUpdateTime == 0L) {
            prefs.edit().putLong("pref_last_known_update_time", currentUpdateTime).apply()
        }
        
        return null
    }

    private fun sendUpgradeNotification(context: Context, version: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "app_update_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "System Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Update Installed Successfully! 🎉")
                .setContentText("Life OS has been updated to Build $version (v${getCurrentVersionName(context)}). Enjoy the new features!")
                .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Life OS has been updated to Build $version (v${getCurrentVersionName(context)}). Enjoy the new features! Your data is fully secure."))
                .build()
            
            notificationManager.notify(10011, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send update completion notification", e)
        }
    }

    /**
     * Retrieves the current app's version code.
     */
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version code", e)
            1
        }
    }

    /**
     * Retrieves the current app's version name.
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version name", e)
            "1.0"
        }
    }

    /**
     * Parses the given JSON string to extract the highest version code and its corresponding apkFileId.
     * Supports standard updates JSON mapping, version entries list, arrays, etc.
     */
    private fun findHighestVersionInJson(body: String): Pair<Int, String?>? {
        try {
            val trimmed = body.trim()
            var highestVersion = -1
            var bestFileId: String? = null

            fun checkEntry(vId: Int, fileId: String?) {
                if (vId > highestVersion && !fileId.isNullOrBlank() && fileId != "null") {
                    highestVersion = vId
                    bestFileId = fileId
                }
            }

            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.get(key)
                    
                    // Case A: key is a version code (like "5"), value is an object or string
                    val keyAsInt = key.toIntOrNull()
                    if (keyAsInt != null) {
                        if (value is JSONObject) {
                            val fileId = value.optString("apkFileId", value.optString("apk_file_id", value.optString("fileId", "")))
                            checkEntry(keyAsInt, fileId)
                        } else if (value is String) {
                            checkEntry(keyAsInt, value)
                        }
                    }

                    // Case B: standard object with "versionId" inside
                    if (value is JSONObject) {
                        val vId = value.optInt("versionId", value.optInt("version_id", value.optInt("version", -1)))
                        val fileId = value.optString("apkFileId", value.optString("apk_file_id", value.optString("fileId", "")))
                        checkEntry(vId, fileId)
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    if (array.isNull(i)) continue
                    val value = array.get(i)
                    if (value is JSONObject) {
                        val vId = value.optInt("versionId", value.optInt("version_id", value.optInt("version", -1)))
                        val fileId = value.optString("apkFileId", value.optString("apk_file_id", value.optString("fileId", "")))
                        checkEntry(vId, fileId)
                    }
                }
            }

            if (highestVersion != -1) {
                return Pair(highestVersion, bestFileId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding highest version in JSON", e)
        }
        return null
    }

    /**
     * Checks for updates from Firebase Realtime Database.
     */
    suspend fun checkForUpdates(context: Context, manualCheck: Boolean = false) {
        if (_updateStatus.value is UpdateStatus.Downloading) {
            Log.i(TAG, "Already downloading an update, ignoring check request")
            return
        }

        if (isPauseUpdatesEnabled(context) && !manualCheck) {
            Log.i(TAG, "Updates are currently paused. Skipping automatic check.")
            _updateStatus.value = UpdateStatus.Idle
            return
        }

        _updateStatus.value = UpdateStatus.Checking
        withContext(Dispatchers.IO) {
            val errorLogs = mutableListOf<String>()
            try {
                val packageCode = getCurrentVersionCode(context)
                val runningFirebase = getRunningFirebaseVersion(context)
                val currentCode = maxOf(packageCode, runningFirebase)
                
                // 1. Fetch update config from Firebase
                var targetVersionCode = -1
                var apkFileId: String? = null
                
                // Let's try multiple potential paths in Firebase to find the highest/latest update version and its apkFileId
                val pathsToTry = listOf(
                    "update_config.json",
                    "versions.json",
                    "releases.json",
                    "updates.json",
                    "update_history.json",
                    "update_config/versions.json",
                    "update_config/history.json"
                )

                for (path in pathsToTry) {
                    try {
                        val url = "${com.example.api.FirebaseClient.activeUrl}$path"
                        val request = Request.Builder().url(url).get().build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank() && body != "null" && !body.contains("\"error\"")) {
                                    if (path == "update_config.json") {
                                        val json = JSONObject(body)
                                        val vId = json.optInt("versionId", json.optInt("version_id", -1))
                                        val fId = json.optString("apkFileId", json.optString("apk_file_id", ""))
                                        if (vId > targetVersionCode) {
                                            targetVersionCode = vId
                                            apkFileId = if (fId != "null" && fId.isNotEmpty()) fId else null
                                        }
                                        
                                        // Learn owner and repository names dynamically from Firebase
                                        val owner = json.optString("githubOwner", json.optString("github_owner", ""))
                                        val repo = json.optString("githubRepo", json.optString("github_repo", ""))
                                        if (owner.isNotEmpty()) {
                                            setGithubOwner(context, owner)
                                            Log.d(TAG, "Dynamically synced GitHub owner from Firebase config: $owner")
                                        }
                                        if (repo.isNotEmpty()) {
                                            setGithubRepo(context, repo)
                                            Log.d(TAG, "Dynamically synced GitHub repository from Firebase config: $repo")
                                        }
                                    }
                                    
                                    val historyResult = findHighestVersionInJson(body)
                                    if (historyResult != null && historyResult.first > targetVersionCode) {
                                        targetVersionCode = historyResult.first
                                        apkFileId = historyResult.second
                                        Log.d(TAG, "Found higher version ${historyResult.first} with file ID ${historyResult.second} from Firebase path: $path")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to check Firebase path: $path", e)
                    }
                }

                // If update_config was empty or failed, try fetching versionId.json directly
                if (targetVersionCode == -1) {
                    val fallbackUrl = "${com.example.api.FirebaseClient.activeUrl}versionId.json"
                    val fallbackRequest = Request.Builder().url(fallbackUrl).get().build()
                    try {
                        client.newCall(fallbackRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank() && body != "null") {
                                    if (body.contains("\"error\"")) {
                                        errorLogs.add("Firebase fallback returned error: $body")
                                    } else {
                                        targetVersionCode = body.trim().toIntOrNull() ?: -1
                                    }
                                } else {
                                    errorLogs.add("Empty response body from versionId.json")
                                }
                            } else {
                                errorLogs.add("HTTP ${response.code} ${response.message} for versionId.json")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch versionId.json", e)
                        errorLogs.add("Failed to fetch versionId.json: ${e.localizedMessage ?: e.toString()}")
                    }
                }

                // If we have a target version, but no apkFileId yet, try to find the specific version's apkFileId from previous versions list
                if (targetVersionCode != -1 && apkFileId == null) {
                    val specificPaths = listOf(
                        "versions/$targetVersionCode.json",
                        "releases/$targetVersionCode.json",
                        "updates/$targetVersionCode.json",
                        "update_config/versions/$targetVersionCode.json",
                        "update_config/history/$targetVersionCode.json"
                    )
                    for (path in specificPaths) {
                        try {
                            val url = "${com.example.api.FirebaseClient.activeUrl}$path"
                            val request = Request.Builder().url(url).get().build()
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (!body.isNullOrBlank() && body != "null" && !body.contains("\"error\"")) {
                                        val trimmed = body.trim()
                                        if (trimmed.startsWith("{")) {
                                            val json = JSONObject(trimmed)
                                            val fId = json.optString("apkFileId", json.optString("apk_file_id", json.optString("fileId", "")))
                                            if (!fId.isNullOrBlank() && fId != "null") {
                                                apkFileId = fId
                                                Log.d(TAG, "Found file ID $apkFileId for target version $targetVersionCode at specific path: $path")
                                                return@use
                                            }
                                        } else if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                                            val fId = trimmed.substring(1, trimmed.length - 1)
                                            if (fId.isNotEmpty() && fId != "null") {
                                                apkFileId = fId
                                                Log.d(TAG, "Found direct file ID string $apkFileId for target version $targetVersionCode at specific path: $path")
                                                return@use
                                            }
                                        } else if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
                                            if (trimmed.isNotEmpty() && trimmed != "null") {
                                                apkFileId = trimmed
                                                Log.d(TAG, "Found plain file ID $apkFileId for target version $targetVersionCode at specific path: $path")
                                                return@use
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed specific path check $path", e)
                        }
                        if (apkFileId != null) break
                    }
                }

                // 2. Fetch update config from GitHub Releases
                var githubVersionCode = -1
                var githubApkUrl: String? = null
                val githubOwner = getGithubOwner(context)
                val githubRepo = getGithubRepo(context)
                val githubUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
                val githubRequest = Request.Builder()
                    .url(githubUrl)
                    .header("User-Agent", "Life-OS-Android-App")
                    .get()
                    .build()
                
                try {
                    client.newCall(githubRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                val tag = json.optString("tag_name", "")
                                val buildNum = tag.substringAfterLast(".").toIntOrNull() 
                                    ?: Regex("""\d+""").findAll(tag).lastOrNull()?.value?.toIntOrNull()
                                if (buildNum != null) {
                                    githubVersionCode = buildNum
                                    val assets = json.optJSONArray("assets")
                                    if (assets != null) {
                                        for (i in 0 until assets.length()) {
                                            val asset = assets.getJSONObject(i)
                                            val assetName = asset.optString("name", "")
                                            if (assetName.endsWith(".apk")) {
                                                githubApkUrl = asset.optString("browser_download_url", "")
                                                break
                                            }
                                        }
                                    }
                                    Log.d(TAG, "GitHub latest release check: Version Code $githubVersionCode, APK URL: $githubApkUrl")
                                } else {
                                    errorLogs.add("Could not parse version code from GitHub tag: $tag")
                                }
                            } else {
                                errorLogs.add("Empty response body from GitHub API")
                            }
                        } else {
                            errorLogs.add("GitHub API HTTP ${response.code} ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch from GitHub API", e)
                    errorLogs.add("GitHub API error: ${e.localizedMessage ?: e.toString()}")
                }

                // 1.5. Fetch update from Firebase App Distribution if SDK is available
                var appDistributionVersionCode = -1
                try {
                    val appDist = com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance()
                    val task = appDist.checkForNewRelease()
                    val release = com.google.android.gms.tasks.Tasks.await(task)
                    if (release != null) {
                        appDistributionVersionCode = release.versionCode.toInt()
                        Log.d(TAG, "Firebase App Distribution latest release code: $appDistributionVersionCode")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Firebase App Distribution check skipped or failed: ${e.localizedMessage}")
                }

                var finalTargetVersionCode = targetVersionCode
                var finalApkFileId = apkFileId

                if (appDistributionVersionCode > finalTargetVersionCode) {
                    finalTargetVersionCode = appDistributionVersionCode
                    // Keep existing APK file ID if available, or fall back to App Distribution in-app installer
                }

                if (githubVersionCode > finalTargetVersionCode) {
                    finalTargetVersionCode = githubVersionCode
                    finalApkFileId = githubApkUrl
                } else if (githubVersionCode == finalTargetVersionCode && !githubApkUrl.isNullOrBlank()) {
                    // Prefer the verified, direct GitHub asset download URL returned by the official API
                    Log.d(TAG, "GitHub version matches target version ($finalTargetVersionCode). Using verified GitHub asset URL: $githubApkUrl")
                    finalApkFileId = githubApkUrl
                }

                Log.d(TAG, "Current Code: $currentCode, Firebase Target Code: $targetVersionCode, App Distribution Code: $appDistributionVersionCode, GitHub Target Code: $githubVersionCode, Chosen Target Code: $finalTargetVersionCode")

                if (finalTargetVersionCode > currentCode) {
                    setPendingUpdateVersion(context, finalTargetVersionCode)
                    _updateStatus.value = UpdateStatus.NewVersionAvailable(
                        versionId = finalTargetVersionCode,
                        currentVersionCode = currentCode,
                        apkFileId = finalApkFileId
                    )
                    
                    if (isAutoUpdateEnabled(context) && !finalApkFileId.isNullOrBlank()) {
                        Log.i(TAG, "Auto-update is enabled. Initiating silent background download...")
                        startDownloadAndInstall(context, finalApkFileId)
                    }
                } else if (finalTargetVersionCode >= 0) {
                    clearPendingUpdateVersion(context)
                    _updateStatus.value = UpdateStatus.NoUpdateAvailable(
                        cloudVersion = finalTargetVersionCode,
                        localVersion = currentCode
                    )
                    if (!manualCheck) {
                        // Return to idle after a while if auto-checked
                        _updateStatus.value = UpdateStatus.Idle
                    }
                } else {
                    // All requests failed to get a valid version code
                    if (errorLogs.isNotEmpty()) {
                        val errorMsg = "Could not fetch updates from Firebase/GitHub:\n" + errorLogs.joinToString("\n")
                        _updateStatus.value = UpdateStatus.Error(errorMsg)
                    } else {
                        clearPendingUpdateVersion(context)
                        _updateStatus.value = UpdateStatus.NoUpdateAvailable(
                            cloudVersion = -1,
                            localVersion = currentCode
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                _updateStatus.value = UpdateStatus.Error("Failed to verify updates: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Sets status back to Idle
     */
    fun resetStatus() {
        if (_updateStatus.value !is UpdateStatus.Downloading) {
            _updateStatus.value = UpdateStatus.Idle
        }
    }

    /**
     * Deletes existing downloaded update files, queries Firebase for latest update configurations,
     * and forces a complete redownload of the system update.
     */
    fun forceRedownloadUpdate(context: Context) {
        updateScope.launch {
            try {
                _updateStatus.value = UpdateStatus.Checking
                
                // 1. Delete all files in updates cache directory
                val updateDir = File(context.cacheDir, "updates")
                if (updateDir.exists()) {
                    updateDir.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }
                setReadyApkPath(context, null)
                
                // 2. Perform a fresh manual update check
                checkForUpdates(context, manualCheck = true)
                
                // 3. Obtain resolved status. If there is an update, trigger download and install.
                val currentStatus = _updateStatus.value
                val username = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getString("current_username", null)
                val forceUrl = if (!username.isNullOrEmpty()) {
                    com.example.api.FirebaseClient.api.getUser(username).body()?.forceApkUrl
                } else null
                if (!forceUrl.isNullOrEmpty()) {
                    _updateStatus.value = UpdateStatus.Downloading(0f)
                    downloadAndInstallUpdate(context, forceUrl)
                    return@launch
                }
                if (currentStatus is UpdateStatus.NewVersionAvailable) {
                    startDownloadAndInstall(context, currentStatus.apkFileId)
                } else {
                    // Even if Firebase states no newer version exists compared to the package code,
                    // we still fetch/resolve the current cloud file and force its download.
                    _updateStatus.value = UpdateStatus.SecuringData
                    val latestFileId = resolveApkFileId(context, null)
                    if (!latestFileId.isNullOrBlank()) {
                        downloadAndInstallUpdate(context, latestFileId)
                    } else {
                        _updateStatus.value = UpdateStatus.Error("Failed to resolve latest update file from Firebase config for force-redownload.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force redownload failed", e)
                _updateStatus.value = UpdateStatus.Error("Force redownload failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Resolves the APK file ID, either from Firebase or by parsing the Drive shared folder HTML.
     */
    private suspend fun resolveApkFileId(context: Context, providedFileId: String?): String? = withContext(Dispatchers.IO) {
        if (!providedFileId.isNullOrBlank()) {
            val url = providedFileId.trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val patterns = listOf(
                    Regex("""file/d/([a-zA-Z0-9_-]{20,60})"""),
                    Regex("""id=([a-zA-Z0-9_-]{20,60})"""),
                    Regex("""/open\?id=([a-zA-Z0-9_-]{20,60})""")
                )
                for (pattern in patterns) {
                    val match = pattern.find(url)
                    val extracted = match?.groupValues?.getOrNull(1)
                    if (!extracted.isNullOrBlank()) {
                        Log.d(TAG, "Extracted file ID from provided URL: $extracted")
                        return@withContext extracted
                    }
                }
            }
            return@withContext url
        }

        // Parse Google Drive shared folder page
        try {
            val folderUrl = "https://drive.google.com/drive/folders/$DEFAULT_FOLDER_ID"
            val request = Request.Builder()
                .url(folderUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null

                val targetVersion = getPendingUpdateVersion(context)
                Log.d(TAG, "Resolving Google Drive folder. Target update version code is: $targetVersion")

                // Look for pairs of (ID, Name) or (Name, ID) in HTML
                val pairs = mutableListOf<Pair<String, String>>() // ID to Name
                
                // Pattern 1: ["ID", "Name.apk"]
                val pattern1 = Regex("""["']([a-zA-Z0-9_-]{28,45})["']\s*,\s*["']([^"']+\.apk)["']""", RegexOption.IGNORE_CASE)
                pattern1.findAll(html).forEach { match ->
                    val id = match.groupValues[1]
                    val name = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }
                
                // Pattern 2: ["Name.apk", "ID"]
                val pattern2 = Regex("""["']([^"']+\.apk)["']\s*,\s*["']([a-zA-Z0-9_-]{28,45})["']""", RegexOption.IGNORE_CASE)
                pattern2.findAll(html).forEach { match ->
                    val name = match.groupValues[1]
                    val id = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }

                // Pattern 3: JSON fields
                val jsonPattern1 = Regex("""id["']:\s*["']([a-zA-Z0-9_-]{28,45})["'].{1,150}title["']:\s*["']([^"']+\.apk)["']""", RegexOption.IGNORE_CASE)
                jsonPattern1.findAll(html).forEach { match ->
                    val id = match.groupValues[1]
                    val name = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }
                val jsonPattern2 = Regex("""title["']:\s*["']([^"']+\.apk)["'].{1,150}id["']:\s*["']([a-zA-Z0-9_-]{28,45})["']""", RegexOption.IGNORE_CASE)
                jsonPattern2.findAll(html).forEach { match ->
                    val name = match.groupValues[1]
                    val id = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }
                val jsonPattern3 = Regex("""id["']:\s*["']([a-zA-Z0-9_-]{28,45})["'].{1,150}name["']:\s*["']([^"']+\.apk)["']""", RegexOption.IGNORE_CASE)
                jsonPattern3.findAll(html).forEach { match ->
                    val id = match.groupValues[1]
                    val name = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }
                val jsonPattern4 = Regex("""name["']:\s*["']([^"']+\.apk)["'].{1,150}id["']:\s*["']([a-zA-Z0-9_-]{28,45})["']""", RegexOption.IGNORE_CASE)
                jsonPattern4.findAll(html).forEach { match ->
                    val name = match.groupValues[1]
                    val id = match.groupValues[2]
                    if (id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                        pairs.add(Pair(id, name))
                    }
                }

                Log.d(TAG, "Parsed Google Drive file pairs: ${pairs.map { "${it.second} -> ${it.first}" }}")

                // Strategy 1: Direct target version match
                if (targetVersion > 0) {
                    val directMatch = pairs.firstOrNull { (_, name) ->
                        val lower = name.lowercase()
                        lower.contains("v$targetVersion") ||
                        lower.contains("_$targetVersion") ||
                        lower.contains("-$targetVersion") ||
                        lower.contains("build$targetVersion") ||
                        lower.contains("build_$targetVersion") ||
                        lower.contains("build-$targetVersion") ||
                        lower.endsWith("$targetVersion.apk") ||
                        lower.startsWith("$targetVersion")
                    }
                    if (directMatch != null) {
                        Log.d(TAG, "Success! Found direct version $targetVersion match in Google Drive folder: ${directMatch.second} (ID: ${directMatch.first})")
                        return@withContext directMatch.first
                    }
                }

                // Strategy 2: Highest parsed version match
                var highestParsedVersion = -1
                var highestParsedId: String? = null
                var highestParsedName: String? = null
                
                for (pair in pairs) {
                    val name = pair.second
                    val regex = Regex("""(?:v|_|-|build|version)(\d+)""", RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(name)
                    var versionNum = matchResult?.groupValues?.getOrNull(1)?.toIntOrNull()
                    
                    if (versionNum == null) {
                        val fallbackRegex = Regex("""(\d+)\.apk""", RegexOption.IGNORE_CASE)
                        val fallbackMatch = fallbackRegex.find(name)
                        versionNum = fallbackMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                    }
                    
                    if (versionNum != null && versionNum > highestParsedVersion) {
                        highestParsedVersion = versionNum
                        highestParsedId = pair.first
                        highestParsedName = name
                    }
                }
                
                if (highestParsedId != null && highestParsedVersion > 0) {
                    Log.d(TAG, "Success! Selected highest parsed version $highestParsedVersion: $highestParsedName (ID: $highestParsedId)")
                    return@withContext highestParsedId
                }

                // Strategy 3: Falling back to first parsed pair
                if (pairs.isNotEmpty()) {
                    val fallback = pairs.first()
                    Log.d(TAG, "No version-specific match. Falling back to first parsed file: ${fallback.second} (ID: ${fallback.first})")
                    return@withContext fallback.first
                }

                // Strategy 4: Legacy fallback matching raw IDs
                val legacyPatterns = listOf(
                    Regex("""file/d/([a-zA-Z0-9_-]{28,45})"""),
                    Regex("""open\?id=([a-zA-Z0-9_-]{28,45})"""),
                    Regex("""id\\":\\"([a-zA-Z0-9_-]{28,45})\\""""),
                    Regex("""id":"([a-zA-Z0-9_-]{28,45})""")
                )
                for (pattern in legacyPatterns) {
                    val matches = pattern.findAll(html)
                    for (match in matches) {
                        val id = match.groupValues.getOrNull(1)
                        if (id != null && id != DEFAULT_FOLDER_ID && id.startsWith("1")) {
                            Log.d(TAG, "Found legacy raw Google Drive file ID: $id")
                            return@withContext id
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Drive folder HTML", e)
        }

        null
    }

    /**
     * Checks if the active network has internet connectivity.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return false
    }

    /**
     * Checks if the downloaded file is a valid ZIP/APK archive.
     * Prevents launching the package installer on HTML error pages, corrupted files, or truncated streams.
     */
    fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 1000) return false
        return try {
            ZipFile(file).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the downloaded file is a valid APK and has a version code strictly greater
     * than the currently running version or the stored running Firebase version.
     */
    fun isValidAndNewerApk(context: Context, file: File): Boolean {
        if (!isValidApk(file)) return false
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (packageInfo != null) {
                val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                val currentCode = getCurrentVersionCode(context)
                val runningFirebase = getRunningFirebaseVersion(context)
                val activeCode = maxOf(currentCode, runningFirebase)
                val isNewer = downloadedCode > activeCode
                Log.d(TAG, "isValidAndNewerApk: APK version code is $downloadedCode, currently active is $activeCode. isNewer = $isNewer")
                isNewer
            } else {
                Log.w(TAG, "isValidAndNewerApk: Failed to read package archive info")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking downloaded APK version code", e)
            false
        }
    }

    /**
     * Downloads and installs the update.
     */
    suspend fun downloadAndInstallUpdate(context: Context, providedFileId: String?) {
        synchronized(this) {
            if (isDownloadingActive) {
                Log.i(TAG, "Download is already in progress, ignoring duplicate request.")
                return
            }
            isDownloadingActive = true
        }

        try {
            _updateStatus.value = UpdateStatus.SecuringData

            withContext(Dispatchers.IO) {
            try {
                // 0. Pre-Update Data Securing: Perform an automatic backup of the app's databases and settings
                try {
                    Log.i(TAG, "Securing user data before initiating update download...")
                    val db = com.example.data.AppDatabase.getInstance(context)
                    val backupSuccess = com.example.util.DatabaseBackupHelper.autoBackup(context, db)
                    if (backupSuccess) {
                        Log.i(TAG, "Pre-update data backup successfully secured to public storage!")
                    } else {
                        Log.w(TAG, "Pre-update database auto-backup failed. Proceeding with caution.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to perform automatic pre-update backup", e)
                }

                // Switch to downloading state now that data is safe
                _updateStatus.value = UpdateStatus.Downloading(0f)
                showProgressNotification(context, 0f)

                // 1. Check Network Connectivity
                if (!isNetworkAvailable(context)) {
                    val errMsg = "Failed to update: No internet connection. Please connect to a stable network and try again. Your local data remains fully secured."
                    _updateStatus.value = UpdateStatus.Error(errMsg)
                    showCompletionNotification(context, "Update Failed", "No internet connection.", false)
                    return@withContext
                }

                // 2. Resolve File ID
                val fileId = resolveApkFileId(context, providedFileId)
                if (fileId.isNullOrBlank()) {
                    val errMsg = "Failed to update: No APK file is available in the Google Drive folder. Please verify that a compatible APK is uploaded to the shared folder (ID: $DEFAULT_FOLDER_ID) or specify the File ID in Firebase. Pre-update data was fully secured."
                    _updateStatus.value = UpdateStatus.Error(errMsg)
                    showCompletionNotification(context, "Update Failed", "No APK file is available.", false)
                    return@withContext
                }

                // 3. Prepare destination file in app cache
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) {
                    updateDir.mkdirs()
                }
                
                // Sanitize fileId to construct a safe filename
                val safeFileName = "update_" + fileId.replace(Regex("[^a-zA-Z0-9]"), "_") + ".apk"
                val apkFile = File(updateDir, safeFileName)
                
                // Clear any other older updates that do not match the current safeFileName
                updateDir.listFiles()?.forEach { file ->
                    if (file.name != apkFile.name) {
                        file.delete()
                    }
                }
                
                // Check if the current file is already complete and valid
                if (isValidApk(apkFile)) {
                    if (isUpdateVersionGreater(context, apkFile)) {
                        Log.i(TAG, "Valid update APK already fully downloaded: ${apkFile.absolutePath}")
                        _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
                        showCompletionNotification(context, "System Update Downloaded", "Life OS update is ready to install. Tap to proceed.", true)
                        return@withContext
                    } else {
                        Log.w(TAG, "Already downloaded APK version is not greater than current version. Deleting.")
                        apkFile.delete()
                    }
                }
                
                val existingLength = if (apkFile.exists()) apkFile.length() else 0L

                // 4. Initiate Download from Google Drive or Direct URL (handling virus scanner warning confirmation)
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                val isDirectUrl = fileId.startsWith("http://") || fileId.startsWith("https://")
                
                val finalRequest = if (isDirectUrl) {
                    Log.d(TAG, "Downloading direct APK from resolved URL: $fileId")
                    val builder = Request.Builder()
                        .url(fileId)
                        .addHeader("User-Agent", userAgent)
                    if (existingLength > 0) {
                        builder.addHeader("Range", "bytes=$existingLength-")
                    }
                    builder.get().build()
                } else {
                    var downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                    val initialRequest = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("User-Agent", userAgent)
                        .get()
                        .build()
                    
                    var confirmToken: String? = null
                    var cookieHeader: String? = null
                    
                    client.newCall(initialRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            _updateStatus.value = UpdateStatus.Error("Failed to connect to Google Drive (HTTP ${response.code}). Pre-update data was fully secured.")
                            showCompletionNotification(context, "Update Failed", "Failed to connect to Google Drive.", false)
                            return@withContext
                        }

                        // Extract cookies (like download_warning_* cookie) to send back with confirm request
                        val cookies = response.headers("Set-Cookie")
                        if (cookies.isNotEmpty()) {
                            cookieHeader = cookies.joinToString("; ") { it.substringBefore(";") }
                        }

                        val contentType = response.header("Content-Type") ?: ""
                        if (contentType.contains("text/html", ignoreCase = true)) {
                            // Probably hit the confirmation page (virus warning/large file warning)
                            val html = response.body?.string() ?: ""
                            val confirmRegex = Regex("""confirm=([^"&'\s]+)""")
                            val match = confirmRegex.find(html)
                            confirmToken = match?.groupValues?.getOrNull(1)
                        }
                    }

                    // If a confirmation token was found, construct the verified download URL with cookies and optional Range header
                    if (!confirmToken.isNullOrBlank()) {
                        Log.d(TAG, "Confirmation token resolved: $confirmToken")
                        downloadUrl = "https://drive.google.com/uc?export=download&confirm=$confirmToken&id=$fileId"
                        val builder = Request.Builder()
                            .url(downloadUrl)
                            .addHeader("User-Agent", userAgent)
                        if (!cookieHeader.isNullOrBlank()) {
                            builder.addHeader("Cookie", cookieHeader)
                        }
                        if (existingLength > 0) {
                            builder.addHeader("Range", "bytes=$existingLength-")
                        }
                        builder.get().build()
                    } else {
                        val builder = Request.Builder()
                            .url(downloadUrl)
                            .addHeader("User-Agent", userAgent)
                        if (existingLength > 0) {
                            builder.addHeader("Range", "bytes=$existingLength-")
                        }
                        builder.get().build()
                    }
                }

                // Start actual download stream
                client.newCall(finalRequest).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        if (response.code == 416) {
                            Log.w(TAG, "Range 416 (Not Satisfiable). Resetting file and starting over.")
                            if (apkFile.exists()) {
                                apkFile.delete()
                            }
                            synchronized(this@AppUpdateManager) {
                                isDownloadingActive = false
                            }
                            downloadAndInstallUpdate(context, providedFileId)
                            return@withContext
                        }
                        _updateStatus.value = UpdateStatus.Error("Failed to stream APK download (HTTP ${response.code}). Pre-update data was fully secured.")
                        showCompletionNotification(context, "Update Failed", "HTTP ${response.code} error streaming APK.", false)
                        return@withContext
                    }

                    val body = response.body
                    if (body == null) {
                        _updateStatus.value = UpdateStatus.Error("Empty response body from Google Drive.")
                        showCompletionNotification(context, "Update Failed", "Empty response from server.", false)
                        return@withContext
                    }

                    val isRangeResponse = response.code == 206
                    val appendMode = isRangeResponse && apkFile.exists()
                    val startBytes = if (appendMode) existingLength else 0L
                    val remainingBytes = body.contentLength()
                    val totalBytes = if (isRangeResponse) {
                        existingLength + remainingBytes
                    } else {
                        remainingBytes
                    }

                    var bytesRead = startBytes
                    val buffer = ByteArray(8192)
                    var lastNotificationTime = 0L
                    
                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile, appendMode).use { outputStream ->
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                outputStream.write(buffer, 0, read)
                                bytesRead += read
                                
                                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes.toFloat() else -1f
                                _updateStatus.value = UpdateStatus.Downloading(progress)
                                
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastNotificationTime >= 500L) {
                                    showProgressNotification(context, progress)
                                    lastNotificationTime = currentTime
                                }
                            }
                        }
                    }
                }

                // Verify downloaded file integrity before installing
                if (!isValidApk(apkFile)) {
                    Log.e(TAG, "Downloaded file is not a valid APK.")
                    var errorMessage = "The downloaded file is corrupted or not a valid Android APK. Please try again. Your data is fully secured."
                    if (apkFile.exists() && apkFile.length() < 100000) {
                        try {
                            val content = apkFile.readText()
                            if (content.contains("<html", ignoreCase = true)) {
                                if (content.contains("recaptcha", ignoreCase = true) || content.contains("unusual traffic", ignoreCase = true)) {
                                    errorMessage = "Google Drive blocked the download because it detected automated traffic/reCAPTCHA. To fix this, please upload your APK to a direct hosting provider (like GitHub Releases, Dropbox, or Discord) and paste the direct .apk URL in the Firebase database instead."
                                } else if (content.contains("sign in", ignoreCase = true) || content.contains("ServiceLogin", ignoreCase = true)) {
                                    errorMessage = "Google Drive blocked the download because the file is private. Please change the Google Drive file share settings to 'Anyone with the link' and try again."
                                } else if (content.contains("quota exceeded", ignoreCase = true) || content.contains("download limit", ignoreCase = true)) {
                                    errorMessage = "Google Drive download quota exceeded for this file. Please host the APK on a direct hosting provider (like Dropbox or GitHub Releases) instead."
                                } else {
                                    errorMessage = "Failed to download from Google Drive: Google returned an HTML error page instead of the APK. Please ensure the file is shared publicly as 'Anyone with the link' or use a direct URL."
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read corrupted file preview", e)
                        }
                    }
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                    _updateStatus.value = UpdateStatus.Error(errorMessage)
                    showCompletionNotification(context, "Update Failed", "Corrupted or invalid APK.", false)
                    return@withContext
                }

                // Check version is strictly greater before proceeding to install
                val packageCode = getCurrentVersionCode(context)
                val runningFirebase = getRunningFirebaseVersion(context)
                val targetFirebaseVersion = getPendingUpdateVersion(context)
                
                var downloadedCode = -1
                val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (packageInfo != null) {
                    downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode
                    }
                }

                val isFirebaseBypass = targetFirebaseVersion > runningFirebase && downloadedCode == packageCode

                if (downloadedCode <= packageCode && !isFirebaseBypass) {
                    Log.w(TAG, "Downloaded APK version code ($downloadedCode) is not greater than current version ($packageCode).")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                    val detailMsg = if (downloadedCode > 0) {
                        "Failed to update: The downloaded APK actually has version code $downloadedCode (same as or older than your current running version $packageCode), even though Firebase was set to version $targetFirebaseVersion. This happens when the Firebase version config is bumped, but the old APK file was not replaced. Please upload the newly compiled APK and try again."
                    } else {
                        "Failed to update: Downloaded APK version is not greater than your current version. Pre-update data remains fully secured."
                    }
                    _updateStatus.value = UpdateStatus.Error(detailMsg)
                    showCompletionNotification(context, "Update Ignored", "Downloaded version is not newer.", false)
                    return@withContext
                }

                Log.i(TAG, "Successfully downloaded update APK to: ${apkFile.absolutePath}")
                setReadyApkPath(context, apkFile.absolutePath)
                _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
                showCompletionNotification(context, "System Update Downloaded", "Life OS update is ready to install. Tap to proceed.", true)

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update", e)
                _updateStatus.value = UpdateStatus.Error("Download failed: ${e.localizedMessage ?: "Unknown network error"}. Pre-update data is fully secured.")
                showCompletionNotification(context, "Update Failed", "Network download failure.", false)
            }
        }
        } finally {
            synchronized(this) {
                isDownloadingActive = false
            }
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        updateScope.launch {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L || !isValidApk(apkFile)) {
                    Log.e(TAG, "APK file is missing, empty, or corrupted")
                    _updateStatus.value = UpdateStatus.Error("The update installation failed: The APK file is missing or corrupted. Please try downloading again.")
                    return@launch
                }

                // Secure user data asynchronously so we don't block launching the package installer
                updateScope.launch(Dispatchers.IO) {
                    try {
                        Log.i(TAG, "Securing user data asynchronously in background prior to package installation...")
                        val db = com.example.data.AppDatabase.getInstance(context)
                        com.example.util.DatabaseBackupHelper.autoBackup(context, db)
                        Log.i(TAG, "Asynchronous pre-installation backup finished.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Pre-installation database auto-backup failed", e)
                    }
                }

                // Check if we need to request "unknown sources" permission on Android Oreo+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        Log.w(TAG, "Requesting MANAGE_UNKNOWN_APP_SOURCES permission")
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        _updateStatus.value = UpdateStatus.Error("Please enable 'Install unknown apps' permission for Life OS and try again. Your local app data is fully secured.")
                        return@launch
                    }
                }

                // Obtain File URI using our com.example.fileprovider
                val authority = "com.example.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                Log.i(TAG, "Launching package installer for URI: $apkUri")
                val targetVersion = getPendingUpdateVersion(context)
                if (targetVersion > 0) {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("pref_install_target_version", targetVersion).commit()
                }
                setReadyApkPath(context, null)
                context.startActivity(intent)
                
                // Set state back to idle so they can click download again if installation fails or is cancelled
                _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch package installer", e)
                _updateStatus.value = UpdateStatus.Error("Failed to launch package installer: ${e.localizedMessage}. This can occur if the APK signature is incompatible or permissions are restricted. Try downloading the APK and installing manually. Your app data is fully secured.")
            }
        }
    }

    /**
     * Publishes a new update configuration to Firebase Realtime Database.
     */
    suspend fun publishUpdateConfig(versionId: Int, apkFileId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${com.example.api.FirebaseClient.activeUrl}update_config.json"
            val json = JSONObject()
            json.put("versionId", versionId)
            json.put("apkFileId", apkFileId ?: JSONObject.NULL)
            
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).put(requestBody).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully published update_config to Firebase: $json")
                    
                    // Also update fallback versionId.json
                    try {
                        val fallbackUrl = "${com.example.api.FirebaseClient.activeUrl}versionId.json"
                        val fallbackBody = versionId.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        val fallbackReq = Request.Builder().url(fallbackUrl).put(fallbackBody).build()
                        client.newCall(fallbackReq).execute().close()
                    } catch (fe: Exception) {
                        Log.e(TAG, "Failed to update fallback versionId.json", fe)
                    }
                    
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to publish update_config: ${response.code} ${response.message}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing update_config to Firebase", e)
            return@withContext false
        }
    }

    private fun showProgressNotification(context: Context, progress: Float) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "app_updates"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "App Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of background app updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Downloading System Update")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (progress >= 0f) {
                val progressPercent = (progress * 100).toInt()
                builder.setContentText("Downloading Life OS: $progressPercent%")
                builder.setProgress(100, progressPercent, false)
            } else {
                builder.setContentText("Connecting and preparing download...")
                builder.setProgress(100, 0, true)
            }

            notificationManager.notify(NOTIFICATION_PROGRESS_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show progress notification", e)
        }
    }

    private fun showCompletionNotification(context: Context, title: String, text: String, isSuccess: Boolean) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_PROGRESS_ID)

            val channelId = "app_updates"
            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            if (isSuccess) {
                // PendingIntent to launch MainActivity
                val launchIntent = Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = android.app.PendingIntent.getActivity(context, 1002, launchIntent, flags)
                builder.setContentIntent(pendingIntent)
            }

            notificationManager.notify(NOTIFICATION_COMPLETE_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show completion notification", e)
        }
    }

    private fun isUpdateVersionGreater(context: Context, file: File): Boolean {
        return try {
            val currentCode = getCurrentVersionCode(context)
            val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (packageInfo != null) {
                val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                Log.d(TAG, "Checking downloaded APK version code: $downloadedCode, Current: $currentCode")
                downloadedCode > currentCode
            } else {
                Log.e(TAG, "Failed to parse downloaded APK package archive info")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking downloaded APK version code", e)
            false
        }
    }

    private const val NOTIFICATION_PROGRESS_ID = 4001
    private const val NOTIFICATION_COMPLETE_ID = 4002
}
