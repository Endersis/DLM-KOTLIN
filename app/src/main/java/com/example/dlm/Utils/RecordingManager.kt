package com.example.dlm.Utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.dlm.Network.ServerManager
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecordingManager(private val context: Context) {

    private val videoPostProcessor = VideoPostProcessor(context)
    private val serverManager = ServerManager()


    private val recordedLandmarks = mutableListOf<HandLandmarkerResult>()


    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState


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
                Log.d("RecordingManager", "Recording state changed to: $state")
                onRecordingStateChanged?.invoke(state)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            _processingState.collect { state ->
                Log.d("RecordingManager", "Processing state changed to: $state")
                onProcessingStateChanged?.invoke(state)
            }
        }
    }


    fun startRecording() {
        Log.d("RecordingManager", "startRecording() called. Current state: ${_recordingState.value}")

        // ✅ Solo iniciar si está en IDLE
        if (_recordingState.value != RecordingState.IDLE) {
            Log.w("RecordingManager", "Cannot start recording. Current state: ${_recordingState.value}")
            return
        }

        _recordingState.value = RecordingState.RECORDING
        recordedLandmarks.clear()
        _processingState.value = ProcessingState.IDLE

        Log.d("RecordingManager", "Recording started successfully")
    }


    fun stopRecording(videoUri: Uri?) {
        Log.d("RecordingManager", "stopRecording() called. Current state: ${_recordingState.value}")

        // ✅ Solo procesar si estaba grabando
        if (_recordingState.value != RecordingState.RECORDING) {
            Log.w("RecordingManager", "Cannot stop recording. Current state: ${_recordingState.value}")
            return
        }

        _recordingState.value = RecordingState.STOPPED

        Log.d("RecordingManager", "Recorded ${recordedLandmarks.size} landmark frames")

        // Procesar los datos según el modo configurado
        videoUri?.let { uri ->
            processAndUpload(uri)
        } ?: run {
            Log.e("RecordingManager", "No video URI provided")
            _processingState.value = ProcessingState.ERROR
            onProcessingCompleted?.invoke(
                ProcessingResult(false, "No video URI provided")
            )
        }
    }


    fun addHandLandmarks(result: HandLandmarkerResult) {
        if (_recordingState.value == RecordingState.RECORDING) {
            recordedLandmarks.add(result)
            // Log cada 30 frames para no spam el log
            if (recordedLandmarks.size % 30 == 0) {
                Log.d("RecordingManager", "Recorded ${recordedLandmarks.size} landmark frames")
            }
        }
    }


    private fun processAndUpload(videoUri: Uri) {
        Log.d("RecordingManager", "Starting processing and upload")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                _processingState.value = ProcessingState.PROCESSING

                // ✅ Procesar según el modo seleccionado
                val processingResult = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        if (recordedLandmarks.isEmpty()) {
                            throw Exception("No hand landmarks were recorded during the session")
                        }
                        Log.d("RecordingManager", "Processing ${recordedLandmarks.size} landmarks")
                        videoPostProcessor.processVideo(videoUri, _processingMode, recordedLandmarks)
                    }
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        Log.d("RecordingManager", "Processing video to extract 10 frames")
                        videoPostProcessor.processVideo(videoUri, _processingMode)
                    }
                    else -> {
                        throw Exception("Unknown processing mode: $_processingMode")
                    }
                }

                if (!processingResult.success) {
                    throw Exception(processingResult.error ?: "Processing failed")
                }

                Log.d("RecordingManager", "Processing completed successfully")

                // Subir al servidor
                _processingState.value = ProcessingState.UPLOADING

                val uploadResponse = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        processingResult.filePath?.let { filePath ->
                            Log.d("RecordingManager", "Uploading landmarks file: $filePath")
                            serverManager.uploadHandLandmarks(filePath)
                        } ?: throw Exception("No file path returned from processing")
                    }
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        processingResult.framesPaths?.let { framesPaths ->
                            Log.d("RecordingManager", "Uploading ${framesPaths.size} frames")
                            serverManager.uploadVideoFrames(framesPaths)
                        } ?: throw Exception("No frames paths returned from processing")
                    }
                    else -> {
                        throw Exception("Unknown processing mode: $_processingMode")
                    }
                }

                if (uploadResponse.success) {
                    _processingState.value = ProcessingState.COMPLETED
                    Log.d("RecordingManager", "Upload completed successfully")

                    // ✅ Reset to IDLE for next recording
                    _recordingState.value = RecordingState.IDLE

                    withContext(Dispatchers.Main) {
                        onProcessingCompleted?.invoke(
                            ProcessingResult(true, "Video procesado y enviado exitosamente", uploadResponse)
                        )
                    }
                } else {
                    throw Exception(uploadResponse.message)
                }

            } catch (e: Exception) {
                Log.e("RecordingManager", "Error during processing/upload", e)
                _processingState.value = ProcessingState.ERROR
                _recordingState.value = RecordingState.IDLE // Reset state

                withContext(Dispatchers.Main) {
                    onProcessingCompleted?.invoke(
                        ProcessingResult(false, e.message ?: "Error desconocido durante el procesamiento")
                    )
                }
            }
        }
    }


    suspend fun checkServerConnection(): Boolean {
        return try {
            serverManager.checkServerConnection()
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error checking server connection", e)
            false
        }
    }


    fun setProcessingMode(mode: VideoPostProcessor.ProcessingMode) {
        if (_recordingState.value == RecordingState.IDLE) {
            _processingMode = mode
            Log.d("RecordingManager", "Processing mode changed to: $mode")
        } else {
            Log.w("RecordingManager", "Cannot change processing mode while recording")
        }
    }


    fun getProcessingMode(): VideoPostProcessor.ProcessingMode {
        return _processingMode
    }


    fun isRecording(): Boolean {
        return _recordingState.value == RecordingState.RECORDING
    }

    fun release() {
        Log.d("RecordingManager", "Releasing RecordingManager")
        recordedLandmarks.clear()
        _recordingState.value = RecordingState.IDLE
        _processingState.value = ProcessingState.IDLE
    }
}