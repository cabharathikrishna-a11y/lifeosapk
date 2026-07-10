package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class MapZone(
    val id: String,
    val name: String,
    val size: String,
    val url: String,
    val fileName: String
)

object MapDownloadManager {
    val zones = listOf(
        MapZone(
            id = "southern",
            name = "Southern India (Primary)",
            size = "511 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/southern-zone.map",
            fileName = "southern-zone.map"
        ),
        MapZone(
            id = "central",
            name = "Central India",
            size = "309 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/central-zone.map",
            fileName = "central-zone.map"
        ),
        MapZone(
            id = "eastern",
            name = "Eastern India",
            size = "205 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/eastern-zone.map",
            fileName = "eastern-zone.map"
        ),
        MapZone(
            id = "north-eastern",
            name = "North-Eastern India",
            size = "105 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/north-eastern-zone.map",
            fileName = "north-eastern-zone.map"
        ),
        MapZone(
            id = "northern",
            name = "Northern India",
            size = "202 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/northern-zone.map",
            fileName = "northern-zone.map"
        ),
        MapZone(
            id = "western",
            name = "Western India",
            size = "191 MB",
            url = "https://download.mapsforge.org/maps/v5/asia/india/western-zone.map",
            fileName = "western-zone.map"
        )
    )

    private val _activeDownloadingZoneId = MutableStateFlow<String?>(null)
    val activeDownloadingZoneId: StateFlow<String?> = _activeDownloadingZoneId

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadProgress = MutableStateFlow<Float?>(null) // null means not downloading, 0f-1f is progress
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun getMapFile(context: Context, zoneId: String = "southern"): File {
        val zone = zones.find { it.id == zoneId } ?: zones.first()
        val mapsDir = File(context.filesDir, "maps")
        if (!mapsDir.exists()) {
            mapsDir.mkdirs()
        }
        return File(mapsDir, zone.fileName)
    }

    fun isMapFileDownloaded(context: Context, zoneId: String = "southern"): Boolean {
        val file = getMapFile(context, zoneId)
        return file.exists() && file.length() > 10 * 1024 * 1024 // Greater than 10MB to be a valid map file
    }

    // Compatibility check for existing callers (e.g. JournalBookView.kt)
    fun isMapFileDownloaded(context: Context): Boolean {
        return isMapFileDownloaded(context, "southern")
    }

    // Compatibility download for existing callers (e.g. JournalBookView.kt)
    suspend fun downloadMap(context: Context) {
        downloadMap(context, "southern")
    }

    suspend fun downloadMap(context: Context, zoneId: String) {
        if (_activeDownloadingZoneId.value != null) return
        _activeDownloadingZoneId.value = zoneId
        _isDownloading.value = true
        _downloadProgress.value = 0f
        _downloadError.value = null

        val zone = zones.find { it.id == zoneId }
        if (zone == null) {
            _downloadError.value = "Unknown map zone ID: $zoneId"
            _activeDownloadingZoneId.value = null
            _isDownloading.value = false
            return
        }

        withContext(Dispatchers.IO) {
            val url = zone.url
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadError.value = "Server returned code: ${response.code}"
                    _activeDownloadingZoneId.value = null
                    _isDownloading.value = false
                    _downloadProgress.value = null
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _downloadError.value = "Response body is empty"
                    _activeDownloadingZoneId.value = null
                    _isDownloading.value = false
                    _downloadProgress.value = null
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val targetFile = getMapFile(context, zoneId)
                val tempFile = File(context.filesDir, "${zone.fileName}.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(65536) // Optimized buffer size for faster file writing (64KB)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastUpdateTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalBytes > 0) {
                        val currentTime = System.currentTimeMillis()
                        // Throttle state updates to at most once every 250ms to prevent blocking the main thread with Compose recompositions
                        if (currentTime - lastUpdateTime >= 250 || totalBytesRead == totalBytes) {
                            _downloadProgress.value = totalBytesRead.toFloat() / totalBytes.toFloat()
                            lastUpdateTime = currentTime
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (tempFile.renameTo(targetFile)) {
                    _downloadProgress.value = 1f
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    _downloadProgress.value = 1f
                }
            } catch (e: java.io.IOException) {
                _downloadError.value = "Network Error: ${e.localizedMessage}"
                e.printStackTrace()
            } catch (e: Exception) {
                _downloadError.value = e.localizedMessage ?: "Unknown error"
                e.printStackTrace()
            } finally {
                _activeDownloadingZoneId.value = null
                _isDownloading.value = false
            }
        }
    }

    fun deleteMap(context: Context, zoneId: String): Boolean {
        val file = getMapFile(context, zoneId)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }

    fun getDownloadedZonesCount(context: Context): Int {
        return zones.count { isMapFileDownloaded(context, it.id) }
    }
}
