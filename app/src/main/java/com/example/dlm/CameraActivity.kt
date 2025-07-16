package com.example.dlm

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.dlm.Utils.HandDetectionManager
import com.example.dlm.Utils.RecordingManager
import com.example.dlm.Utils.VideoPostProcessor
import com.example.dlm.managers.VideoRecordingManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnStartCamera: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnContinuePortrait: MaterialButton
    private lateinit var handStatus: TextView
    private lateinit var handIndicator: View
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var orientationOverlay: LinearLayout
    private lateinit var orientationHint: LinearLayout
    private lateinit var leftBlackBar: View
    private lateinit var rightBlackBar: View
    private lateinit var recordingManager: RecordingManager
    private lateinit var handDetectionManager: HandDetectionManager
    private lateinit var videoRecordingManager: VideoRecordingManager
    private lateinit var cameraExecutor: ExecutorService
    private var processingMode = VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
    private var imageAnalysis: ImageAnalysis? = null
    private var isCameraStarted = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var userAcceptedPortrait = false

    // Launcher para permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            setupCamera()
        } else {
            Toast.makeText(this, "Se necesitan permisos de cámara y audio", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        initViews()
        initManagers()
        setupClickListeners()
        checkOrientation()

        // Verificar permisos
        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        btnStartCamera = findViewById(R.id.btnStartCamera)
        btnBack = findViewById(R.id.btnBack)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnContinuePortrait = findViewById(R.id.btnContinuePortrait)
        handStatus = findViewById(R.id.handStatus)
        handIndicator = findViewById(R.id.handIndicator)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        orientationOverlay = findViewById(R.id.orientationOverlay)
        orientationHint = findViewById(R.id.orientationHint)
        leftBlackBar = findViewById(R.id.leftBlackBar)
        rightBlackBar = findViewById(R.id.rightBlackBar)
    }

    private fun initManagers() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        handDetectionManager = HandDetectionManager(this)
        videoRecordingManager = VideoRecordingManager(this, this, cameraExecutor)
        recordingManager = RecordingManager(this)

        setupCallbacks()
        observeStates()
        setupRecordingManagerCallbacks()
    }

    private fun setupRecordingManagerCallbacks() {
        // Conectar HandDetectionManager con RecordingManager
        handDetectionManager.onHandLandmarksDetected = { result ->
            recordingManager.addHandLandmarks(result)
        }

        // Callbacks del RecordingManager
        recordingManager.onRecordingStateChanged = { state ->
            // Manejar cambios de estado de grabación
        }

        recordingManager.onProcessingCompleted = { result ->
            runOnUiThread {
                if (result.success) {
                    Toast.makeText(this, "Video procesado y enviado al servidor", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnStartCamera.setOnClickListener {
            if (!isCameraStarted) {
                startHandDetection()
            } else {
                stopHandDetection()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        btnContinuePortrait.setOnClickListener {
            userAcceptedPortrait = true
            hideOrientationOverlay()
            showOrientationHint()
        }
    }

    private fun checkOrientation() {
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT && !userAcceptedPortrait) {
            showOrientationOverlay()
        } else {
            hideOrientationOverlay()
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                showOrientationHint()
            } else {
                hideOrientationHint()
            }
        }

        adjustBlackBars(orientation)
    }

    private fun adjustBlackBars(orientation: Int) {
        val barWidth = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // En horizontal, barras más pequeñas
            resources.getDimensionPixelSize(R.dimen.black_bar_width_landscape) // 120dp
        } else {
            // En vertical, barras medianas
            resources.getDimensionPixelSize(R.dimen.black_bar_width_portrait) // 150dp
        }

        val leftParams = leftBlackBar.layoutParams
        leftParams.width = barWidth
        leftBlackBar.layoutParams = leftParams

        val rightParams = rightBlackBar.layoutParams
        rightParams.width = barWidth
        rightBlackBar.layoutParams = rightParams

        // Ajustar márgenes del PreviewView
        val previewParams = previewView.layoutParams as android.widget.FrameLayout.LayoutParams
        previewParams.marginStart = barWidth
        previewParams.marginEnd = barWidth
        previewView.layoutParams = previewParams
    }

    private fun showOrientationOverlay() {
        orientationOverlay.visibility = View.VISIBLE
        btnStartCamera.isEnabled = false
        btnSwitchCamera.isEnabled = false
    }

    private fun hideOrientationOverlay() {
        orientationOverlay.visibility = View.GONE
        btnStartCamera.isEnabled = true
        btnSwitchCamera.isEnabled = true
    }

    private fun showOrientationHint() {
        orientationHint.visibility = View.VISIBLE
    }

    private fun hideOrientationHint() {
        orientationHint.visibility = View.GONE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation()

        // Si el usuario giró a horizontal, ocultar todas las notificaciones
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            userAcceptedPortrait = false
            hideOrientationOverlay()
            hideOrientationHint()
        }
    }

    private fun switchCamera() {
        // Cambiar entre cámara frontal y trasera
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        // Si la detección está activa, la detenemos temporalmente
        val wasDetecting = isCameraStarted
        if (wasDetecting) {
            imageAnalysis?.clearAnalyzer()
        }

        // Reconfigurar la cámara con la nueva orientación
        bindCameraUseCases()

        // Reconfigurar también el VideoRecordingManager
        videoRecordingManager.setupCamera(previewView)

        // Si estaba detectando, volver a activar
        if (wasDetecting) {
            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                handDetectionManager.detectHands(imageProxy)
            }
        }

        // Mostrar mensaje
        val cameraType = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Frontal" else "Trasera"
        Toast.makeText(this, "Cámara $cameraType", Toast.LENGTH_SHORT).show()
    }

    private fun setupCallbacks() {
        // Callback cuando cambia la detección de manos
        handDetectionManager.onHandsDetectionChanged = { handsDetected ->
            runOnUiThread {
                if (handsDetected) {
                    // Manos detectadas - iniciar grabación
                    updateHandUI(true)
                    recordingManager.startRecording()
                    if (!videoRecordingManager.isRecording.value) {
                        videoRecordingManager.startRecording()
                    }
                } else {
                    // No hay manos - detener grabación
                    updateHandUI(false)
                    if (videoRecordingManager.isRecording.value) {
                        videoRecordingManager.stopRecording()
                    }
                }
            }
        }

        // Callbacks de grabación
        videoRecordingManager.onRecordingStarted = {
            runOnUiThread {
                updateRecordingUI(true)
                Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()
            }
        }

        videoRecordingManager.onRecordingStopped = { videoPath ->
            runOnUiThread {
                updateRecordingUI(false)

                // Procesar con RecordingManager
                val videoUri = Uri.fromFile(File(videoPath))
                recordingManager.stopRecording(videoUri)

                Toast.makeText(this, "Video guardado y procesando...", Toast.LENGTH_LONG).show()
            }
        }

        videoRecordingManager.onRecordingError = { error ->
            runOnUiThread {
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeStates() {
        lifecycleScope.launch {
            handDetectionManager.handsDetected.collect { handsDetected ->
                updateHandUI(handsDetected)
            }
        }

        lifecycleScope.launch {
            videoRecordingManager.isRecording.collect { isRecording ->
                updateRecordingUI(isRecording)
            }
        }
    }

    private fun setupCamera() {
        videoRecordingManager.setupCamera(previewView)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // Preview
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // Image Analysis para detección de manos con resolución optimizada
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(
                // Resolución más pequeña para mayor eficiencia
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    android.util.Size(800, 480) // 16:9 reducido para landscape
                } else {
                    android.util.Size(480, 640) // 4:3 reducido para portrait
                }
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // Selector de cámara basado en lensFacing
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            // Desvincular casos de uso anteriores
            cameraProvider.unbindAll()

            // Vincular casos de uso a la cámara
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHandDetection() {
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            handDetectionManager.detectHands(imageProxy)
        }

        isCameraStarted = true
        btnStartCamera.text = "Detener" // ✅ Texto más corto
        Toast.makeText(this, "Detección iniciada - Muestra tus manos", Toast.LENGTH_SHORT).show()
    }

    private fun stopHandDetection() {
        imageAnalysis?.clearAnalyzer()

        // Detener grabación si está activa
        if (videoRecordingManager.isRecording.value) {
            videoRecordingManager.stopRecording()
        }

        isCameraStarted = false
        btnStartCamera.text = "Iniciar" // ✅ Texto más corto
        updateHandUI(false)
    }

    private fun updateHandUI(handsDetected: Boolean) {
        handStatus.text = if (handsDetected) "Manos detectadas" else "Sin manos"
        handIndicator.setBackgroundResource(
            if (handsDetected) R.drawable.indicator_green else R.drawable.indicator_circle
        )
    }

    private fun updateRecordingUI(isRecording: Boolean) {
        recordingIndicator.visibility = if (isRecording) View.VISIBLE else View.INVISIBLE
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handDetectionManager.release()
        videoRecordingManager.release()
        recordingManager.release()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}