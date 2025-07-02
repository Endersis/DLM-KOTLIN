package com.example.dlm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
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
import com.example.dlm.managers.VideoRecordingManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnStartCamera: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var handStatus: TextView
    private lateinit var handIndicator: View
    private lateinit var recordingIndicator: View

    private lateinit var handDetectionManager: HandDetectionManager
    private lateinit var videoRecordingManager: VideoRecordingManager
    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalysis: ImageAnalysis? = null
    private var isCameraStarted = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null

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

        // Verificar permisos
        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
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
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        btnStartCamera = findViewById(R.id.btnStartCamera)
        btnBack = findViewById(R.id.btnBack)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        handStatus = findViewById(R.id.handStatus)
        handIndicator = findViewById(R.id.handIndicator)
        recordingIndicator = findViewById(R.id.recordingIndicator)
    }

    private fun initManagers() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        handDetectionManager = HandDetectionManager(this)
        videoRecordingManager = VideoRecordingManager(this, this, cameraExecutor)

        setupCallbacks()
        observeStates()
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
    }

    private fun setupCallbacks() {
        // Callback cuando cambia la detección de manos
        handDetectionManager.onHandsDetectionChanged = { handsDetected ->
            runOnUiThread {
                if (handsDetected) {
                    // Manos detectadas - iniciar grabación
                    updateHandUI(true)
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
                Toast.makeText(this, "Video guardado", Toast.LENGTH_LONG).show()
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

        // Image Analysis para detección de manos
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
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
        btnStartCamera.text = "Detener Detección"
        Toast.makeText(this, "Detección iniciada - Muestra tus manos", Toast.LENGTH_SHORT).show()
    }

    private fun stopHandDetection() {
        imageAnalysis?.clearAnalyzer()

        // Detener grabación si está activa
        if (videoRecordingManager.isRecording.value) {
            videoRecordingManager.stopRecording()
        }

        isCameraStarted = false
        btnStartCamera.text = "Iniciar Detección"
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
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}