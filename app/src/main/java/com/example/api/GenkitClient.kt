package com.example.api

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GenkitClient {
    private const val TAG = "GenkitClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Helper to await Google Play Services Task without external dependency conflicts
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: Exception("Unknown Firebase Task error"))
            }
        }
    }

    suspend fun executeGenkitFlow(
        prompt: String,
        url: String,
        flowName: String,
        authToken: String,
        method: String
    ): String {
        Log.d(TAG, "Executing Genkit Flow. Method: $method, URL: $url, FlowName: $flowName")
        
        return if (method.lowercase() == "callable") {
            callFirebaseHttpsCallable(prompt, flowName)
        } else {
            callHttpPost(prompt, url, authToken)
        }
    }

    private suspend fun callFirebaseHttpsCallable(prompt: String, flowName: String): String {
        try {
            val functions = FirebaseFunctions.getInstance()
            // Support both direct parameter passing and structured payloads
            val data = mapOf(
                "prompt" to prompt,
                "text" to prompt,
                "data" to prompt
            )
            
            Log.d(TAG, "Calling Firebase Functions HTTPS Callable for flow: $flowName")
            val task = functions.getHttpsCallable(flowName).call(data)
            val result = task.awaitTask()
            val resultData = result.data
            Log.d(TAG, "Firebase Functions Callable response: $resultData")
            
            return when (resultData) {
                is String -> resultData
                is Map<*, *> -> {
                    // Look for standard result keys
                    val foundVal = resultData["result"] ?: resultData["response"] ?: resultData["text"] ?: resultData["data"]
                    if (foundVal != null) {
                        if (foundVal is Map<*, *>) {
                            (foundVal["text"] ?: foundVal.toString()).toString()
                        } else {
                            foundVal.toString()
                        }
                    } else {
                        resultData.toString()
                    }
                }
                else -> resultData?.toString() ?: "Empty response from Genkit callable flow."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Firebase Functions Genkit flow", e)
            throw Exception("Firebase Functions Genkit error: ${e.localizedMessage}")
        }
    }

    private suspend fun callHttpPost(prompt: String, url: String, authToken: String): String {
        if (url.isEmpty()) {
            throw Exception("Genkit Endpoint URL is empty. Please configure it in Deepa AI Settings.")
        }
        
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject()
                
                // Wrap prompt in structured data fields commonly expected by Genkit flows
                val innerData = JSONObject()
                innerData.put("prompt", prompt)
                innerData.put("text", prompt)
                
                jsonPayload.put("data", prompt)
                jsonPayload.put("prompt", prompt)
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonPayload.toString().toRequestBody(mediaType)
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    
                if (authToken.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $authToken")
                }
                
                val request = requestBuilder.build()
                
                Log.d(TAG, "Sending HTTP POST request to $url")
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Unexpected HTTP Code: ${response.code} - ${response.message}")
                    }
                    
                    val responseBody = response.body?.string() ?: throw Exception("Empty response body from Genkit endpoint.")
                    Log.d(TAG, "Received HTTP POST Response: $responseBody")
                    
                    parseGenkitHttpResponse(responseBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing Genkit HTTP POST flow", e)
                throw Exception("Genkit HTTP Error: ${e.localizedMessage}")
            }
        }
    }

    private fun parseGenkitHttpResponse(body: String): String {
        return try {
            val json = JSONObject(body)
            when {
                json.has("result") -> json.getString("result")
                json.has("response") -> json.getString("response")
                json.has("text") -> json.getString("text")
                json.has("data") -> {
                    val dataVal = json.get("data")
                    if (dataVal is JSONObject && dataVal.has("text")) {
                        dataVal.getString("text")
                    } else if (dataVal is JSONObject && dataVal.has("result")) {
                        dataVal.getString("result")
                    } else {
                        dataVal.toString()
                    }
                }
                else -> body
            }
        } catch (e: Exception) {
            body
        }
    }
}
