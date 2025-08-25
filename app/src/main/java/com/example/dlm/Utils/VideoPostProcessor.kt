package com.example.dlm.Utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
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
     * Ahora acepta resultados combinados o solo de manos para compatibilidad
     */
    suspend fun processVideo(
        videoUri: Uri,
        mode: ProcessingMode,
        handLandmarks: List<HandLandmarkerResult>? = null,
        combinedLandmarks: List<CombinedLandmarksResult>? = null
    ): ProcessingResult = withContext(Dispatchers.IO) {
        try {
            when (mode) {
                ProcessingMode.HAND_LANDMARKS -> {
                    val filePath = when {
                        combinedLandmarks != null -> saveCombinedLandmarksToTxt(combinedLandmarks)
                        handLandmarks != null -> saveHandLandmarksToTxt(handLandmarks)
                        else -> return@withContext ProcessingResult(false, error = "No landmarks provided")
                    }
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
     * Guarda los landmarks combinados (manos detalladas + torso/brazos) en un archivo TXT
     */
    private fun saveCombinedLandmarksToTxt(combinedLandmarks: List<CombinedLandmarksResult>): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "combined_landmarks_$timestamp.txt"
        val file = File(context.getExternalFilesDir(null), fileName)

        FileWriter(file).use { writer ->
            writer.appendLine(" (Detailed Hands + Torso/Arms)")
            writer.appendLine("Generados: ${Date()}")
            writer.appendLine("Total de frames: ${combinedLandmarks.size}")
            writer.appendLine("${"=".repeat(70)}")
            writer.appendLine()

            combinedLandmarks.forEachIndexed { frameIndex, result ->
                writer.appendLine("FRAME $frameIndex (Timestamp: ${result.timestamp} ms):")
                writer.appendLine("-".repeat(50))

                // ========== MANOS DETALLADAS (21 puntos por mano) ==========
                writer.appendLine("  DETAILED HAND LANDMARKS:")

                val handResult = result.handLandmarksResult

                // Procesar cada mano detectada
                handResult.landmarks().forEachIndexed { handIndex, landmarks ->
                    val handedness = try {
                        if (handResult.handednesses().size > handIndex &&
                            handResult.handednesses()[handIndex].isNotEmpty()) {
                            handResult.handednesses()[handIndex][0].categoryName()
                        } else "Unknown"
                    } catch (e: Exception) { "Unknown" }

                    writer.appendLine("    Hand $handIndex ($handedness):")

                    landmarks.forEachIndexed { landmarkIndex, landmark ->
                        val landmarkName = getHandLandmarkName(landmarkIndex)
                        writer.appendLine("      $landmarkName: " +
                                "x=${String.format("%.4f", landmark.x())}, " +
                                "y=${String.format("%.4f", landmark.y())}, " +
                                "z=${String.format("%.4f", landmark.z())}")
                    }
                }

                // Coordenadas mundiales de las manos
                if (handResult.worldLandmarks().isNotEmpty()) {
                    writer.appendLine("    World Coordinates:")
                    handResult.worldLandmarks().forEachIndexed { handIndex, worldLandmarks ->
                        writer.appendLine("      Hand $handIndex (World):")
                        worldLandmarks.forEachIndexed { landmarkIndex, landmark ->
                            val landmarkName = getHandLandmarkName(landmarkIndex)
                            writer.appendLine("        $landmarkName: " +
                                    "x=${String.format("%.4f", landmark.x())}, " +
                                    "y=${String.format("%.4f", landmark.y())}, " +
                                    "z=${String.format("%.4f", landmark.z())}")
                        }
                    }
                }

                // ========== TORSO Y BRAZOS (8 puntos seleccionados) ==========
                writer.appendLine("  TORSO y BRAZOS LANDMARKS:")

                val poseResult = result.poseLandmarksResult

                // Puntos de pose (imagen normalizada)
                poseResult.landmarks().firstOrNull()?.let { landmarks ->
                    writer.appendLine("    Pose Points (Normalized):")
                    HandDetectionManager.TORSO_ARM_INDICES.forEach { index ->
                        if (index < landmarks.size) {
                            val landmark = landmarks[index]
                            val pointName = HandDetectionManager.POSE_POINT_NAMES[index] ?: "Point $index"
                            writer.appendLine("      $pointName: " +
                                    "x=${String.format("%.4f", landmark.x())}, " +
                                    "y=${String.format("%.4f", landmark.y())}, " +
                                    "z=${String.format("%.4f", landmark.z())}")
                        }
                    }
                }

                // Puntos de pose (coordenadas mundiales)
                poseResult.worldLandmarks().firstOrNull()?.let { landmarks ->
                    writer.appendLine("    Pose Points (World Coordinates):")
                    HandDetectionManager.TORSO_ARM_INDICES.forEach { index ->
                        if (index < landmarks.size) {
                            val landmark = landmarks[index]
                            val pointName = HandDetectionManager.POSE_POINT_NAMES[index] ?: "Point $index"
                            writer.appendLine("      $pointName: " +
                                    "x=${String.format("%.4f", landmark.x())}, " +
                                    "y=${String.format("%.4f", landmark.y())}, " +
                                    "z=${String.format("%.4f", landmark.z())}")
                        }
                    }
                }

                writer.appendLine()
            }

            // Agregar resumen al final
            writer.appendLine("${"=".repeat(70)}")
            writer.appendLine("SUMMARY:")
            writer.appendLine("- Total frames analyzed: ${combinedLandmarks.size}")

            var framesWithHands = 0
            var framesWithPose = 0
            var totalHandsDetected = 0
            var framesWithBothHands = 0
            var framesWithLeftHand = 0
            var framesWithRightHand = 0

            combinedLandmarks.forEach { result ->
                if (result.handLandmarksResult.landmarks().isNotEmpty()) {
                    framesWithHands++
                    totalHandsDetected += result.handLandmarksResult.landmarks().size

                    // Contar manos específicas
                    if (result.handLandmarksResult.landmarks().size >= 2) {
                        framesWithBothHands++
                    }

                    result.handLandmarksResult.handednesses().forEach { handedness ->
                        if (handedness.isNotEmpty()) {
                            when (handedness[0].categoryName()) {
                                "Left" -> framesWithLeftHand++
                                "Right" -> framesWithRightHand++
                            }
                        }
                    }
                }
                if (result.poseLandmarksResult.landmarks().isNotEmpty()) {
                    framesWithPose++
                }
            }

            writer.appendLine("- Frames con manos detectadas: $framesWithHands")
            writer.appendLine("- Frames con ambas manos: $framesWithBothHands")
            writer.appendLine("- Frames con mano izquierda: $framesWithLeftHand")
            writer.appendLine("- Frames con mano derecha: $framesWithRightHand")
            writer.appendLine("- Total de manos detectadas: $totalHandsDetected")
            writer.appendLine("- Frames con pose detectada: $framesWithPose")
            writer.appendLine("- Puntos de pose rastreados: ${HandDetectionManager.TORSO_ARM_INDICES.size} (solo torso y brazos)")
        }

        return file.absolutePath
    }

    /**
     * Guarda solo los landmarks de las manos en un archivo TXT (compatibilidad con versión anterior)
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
                    val handedness = try {
                        if (result.handednesses().size > handIndex &&
                            result.handednesses()[handIndex].isNotEmpty()) {
                            result.handednesses()[handIndex][0].categoryName()
                        } else "Unknown"
                    } catch (e: Exception) { "Unknown" }

                    writer.appendLine("  Hand $handIndex ($handedness):")
                    landmarks.forEachIndexed { landmarkIndex, landmark ->
                        val landmarkName = getHandLandmarkName(landmarkIndex)
                        writer.appendLine("    $landmarkName: " +
                                "x=${String.format("%.4f", landmark.x())}, " +
                                "y=${String.format("%.4f", landmark.y())}, " +
                                "z=${String.format("%.4f", landmark.z())}")
                    }
                }

                if (result.worldLandmarks().isNotEmpty()) {
                    writer.appendLine("  World Coordinates:")
                    result.worldLandmarks().forEachIndexed { handIndex, worldLandmarks ->
                        writer.appendLine("    Hand $handIndex (World):")
                        worldLandmarks.forEachIndexed { landmarkIndex, landmark ->
                            val landmarkName = getHandLandmarkName(landmarkIndex)
                            writer.appendLine("      $landmarkName: " +
                                    "x=${String.format("%.4f", landmark.x())}, " +
                                    "y=${String.format("%.4f", landmark.y())}, " +
                                    "z=${String.format("%.4f", landmark.z())}")
                        }
                    }
                }
                writer.appendLine()
            }
        }

        return file.absolutePath
    }

    /**
     * Obtiene el nombre descriptivo de cada punto de la mano
     */
    private fun getHandLandmarkName(index: Int): String {
        return when (index) {
            0 -> "MUNECA"
            1  -> "PULGAR_CMC"
            2  -> "PULGAR_MCP"
            3  -> "PULGAR_IP"
            4  -> "PULGAR_PUNTA"
            5  -> "ÍNDICE_MCP"
            6  -> "ÍNDICE_PIP"
            7  -> "ÍNDICE_DIP"
            8  -> "ÍNDICE_PUNTA"
            9  -> "MEDIO_MCP"
            10 -> "MEDIO_PIP"
            11 -> "MEDIO_DIP"
            12 -> "MEDIO_PUNTA"
            13 -> "ANULAR_MCP"
            14 -> "ANULAR_PIP"
            15 -> "ANULAR_DIP"
            16 -> "ANULAR_PUNTA"
            17 -> "MEÑIQUE_MCP"
            18 -> "MEÑIQUE_PIP"
            19 -> "MEÑIQUE_DIP"
            20 -> "MEÑIQUE_PUNTA"
            else -> "Point_$index"
        }
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
            val durationMicros = duration * 1000

            // Calcular intervalos para extraer 10 frames
            val interval = durationMicros / 20

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            repeat(20) { frameIndex ->
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