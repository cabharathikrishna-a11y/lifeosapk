@file:Suppress("DEPRECATION")
package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.Task
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GoogleTasksSyncManager {
    private const val TAG = "GoogleTasksSync"
    private const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_tasks_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, TASKS_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Tasks scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Tasks: ${e.message}", e)
            null
        }
    }

    /**
     * Performs a full 2-way sync for Google Tasks (tasks with NO date and time).
     */
    suspend fun syncTasks(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val database = AppDatabase.getInstance(context)
            val taskDao = database.taskDao()
            val allLocalTasks = taskDao.getAllTasks().first()

            // Filter local tasks that have NO date/time (dueDateString is empty)
            val localTasksNoDate = allLocalTasks.filter { it.dueDateString.isEmpty() }

            // ---- STEP 1: FETCH FROM GOOGLE TASKS ----
            val googleTasks = fetchGoogleTasks(token)
            val googleIdToTask = googleTasks.associateBy { it.id }

            var importedCount = 0
            var updatedCount = 0
            var exportedCount = 0
            var deletedCount = 0

            // Keep track of which Google tasks we matched to local tasks
            val matchedGoogleIds = mutableSetOf<String>()

            // ---- STEP 2: PROCESS GOOGLE TASKS AND UPDATE/CREATE LOCALLY ----
            for (gTask in googleTasks) {
                // Check if this Google task has been deleted locally
                val isDeletedLocally = DeletedTaskLogHelper.isGTaskIdDeletedLocally(context, gTask.id) ||
                        DeletedTaskLogHelper.isGoogleTaskDeletedLocally(context, gTask.title)

                if (isDeletedLocally) {
                    Log.d(TAG, "Sync: Google Task '${gTask.title}' (ID: ${gTask.id}) was deleted locally. Deleting from Google.")
                    try {
                        deleteGoogleTask(token, gTask.id)
                        deletedCount++
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting GTask ${gTask.id}: ${e.message}", e)
                    }
                    continue
                }

                // Find local task by [GTaskId: ...] tag or fallback to title matching (for tasks without date)
                val matchedLocal = localTasksNoDate.find { task ->
                    task.description.contains("[GTaskId: ${gTask.id}]")
                } ?: localTasksNoDate.find { task ->
                    task.title.trim().equals(gTask.title.trim(), ignoreCase = true) &&
                    !task.description.contains("[GTaskId:")
                }

                if (matchedLocal != null) {
                    matchedGoogleIds.add(gTask.id)
                    
                    // Check if local description already has the [GTaskId: ...] tag
                    val hasTag = matchedLocal.description.contains("[GTaskId: ${gTask.id}]")
                    val cleanLocalDesc = getCleanDescription(matchedLocal.description)

                    // Determine if updates are needed
                    val isGoogleCompleted = gTask.status == "completed"
                    val isLocalCompleted = matchedLocal.isCompleted

                    var needsUpdateLocal = false
                    var needsUpdateGoogle = false

                    var updatedLocalTask = matchedLocal

                    // 1. Resolve completion status difference
                    if (isGoogleCompleted != isLocalCompleted) {
                        // If one is completed and the other isn't, we can sync completion.
                        // Let's assume the local completed state is the latest, unless the Google task was marked completed.
                        // To be safe, if either is completed, mark both completed, or sync Google -> Local if Google is completed.
                        if (isGoogleCompleted) {
                            updatedLocalTask = updatedLocalTask.copy(isCompleted = true)
                            needsUpdateLocal = true
                        } else {
                            // Local is completed, but Google is not. Update Google.
                            needsUpdateGoogle = true
                        }
                    }

                    // 2. Resolve Title / Notes difference
                    if (matchedLocal.title != gTask.title || cleanLocalDesc != gTask.notes) {
                        // If they differ, update Google task with local changes (as user interacts with the app primarily)
                        needsUpdateGoogle = true
                    }

                    // 3. Ensure local task has the [GTaskId: ...] tag
                    if (!hasTag) {
                        val newDesc = if (matchedLocal.description.isEmpty()) {
                            "[GTaskId: ${gTask.id}]"
                        } else {
                            "${matchedLocal.description}\n\n[GTaskId: ${gTask.id}]"
                        }
                        updatedLocalTask = updatedLocalTask.copy(description = newDesc)
                        needsUpdateLocal = true
                    }

                    if (needsUpdateLocal) {
                        taskDao.updateTask(updatedLocalTask)
                        updatedCount++
                    }

                    if (needsUpdateGoogle) {
                        val notesWithId = if (cleanLocalDesc.isEmpty()) {
                            "[AppTaskId: ${updatedLocalTask.id}]"
                        } else {
                            "$cleanLocalDesc\n\n[AppTaskId: ${updatedLocalTask.id}]"
                        }
                        updateGoogleTask(token, gTask.id, updatedLocalTask.title, notesWithId, if (updatedLocalTask.isCompleted) "completed" else "needsAction")
                    }
                } else {
                    // Google Task has no local counterpart, so import it as a new local task (with no date)
                    val notes = gTask.notes
                    val cleanNotes = notes.replace(Regex("""\[AppTaskId:\s*([^\]]+)\]"""), "").trim()
                    val finalDesc = if (cleanNotes.isEmpty()) {
                        "[GTaskId: ${gTask.id}]"
                    } else {
                        "$cleanNotes\n\n[GTaskId: ${gTask.id}]"
                    }

                    val newLocal = Task(
                        title = gTask.title,
                        description = finalDesc,
                        isCompleted = gTask.status == "completed",
                        listCategory = "Google Tasks",
                        dueDateString = ""
                    )
                    val insertedId = taskDao.insertTask(newLocal)
                    importedCount++
                    matchedGoogleIds.add(gTask.id)

                    // Update Google Task's notes with the newly inserted AppTaskId so we can track deletion
                    try {
                        val notesWithId = if (cleanNotes.isEmpty()) {
                            "[AppTaskId: $insertedId]"
                        } else {
                            "$cleanNotes\n\n[AppTaskId: $insertedId]"
                        }
                        updateGoogleTask(token, gTask.id, gTask.title, notesWithId, gTask.status)
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        Log.e(TAG, "Failed to update Google Task notes with AppTaskId: ${e.message}", e)
                    }
                }
            }

            // ---- STEP 3: EXPORT NEW LOCAL TASKS TO GOOGLE TASKS ----
            for (local in localTasksNoDate) {
                val gTaskId = extractGoogleTaskId(local.description)
                if (gTaskId == null) {
                    // This is a new local task with NO date and NO Google Task ID yet! Export it.
                    val cleanDesc = getCleanDescription(local.description)
                    val notesWithId = if (cleanDesc.isEmpty()) {
                        "[AppTaskId: ${local.id}]"
                    } else {
                        "$cleanDesc\n\n[AppTaskId: ${local.id}]"
                    }
                    val status = if (local.isCompleted) "completed" else "needsAction"
                    val newGTaskId = createGoogleTask(token, local.title, notesWithId, status)
                    if (newGTaskId != null) {
                        val updatedDesc = if (local.description.isEmpty()) {
                            "[GTaskId: $newGTaskId]"
                        } else {
                            "${local.description}\n\n[GTaskId: $newGTaskId]"
                        }
                        taskDao.updateTask(local.copy(description = updatedDesc))
                        exportedCount++
                    }
                } else {
                    // Local task has a Google Task ID, but was it deleted on Google?
                    if (!matchedGoogleIds.contains(gTaskId)) {
                        // The task has a Google Task ID tag, but that ID was not returned by Google.
                        // This means the task was deleted on Google Tasks, so we delete it locally to keep them in sync.
                        taskDao.deleteTask(local)
                        deletedCount++
                    }
                }
            }

            // ---- STEP 4: DETECT LOCALLY DELETED TASKS AND DELETE THEM FROM GOOGLE ----
            // If there are Google Tasks that have [AppTaskId: ...] (if we used that), or if we want to be safe,
            // we can clean up Google Tasks that are no longer present in local tasks.
            // Wait, we didn't store AppTaskId in Google Tasks notes, but we can do that in the future to make delete sync 100% perfect.
            // For now, if local task with a GTaskId is deleted from the app's database, it won't be in localTasksNoDate.
            // But we can't easily know which GTaskId was deleted unless we track deletions, OR we can check if there are Google Tasks with notes containing "[AppTaskId: ID]" where ID is not in our database!
            // Let's add [AppTaskId: ID] to the notes we send to Google, so we can delete them on Google if the local task is deleted!
            // This is brilliant! Let's do that in createGoogleTask and updateGoogleTask.
            for (gTask in googleTasks) {
                val appTaskId = extractAppTaskId(gTask.notes)
                if (appTaskId != null) {
                    val localExists = allLocalTasks.any { it.id == appTaskId }
                    if (!localExists) {
                        // The local task was deleted by the user in our app. So delete it from Google Tasks!
                        deleteGoogleTask(token, gTask.id)
                        deletedCount++
                    }
                }
            }

            Pair(true, "Sync Complete! Imported $importedCount, Updated $updatedCount, Exported $exportedCount, Synced $deletedCount deletions.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing tasks: ${e.message}", e)
            Pair(false, "Sync failed: ${e.localizedMessage}")
        }
    }

    data class GoogleTaskDetails(
        val id: String,
        val title: String,
        val notes: String,
        val status: String,
        val updated: String
    )

    private fun fetchGoogleTasks(token: String): List<GoogleTaskDetails> {
        val list = mutableListOf<GoogleTaskDetails>()
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks?showCompleted=true&showHidden=true"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch Google tasks: code=${response.code}, msg=${response.message}")
                    return emptyList()
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("items") ?: return emptyList()

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.optString("id", "")
                    val title = item.optString("title", "")
                    val notes = item.optString("notes", "")
                    val status = item.optString("status", "needsAction")
                    val updated = item.optString("updated", "")

                    if (id.isNotEmpty()) {
                        list.add(GoogleTaskDetails(id, title, notes, status, updated))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google tasks: ${e.message}", e)
        }
        return list
    }

    private fun createGoogleTask(token: String, title: String, notes: String, status: String): String? {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks"
        val payload = JSONObject().apply {
            put("title", title)
            put("notes", notes)
            put("status", status)
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to create Google task: code=${response.code}, msg=${response.message}")
                    return null
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("id").takeIf { it.isNotEmpty() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google task: ${e.message}", e)
        }
        return null
    }

    private fun updateGoogleTask(token: String, id: String, title: String, notes: String, status: String): Boolean {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks/$id"
        val payload = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("notes", notes)
            put("status", status)
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to update Google task: code=${response.code}, msg=${response.message}")
                    return false
                }
                return true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Google task: ${e.message}", e)
        }
        return false
    }

    private fun deleteGoogleTask(token: String, id: String): Boolean {
        val url = "https://tasks.googleapis.com/v1/lists/@default/tasks/$id"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to delete Google task: code=${response.code}, msg=${response.message}")
                    return false
                }
                return true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Google task: ${e.message}", e)
        }
        return false
    }

    // Helpers to parse tags
    fun extractGoogleTaskId(description: String): String? {
        val regex = Regex("""\[GTaskId:\s*([^\]]+)\]""")
        val match = regex.find(description)
        return match?.groupValues?.get(1)?.trim()
    }

    fun extractAppTaskId(notes: String): Int? {
        val regex = Regex("""\[AppTaskId:\s*([^\]]+)\]""")
        val match = regex.find(notes)
        return match?.groupValues?.get(1)?.trim()?.toIntOrNull()
    }

    fun getCleanDescription(description: String): String {
        // Remove [GTaskId: ...] and empty lines around it
        val cleaned = description.replace(Regex("""\[GTaskId:\s*([^\]]+)\]"""), "").trim()
        return cleaned
    }
}
