package com.example.dlm.Utils.Mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.dlm.manager.CombinedLandmarksResult
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
     * Procesa el video segun el modo seleccionado
     * Acepta resultados combinados o solo de manos para compatibilidad
     */
    suspend fun processVideo(
        videoUri: Uri,
        mode: ProcessingMode,
        handLandmarks: List<HandLandmarkerResult>? = null,
        combinedLandmarks: List<CombinedLandmarksResult>? = null,
        frameTimestamps: List<Long>? = null // Nuevo parámetro para timestamps de frames
    ): ProcessingResult = withContext(Dispatchers.IO) {
        try {
            when (mode) {
                ProcessingMode.HAND_LANDMARKS -> {
                    val filePath = when {
                        combinedLandmarks != null && combinedLandmarks.isNotEmpty() -> {
                            // Usar datos combinados si estan disponibles
                            saveCombinedLandmarksToCSV(combinedLandmarks, frameTimestamps, videoUri)
                        }
                        handLandmarks != null && handLandmarks.isNotEmpty() -> {
                            // Solo datos de manos
                            saveHandLandmarksToCSV(handLandmarks, frameTimestamps)
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
     * Guarda los landmarks combinados (manos + pose) en formato CSV
     * Incluye columna timestamp basado en el tiempo del video (empezando desde 0)
     * Estructura: timestamp + pose (0..16 → 17 puntos x 4 valores) + mano_izq (21 x 3) + mano_der (21 x 3)
     */
    private fun saveCombinedLandmarksToCSV(
        combinedLandmarks: List<CombinedLandmarksResult>,
        frameTimestamps: List<Long>? = null,
        videoUri: Uri? = null // Nuevo parámetro para obtener FPS automáticamente
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "landmarks_$timestamp.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileWriter(file).use { writer ->
                // Pose: 17 puntos x 4 valores = 68
                // Mano izquierda: 21 x 3 = 63
                // Mano derecha: 21 x 3 = 63
                // +1 columna extra para timestamp
                val totalColumns = 1 + 68 + 63 + 63 // = 195

                // Escribir headers
                val headers = buildList {
                    add("timestamp_ms") // Cambiar nombre para claridad
                    for (i in 0 until totalColumns - 1) {
                        add("kp_$i")
                    }
                }.joinToString(",")
                writer.appendLine(headers)

                // Calcular timestamps relativos si no se proporcionan
                val videoTimestamps = if (frameTimestamps != null && frameTimestamps.size == combinedLandmarks.size) {
                    frameTimestamps
                } else {
                    // Obtener FPS real del video si está disponible
                    val realFPS = if (videoUri != null) {
                        getVideoFPS(videoUri)
                    } else {
                        30.0f // FPS por defecto
                    }

                    Log.d(TAG, "Usando FPS: $realFPS para generar timestamps")
                    val frameDurationMs = 1000.0 / realFPS
                    combinedLandmarks.indices.map { frameIndex ->
                        (frameIndex * frameDurationMs).toLong()
                    }
                }

                // Procesar cada frame
                combinedLandmarks.forEachIndexed { frameIndex, combinedResult ->
                    val values = mutableListOf<String>()

                    // Agregar timestamp del video (relativo desde el inicio)
                    val videoTimestamp = videoTimestamps.getOrNull(frameIndex) ?: (frameIndex * 33L) // Fallback
                    values.add(videoTimestamp.toString())

                    // ---------- POSE ----------
                    val poseLandmarksResult = combinedResult.poseLandmarksResult
                    val poseLandmarks = poseLandmarksResult.landmarks()

                    if (poseLandmarks.isNotEmpty()) {
                        val pose = poseLandmarks[0] // Primera persona detectada

                        // Solo usar índices 0 al 16
                        (0..16).forEach { index ->
                            if (index < pose.size) {
                                val lm = pose[index]
                                val visibility = getRealVisibility(poseLandmarksResult, 0, index)

                                if (visibility > 0.3f) {
                                    values.add(formatFloat(lm.x()))
                                    values.add(formatFloat(lm.y()))
                                    values.add(formatFloat(lm.z()))
                                    values.add(formatFloat(visibility))
                                } else {
                                    repeat(4) { values.add("0.000000") }
                                }
                            } else {
                                repeat(4) { values.add("0.000000") }
                            }
                        }
                    } else {
                        // Si no hay pose detectada, rellenar con ceros (68 valores)
                        repeat(68) { values.add("0.000000") }
                    }

                    // ---------- MANO IZQUIERDA ----------
                    val handLandmarks = combinedResult.handLandmarksResult.landmarks()
                    val handedness = combinedResult.handLandmarksResult.handednesses()

                    var leftHandProcessed = false
                    for (i in handLandmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Left") {
                                val landmarks = handLandmarks[i]
                                for (j in 0 until 21) {
                                    if (j < landmarks.size) {
                                        val lm = landmarks[j]
                                        values.add(formatFloat(lm.x()))
                                        values.add(formatFloat(lm.y()))
                                        values.add(formatFloat(lm.z()))
                                    } else {
                                        repeat(3) { values.add("0.000000") }
                                    }
                                }
                                leftHandProcessed = true
                                break
                            }
                        }
                    }
                    if (!leftHandProcessed) {
                        repeat(63) { values.add("0.000000") }
                    }

                    // ---------- MANO DERECHA ----------
                    var rightHandProcessed = false
                    for (i in handLandmarks.indices) {
                        if (i < handedness.size && handedness[i].isNotEmpty()) {
                            val hand = handedness[i][0]
                            if (hand.categoryName() == "Right") {
                                val landmarks = handLandmarks[i]
                                for (j in 0 until 21) {
                                    if (j < landmarks.size) {
                                        val lm = landmarks[j]
                                        values.add(formatFloat(lm.x()))
                                        values.add(formatFloat(lm.y()))
                                        values.add(formatFloat(lm.z()))
                                    } else {
                                        repeat(3) { values.add("0.000000") }
                                    }
                                }
                                rightHandProcessed = true
                                break
                            }
                        }
                    }
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
    private fun saveHandLandmarksToCSV(
        handLandmarks: List<HandLandmarkerResult>,
        frameTimestamps: List<Long>? = null
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "hand_landmarks_$timestamp.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileWriter(file).use { writer ->
                // Solo manos: 21 puntos x 3 valores x 2 manos = 126 columnas + timestamp
                val totalColumns = 1 + 126

                // Escribir headers
                val headers = buildList {
                    add("timestamp_ms")
                    for (i in 0 until totalColumns - 1) {
                        add("kp_$i")
                    }
                }.joinToString(",")
                writer.appendLine(headers)

                // Calcular timestamps si no se proporcionan
                val videoTimestamps = if (frameTimestamps != null && frameTimestamps.size == handLandmarks.size) {
                    frameTimestamps
                } else {
                    val frameDurationMs = 1000.0 / 30.0 // 30 FPS por defecto
                    handLandmarks.indices.map { frameIndex ->
                        (frameIndex * frameDurationMs).toLong()
                    }
                }

                // Procesar cada frame
                handLandmarks.forEachIndexed { frameIndex, result ->
                    val values = mutableListOf<String>()

                    // Agregar timestamp del video
                    val videoTimestamp = videoTimestamps.getOrNull(frameIndex) ?: (frameIndex * 33L)
                    values.add(videoTimestamp.toString())

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
     * Extrae frames del video sin limitar FPS
     * Ahora extrae todos los frames disponibles según la duración del video
     */
    private fun extractFramesFromVideo(videoUri: Uri): List<String> {
        val framesPaths = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)

            // Obtener información del video
            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val frameRateString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)

            val duration = durationString?.toLong() ?: 0L // duración en ms
            val frameRate = frameRateString?.toFloat() ?: 30f // FPS del video

            // Calcular número total de frames basado en FPS nativo
            val totalFrames = ((duration / 1000.0) * frameRate).toInt()
            val frameDurationMs = 1000.0 / frameRate // duración de cada frame en ms

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

            Log.d(TAG, "Video: ${duration}ms, ${frameRate}fps, ${totalFrames} frames totales")

            // Extraer todos los frames usando el FPS nativo
            repeat(totalFrames) { frameIndex ->
                val timeMs = (frameIndex * frameDurationMs).toLong()
                val timeMicros = timeMs * 1000

                val bitmap = retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST)

                bitmap?.let {
                    val fileName = "frame_${timestamp}_${String.format("%04d", frameIndex)}.jpg"
                    val file = File(context.getExternalFilesDir(null), fileName)

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    framesPaths.add(file.absolutePath)

                    // Log progreso cada 100 frames
                    if (frameIndex % 100 == 0) {
                        Log.d(TAG, "Extraído frame $frameIndex/$totalFrames")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo frames", e)
        } finally {
            retriever.release()
        }

        Log.d(TAG, "Extraídos ${framesPaths.size} frames en total")
        return framesPaths
    }

    /**
     * Función auxiliar para obtener timestamps de video basados en FPS
     * Útil cuando procesas el video frame por frame
     */
    fun generateVideoTimestamps(totalFrames: Int, fps: Float = 30f): List<Long> {
        val frameDurationMs = 1000.0 / fps
        return (0 until totalFrames).map { frameIndex ->
            (frameIndex * frameDurationMs).toLong()
        }
    }

    /**
     * Función para obtener FPS del video
     */
    fun getVideoFPS(videoUri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val frameRateString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            frameRateString?.toFloat() ?: 30f
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo FPS del video", e)
            30f // FPS por defecto
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "VideoPostProcessor"
    }
}