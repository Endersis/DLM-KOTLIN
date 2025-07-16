package com.example.dlm.Utils

import android.content.Context
import android.net.Uri
import com.example.dlm.Network.ServerManager
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecordingManager(private val context: Context) {

    private val videoPostProcessor = VideoPostProcessor(context)
    private val serverManager = ServerManager()

    // Lista para almacenar los landmarks durante la grabación
    private val recordedLandmarks = mutableListOf<HandLandmarkerResult>()

    // Estados del manager
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState

    // ✅ CORREGIDO: Hacer la propiedad PRIVADA para evitar conflicto
    private var _processingMode = VideoPostProcessor.ProcessingMode.HAND_LANDMARKS

    enum class RecordingState {
        IDLE,
        RECORDING,
        STOPPED
    }

    enum class ProcessingState {
        IDLE,
        PROCESSING,
        UPLOADING,
        COMPLETED,
        ERROR
    }

    data class ProcessingResult(
        val success: Boolean,
        val message: String,
        val uploadResponse: ServerManager.UploadResponse? = null
    )

    // Callbacks para eventos
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onProcessingStateChanged: ((ProcessingState) -> Unit)? = null
    var onProcessingCompleted: ((ProcessingResult) -> Unit)? = null

    init {
        // Observar cambios de estado
        CoroutineScope(Dispatchers.Main).launch {
            _recordingState.collect { state ->
                onRecordingStateChanged?.invoke(state)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            _processingState.collect { state ->
                onProcessingStateChanged?.invoke(state)
            }
        }
    }

    /**
     * Inicia la grabación y la detección de manos
     */
    fun startRecording() {
        if (_recordingState.value != RecordingState.IDLE) return

        _recordingState.value = RecordingState.RECORDING
        recordedLandmarks.clear()
        _processingState.value = ProcessingState.IDLE
    }

    /**
     * Detiene la grabación y procesa los datos
     */
    fun stopRecording(videoUri: Uri?) {
        if (_recordingState.value != RecordingState.RECORDING) return

        _recordingState.value = RecordingState.STOPPED

        // Procesar los datos según el modo configurado
        videoUri?.let { uri ->
            processAndUpload(uri)
        } ?: run {
            _processingState.value = ProcessingState.ERROR
            onProcessingCompleted?.invoke(
                ProcessingResult(false, "No video URI provided")
            )
        }
    }

    /**
     * Agrega landmarks detectados a la lista (llamar desde HandDetectionManager de CameraActivity)
     */
    fun addHandLandmarks(result: HandLandmarkerResult) {
        if (_recordingState.value == RecordingState.RECORDING) {
            recordedLandmarks.add(result)
        }
    }

    /**
     * Procesa el video y lo envía al servidor
     */
    private fun processAndUpload(videoUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _processingState.value = ProcessingState.PROCESSING

                // ✅ CORREGIDO: Usar la propiedad privada
                val processingResult = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        if (recordedLandmarks.isEmpty()) {
                            throw Exception("No hand landmarks were recorded")
                        }
                        videoPostProcessor.processVideo(videoUri, _processingMode, recordedLandmarks)
                    }
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        videoPostProcessor.processVideo(videoUri, _processingMode)
                    }
                    else -> {
                        throw Exception("Unknown processing mode: $_processingMode")
                    }
                }

                if (!processingResult.success) {
                    throw Exception(processingResult.error ?: "Processing failed")
                }

                // Subir al servidor
                _processingState.value = ProcessingState.UPLOADING

                val uploadResponse = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        processingResult.filePath?.let { filePath ->
                            serverManager.uploadHandLandmarks(filePath)
                        } ?: throw Exception("No file path returned from processing")
                    }
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        processingResult.framesPaths?.let { framesPaths ->
                            serverManager.uploadVideoFrames(framesPaths)
                        } ?: throw Exception("No frames paths returned from processing")
                    }
                    else -> {
                        throw Exception("Unknown processing mode: $_processingMode")
                    }
                }

                if (uploadResponse.success) {
                    _processingState.value = ProcessingState.COMPLETED
                    withContext(Dispatchers.Main) {
                        onProcessingCompleted?.invoke(
                            ProcessingResult(true, "Processing and upload completed successfully", uploadResponse)
                        )
                    }
                } else {
                    throw Exception(uploadResponse.message)
                }

            } catch (e: Exception) {
                _processingState.value = ProcessingState.ERROR
                withContext(Dispatchers.Main) {
                    onProcessingCompleted?.invoke(
                        ProcessingResult(false, e.message ?: "Unknown error occurred")
                    )
                }
            }
        }
    }

    /**
     * Verifica la conectividad con el servidor
     */
    suspend fun checkServerConnection(): Boolean {
        return serverManager.checkServerConnection()
    }

    /**
     * ✅ CORREGIDO: Función pública que maneja la lógica correctamente
     */
    fun setProcessingMode(mode: VideoPostProcessor.ProcessingMode) {
        if (_recordingState.value == RecordingState.IDLE) {
            _processingMode = mode
        }
    }

    /**
     * Getter público para el modo de procesamiento
     */
    fun getProcessingMode(): VideoPostProcessor.ProcessingMode {
        return _processingMode
    }

    /**
     * Limpia recursos
     */
    fun release() {
        recordedLandmarks.clear()
        _recordingState.value = RecordingState.IDLE
        _processingState.value = ProcessingState.IDLE
    }
}