package com.example.dlm.Utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.dlm.Network.ServerManager
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordina la captura de landmarks, procesamiento de video y subida al servidor
 */
class RecordingManager(private val context: Context) {

    // Procesadores y gestores auxiliares
    private val videoPostProcessor = VideoPostProcessor(context)
    private val serverManager = ServerManager()
    private val fileExportManager = FileExportManager(context)

    // Listas para almacenar los datos capturados durante la grabacion
    private val recordedLandmarks = mutableListOf<HandLandmarkerResult>()
    private val combinedLandmarksResults = mutableListOf<CombinedLandmarksResult>() // Datos combinados de manos + pose

    // Estados observables de grabacion y procesamiento
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState

    // Modo de procesamiento actual (landmarks o frames)
    private var _processingMode = VideoPostProcessor.ProcessingMode.HAND_LANDMARKS

    /**
     * Estados posibles de la grabacion
     */
    enum class RecordingState {
        IDLE,      // Sin actividad
        RECORDING, // Grabando activamente
        STOPPED    // Grabacion detenida
    }

    /**
     * Estados posibles del procesamiento de video
     */
    enum class ProcessingState {
        IDLE,       // Sin procesar
        PROCESSING, // Procesando video
        UPLOADING,  // Subiendo al servidor
        COMPLETED,  // Completado exitosamente
        ERROR       // Error en el proceso
    }

    /**
     * Resultado del procesamiento con toda la informacion relevante
     */
    data class ProcessingResult(
        val success: Boolean,
        val message: String,
        val uploadResponse: ServerManager.UploadResponse? = null,
        val localFiles: List<String>? = null,
        val exportResult: FileExportManager.ExportResult? = null
    )

    // Callbacks para notificar cambios de estado
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onProcessingStateChanged: ((ProcessingState) -> Unit)? = null
    var onProcessingCompleted: ((ProcessingResult) -> Unit)? = null

    init {
        // Observar y notificar cambios en el estado de grabacion
        CoroutineScope(Dispatchers.Main).launch {
            _recordingState.collect { state ->
                onRecordingStateChanged?.invoke(state)
            }
        }

        // Observar y notificar cambios en el estado de procesamiento
        CoroutineScope(Dispatchers.Main).launch {
            _processingState.collect { state ->
                onProcessingStateChanged?.invoke(state)
            }
        }
    }

    /**
     * Inicia la grabacion de landmarks
     * Limpia los datos anteriores y prepara para nueva captura
     */
    fun startRecording() {
        // Verificar que no hay una grabacion en curso
        if (_recordingState.value != RecordingState.IDLE) {
            return
        }

        _recordingState.value = RecordingState.RECORDING

        // Limpiar datos anteriores
        recordedLandmarks.clear()
        combinedLandmarksResults.clear()
        _processingState.value = ProcessingState.IDLE
    }

    /**
     * Detiene la grabacion y procesa el video capturado
     * @param videoUri URI del video grabado
     */
    fun stopRecording(videoUri: Uri?) {
        // Solo procesar si estaba grabando
        if (_recordingState.value != RecordingState.RECORDING) {
            return
        }

        _recordingState.value = RecordingState.STOPPED

        // Procesar el video si se proporciono URI
        videoUri?.let { uri ->
            processAndUpload(uri)
        } ?: run {
            Log.e(TAG, "No video URI provided")
            _processingState.value = ProcessingState.ERROR
            _recordingState.value = RecordingState.IDLE
            onProcessingCompleted?.invoke(
                ProcessingResult(false, "No se proporcionó el video para procesar")
            )
        }
    }

    /**
     * Agrega landmarks de manos detectados al buffer de grabacion
     * @param result Resultado de deteccion de manos
     */
    fun addHandLandmarks(result: HandLandmarkerResult) {
        if (_recordingState.value == RecordingState.RECORDING) {
            recordedLandmarks.add(result)
        }
    }

    /**
     * Agrega resultados combinados de manos y pose al buffer
     * @param result Resultado combinado con manos y pose corporal
     */
    fun addCombinedLandmarks(result: CombinedLandmarksResult) {
        if (_recordingState.value == RecordingState.RECORDING) {
            combinedLandmarksResults.add(result)
            // Mantener compatibilidad agregando tambien a lista de solo manos
            recordedLandmarks.add(result.handLandmarksResult)
        }
    }

    /**
     * Procesa el video grabado y opcionalmente lo sube al servidor
     * @param videoUri URI del video a procesar
     */
    private fun processAndUpload(videoUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _processingState.value = ProcessingState.PROCESSING

                // Procesar segun el modo seleccionado
                val processingResult = when (_processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                        // Preferir datos combinados si estan disponibles
                        when {
                            // Si hay datos combinados (manos + pose), usarlos
                            combinedLandmarksResults.isNotEmpty() -> {
                                videoPostProcessor.processVideo(
                                    videoUri,
                                    _processingMode,
                                    handLandmarks = null,
                                    combinedLandmarks = combinedLandmarksResults
                                )
                            }
                            // Si solo hay datos de manos, usarlos
                            recordedLandmarks.isNotEmpty() -> {
                                videoPostProcessor.processVideo(
                                    videoUri,
                                    _processingMode,
                                    handLandmarks = recordedLandmarks,
                                    combinedLandmarks = null
                                )
                            }
                            // Si no hay landmarks, procesar solo el video
                            else -> {
                                videoPostProcessor.processVideo(videoUri, _processingMode)
                            }
                        }
                    }
                    // Modo de extraccion de frames
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                        videoPostProcessor.processVideo(videoUri, _processingMode)
                    }
                    else -> {
                        throw Exception("Modo de procesamiento desconocido: $_processingMode")
                    }
                }

                // Verificar si el procesamiento fue exitoso
                if (!processingResult.success) {
                    throw Exception(processingResult.error ?: "El procesamiento falló")
                }

                // Exportar archivos generados para debug local
                val filesToExport = mutableListOf<String>()
                processingResult.filePath?.let { filesToExport.add(it) }
                processingResult.framesPaths?.let { filesToExport.addAll(it) }

                val exportResult = fileExportManager.exportFilesToDownloads(filesToExport, _processingMode)

                // Intentar subir al servidor (opcional)
                _processingState.value = ProcessingState.UPLOADING

                val uploadResponse = try {
                    when (_processingMode) {
                        VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> {
                            processingResult.filePath?.let { filePath ->
                                serverManager.uploadHandLandmarks(filePath)
                            } ?: ServerManager.UploadResponse(false, "No se generó archivo de landmarks")
                        }
                        VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> {
                            processingResult.framesPaths?.let { framesPaths ->
                                serverManager.uploadVideoFrames(framesPaths)
                            } ?: ServerManager.UploadResponse(false, "No se generaron frames")
                        }
                        else -> {
                            ServerManager.UploadResponse(false, "Modo de procesamiento desconocido")
                        }
                    }
                } catch (e: Exception) {
                    // Si falla la subida, continuar localmente
                    ServerManager.UploadResponse(false, "Servidor no disponible (modo local)")
                }

                _processingState.value = ProcessingState.COMPLETED
                _recordingState.value = RecordingState.IDLE

                // Crear mensaje descriptivo del resultado
                val dataTypeMessage = when {
                    combinedLandmarksResults.isNotEmpty() -> " (con datos de manos + torso/brazos)"
                    recordedLandmarks.isNotEmpty() -> " (solo datos de manos)"
                    else -> ""
                }

                val successMessage = when {
                    uploadResponse.success -> "Video procesado$dataTypeMessage y enviado al servidor"
                    exportResult.success -> "Video procesado$dataTypeMessage. Archivos guardados en: ${exportResult.exportPath}"
                    else -> "Video procesado$dataTypeMessage. Archivos guardados en la app"
                }

                // Notificar resultado exitoso
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
                Log.e(TAG, "Error procesando/subiendo video", e)
                _processingState.value = ProcessingState.ERROR
                _recordingState.value = RecordingState.IDLE

                // Notificar error
                withContext(Dispatchers.Main) {
                    onProcessingCompleted?.invoke(
                        ProcessingResult(
                            false,
                            "Error: ${e.message ?: "Error desconocido durante el procesamiento"}"
                        )
                    )
                }
            } finally {
                // Limpiar buffers despues del procesamiento
                recordedLandmarks.clear()
                combinedLandmarksResults.clear()
            }
        }
    }

    /**
     * Verifica la conexion con el servidor
     * @return true si hay conexion, false en caso contrario
     */
    suspend fun checkServerConnection(): Boolean {
        return try {
            serverManager.checkServerConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conexión con servidor", e)
            false
        }
    }

    /**
     * Cambia el modo de procesamiento (landmarks o frames)
     * Solo se puede cambiar cuando no hay grabacion activa
     * @param mode Nuevo modo de procesamiento
     */
    fun setProcessingMode(mode: VideoPostProcessor.ProcessingMode) {
        if (_recordingState.value == RecordingState.IDLE) {
            _processingMode = mode
        }
    }

    /**
     * Obtiene el modo de procesamiento actual
     * @return Modo actual (HAND_LANDMARKS o VIDEO_FRAMES)
     */
    fun getProcessingMode(): VideoPostProcessor.ProcessingMode {
        return _processingMode
    }

    /**
     * Verifica si hay una grabacion en curso
     * @return true si esta grabando, false en caso contrario
     */
    fun isRecording(): Boolean {
        return _recordingState.value == RecordingState.RECORDING
    }

    /**
     * Libera recursos y limpia datos
     * Debe llamarse cuando se destruye la actividad
     */
    fun release() {
        recordedLandmarks.clear()
        combinedLandmarksResults.clear()
        _recordingState.value = RecordingState.IDLE
        _processingState.value = ProcessingState.IDLE
    }

    companion object {
        private const val TAG = "RecordingManager"
    }
}