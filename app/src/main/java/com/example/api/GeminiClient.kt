package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
) {
    @JsonClass(generateAdapter = true)
    data class Content(
        @Json(name = "parts") val parts: List<Part>
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        @Json(name = "text") val text: String? = null,
        @Json(name = "inlineData") val inlineData: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        @Json(name = "mimeType") val mimeType: String,
        @Json(name = "data") val data: String
    )

    @JsonClass(generateAdapter = true)
    data class GenerationConfig(
        @Json(name = "responseModalities") val responseModalities: List<String>? = null,
        @Json(name = "imageConfig") val imageConfig: ImageConfig? = null
    )

    @JsonClass(generateAdapter = true)
    data class ImageConfig(
        @Json(name = "aspectRatio") val aspectRatio: String,
        @Json(name = "imageSize") val imageSize: String
    )
}

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
) {
    @JsonClass(generateAdapter = true)
    data class Candidate(
        @Json(name = "content") val content: Content?
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        @Json(name = "parts") val parts: List<Part>?
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        @Json(name = "text") val text: String? = null,
        @Json(name = "inlineData") val inlineData: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        @Json(name = "mimeType") val mimeType: String?,
        @Json(name = "data") val data: String?
    )
}

data class GeminiResult(
    val text: String,
    val base64Image: String? = null,
    val modelUsed: String
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun autoSelectModel(prompt: String): String {
        val lowercase = prompt.lowercase()
        return when {
            lowercase.contains("generate image") || lowercase.contains("draw") || 
            lowercase.contains("create image") || lowercase.contains("paint") || 
            lowercase.contains("sketch") || lowercase.contains("generate art") || 
            lowercase.contains("photo") || lowercase.contains("visual asset") ||
            lowercase.contains("picture") -> "gemini-2.5-flash-image"
            
            lowercase.contains("analyze") || lowercase.contains("optimize") || 
            lowercase.contains("ledger") || lowercase.contains("net worth") || 
            lowercase.contains("finance") || lowercase.contains("audit") || 
            lowercase.contains("schedule") || lowercase.contains("complex") || 
            lowercase.contains("pro") || lowercase.contains("reasoning") -> "gemini-3.1-pro-preview"
            
            else -> "gemini-3.5-flash"
        }
    }

    suspend fun getGeminiResponse(prompt: String): String {
        val result = getGeminiResult(prompt)
        return result.text
    }

    suspend fun getGeminiResult(prompt: String): GeminiResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw java.io.IOException("No active API Key configured inside Secrets Panel. Switching to Offline Intelligence mode.")
        }
        val model = autoSelectModel(prompt)
        
        val modelsToTry = mutableListOf(model)
        if (model == "gemini-3.5-flash") {
            modelsToTry.add("gemini-3.1-pro-preview")
        } else if (model == "gemini-3.1-pro-preview") {
            modelsToTry.add("gemini-3.5-flash")
        }
        
        var lastException: Exception? = null
        for (candidateModel in modelsToTry) {
            try {
                val isImage = candidateModel == "gemini-2.5-flash-image" || candidateModel == "imagen-3.0-generate-002"
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(text = prompt))
                        )
                    ),
                    generationConfig = if (isImage) {
                        GeminiRequest.GenerationConfig(
                            responseModalities = listOf("TEXT", "IMAGE"),
                            imageConfig = GeminiRequest.ImageConfig(aspectRatio = "1:1", imageSize = "1K")
                        )
                    } else null
                )
                val response = apiService.generateContent(candidateModel, apiKey, request)
                val candidate = response.candidates?.firstOrNull()
                val textPart = candidate?.content?.parts?.find { it.text != null }?.text
                val imagePart = candidate?.content?.parts?.find { it.inlineData != null }?.inlineData
                
                return GeminiResult(
                    text = textPart ?: if (imagePart != null) "Here is your generated image:" else "No valid text response from model.",
                    base64Image = imagePart?.data,
                    modelUsed = candidateModel
                )
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("GeminiClient", "Candidate model $candidateModel failed, trying next", e)
            }
        }
        throw lastException ?: java.io.IOException("Failed to call any Gemini model candidate.")
    }
}
