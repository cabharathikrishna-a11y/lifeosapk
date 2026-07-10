@file:Suppress("DEPRECATION")
package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleFitSyncManager {
    private const val TAG = "GoogleFitSync"
    private const val FIT_SCOPE = "oauth2:https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.body.read"

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val email = account?.email
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, FIT_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered.", recoverable)
            recoverable.intent?.let { intent -> 
                withContext(Dispatchers.Main) { onAuthResolutionRequired(intent) }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token: ${e.message}", e)
            null
        }
    }

    fun hasFitPermission(context: Context): Boolean {
        val scope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/fitness.activity.read")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, scope)
    }
}
