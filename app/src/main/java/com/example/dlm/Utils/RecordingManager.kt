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
    private val fileExportManager = FileExportManager(context)

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
        val uploadResponse: ServerManager.UploadResponse? = null,
        val localFiles: List<String>? = null,
        val exportResult: FileExportManager.ExportResult? = null
    )

    // Callbacks para eventos
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onProcessingStateChanged: ((ProcessingState) -> Unit)? = null
    var onProcessingCompleted: ((ProcessingResult) -> Unit)? = null

    init {
        // Observar cambios de estado
        CoroutineScope(Dispatchers.Main).launch {
            _recordingState.collect { state ->
                Log.d(TAG, "Recording state changed to: $state")
                onRecordingStateChanged?.invoke(state)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            _processingState.collect { state ->
                Log.d(TAG, "Processing state changed to: $state")
                onProcessingStateChanged?.invoke(state)
            }
        }
    }

    fun startRecording() {
        Log.d(TAG, "📹 startRecording() called. Current state: ${_recordingState.value}")

        if (_recordingState.value != RecordingState.IDLE) {
            Log.w(TAG, "⚠️ Cannot start recording. Current state: ${_recordingState.value}")
            return
        }

        _recordingState.value = RecordingState.RECORDING
        recordedLandmarks.clear()
        _processingState.value = ProcessingState.IDLE

        Log.d(TAG, "✅ Recording started successfully")
        Log.d(TAG, "📊 Recording landmarks in mode: $_processingMode")
    }

    fun stopRecording(videoUri: Uri?) {
        Log.d(TAG, "🛑 stopRecording() called. Current state: ${_recordingState.value}")

        // Solo procesar si estaba grabando
        if (_recordingState.value != RecordingState.RECORDING) {
            Log.w(TAG, "⚠️ Cannot stop recording. Current state: ${_recordingState.value}")
            return
        }

        _recordingState.value = RecordingState.STOPPED

        Log.d(TAG, "📊 Recorded ${recordedLandmarks.size} landmark frames")
        Log.d(TAG, "📊 Processing mode: $_processingMode")

        // Procesar los datos según el modo configurado
        videoUri?.let { uri ->
            Log.d(TAG, "🎬 Video URI received: $uri")
            processAndUpload(uri)
        } ?: run {
            Log.e(TAG, "❌ No video URI provided")
            _processingState.value = ProcessingState.ERROR
            _recordingState.value = RecordingState.IDLE
            onProcessingCompleted?.invoke(
                ProcessingResult(false, "No se proporcionó el video para procesar")
            )
        }
    }

    fun addHandLandmarks(result: HandLandmarkerResult) {
        if (_recordingState.value == RecordingState.RECORDING) {
            recordedLandmarks.add(result)
            // Log cada 30 frames para no saturar el log
            if (recordedLandmarks.size % 30 == 0) {
                Log.d(TAG, "🖐️ Recorded ${recordedLandmarks.size} landmark frames")
            }
        }
    }

    private fun processAndUpload(videoUri: Uri) {
        Log.d(TAG, "⚙️ Starting processing and upload for URI: $videoUri")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                _processingState.value = ProcessingState.PROCESSING

                // Procesar según el modo seleccionado
                val processingResult = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        if (recordedLandmarks.isEmpty()) {
                            Log.w(TAG, "⚠️ No hand landmarks recorded, but continuing with processing")
                            // Continuar con el procesamiento aunque no haya landmarks
                            // El VideoPostProcessor puede extraer frames del video
                        }
                        Log.d(TAG, "🔍 Processing ${recordedLandmarks.size} landmarks from video")
                        videoPostProcessor.processVideo(videoUri, _processingMode, recordedLandmarks)
                    }
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        Log.d(TAG, "🎞️ Processing video to extract frames")
                        videoPostProcessor.processVideo(videoUri, _processingMode)
                    }
                    else -> {
                        throw Exception("Unknown processing mode: $_processingMode")
                    }
                }

                if (!processingResult.success) {
                    throw Exception(processingResult.error ?: "Processing failed")
                }

                Log.d(TAG, "✅ Processing completed successfully")
                Log.d(TAG, "- File path: ${processingResult.filePath}")
                Log.d(TAG, "- Frames paths: ${processingResult.framesPaths?.size ?: 0} frames")

                // Exportar archivos automáticamente para debugging local
                val filesToExport = mutableListOf<String>()
                processingResult.filePath?.let { filesToExport.add(it) }
                processingResult.framesPaths?.let { filesToExport.addAll(it) }

                Log.d(TAG, "📁 Exporting ${filesToExport.size} files for local debugging")
                val exportResult = fileExportManager.exportFilesToDownloads(filesToExport, _processingMode)

                if (exportResult.success) {
                    Log.d(TAG, "📥 Files exported to: ${exportResult.exportPath}")
                } else {
                    Log.w(TAG, "⚠️ Export failed: ${exportResult.message}")
                }

                // Intentar subir al servidor (opcional para debugging)
                _processingState.value = ProcessingState.UPLOADING
                Log.d(TAG, "🌐 Attempting to upload to server...")

                val uploadResponse = try {
                    when (_processingMode) {
                        VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                            processingResult.filePath?.let { filePath ->
                                Log.d(TAG, "📤 Uploading landmarks file: $filePath")
                                serverManager.uploadHandLandmarks(filePath)
                            } ?: ServerManager.UploadResponse(false, "No landmark file generated")
                        }
                        VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                            processingResult.framesPaths?.let { framesPaths ->
                                Log.d(TAG, "📤 Uploading ${framesPaths.size} frames")
                                serverManager.uploadVideoFrames(framesPaths)
                            } ?: ServerManager.UploadResponse(false, "No frames generated")
                        }
                        else -> {
                            ServerManager.UploadResponse(false, "Unknown processing mode")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Server upload failed (OK for local testing): ${e.message}")
                    ServerManager.UploadResponse(false, "Server not available (local testing mode)")
                }

                _processingState.value = ProcessingState.COMPLETED
                _recordingState.value = RecordingState.IDLE

                val successMessage = when {
                    uploadResponse.success -> "✅ Video procesado y enviado al servidor"
                    exportResult.success -> "✅ Video procesado. Archivos guardados en: ${exportResult.exportPath}"
                    else -> "✅ Video procesado. Archivos guardados en la app"
                }

                withContext(Dispatchers.Main) {
                    onProcessingCompleted?.invoke(
                        ProcessingResult(
                            success = true,
                            message = successMessage,
                            uploadResponse = uploadResponse,
                            localFiles = filesToExport,
                            exportResult = exportResult
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during processing/upload", e)
                _processingState.value = ProcessingState.ERROR
                _recordingState.value = RecordingState.IDLE

                withContext(Dispatchers.Main) {
                    onProcessingCompleted?.invoke(
                        ProcessingResult(
                            false,
                            "Error: ${e.message ?: "Error desconocido durante el procesamiento"}"
                        )
                    )
                }
            }
        }
    }

    suspend fun checkServerConnection(): Boolean {
        return try {
            val isConnected = serverManager.checkServerConnection()
            Log.d(TAG, "🌐 Server connection: ${if (isConnected) "✅ Connected" else "❌ Disconnected"}")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking server connection", e)
            false
        }
    }

    fun setProcessingMode(mode: VideoPostProcessor.ProcessingMode) {
        if (_recordingState.value == RecordingState.IDLE) {
            _processingMode = mode
            Log.d(TAG, "⚙️ Processing mode changed to: $mode")
        } else {
            Log.w(TAG, "⚠️ Cannot change processing mode while recording")
        }
    }

    fun getProcessingMode(): VideoPostProcessor.ProcessingMode {
        return _processingMode
    }

    fun isRecording(): Boolean {
        return _recordingState.value == RecordingState.RECORDING
    }

    fun release() {
        Log.d(TAG, "🧹 Releasing RecordingManager")
        recordedLandmarks.clear()
        _recordingState.value = RecordingState.IDLE
        _processingState.value = ProcessingState.IDLE
    }

    companion object {
        private const val TAG = "RecordingManager"
    }
}