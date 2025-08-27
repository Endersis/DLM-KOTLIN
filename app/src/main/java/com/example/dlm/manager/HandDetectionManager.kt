package com.example.dlm.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

class HandDetectionManager(private val context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null

    private val _handsDetected = MutableStateFlow(false)
    val handsDetected: StateFlow<Boolean> = _handsDetected

    // Almacenar los últimos resultados de cada detector
    private var lastHandResult: HandLandmarkerResult? = null
    private var lastPoseResult: PoseLandmarkerResult? = null
    private var lastTimestamp: Long = 0

    // Callbacks combinados
    var onHandLandmarksDetected: ((HandLandmarkerResult) -> Unit)? = null
    var onCombinedLandmarksDetected: ((CombinedLandmarksResult) -> Unit)? = null
    var onHandsDetectionChanged: ((Boolean) -> Unit)? = null

    companion object {
        // TODOS los 33 puntos de MediaPipe Pose (índices 0-32)
        // Usaremos todos los puntos disponibles
        val ALL_POSE_INDICES = (0..16).toList()

        val POSE_POINT_NAMES = mapOf(
            // Cara
            0 to "Nariz",
            1 to "Ojo Interno Izquierdo",
            2 to "Ojo Izquierdo",
            3 to "Ojo Externo Izquierdo",
            4 to "Ojo Interno Derecho",
            5 to "Ojo Derecho",
            6 to "Ojo Externo Derecho",
            7 to "Oreja Izquierda",
            8 to "Oreja Derecha",
            9 to "Boca Izquierda",
            10 to "Boca Derecha",

            // Torso y brazos
            11 to "Hombro Izquierdo",
            12 to "Hombro Derecho",
            13 to "Codo Izquierdo",
            14 to "Codo Derecho",
            15 to "Muñeca Izquierda",
            16 to "Muñeca Derecha",

        )


    }

    init {
        setupHandLandmarker()
        setupPoseLandmarker()
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2) // Detectar hasta 2 manos
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, image ->
                handleHandDetectionResult(result)
            }
            .setErrorListener { error ->
                error.printStackTrace()
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setNumPoses(1) // Solo detectar una persona
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, image ->
                handlePoseDetectionResult(result)
            }
            .setErrorListener { error ->
                error.printStackTrace()
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    private fun handleHandDetectionResult(result: HandLandmarkerResult) {
        lastHandResult = result

        val hasHands = result.landmarks().isNotEmpty()

        if (hasHands != _handsDetected.value) {
            _handsDetected.value = hasHands
            onHandsDetectionChanged?.invoke(hasHands)
        }

        if (hasHands) {
            onHandLandmarksDetected?.invoke(result)
            // Combinar con resultado de pose si está disponible y es del mismo frame
            combineResultsIfReady()
        }
    }

    private fun handlePoseDetectionResult(result: PoseLandmarkerResult) {
        lastPoseResult = result
        // Combinar con resultado de manos si está disponible
        combineResultsIfReady()
    }

    private fun combineResultsIfReady() {
        if (lastHandResult != null && lastPoseResult != null) { // Quita el && _handsDetected.value
            val combinedResult = CombinedLandmarksResult(
                handLandmarksResult = lastHandResult!!,
                poseLandmarksResult = lastPoseResult!!,
                timestamp = lastTimestamp
            )
            onCombinedLandmarksDetected?.invoke(combinedResult)
        }
    }

    fun detectHands(imageProxy: ImageProxy) {
        lastTimestamp = System.currentTimeMillis()

        // Convertir ImageProxy a Bitmap
        val bitmap = imageProxy.toBitmap()

        // Rotar si es necesario
        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

        // Crear MPImage
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        // Detectar manos y pose en paralelo
        handLandmarker?.detectAsync(mpImage, lastTimestamp)
        poseLandmarker?.detectAsync(mpImage, lastTimestamp)

        imageProxy.close()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            this.width,
            this.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, yuvImage.width, yuvImage.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    fun release() {
        handLandmarker?.close()
        handLandmarker = null
        poseLandmarker?.close()
        poseLandmarker = null
    }
}

// Clase de datos para combinar resultados de manos y pose
data class CombinedLandmarksResult(
    val handLandmarksResult: HandLandmarkerResult,
    val poseLandmarksResult: PoseLandmarkerResult,
    val timestamp: Long
)