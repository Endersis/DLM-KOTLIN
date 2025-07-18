package com.example.dlm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnStartCamera: MaterialButton
    private lateinit var btnBack: android.widget.Button
    private lateinit var btnSwitchCamera: android.widget.ImageButton
    private lateinit var btnContinuePortrait: MaterialButton
    private lateinit var btnViewFiles: MaterialButton
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

    // Variables para control de estado
    private var lastHandsDetected = false
    private var isProcessingRecordingChange = false

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

        Log.d("SIMPLE_DEBUG", "🚀 CameraActivity onCreate")

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
        btnViewFiles = findViewById(R.id.btnViewFiles)
        handStatus = findViewById(R.id.handStatus)
        handIndicator = findViewById(R.id.handIndicator)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        orientationOverlay = findViewById(R.id.orientationOverlay)
        orientationHint = findViewById(R.id.orientationHint)
        leftBlackBar = findViewById(R.id.leftBlackBar)
        rightBlackBar = findViewById(R.id.rightBlackBar)
    }

    private fun initManagers() {
        Log.d("SIMPLE_DEBUG", "⚙️ Initializing managers...")

        cameraExecutor = Executors.newSingleThreadExecutor()
        handDetectionManager = HandDetectionManager(this)
        videoRecordingManager = VideoRecordingManager(this, this, cameraExecutor)
        recordingManager = RecordingManager(this)

        setupCallbacks()
        observeStates()
        setupRecordingManagerCallbacks()

        Log.d("SIMPLE_DEBUG", "✅ Managers initialized")
    }

    private fun setupRecordingManagerCallbacks() {
        Log.d("SIMPLE_DEBUG", "🔗 Setting up recording manager callbacks...")

        // Conectar HandDetectionManager con RecordingManager
        handDetectionManager.onHandLandmarksDetected = { result ->
            recordingManager.addHandLandmarks(result)
        }

        // Callbacks del RecordingManager
        recordingManager.onRecordingStateChanged = { state ->
            Log.d("SIMPLE_DEBUG", "📊 RecordingManager state: $state")
        }

        recordingManager.onProcessingCompleted = { result ->
            runOnUiThread {
                if (result.success) {
                    Toast.makeText(this, "✅ ${result.message}", Toast.LENGTH_LONG).show()
                    Log.d("SIMPLE_DEBUG", "✅ Processing completed successfully")

                    // Mostrar sugerencia para ver archivos
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Toast.makeText(
                            this,
                            "💡 Toca 'Ver archivos' para revisar los archivos generados",
                            Toast.LENGTH_LONG
                        ).show()
                    }, 2000)

                } else {
                    Toast.makeText(this, "❌ Error: ${result.message}", Toast.LENGTH_LONG).show()
                    Log.e("SIMPLE_DEBUG", "❌ Processing failed: ${result.message}")
                }
            }
        }

        Log.d("SIMPLE_DEBUG", "✅ Recording manager callbacks configured")
    }

    private fun setupCallbacks() {
        Log.d("SIMPLE_DEBUG", "🔗 Setting up detection callbacks...")

        // Callback cuando cambia la detección de manos
        handDetectionManager.onHandsDetectionChanged = { handsDetected ->
            runOnUiThread {
                // Solo procesar si hay un cambio real en el estado
                if (handsDetected != lastHandsDetected && !isProcessingRecordingChange) {
                    isProcessingRecordingChange = true
                    lastHandsDetected = handsDetected

                    Log.d("SIMPLE_DEBUG", "🖐️ Hand detection changed to: $handsDetected")

                    if (handsDetected) {
                        handleHandsDetected()
                    } else {
                        handleHandsLost()
                    }

                    // Reset el flag después de un delay más largo
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isProcessingRecordingChange = false
                    }, 1000) // 1 segundo para evitar cambios rápidos
                }
            }
        }

        // Video recording callbacks
        videoRecordingManager.onRecordingStarted = {
            runOnUiThread {
                Log.d("VIDEO_RECORD_DEBUG", "🎉 VIDEO RECORDING STARTED CALLBACK FIRED!")
                updateRecordingUI(true)
                Toast.makeText(this, "🔴 Grabación iniciada", Toast.LENGTH_SHORT).show()
            }
        }

        videoRecordingManager.onRecordingStopped = { videoPath ->
            runOnUiThread {
                Log.d("VIDEO_RECORD_DEBUG", "🎬 VIDEO RECORDING STOPPED: $videoPath")
                updateRecordingUI(false)

                val videoUri = Uri.fromFile(File(videoPath))
                recordingManager.stopRecording(videoUri)

                Toast.makeText(this, "📹 Procesando video...", Toast.LENGTH_LONG).show()
            }
        }

        videoRecordingManager.onRecordingError = { error ->
            runOnUiThread {
                Log.e("VIDEO_RECORD_DEBUG", "❌ VIDEO RECORDING ERROR: $error")
                updateRecordingUI(false)
                updateHandUI(false)
                Toast.makeText(this, "❌ Error de grabación: $error", Toast.LENGTH_SHORT).show()

                // Reset states on error
                isProcessingRecordingChange = false
                lastHandsDetected = false
            }
        }

        Log.d("SIMPLE_DEBUG", "✅ Detection callbacks configured")
    }

    private fun handleHandsDetected() {
        updateHandUI(true)

        // Verificar que podemos iniciar la grabación
        if (canStartRecording()) {
            Log.d("SIMPLE_DEBUG", "🎬 Starting recording process...")

            lifecycleScope.launch {
                try {
                    // Iniciar ambos managers de forma secuencial
                    recordingManager.startRecording()

                    // Pequeño delay para asegurar que el estado se actualice
                    delay(100)

                    videoRecordingManager.startRecording()

                } catch (e: Exception) {
                    Log.e("SIMPLE_DEBUG", "❌ Error starting recording", e)
                    Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()

                    // Resetear estados en caso de error
                    updateHandUI(false)
                    updateRecordingUI(false)
                    isProcessingRecordingChange = false
                    lastHandsDetected = false
                }
            }
        } else {
            Log.d("SIMPLE_DEBUG", "⚠️ Cannot start recording - conditions not met")
        }
    }

    private fun handleHandsLost() {
        updateHandUI(false)

        if (videoRecordingManager.isRecording.value) {
            Log.d("SIMPLE_DEBUG", "🛑 Stopping recording process...")

            lifecycleScope.launch {
                try {
                    videoRecordingManager.stopRecording()
                } catch (e: Exception) {
                    Log.e("SIMPLE_DEBUG", "❌ Error stopping recording", e)
                    Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun canStartRecording(): Boolean {
        val canStart = allPermissionsGranted() &&
                isCameraStarted &&
                cameraProvider != null &&
                !videoRecordingManager.isRecording.value &&
                !recordingManager.isRecording()

        Log.d("SIMPLE_DEBUG", "Can start recording check: $canStart")
        Log.d("SIMPLE_DEBUG", "- Permissions: ${allPermissionsGranted()}")
        Log.d("SIMPLE_DEBUG", "- Camera started: $isCameraStarted")
        Log.d("SIMPLE_DEBUG", "- Camera provider: ${cameraProvider != null}")
        Log.d("SIMPLE_DEBUG", "- Video not recording: ${!videoRecordingManager.isRecording.value}")
        Log.d("SIMPLE_DEBUG", "- Recording manager idle: ${!recordingManager.isRecording()}")

        return canStart
    }

    private fun observeStates() {
        lifecycleScope.launch {
            handDetectionManager.handsDetected.collect { handsDetected ->
                // UI updates only, main logic is in onHandsDetectionChanged callback
                updateHandUI(handsDetected)
            }
        }

        lifecycleScope.launch {
            videoRecordingManager.isRecording.collect { isRecording ->
                Log.d("SIMPLE_DEBUG", "📊 Video recording state observed: $isRecording")
                updateRecordingUI(isRecording)
            }
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()

            // Obtener VideoCapture del VideoRecordingManager
            val videoCapture = videoRecordingManager.getVideoCapture()

            // Vincular todos los use cases incluyendo VideoCapture
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis,
                videoCapture
            )

            // Pasar la cámara al VideoRecordingManager (si es necesario)
            // videoRecordingManager.setCamera(camera)

            Log.d("SIMPLE_DEBUG", "✅ Camera use cases bound successfully with VideoCapture")

        } catch (e: Exception) {
            Log.e("SIMPLE_DEBUG", "❌ Error binding camera use cases", e)
            Toast.makeText(this, "Error al iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHandDetection() {
        Log.d("SIMPLE_DEBUG", "🚀 Starting hand detection...")

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            handDetectionManager.detectHands(imageProxy)
        }

        isCameraStarted = true
        btnStartCamera.text = "Detener"
        Toast.makeText(this, "🖐️ Detección iniciada - Muestra tus manos", Toast.LENGTH_SHORT).show()
    }

    private fun stopHandDetection() {
        Log.d("SIMPLE_DEBUG", "🛑 Stopping hand detection...")

        imageAnalysis?.clearAnalyzer()

        if (videoRecordingManager.isRecording.value) {
            videoRecordingManager.stopRecording()
        }

        isCameraStarted = false
        btnStartCamera.text = "Iniciar"
        updateHandUI(false)
        updateRecordingUI(false)

        // Reset control variables
        lastHandsDetected = false
        isProcessingRecordingChange = false
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

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val wasDetecting = isCameraStarted
        if (wasDetecting) {
            imageAnalysis?.clearAnalyzer()
        }

        bindCameraUseCases()

        if (wasDetecting) {
            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                handDetectionManager.detectHands(imageProxy)
            }
        }

        val cameraType = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Frontal" else "Trasera"
        Toast.makeText(this, "Cámara $cameraType", Toast.LENGTH_SHORT).show()
    }

    private fun showFilesInLogs() {
        Log.d("SIMPLE_DEBUG", "📁 Checking files...")

        try {
            val appDir = getExternalFilesDir(null)
            if (appDir == null || !appDir.exists()) {
                Toast.makeText(this, "📁 No se puede acceder al directorio", Toast.LENGTH_SHORT).show()
                return
            }

            val files = appDir.listFiles()
            if (files == null) {
                Toast.makeText(this, "📁 No se puede leer el directorio", Toast.LENGTH_SHORT).show()
                return
            }

            // Listar TODOS los archivos
            Log.d("FILES_DEBUG", "🔍 ===== ALL FILES =====")
            files.forEach { file ->
                Log.d("FILES_DEBUG", "📄 ${file.name} (${file.length()} bytes)")
            }
            Log.d("FILES_DEBUG", "=======================")

            val generatedFiles = files.filter { file ->
                (file.name.startsWith("hand_landmarks_") && file.name.endsWith(".txt")) ||
                        (file.name.startsWith("frame_") && file.name.endsWith(".jpg")) ||
                        file.name.endsWith(".mp4")
            }

            if (generatedFiles.isEmpty()) {
                Toast.makeText(this, "📁 No hay archivos generados aún", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("FILES_DEBUG", "🎉 ===== GENERATED FILES =====")
            generatedFiles.forEachIndexed { index, file ->
                val type = when {
                    file.name.endsWith(".txt") -> "📝 TXT"
                    file.name.endsWith(".jpg") -> "🖼️ JPG"
                    file.name.endsWith(".mp4") -> "🎬 MP4"
                    else -> "📄 FILE"
                }
                Log.d("FILES_DEBUG", "[$index] $type ${file.name} (${file.length() / 1024}KB)")
                Log.d("FILES_DEBUG", "    📍 ${file.absolutePath}")
            }
            Log.d("FILES_DEBUG", "=============================")

            val txtFiles = generatedFiles.count { it.name.endsWith(".txt") }
            val jpgFiles = generatedFiles.count { it.name.endsWith(".jpg") }
            val mp4Files = generatedFiles.count { it.name.endsWith(".mp4") }

            Toast.makeText(
                this,
                "📁 Archivos: ${txtFiles} TXT, ${jpgFiles} JPG, ${mp4Files} MP4",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("SIMPLE_DEBUG", "❌ Error checking files", e)
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

        btnViewFiles.setOnClickListener {
            Log.d("SIMPLE_DEBUG", "📁 View files clicked")
            // Abrir la actividad de archivos
            val intent = Intent(this, FilesActivity::class.java)
            startActivity(intent)
        }

        // Mode indicator
        findViewById<LinearLayout>(R.id.modeIndicator)?.setOnClickListener {
            showModeSelectionDialog()
        }

        findViewById<LinearLayout>(R.id.modeIndicator)?.setOnLongClickListener {
            // Abrir la actividad de archivos con un long press
            val intent = Intent(this, FilesActivity::class.java)
            startActivity(intent)
            true
        }
    }

    private fun showModeSelectionDialog() {
        if (isCameraStarted) {
            Toast.makeText(this, "⚠️ Detén la detección primero", Toast.LENGTH_SHORT).show()
            return
        }

        val currentMode = recordingManager.getProcessingMode()
        val newMode = if (currentMode == VideoPostProcessor.ProcessingMode.HAND_LANDMARKS) {
            VideoPostProcessor.ProcessingMode.VIDEO_FRAMES
        } else {
            VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
        }

        recordingManager.setProcessingMode(newMode)
        processingMode = newMode

        val modeText = findViewById<TextView>(R.id.modeText)
        val newText = if (newMode == VideoPostProcessor.ProcessingMode.HAND_LANDMARKS) {
            "📝 Landmarks"
        } else {
            "🎞️ Frames"
        }
        modeText?.text = newText

        Toast.makeText(this, "⚙️ Modo: $newText", Toast.LENGTH_SHORT).show()
    }

    // Métodos de orientación sin cambios
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
            (170 * resources.displayMetrics.density).toInt()
        } else {
            (250 * resources.displayMetrics.density).toInt()
        }

        val leftParams = leftBlackBar.layoutParams
        leftParams.width = barWidth
        leftBlackBar.layoutParams = leftParams

        val rightParams = rightBlackBar.layoutParams
        rightParams.width = barWidth
        rightBlackBar.layoutParams = rightParams

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
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            userAcceptedPortrait = false
            hideOrientationOverlay()
            hideOrientationHint()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SIMPLE_DEBUG", "🧹 Destroying CameraActivity")

        try {
            if (videoRecordingManager.isRecording.value) {
                videoRecordingManager.stopRecording()
            }
        } catch (e: Exception) {
            Log.e("SIMPLE_DEBUG", "❌ Error stopping recording on destroy", e)
        }

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