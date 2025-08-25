package com.example.dlm

import android.Manifest
import com.example.dlm.Utils.CombinedLandmarksResult
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

/**
 * Actividad principal de la camara que maneja la deteccion de manos y grabacion de video
 * Se encarga de coordinar la captura de video con la deteccion de landmarks de manos y pose
 */
class CameraActivity : AppCompatActivity() {

    // Vistas de la interfaz de usuario
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

    // Managers que controlan diferentes aspectos de la aplicacion
    private lateinit var recordingManager: RecordingManager
    private lateinit var handDetectionManager: HandDetectionManager
    private lateinit var videoRecordingManager: VideoRecordingManager
    private lateinit var cameraExecutor: ExecutorService

    // Variables de configuracion y estado
    private var processingMode = VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
    private var imageAnalysis: ImageAnalysis? = null
    private var isCameraStarted = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var userAcceptedPortrait = false

    // Variables para control de estado de deteccion
    private var lastHandsDetected = false
    private var isProcessingRecordingChange = false

    // Launcher para solicitar permisos de camara y audio
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

        // Verificar y solicitar permisos si es necesario
        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    /**
     * Inicializa todas las vistas de la interfaz de usuario
     */
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

    /**
     * Inicializa los managers que controlan la deteccion de manos, grabacion de video y procesamiento
     */
    private fun initManagers() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        handDetectionManager = HandDetectionManager(this)
        videoRecordingManager = VideoRecordingManager(this, this, cameraExecutor)
        recordingManager = RecordingManager(this)

        setupCallbacks()
        observeStates()
        setupRecordingManagerCallbacks()
    }

    /**
     * Configura los callbacks del RecordingManager para procesar landmarks detectados
     */
    private fun setupRecordingManagerCallbacks() {
        // Callback para resultados combinados (manos + pose)
        handDetectionManager.onCombinedLandmarksDetected = { combinedResult ->
            recordingManager.addCombinedLandmarks(combinedResult)
        }

        // Callback de solo manos como respaldo
        handDetectionManager.onHandLandmarksDetected = { result ->
            recordingManager.addHandLandmarks(result)
        }

        // Callback cuando el procesamiento de video termina
        recordingManager.onProcessingCompleted = { result ->
            runOnUiThread {
                if (result.success) {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()

                    // Informar al usuario sobre los archivos generados
                    val dataInfo = if (result.message.contains("torso/brazos")) {
                        "con detección completa de cuerpo"
                    } else {
                        "solo con detección de manos"
                    }

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Toast.makeText(
                            this,
                            "Archivos generados $dataInfo. Toca 'Ver archivos' para revisar.",
                            Toast.LENGTH_LONG
                        ).show()
                    }, 2000)

                } else {
                    Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Configura los callbacks para la deteccion de manos y grabacion de video
     */
    private fun setupCallbacks() {
        // Callback cuando cambia la deteccion de manos
        handDetectionManager.onHandsDetectionChanged = { handsDetected ->
            runOnUiThread {
                // Solo procesar si hay un cambio real en el estado
                if (handsDetected != lastHandsDetected && !isProcessingRecordingChange) {
                    isProcessingRecordingChange = true
                    lastHandsDetected = handsDetected

                    if (handsDetected) {
                        handleHandsDetected()
                    } else {
                        handleHandsLost()
                    }

                    // Reset el flag despues de un delay para evitar cambios muy rapidos
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isProcessingRecordingChange = false
                    }, 200)
                }
            }
        }

        // Callbacks de grabacion de video
        videoRecordingManager.onRecordingStarted = {
            runOnUiThread {
                updateRecordingUI(true)
                Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()
            }
        }

        videoRecordingManager.onRecordingStopped = { videoPath ->
            runOnUiThread {
                updateRecordingUI(false)

                val videoUri = Uri.fromFile(File(videoPath))
                recordingManager.stopRecording(videoUri)

                Toast.makeText(this, "Procesando video...", Toast.LENGTH_LONG).show()
            }
        }

        videoRecordingManager.onRecordingError = { error ->
            runOnUiThread {
                updateRecordingUI(false)
                updateHandUI(false)
                Toast.makeText(this, "Error de grabación: $error", Toast.LENGTH_SHORT).show()

                // Resetear estados en caso de error
                isProcessingRecordingChange = false
                lastHandsDetected = false
            }
        }
    }

    /**
     * Maneja cuando se detectan manos en el frame
     * Inicia la grabacion de video y el registro de landmarks
     */
    private fun handleHandsDetected() {
        updateHandUI(true)

        // Verificar que se puede iniciar la grabacion
        if (canStartRecording()) {
            lifecycleScope.launch {
                try {
                    // Iniciar ambos managers de forma secuencial
                    recordingManager.startRecording()

                    // Pequeno delay para asegurar que el estado se actualice
                    delay(50)

                    videoRecordingManager.startRecording()

                } catch (e: Exception) {
                    Log.e("CameraActivity", "Error starting recording", e)
                    Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()

                    // Resetear estados en caso de error
                    updateHandUI(false)
                    updateRecordingUI(false)
                    isProcessingRecordingChange = false
                    lastHandsDetected = false
                }
            }
        }
    }

    /**
     * Maneja cuando se dejan de detectar manos
     * Detiene la grabacion de video
     */
    private fun handleHandsLost() {
        updateHandUI(false)

        if (videoRecordingManager.isRecording.value) {
            lifecycleScope.launch {
                try {
                    videoRecordingManager.stopRecording()
                } catch (e: Exception) {
                    Log.e("CameraActivity", "Error stopping recording", e)
                    Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Verifica si se cumplen todas las condiciones para iniciar la grabacion
     */
    private fun canStartRecording(): Boolean {
        return allPermissionsGranted() &&
                isCameraStarted &&
                cameraProvider != null &&
                !videoRecordingManager.isRecording.value &&
                !recordingManager.isRecording()
    }

    /**
     * Observa los cambios de estado de los managers
     */
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

    /**
     * Configura el proveedor de camara
     */
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Vincula los casos de uso de la camara (preview, analisis de imagen, captura de video)
     */
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

        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Inicia la deteccion de manos usando el analizador de imagenes
     */
    private fun startHandDetection() {
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            handDetectionManager.detectHands(imageProxy)
        }

        isCameraStarted = true
        btnStartCamera.text = "Detener"
        Toast.makeText(this, "Detección iniciada - Muestra tus manos", Toast.LENGTH_SHORT).show()
    }

    /**
     * Detiene la deteccion de manos y limpia los recursos
     */
    private fun stopHandDetection() {
        imageAnalysis?.clearAnalyzer()

        if (videoRecordingManager.isRecording.value) {
            videoRecordingManager.stopRecording()
        }

        isCameraStarted = false
        btnStartCamera.text = "Iniciar"
        updateHandUI(false)
        updateRecordingUI(false)

        // Reset variables de control
        lastHandsDetected = false
        isProcessingRecordingChange = false
    }

    /**
     * Actualiza la UI para mostrar el estado de deteccion de manos
     */
    private fun updateHandUI(handsDetected: Boolean) {
        handStatus.text = if (handsDetected) "Manos detectadas" else "Sin manos"
        handIndicator.setBackgroundResource(
            if (handsDetected) R.drawable.indicator_green else R.drawable.indicator_circle
        )
    }

    /**
     * Actualiza la UI para mostrar el estado de grabacion
     */
    private fun updateRecordingUI(isRecording: Boolean) {
        recordingIndicator.visibility = if (isRecording) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Cambia entre camara frontal y trasera
     */
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

    /**
     * Configura los listeners de los botones
     */
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
            // Abrir la actividad de archivos
            val intent = Intent(this, FilesActivity::class.java)
            startActivity(intent)
        }

        // Indicador de modo para cambiar entre landmarks y frames
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

    /**
     * Muestra dialogo para cambiar el modo de procesamiento (landmarks o frames)
     */
    private fun showModeSelectionDialog() {
        if (isCameraStarted) {
            Toast.makeText(this, "Detén la detección primero", Toast.LENGTH_SHORT).show()
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
            "Landmarks"
        } else {
            "Frames"
        }
        modeText?.text = newText

        Toast.makeText(this, "Modo: $newText", Toast.LENGTH_SHORT).show()
    }

    /**
     * Verifica la orientacion del dispositivo y muestra avisos si es necesario
     */
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

    /**
     * Ajusta el tamano de las barras negras laterales segun la orientacion
     */
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

    /**
     * Muestra overlay de advertencia de orientacion
     */
    private fun showOrientationOverlay() {
        orientationOverlay.visibility = View.VISIBLE
        btnStartCamera.isEnabled = false
        btnSwitchCamera.isEnabled = false
    }

    /**
     * Oculta overlay de advertencia de orientacion
     */
    private fun hideOrientationOverlay() {
        orientationOverlay.visibility = View.GONE
        btnStartCamera.isEnabled = true
        btnSwitchCamera.isEnabled = true
    }

    /**
     * Muestra hint de orientacion
     */
    private fun showOrientationHint() {
        orientationHint.visibility = View.VISIBLE
    }

    /**
     * Oculta hint de orientacion
     */
    private fun hideOrientationHint() {
        orientationHint.visibility = View.GONE
    }

    /**
     * Maneja cambios de configuracion del dispositivo
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            userAcceptedPortrait = false
            hideOrientationOverlay()
            hideOrientationHint()
        }
    }

    /**
     * Verifica si todos los permisos necesarios estan otorgados
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Limpia recursos cuando se destruye la actividad
     */
    override fun onDestroy() {
        super.onDestroy()

        try {
            if (videoRecordingManager.isRecording.value) {
                videoRecordingManager.stopRecording()
            }
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error stopping recording on destroy", e)
        }

        cameraExecutor.shutdown()
        handDetectionManager.release()
        videoRecordingManager.release()
        recordingManager.release()
    }

    companion object {
        // Permisos requeridos para el funcionamiento de la aplicacion
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}