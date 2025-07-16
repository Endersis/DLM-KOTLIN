package com.example.dlm.Network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ServerManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // TODO: Configurar la URL del servidor cuando esté listo
    private val baseUrl = "https://your-server-url.com/api"

    data class UploadResponse(
        val success: Boolean,
        val message: String,
        val data: Any? = null
    )

    /**
     * Envía archivo de landmarks de manos al servidor
     */
    suspend fun uploadHandLandmarks(filePath: String): UploadResponse = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext UploadResponse(false, "File not found: $filePath")
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "landmarks_file",
                    file.name,
                    file.asRequestBody("text/plain".toMediaType())
                )
                .addFormDataPart("type", "hand_landmarks")
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/upload/landmarks")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                UploadResponse(true, "Hand landmarks uploaded successfully")
            } else {
                UploadResponse(false, "Upload failed: ${response.code} - ${response.message}")
            }

        } catch (e: IOException) {
            UploadResponse(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            UploadResponse(false, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Envía múltiples frames de video al servidor
     */
    suspend fun uploadVideoFrames(framesPaths: List<String>): UploadResponse = withContext(Dispatchers.IO) {
        try {
            if (framesPaths.isEmpty()) {
                return@withContext UploadResponse(false, "No frames to upload")
            }

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "video_frames")
                .addFormDataPart("frames_count", framesPaths.size.toString())
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())

            // Agregar cada frame al multipart
            framesPaths.forEachIndexed { index, framePath ->
                val file = File(framePath)
                if (file.exists()) {
                    multipartBuilder.addFormDataPart(
                        "frame_$index",
                        file.name,
                        file.asRequestBody("image/jpeg".toMediaType())
                    )
                }
            }

            val requestBody = multipartBuilder.build()

            val request = Request.Builder()
                .url("$baseUrl/upload/frames")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                UploadResponse(true, "Video frames uploaded successfully")
            } else {
                UploadResponse(false, "Upload failed: ${response.code} - ${response.message}")
            }

        } catch (e: IOException) {
            UploadResponse(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            UploadResponse(false, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Envía datos del video original (opcional)
     */
    suspend fun uploadVideoFile(videoPath: String): UploadResponse = withContext(Dispatchers.IO) {
        try {
            val file = File(videoPath)
            if (!file.exists()) {
                return@withContext UploadResponse(false, "Video file not found: $videoPath")
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "video_file",
                    file.name,
                    file.asRequestBody("video/mp4".toMediaType())
                )
                .addFormDataPart("type", "original_video")
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/upload/video")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                UploadResponse(true, "Video uploaded successfully")
            } else {
                UploadResponse(false, "Upload failed: ${response.code} - ${response.message}")
            }

        } catch (e: IOException) {
            UploadResponse(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            UploadResponse(false, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Verifica la conectividad con el servidor
     */
    suspend fun checkServerConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}