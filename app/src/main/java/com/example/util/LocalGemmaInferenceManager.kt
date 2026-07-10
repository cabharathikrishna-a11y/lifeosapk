package com.example.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalGemmaInferenceManager {
    private const val TAG = "LocalGemmaManager"
    private var llmInference: LlmInference? = null
    private var initializedModelPath: String? = null

    // Potential paths where the user can place a real Gemma .bin model file
    fun getPossibleModelPaths(context: Context): List<String> {
        return listOf(
            File(context.filesDir, "gemma.bin").absolutePath,
            File(context.filesDir, "gemma-2b.bin").absolutePath,
            File(context.getExternalFilesDir(null), "gemma.bin").absolutePath,
            File(context.getExternalFilesDir(null), "gemma-2b.bin").absolutePath,
            "/sdcard/Download/gemma.bin",
            "/sdcard/Download/gemma2b.bin",
            "/sdcard/Download/gemma-2b-it-gpu.bin",
            "/sdcard/Download/gemma-2b-it-cpu.bin"
        )
    }

    /**
     * Checks if a real, valid model file exists in any of the standard paths.
     */
    fun findAvailableModelPath(context: Context): String? {
        val paths = getPossibleModelPaths(context)
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.isFile && file.length() > 50_000_000L) { // At least 50MB to be a real model file
                Log.d(TAG, "Found real local model file at: $path (${file.length()} bytes)")
                return path
            }
        }
        return null
    }

    /**
     * Attempts to initialize the native MediaPipe LlmInference task.
     * Returns true if successful, false otherwise.
     */
    fun initialize(context: Context): Boolean {
        val path = findAvailableModelPath(context)
        if (path == null) {
            Log.d(TAG, "No real Gemma model file found on storage. Running in Gemini-backed smart-sandbox mode.")
            close()
            return false
        }

        if (llmInference != null && initializedModelPath == path) {
            return true // Already initialized with this model
        }

        return try {
            close()
            Log.d(TAG, "Initializing native MediaPipe LLM Inference with model: $path")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            initializedModelPath = path
            Log.i(TAG, "Native MediaPipe LlmInference successfully initialized.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native LlmInference with model: $path", e)
            close()
            false
        }
    }

    /**
     * Generates a response synchronously from the local Gemma model.
     */
    suspend fun generateLocalResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext null
        try {
            Log.d(TAG, "Generating response from local Gemma model...")
            val startTime = System.currentTimeMillis()
            val response = inference.generateResponse(prompt)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Local inference completed in ${duration}ms")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error during local model inference", e)
            null
        }
    }

    fun isNativeEngineActive(): Boolean {
        return llmInference != null
    }

    fun getActiveModelPath(): String? {
        return initializedModelPath
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference", e)
        }
        llmInference = null
        initializedModelPath = null
    }
}
