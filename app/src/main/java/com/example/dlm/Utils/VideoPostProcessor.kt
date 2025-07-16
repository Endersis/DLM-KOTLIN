package com.example.dlm.Utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class VideoPostProcessor(private val context: Context) {

    enum class ProcessingMode {
        HAND_LANDMARKS,
        VIDEO_FRAMES
    }

    data class ProcessingResult(
        val success: Boolean,
        val filePath: String? = null,
        val framesPaths: List<String>? = null,
        val error: String? = null
    )

    /**
     * Procesa el video según el modo seleccionado
     */
    suspend fun processVideo(
        videoUri: Uri,
        mode: ProcessingMode,
        handLandmarks: List<HandLandmarkerResult>? = null
    ): ProcessingResult = withContext(Dispatchers.IO) {
        try {
            when (mode) {
                ProcessingMode.HAND_LANDMARKS -> {
                    if (handLandmarks == null) {
                        return@withContext ProcessingResult(false, error = "No hand landmarks provided")
                    }
                    val filePath = saveHandLandmarksToTxt(handLandmarks)
                    ProcessingResult(true, filePath = filePath)
                }
                ProcessingMode.VIDEO_FRAMES -> {
                    val framesPaths = extractFramesFromVideo(videoUri)
                    ProcessingResult(true, framesPaths = framesPaths)
                }
            }
        } catch (e: Exception) {
            ProcessingResult(false, error = e.message)
        }
    }

    /**
     * Guarda los landmarks de las manos en un archivo TXT
     */
    private fun saveHandLandmarksToTxt(handLandmarks: List<HandLandmarkerResult>): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "hand_landmarks_$timestamp.txt"
        val file = File(context.getExternalFilesDir(null), fileName)

        FileWriter(file).use { writer ->
            writer.appendLine("Hand Landmarks Data")
            writer.appendLine("Generated: ${Date()}")
            writer.appendLine("Total frames: ${handLandmarks.size}")
            writer.appendLine("${"=".repeat(50)}")
            writer.appendLine()

            handLandmarks.forEachIndexed { frameIndex, result ->
                writer.appendLine("Frame $frameIndex:")

                result.landmarks().forEachIndexed { handIndex, landmarks ->
                    writer.appendLine("  Hand $handIndex:")
                    landmarks.forEachIndexed { landmarkIndex, landmark ->
                        writer.appendLine("    Landmark $landmarkIndex: x=${landmark.x()}, y=${landmark.y()}, z=${landmark.z()}")
                    }
                }

                if (result.worldLandmarks().isNotEmpty()) {
                    writer.appendLine("  World Coordinates:")
                    result.worldLandmarks().forEachIndexed { handIndex, worldLandmarks ->
                        writer.appendLine("    Hand $handIndex (World):")
                        worldLandmarks.forEachIndexed { landmarkIndex, landmark ->
                            writer.appendLine("      Landmark $landmarkIndex: x=${landmark.x()}, y=${landmark.y()}, z=${landmark.z()}")
                        }
                    }
                }
                writer.appendLine()
            }
        }

        return file.absolutePath
    }

    /**
     * Extrae 10 frames del video sin importar la duración
     */
    private fun extractFramesFromVideo(videoUri: Uri): List<String> {
        val framesPaths = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)

            // Obtener duración del video en microsegundos
            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationString?.toLong() ?: 0L
            val durationMicros = duration * 1000 // Convertir a microsegundos

            // Calcular intervalos para extraer 10 frames
            val interval = durationMicros / 10

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            repeat(10) { frameIndex ->
                val timeUs = interval * frameIndex
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                bitmap?.let {
                    val fileName = "frame_${timestamp}_${frameIndex}.jpg"
                    val file = File(context.getExternalFilesDir(null), fileName)

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    framesPaths.add(file.absolutePath)
                }
            }
        } finally {
            retriever.release()
        }

        return framesPaths
    }
}