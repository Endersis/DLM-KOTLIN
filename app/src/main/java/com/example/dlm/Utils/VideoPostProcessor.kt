package com.example.dlm.Utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.formats.proto.LandmarkProto
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
     * Procesa el video segun el modo seleccionado
     * Acepta resultados combinados o solo de manos para compatibilidad
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
                        combinedLandmarks != null && combinedLandmarks.isNotEmpty() -> {
                            // Usar datos combinados si estan disponibles
                            saveCombinedLandmarksToCSV(combinedLandmarks)
                        }
                        handLandmarks != null && handLandmarks.isNotEmpty() -> {
                            // Solo datos de manos
                            saveHandLandmarksToCSV(handLandmarks)
                        }
                        else -> {
                            return@withContext ProcessingResult(false, error = "No se proporcionaron landmarks")
                        }
                    }
                    ProcessingResult(success = filePath != null, filePath = filePath)
                }
                ProcessingMode.VIDEO_FRAMES -> {
                    val framesPaths = extractFramesFromVideo(videoUri)
                    ProcessingResult(success = framesPaths.isNotEmpty(), framesPaths = framesPaths)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando video", e)
            ProcessingResult(false, error = e.message)
        }
    }

    /**
     * Obtiene el valor real de visibility de un landmark de pose
     */
    private fun getRealVisibility(poseLandmarkerResult: PoseLandmarkerResult, personIndex: Int, landmarkIndex: Int): Float {
        return try {
            // Intentar obtener desde worldLandmarks primero (si están disponibles)
            val worldLandmarks = poseLandmarkerResult.worldLandmarks()
            if (worldLandmarks.isNotEmpty() && personIndex < worldLandmarks.size) {
                val landmarks = worldLandmarks[personIndex]
                if (landmarkIndex < landmarks.size) {
                    // WorldLandmarks a veces tienen visibility
                    val landmark = landmarks[landmarkIndex]
                    // Usar reflection para intentar acceder a visibility si existe
                    try {
                        val visibilityField = landmark.javaClass.getDeclaredField("visibility")
                        visibilityField.isAccessible = true
                        return visibilityField.getFloat(landmark)
                    } catch (e: Exception) {
                        // Si no funciona, continuar con el siguiente método
                    }
                }
            }

            // Método alternativo: acceder a través de landmarks normalizados
            val normalizedLandmarks = poseLandmarkerResult.landmarks()
            if (normalizedLandmarks.isNotEmpty() && personIndex < normalizedLandmarks.size) {
                val landmarks = normalizedLandmarks[personIndex]
                if (landmarkIndex < landmarks.size) {
                    val landmark = landmarks[landmarkIndex]

                    // Intentar acceder a visibility usando reflection
                    try {
                        val visibilityMethod = landmark.javaClass.getMethod("visibility")
                        return visibilityMethod.invoke(landmark) as Float
                    } catch (e: Exception) {
                        Log.d(TAG, "No se pudo acceder a visibility con método: ${e.message}")
                    }

                    // Intentar con campo directo
                    try {
                        val visibilityField = landmark.javaClass.getDeclaredField("visibility")
                        visibilityField.isAccessible = true
                        return visibilityField.getFloat(landmark)
                    } catch (e: Exception) {
                        Log.d(TAG, "No se pudo acceder a visibility con field: ${e.message}")
                    }

                    // Si landmark tiene presence, usar eso
                    try {
                        val presenceMethod = landmark.javaClass.getMethod("presence")
                        return presenceMethod.invoke(landmark) as Float
                    } catch (e: Exception) {
                        Log.d(TAG, "No se pudo acceder a presence: ${e.message}")
                    }
                }
            }

            // Si todo falla, calcular estimación basada en coordenadas
            return calculateVisibilityFromCoordinates(poseLandmarkerResult, personIndex, landmarkIndex)

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo visibility para landmark $landmarkIndex", e)
            return 0.5f // Valor por defecto
        }
    }

    /**
     * Calcula visibility basado en las coordenadas y la posición del landmark
     */
    private fun calculateVisibilityFromCoordinates(
        poseLandmarkerResult: PoseLandmarkerResult,
        personIndex: Int,
        landmarkIndex: Int
    ): Float {
        try {
            val normalizedLandmarks = poseLandmarkerResult.landmarks()
            if (normalizedLandmarks.isNotEmpty() && personIndex < normalizedLandmarks.size) {
                val landmarks = normalizedLandmarks[personIndex]
                if (landmarkIndex < landmarks.size) {
                    val landmark = landmarks[landmarkIndex]
                    val x = landmark.x()
                    val y = landmark.y()
                    val z = landmark.z()

                    // Calcular visibility basado en:
                    // 1. Si está dentro del frame (x,y en [0,1])
                    // 2. Profundidad relativa (z)
                    // 3. Tipo de punto anatómico

                    var visibility = 1.0f

                    // Penalizar si está fuera del frame
                    if (x < 0 || x > 1 || y < 0 || y > 1) {
                        visibility *= 0.3f
                    }

                    // Ajustar por profundidad (z más negativo = más lejos)
                    if (z < -0.5f) {
                        visibility *= 0.7f
                    } else if (z < -0.2f) {
                        visibility *= 0.85f
                    }

                    // Ajustar por tipo de punto anatómico
                    visibility *= when (landmarkIndex) {
                        11, 12, 23, 24 -> 0.95f  // Hombros y caderas - alta visibility
                        13, 14 -> 0.90f           // Codos
                        15, 16 -> 0.80f           // Muñecas
                        else -> 0.85f
                    }

                    return maxOf(0.0f, minOf(1.0f, visibility))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando visibility desde coordenadas", e)
        }

        return 0.5f
    }

    /**
     * Formato: kp_1, kp_2, ... kp_158
     * Estructura: pose (8 puntos x 4 valores) + mano_izq (21 x 3) + mano_der (21 x 3)
     */
    private fun saveCombinedLandmarksToCSV(combinedLandmarks: List<CombinedLandmarksResult>): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "landmarks_$timestamp.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileWriter(file).use { writer ->
                // Calcular numero total de columnas
                // 8 puntos pose x 4 valores (x,y,z,visibility) = 32
                // 21 puntos mano izq x 3 valores (x,y,z) = 63
                // 21 puntos mano der x 3 valores (x,y,z) = 63
                // Total = 32 + 63 + 63 = 158 columnas

                val totalColumns = 158

                // Escribir headers
                val headers = (0..totalColumns).joinToString(",") { "kp_$it" }
                writer.appendLine(headers)

                // Procesar cada frame
                combinedLandmarks.forEach { combinedResult ->
                    val values = mutableListOf<String>()

                    // 1. AGREGAR PUNTOS DE POSE (torso y brazos)
                    val poseLandmarksResult = combinedResult.poseLandmarksResult
                    val poseLandmarks = poseLandmarksResult.landmarks()

                    if (poseLandmarks.isNotEmpty()) {
                        val pose = poseLandmarks[0] // Primera persona detectada

                        // Extraer solo los puntos del torso y brazos
                        HandDetectionManager.TORSO_ARM_INDICES.forEach { index ->
                            if (index < pose.size) {
                                val lm = pose[index]
                                // X, Y, Z coordenadas
                                values.add(formatFloat(lm.x()))
                                values.add(formatFloat(lm.y()))
                                values.add(formatFloat(lm.z()))

                                // Obtener visibility real
                                val visibility = getRealVisibility(poseLandmarksResult, 0, index)
                                values.add(formatFloat(visibility))

                                // Log para debugging
                                Log.d(TAG, "Landmark $index: visibility = $visibility")
                            } else {
                                // Si el punto no existe, agregar ceros
                                repeat(4) { values.add("0.000000") }
                            }
                        }
                    } else {
                        repeat(32) { values.add("0.000000") }
                    }

                    // 2. PROCESAR MANOS (el resto del código permanece igual)
                    val handLandmarks = combinedResult.handLandmarksResult.landmarks()
                    val handedness = combinedResult.handLandmarksResult.handednesses()

                    // Buscar y procesar mano izquierda
                    var leftHandProcessed = false
                    for (i in handLandmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Left") {
                                val landmarks = handLandmarks[i]
                                // 21 puntos de la mano izquierda
                                landmarks.forEach { lm ->
                                    values.add(formatFloat(lm.x()))
                                    values.add(formatFloat(lm.y()))
                                    values.add(formatFloat(lm.z()))
                                }
                                leftHandProcessed = true
                                break
                            }
                        }
                    }

                    // Si no hay mano izquierda, agregar ceros (21 puntos x 3 valores = 63)
                    if (!leftHandProcessed) {
                        repeat(63) { values.add("0.000000") }
                    }

                    // Buscar y procesar mano derecha
                    var rightHandProcessed = false
                    for (i in handLandmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Right") {
                                val landmarks = handLandmarks[i]
                                // 21 puntos de la mano derecha
                                landmarks.forEach { lm ->
                                    values.add(formatFloat(lm.x()))
                                    values.add(formatFloat(lm.y()))
                                    values.add(formatFloat(lm.z()))
                                }
                                rightHandProcessed = true
                                break
                            }
                        }
                    }

                    // Si no hay mano derecha, agregar ceros (21 puntos x 3 valores = 63)
                    if (!rightHandProcessed) {
                        repeat(63) { values.add("0.000000") }
                    }

                    // Escribir la fila con todos los valores
                    writer.appendLine(values.joinToString(","))
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando CSV combinado", e)
            null
        }
    }

    /**
     * Guarda solo landmarks de manos en formato CSV (sin pose)
     * Para mantener compatibilidad cuando no hay datos de pose
     */
    private fun saveHandLandmarksToCSV(handLandmarks: List<HandLandmarkerResult>): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "hand_landmarks_$timestamp.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileWriter(file).use { writer ->
                // Solo manos: 21 puntos x 3 valores x 2 manos = 126 columnas
                val totalColumns = 126

                // Escribir headers
                val headers = (1..totalColumns).joinToString(",") { "kp_$it" }
                writer.appendLine(headers)

                // Procesar cada frame
                handLandmarks.forEach { result ->
                    val values = mutableListOf<String>()
                    val landmarks = result.landmarks()
                    val handedness = result.handednesses()

                    // Procesar mano izquierda
                    var leftFound = false
                    for (i in landmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Left") {
                                landmarks[i].forEach { lm ->
                                    values.add(formatFloat(lm.x()))
                                    values.add(formatFloat(lm.y()))
                                    values.add(formatFloat(lm.z()))
                                }
                                leftFound = true
                                break
                            }
                        }
                    }
                    if (!leftFound) {
                        repeat(63) { values.add("0.000000") }
                    }

                    // Procesar mano derecha
                    var rightFound = false
                    for (i in landmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Right") {
                                landmarks[i].forEach { lm ->
                                    values.add(formatFloat(lm.x()))
                                    values.add(formatFloat(lm.y()))
                                    values.add(formatFloat(lm.z()))
                                }
                                rightFound = true
                                break
                            }
                        }
                    }
                    if (!rightFound) {
                        repeat(63) { values.add("0.000000") }
                    }

                    // Escribir fila
                    writer.appendLine(values.joinToString(","))
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando CSV de manos", e)
            null
        }
    }

    /**
     * Formatea un float a string con 6 decimales
     */
    private fun formatFloat(value: Float): String {
        return String.format(Locale.US, "%.6f", value)
    }

    /**
     * Extrae frames del video
     */
    private fun extractFramesFromVideo(videoUri: Uri): List<String> {
        val framesPaths = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)

            // Obtener duracion del video en microsegundos
            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationString?.toLong() ?: 0L
            val durationMicros = duration * 1000

            // Calcular intervalos para extraer 20 frames
            val numFrames = 10
            val interval = if (durationMicros > 0) durationMicros / numFrames else 0L

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

            repeat(numFrames) { frameIndex ->
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
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo frames", e)
        } finally {
            retriever.release()
        }

        return framesPaths
    }

    companion object {
        private const val TAG = "VideoPostProcessor"
    }
}