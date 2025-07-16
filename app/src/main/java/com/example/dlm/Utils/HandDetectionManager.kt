package com.example.dlm.Utils

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

class HandDetectionManager(private val context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private val _handsDetected = MutableStateFlow(false)
    val handsDetected: StateFlow<Boolean> = _handsDetected
    var onHandLandmarksDetected: ((HandLandmarkerResult) -> Unit)? = null
    // Callback para cuando se detectan/dejan de detectar manos
    var onHandsDetectionChanged: ((Boolean) -> Unit)? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task") // Descarga el modelo de MediaPipe
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2) // Detectar hasta 2 manos
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, image ->
                handleDetectionResult(result)
            }
            .setErrorListener { error ->
                error.printStackTrace()
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun handleDetectionResult(result: HandLandmarkerResult) {
        val hasHands = result.landmarks().isNotEmpty()

        if (hasHands != _handsDetected.value) {
            _handsDetected.value = hasHands
            onHandsDetectionChanged?.invoke(hasHands)
        }


        if (hasHands) {
            onHandLandmarksDetected?.invoke(result)
        }
    }

    fun detectHands(imageProxy: ImageProxy) {
        val frameTime = System.currentTimeMillis()

        // Convertir ImageProxy a Bitmap
        val bitmap = imageProxy.toBitmap()

        // Rotar si es necesario
        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

        // Crear MPImage
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        // Detectar
        handLandmarker?.detectAsync(mpImage, frameTime)

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
    }
}