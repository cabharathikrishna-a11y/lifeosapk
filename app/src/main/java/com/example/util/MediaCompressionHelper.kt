package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * A client-side helper to compress and optimize images memory-safely
 * before they are stored or transferred, preventing excessive memory usage
 * and storage bloat.
 */
object MediaCompressionHelper {
    private const val TAG = "MediaCompressionHelper"

    /**
     * Compresses an existing image file in-place or returns the optimized file.
     * Prevents out-of-memory issues by downscaling large pictures.
     */
    fun compressImageFile(context: Context, sourceFile: File, maxDimension: Int = 1280, quality: Int = 80): File {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return sourceFile

        val name = sourceFile.name.lowercase()
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp")) {
            return sourceFile // Keep other files as-is
        }

        try {
            // Phase 1: Determine dimensions without loading bitmap into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return sourceFile

            // Phase 2: Compute sample size
            var sampleSize = 1
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // Phase 3: Decode bitmap safely
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return sourceFile

            // Phase 4: Output compressed JPEG bytes to a temporary cache file
            val tempCompressed = File(context.cacheDir, "cmp_${System.currentTimeMillis()}_${sourceFile.name}")
            FileOutputStream(tempCompressed).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            bitmap.recycle()

            // Phase 5: Swap files if compressed result is smaller
            if (tempCompressed.exists() && tempCompressed.length() < sourceFile.length()) {
                Log.d(TAG, "Compressed: ${sourceFile.name} (${sourceFile.length()} -> ${tempCompressed.length()} bytes)")
                sourceFile.delete()
                tempCompressed.renameTo(sourceFile)
                return sourceFile
            } else {
                tempCompressed.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image file: ${sourceFile.name}", e)
        }
        return sourceFile
    }

    /**
     * Read from image Uri, downscale, and compress directly into target destination file memory-safely.
     */
    fun compressImageFromUri(context: Context, uri: Uri, destFile: File, maxDimension: Int = 1280, quality: Int = 80): Boolean {
        return try {
            val resolver = context.contentResolver

            // Phase 1: Read bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return false

            // Phase 2: Compute sample scale
            var sampleSize = 1
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // Phase 3: Decode bitmap with sampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return false

            // Phase 4: Save compressed format
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            }
            bitmap.recycle()

            Log.d(TAG, "Successfully compressed uri image into: ${destFile.name} (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing Uri image: $uri", e)
            false
        }
    }

    /**
     * Web Compression API equivalent: Compresses a file using the GZIP format.
     * Memory-safe streaming implementation avoiding load of full files into RAM.
     */
    fun compressFileGzip(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        return try {
            destination.parentFile?.mkdirs()
            java.io.FileInputStream(source).use { fileIn ->
                java.io.FileOutputStream(destination).use { fileOut ->
                    java.util.zip.GZIPOutputStream(fileOut).use { gzipOut ->
                        val buffer = ByteArray(8192)
                        var bytesRead = fileIn.read(buffer)
                        while (bytesRead != -1) {
                            gzipOut.write(buffer, 0, bytesRead)
                            bytesRead = fileIn.read(buffer)
                        }
                    }
                }
            }
            Log.d(TAG, "GZIP Compressed: ${source.name} (${source.length()} -> ${destination.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "GZIP Compression failed for: ${source.name}", e)
            false
        }
    }

    /**
     * Web Compression API equivalent: Decompresses a GZIP-compressed file back to its original raw form.
     */
    fun decompressFileGzip(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        return try {
            destination.parentFile?.mkdirs()
            java.io.FileInputStream(source).use { fileIn ->
                java.util.zip.GZIPInputStream(fileIn).use { gzipIn ->
                    java.io.FileOutputStream(destination).use { fileOut ->
                        val buffer = ByteArray(8192)
                        var bytesRead = gzipIn.read(buffer)
                        while (bytesRead != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                            bytesRead = gzipIn.read(buffer)
                        }
                    }
                }
            }
            Log.d(TAG, "GZIP Decompressed: ${source.name} to ${destination.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "GZIP Decompression failed for: ${source.name}", e)
            false
        }
    }

    /**
     * Web Compression API equivalent: Compresses a file using DEFLATE (zlib wrapper) format.
     */
    fun compressFileDeflate(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        return try {
            destination.parentFile?.mkdirs()
            java.io.FileInputStream(source).use { fileIn ->
                java.io.FileOutputStream(destination).use { fileOut ->
                    java.util.zip.DeflaterOutputStream(fileOut).use { deflateOut ->
                        val buffer = ByteArray(8192)
                        var bytesRead = fileIn.read(buffer)
                        while (bytesRead != -1) {
                            deflateOut.write(buffer, 0, bytesRead)
                            bytesRead = fileIn.read(buffer)
                        }
                    }
                }
            }
            Log.d(TAG, "DEFLATE Compressed: ${source.name} (${source.length()} -> ${destination.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "DEFLATE Compression failed for: ${source.name}", e)
            false
        }
    }

    /**
     * Web Compression API equivalent: Decompresses a DEFLATE-compressed file back to original form.
     */
    fun decompressFileDeflate(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        return try {
            destination.parentFile?.mkdirs()
            java.io.FileInputStream(source).use { fileIn ->
                java.util.zip.InflaterInputStream(fileIn).use { inflateIn ->
                    java.io.FileOutputStream(destination).use { fileOut ->
                        val buffer = ByteArray(8192)
                        var bytesRead = inflateIn.read(buffer)
                        while (bytesRead != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                            bytesRead = inflateIn.read(buffer)
                        }
                    }
                }
            }
            Log.d(TAG, "DEFLATE Decompressed: ${source.name} to ${destination.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "DEFLATE Decompression failed for: ${source.name}", e)
            false
        }
    }

    /**
     * Checks if a file has a GZIP magic header (signature is 0x1f8b in big endian or little endian bytes).
     */
    fun isGzipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 2) return false
        return try {
            java.io.FileInputStream(file).use { fileIn ->
                val b1 = fileIn.read()
                val b2 = fileIn.read()
                b1 == 0x1F && b2 == 0x8B
            }
        } catch (e: Exception) {
            false
        }
    }
}
