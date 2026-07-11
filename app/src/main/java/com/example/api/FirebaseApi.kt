package com.example.api

import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonClass

import com.example.ui.FocusRecord

@JsonClass(generateAdapter = true)
data class UserRemote(
    val password: String = "",
    val name: String? = null,
    val nickname: String? = null,
    
    val emoji: String? = "🎯",
    val isFocusing: Boolean? = false,
    val accumulatedTimeMs: Long = 0L,
    val lastResumeTimeMs: Long? = null,
    val currentTaskTitle: String? = null,
    val todaysFocusRecords: List<FocusRecord>? = emptyList(),
    val focusRecords: List<FocusRecord>? = emptyList(),
    val isStopwatchMode: Boolean? = false,

    val profile: UserProfile? = null,
    val activeTimer: ActiveTimer? = null,
    val todayStats: TodayStats? = null,
    val stats_dashboard: StatsDashboard? = null,
    val lastUpdatedTimestamp: Long? = null,
    val lastButtonClicked: String? = null,
    val lastButtonClickedTimestamp: Long? = null,
    val focusStatus: String? = null,
    val currentTag: String? = null,
    val isGoogleUser: Boolean? = null,
    val email: String? = null,
    val status: String? = null,
    val appVersion: String? = null,
    val forceApkUrl: String? = null,
    val lastUpdatedDeviceId: String? = null,
    val deviceLogs: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val name: String = "",
    val nickname: String = "",
    val photoUpdatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class ActiveTimer(
    val status: String = "RELAXING",
    val mode: String = "POMODORO",
    val startTimeMs: Long = 0L,
    val targetEndTimeMs: Long = 0L,
    val accumulatedFocusMs: Long = 0L,
    val accumulatedBreakMs: Long = 0L,
    val timezoneOffsetMinutes: Int = 0
)

@JsonClass(generateAdapter = true)
data class TodayStats(
    val todayFocusTimeMs: Long = 0L,
    val dateString: String = ""
)

@JsonClass(generateAdapter = true)
data class StatsDashboard(
    val status: String = "RELAXING",
    val mode: String = "POMODORO",
    val startTimeMs: Long = 0L,
    val targetEndTimeMs: Long = 0L,
    val accumulatedFocusMs: Long = 0L,
    val accumulatedBreakMs: Long = 0L,
    val todayFocusTimeMs: Long = 0L,
    val dateString: String = ""
)

@JsonClass(generateAdapter = true)
data class BellSignal(
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val timestamp: Long = 0L,
    val isProcessed: Boolean = false
)

interface FirebaseApi {
    @GET("users.json")
    suspend fun getUsers(): retrofit2.Response<Map<String, UserRemote>?>

    @GET("users/{username}.json")
    suspend fun getUser(
        @Path("username") username: String
    ): retrofit2.Response<UserRemote?>

    @PUT("users/{username}.json")
    suspend fun putUser(
        @Path("username") username: String,
        @Body user: UserRemote
    ): UserRemote

    @DELETE("users/{username}.json")
    suspend fun deleteUser(
        @Path("username") username: String
    ): retrofit2.Response<Unit>

    @GET("bells/{username}.json")
    suspend fun getBellSignal(
        @Path("username") username: String
    ): retrofit2.Response<BellSignal?>

    @PUT("bells/{username}.json")
    suspend fun putBellSignal(
        @Path("username") username: String,
        @Body signal: BellSignal?
    ): BellSignal?

    @GET("requests/{username}.json")
    suspend fun getPeerRequests(
        @Path("username") username: String
    ): retrofit2.Response<Map<String, Boolean>?>

    @PUT("requests/{username}/{requester}.json")
    suspend fun putPeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String,
        @Body request: Boolean
    ): Boolean

    @DELETE("requests/{username}/{requester}.json")
    suspend fun deletePeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String
    ): retrofit2.Response<Unit>

    @GET("transfer/{requester}/{provider}.json")
    suspend fun getTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): retrofit2.Response<List<FocusRecord>?>

    @PUT("transfer/{requester}/{provider}.json")
    suspend fun putTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String,
        @Body records: List<FocusRecord>?
    ): List<FocusRecord>?

    @DELETE("transfer/{requester}/{provider}.json")
    suspend fun deleteTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): retrofit2.Response<Unit>
}

object FirebaseClient {
    private var cachedApi: FirebaseApi? = null
    private var cachedUrl: String? = null

    @Volatile
    private var appContextRef: java.lang.ref.WeakReference<android.content.Context>? = null

    var appContext: android.content.Context?
        get() = appContextRef?.get()
        set(value) {
            appContextRef = value?.let { java.lang.ref.WeakReference(it.applicationContext) }
        }

    val api: FirebaseApi
        get() {
            val url = activeUrl
            synchronized(this) {
                if (cachedApi == null || cachedUrl != url) {
                    cachedUrl = url
                    cachedApi = InterceptingFirebaseApi { appContext }
                }
                return cachedApi!!
            }
        }

    @Volatile
    var activeUrl: String = if (FirebaseConfig.DATABASE_URL.endsWith("/")) FirebaseConfig.DATABASE_URL else "${FirebaseConfig.DATABASE_URL}/"
        set(value) {
            val sanitized = if (value.endsWith("/")) value else "$value/"
            field = sanitized
        }
}

class InterceptingFirebaseApi(
    private val contextProvider: () -> android.content.Context?
) : FirebaseApi {

    private fun isTester(): Boolean {
        val ctx = contextProvider() ?: return false
        val prefs = ctx.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("is_tester_mode", false)
    }

    private fun getDatabase(): com.google.firebase.database.FirebaseDatabase {
        val url = FirebaseClient.activeUrl.removeSuffix("/")
        return com.google.firebase.database.FirebaseDatabase.getInstance(url)
    }

    private fun getAppVersionString(): String {
        val ctx = contextProvider() ?: return "Unknown"
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override suspend fun getUsers(): retrofit2.Response<Map<String, UserRemote>?> {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("users").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                        return
                    }
                    val map = mutableMapOf<String, UserRemote>()
                    snapshot.children.forEach { child ->
                        val username = child.key ?: return@forEach
                        val user = FirebaseSyncManager.parseUserSnapshot(child)
                        if (user != null) {
                            map[username] = user
                        }
                    }
                    continuation.resume(retrofit2.Response.success(map), onCancellation = null)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, error.message)), onCancellation = null)
                }
            })
        }
    }

    override suspend fun getUser(username: String): retrofit2.Response<UserRemote?> {
        if (isTester() || username == "tester_mode_user") {
            return retrofit2.Response.success(UserRemote(password = "tester"))
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("users").child(username).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                        return
                    }
                    val user = FirebaseSyncManager.parseUserSnapshot(snapshot)
                    continuation.resume(retrofit2.Response.success(user), onCancellation = null)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, error.message)), onCancellation = null)
                }
            })
        }
    }

    override suspend fun putUser(username: String, user: UserRemote): UserRemote {
        if (isTester() || username == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putUser for username: $username in Tester Mode")
            return user
        }
        val userWithVersion = user.copy(appVersion = getAppVersionString())
        val userMap = mutableMapOf<String, Any>()
        userWithVersion.password?.let { userMap["password"] = it }
        userWithVersion.name?.let { userMap["name"] = it }
        userWithVersion.nickname?.let { userMap["nickname"] = it }
        userWithVersion.emoji?.let { userMap["emoji"] = it }
        userWithVersion.isFocusing?.let { userMap["isFocusing"] = it }
        userMap["accumulatedTimeMs"] = userWithVersion.accumulatedTimeMs
        userWithVersion.lastResumeTimeMs?.let { userMap["lastResumeTimeMs"] = it }
        userWithVersion.currentTaskTitle?.let { userMap["currentTaskTitle"] = it }
        userWithVersion.isStopwatchMode?.let { userMap["isStopwatchMode"] = it }
        userWithVersion.lastUpdatedTimestamp?.let { userMap["lastUpdatedTimestamp"] = it }
        userWithVersion.lastButtonClicked?.let { userMap["lastButtonClicked"] = it }
        userWithVersion.lastButtonClickedTimestamp?.let { userMap["lastButtonClickedTimestamp"] = it }
        userWithVersion.focusStatus?.let { userMap["focusStatus"] = it }
        userWithVersion.currentTag?.let { userMap["currentTag"] = it }
        userWithVersion.isGoogleUser?.let { userMap["isGoogleUser"] = it }
        userWithVersion.email?.let { userMap["email"] = it }
        userWithVersion.status?.let { userMap["status"] = it }
        userWithVersion.appVersion?.let { userMap["appVersion"] = it }
        userWithVersion.forceApkUrl?.let { userMap["forceApkUrl"] = it }
        userWithVersion.lastUpdatedDeviceId?.let { userMap["lastUpdatedDeviceId"] = it }
        userWithVersion.deviceLogs?.let { userMap["deviceLogs"] = it }

        userWithVersion.profile?.let { p ->
            userMap["profile"] = mapOf("name" to p.name, "nickname" to p.nickname, "photoUpdatedAt" to p.photoUpdatedAt)
        }
        userWithVersion.activeTimer?.let { t ->
            userMap["active_timer"] = mapOf(
                "status" to t.status,
                "mode" to t.mode,
                "startTimeMs" to t.startTimeMs,
                "targetEndTimeMs" to t.targetEndTimeMs,
                "accumulatedFocusMs" to t.accumulatedFocusMs,
                "accumulatedBreakMs" to t.accumulatedBreakMs
            )
        }
        userWithVersion.todayStats?.let { s ->
            userMap["today_stats"] = mapOf("todayFocusTimeMs" to s.todayFocusTimeMs, "dateString" to s.dateString)
        }

        // Keep stats_dashboard aligned
        val t = userWithVersion.activeTimer ?: ActiveTimer()
        val s = userWithVersion.todayStats ?: TodayStats()
        userMap["stats_dashboard"] = mapOf(
            "status" to t.status,
            "mode" to t.mode,
            "startTimeMs" to t.startTimeMs,
            "targetEndTimeMs" to t.targetEndTimeMs,
            "accumulatedFocusMs" to t.accumulatedFocusMs,
            "accumulatedBreakMs" to t.accumulatedBreakMs,
            "todayFocusTimeMs" to s.todayFocusTimeMs,
            "dateString" to s.dateString
        )

        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("users").child(username).setValue(userMap)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(userWithVersion, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to putUser")))
                    }
                }
        }
    }

    override suspend fun deleteUser(username: String): retrofit2.Response<Unit> {
        if (isTester() || username == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deleteUser for username: $username in Tester Mode")
            return retrofit2.Response.success(Unit)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("users").child(username).removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(retrofit2.Response.success(Unit), onCancellation = null)
                    } else {
                        continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, task.exception?.message ?: "Failed")), onCancellation = null)
                    }
                }
        }
    }

    override suspend fun getBellSignal(username: String): retrofit2.Response<BellSignal?> {
        if (isTester() || username == "tester_mode_user") {
            return retrofit2.Response.success(null)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("bells").child(username).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                        return
                    }
                    try {
                        val senderUsername = snapshot.child("senderUsername").getValue(String::class.java) ?: ""
                        val senderDisplayName = snapshot.child("senderDisplayName").getValue(String::class.java) ?: ""
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        val isProcessed = snapshot.child("isProcessed").getValue(Boolean::class.java) ?: false
                        continuation.resume(retrofit2.Response.success(BellSignal(senderUsername, senderDisplayName, timestamp, isProcessed)), onCancellation = null)
                    } catch (e: Exception) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, error.message)), onCancellation = null)
                }
            })
        }
    }

    override suspend fun putBellSignal(username: String, signal: BellSignal?): BellSignal? {
        if (isTester() || username == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putBellSignal for username: $username in Tester Mode")
            return signal
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val ref = getDatabase().getReference("bells").child(username)
            if (signal == null) {
                ref.removeValue().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(null, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to delete bell")))
                    }
                }
            } else {
                val signalMap = mapOf(
                    "senderUsername" to signal.senderUsername,
                    "senderDisplayName" to signal.senderDisplayName,
                    "timestamp" to signal.timestamp,
                    "isProcessed" to signal.isProcessed
                )
                ref.setValue(signalMap).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(signal, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to putBellSignal")))
                    }
                }
            }
        }
    }

    override suspend fun getPeerRequests(username: String): retrofit2.Response<Map<String, Boolean>?> {
        if (isTester() || username == "tester_mode_user") {
            return retrofit2.Response.success(null)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("requests").child(username).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                        return
                    }
                    val map = mutableMapOf<String, Boolean>()
                    snapshot.children.forEach { child ->
                        val key = child.key ?: return@forEach
                        val value = child.getValue(Boolean::class.java) ?: false
                        map[key] = value
                    }
                    continuation.resume(retrofit2.Response.success(map), onCancellation = null)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, error.message)), onCancellation = null)
                }
            })
        }
    }

    override suspend fun putPeerRequest(username: String, requester: String, request: Boolean): Boolean {
        if (isTester() || username == "tester_mode_user" || requester == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putPeerRequest in Tester Mode")
            return true
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("requests").child(username).child(requester).setValue(request)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(request, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to putPeerRequest")))
                    }
                }
        }
    }

    override suspend fun deletePeerRequest(username: String, requester: String): retrofit2.Response<Unit> {
        if (isTester() || username == "tester_mode_user" || requester == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deletePeerRequest in Tester Mode")
            return retrofit2.Response.success(Unit)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("requests").child(username).child(requester).removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(retrofit2.Response.success(Unit), onCancellation = null)
                    } else {
                        continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, task.exception?.message ?: "Failed")), onCancellation = null)
                    }
                }
        }
    }

    override suspend fun getTransferredData(requester: String, provider: String): retrofit2.Response<List<FocusRecord>?> {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("transfer").child(requester).child(provider).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                        return
                    }
                    try {
                        val list = mutableListOf<FocusRecord>()
                        snapshot.children.forEach { recordSnapshot ->
                            val recordId = recordSnapshot.child("id").getValue(String::class.java) ?: java.util.UUID.randomUUID().toString()
                            val recTaskTitle = recordSnapshot.child("taskTitle").getValue(String::class.java) ?: ""
                            val recDuration = recordSnapshot.child("durationSeconds").getValue(Int::class.java) ?: 0
                            val recDurationMinutes = recordSnapshot.child("durationMinutes").getValue(Int::class.java) ?: (recDuration / 60)
                            val recStartTime = recordSnapshot.child("startTime").getValue(String::class.java) ?: ""
                            val recEndTime = recordSnapshot.child("endTime").getValue(String::class.java) ?: ""
                            val recDate = recordSnapshot.child("dateString").getValue(String::class.java) ?: ""
                            val recTag = recordSnapshot.child("tag").getValue(String::class.java) ?: ""
                            val recNotes = recordSnapshot.child("notes").getValue(String::class.java) ?: ""
                            val recTimestamp = recordSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                            list.add(
                                FocusRecord(
                                    id = recordId,
                                    taskTitle = recTaskTitle,
                                    durationMinutes = recDurationMinutes,
                                    durationSeconds = recDuration,
                                    startTime = recStartTime,
                                    endTime = recEndTime,
                                    dateString = recDate,
                                    tag = recTag,
                                    notes = recNotes,
                                    timestamp = recTimestamp
                                )
                            )
                        }
                        continuation.resume(retrofit2.Response.success(list), onCancellation = null)
                    } catch (e: Exception) {
                        continuation.resume(retrofit2.Response.success(null), onCancellation = null)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, error.message)), onCancellation = null)
                }
            })
        }
    }

    override suspend fun putTransferredData(requester: String, provider: String, records: List<FocusRecord>?): List<FocusRecord>? {
        if (isTester() || requester == "tester_mode_user" || provider == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putTransferredData in Tester Mode")
            return records
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val ref = getDatabase().getReference("transfer").child(requester).child(provider)
            if (records == null) {
                ref.removeValue().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(null, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to delete transferred data")))
                    }
                }
            } else {
                val recordList = records.map { r ->
                    mapOf(
                        "id" to r.id,
                        "taskTitle" to r.taskTitle,
                        "durationMinutes" to r.durationMinutes,
                        "durationSeconds" to r.durationSeconds,
                        "startTime" to r.startTime,
                        "endTime" to r.endTime,
                        "dateString" to r.dateString,
                        "tag" to r.tag,
                        "notes" to r.notes,
                        "timestamp" to r.timestamp
                    )
                }
                ref.setValue(recordList).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(records, onCancellation = null)
                    } else {
                        continuation.resumeWith(Result.failure(task.exception ?: Exception("Failed to putTransferredData")))
                    }
                }
            }
        }
    }

    override suspend fun deleteTransferredData(requester: String, provider: String): retrofit2.Response<Unit> {
        if (isTester() || requester == "tester_mode_user" || provider == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deleteTransferredData in Tester Mode")
            return retrofit2.Response.success(Unit)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            getDatabase().getReference("transfer").child(requester).child(provider).removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(retrofit2.Response.success(Unit), onCancellation = null)
                    } else {
                        continuation.resume(retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, task.exception?.message ?: "Failed")), onCancellation = null)
                    }
                }
        }
    }
}
