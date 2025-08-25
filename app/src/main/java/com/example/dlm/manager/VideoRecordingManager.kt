package com.example.dlm.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

/**
 * Gestor de grabacion de video usando CameraX
 * Maneja la captura, inicio, pausa y detencion de grabaciones de video
 */
class VideoRecordingManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: ExecutorService
) {
    // Componentes de CameraX para grabacion
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recorder: Recorder? = null

    // Estado observable de grabacion
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    // Callbacks para eventos de grabacion
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: ((String) -> Unit)? = null  // Retorna la ruta del archivo
    var onRecordingError: ((String) -> Unit)? = null

    init {
        setupRecorder()
    }

    /**
     * Configura el grabador de video con calidad HD
     * Prepara el VideoCapture para ser usado con la camara
     */
    private fun setupRecorder() {
        try {
            // Crear grabador con calidad HD
            recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .setExecutor(executor)
                .build()

            // Crear VideoCapture asociado al grabador
            videoCapture = VideoCapture.withOutput(recorder!!)

        } catch (e: Exception) {
            Log.e(TAG, "Error configurando grabador", e)
            onRecordingError?.invoke("Error configurando grabador: ${e.message}")
        }
    }

    /**
     * Obtiene el VideoCapture para vincularlo con CameraX
     * @return Instancia de VideoCapture configurada
     * @throws IllegalStateException si VideoCapture no esta inicializado
     */
    fun getVideoCapture(): VideoCapture<Recorder> {
        return videoCapture ?: throw IllegalStateException("VideoCapture no inicializado")
    }

    /**
     * Inicia una nueva grabacion de video
     * Crea un archivo con timestamp y configura el audio si hay permisos
     */
    fun startRecording() {
        // Verificar que no hay grabacion activa
        if (_isRecording.value) {
            return
        }

        // Verificar que el grabador esta inicializado
        if (recorder == null) {
            Log.e(TAG, "Grabador no inicializado")
            onRecordingError?.invoke("Grabador no inicializado")
            return
        }

        try {
            // Crear archivo de salida con timestamp
            val fileName = "recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val videoFile = File(context.getExternalFilesDir(null), fileName)

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            // Verificar permiso de audio
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            // Preparar grabacion con o sin audio segun permisos
            val pendingRecording = recorder!!.prepareRecording(context, outputOptions)
                .apply {
                    if (hasAudioPermission) {
                        withAudioEnabled()
                    }
                }

            // Iniciar grabacion con callbacks para eventos
            recording = pendingRecording.start(executor) { recordEvent ->
                handleRecordingEvent(recordEvent, videoFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando grabacion", e)
            _isRecording.value = false
            onRecordingError?.invoke("Error al iniciar grabacion: ${e.message}")
        }
    }

    /**
     * Maneja los eventos de grabacion de video
     * @param recordEvent Evento recibido del grabador
     * @param videoFile Archivo donde se guarda el video
     */
    private fun handleRecordingEvent(recordEvent: VideoRecordEvent, videoFile: File) {
        when(recordEvent) {
            // Grabacion iniciada exitosamente
            is VideoRecordEvent.Start -> {
                _isRecording.value = true
                onRecordingStarted?.invoke()
            }

            // Grabacion pausada
            is VideoRecordEvent.Pause -> {
                // No se usa actualmente
            }

            // Grabacion reanudada
            is VideoRecordEvent.Resume -> {
                // No se usa actualmente
            }

            // Grabacion finalizada
            is VideoRecordEvent.Finalize -> {
                _isRecording.value = false

                if (!recordEvent.hasError()) {
                    // Grabacion exitosa, notificar con la ruta del archivo
                    onRecordingStopped?.invoke(videoFile.absolutePath)
                } else {
                    // Error durante la grabacion
                    val error = getErrorMessage(recordEvent.error)
                    Log.e(TAG, "Error en grabacion: $error")
                    onRecordingError?.invoke(error)
                }

                recording = null
            }

            // Estadisticas de grabacion (opcional para debug)
            is VideoRecordEvent.Status -> {
                // Se puede usar para mostrar duracion o tamano del archivo
            }
        }
    }

    /**
     * Convierte codigo de error en mensaje legible
     * @param errorCode Codigo de error de VideoRecordEvent
     * @return Mensaje descriptivo del error
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "Fallo la codificacion"
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "Limite de tamano alcanzado"
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Almacenamiento insuficiente"
            VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "Opciones de salida invalidas"
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "Sin datos validos"
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "Error del grabador"
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Fuente inactiva"
            else -> "Error desconocido: $errorCode"
        }
    }

    /**
     * Detiene la grabacion activa
     * El video se guardara automaticamente
     */
    fun stopRecording() {
        // Verificar que hay una grabacion activa
        if (!_isRecording.value || recording == null) {
            return
        }

        try {
            recording?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo grabacion", e)
            onRecordingError?.invoke("Error al detener grabacion: ${e.message}")
        }

        recording = null
    }

    /**
     * Pausa la grabacion activa
     * Se puede reanudar posteriormente con resumeRecording()
     */
    fun pauseRecording() {
        if (!_isRecording.value || recording == null) {
            return
        }

        try {
            recording?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausando grabacion", e)
        }
    }

    /**
     * Reanuda una grabacion pausada
     */
    fun resumeRecording() {
        if (!_isRecording.value || recording == null) {
            return
        }

        try {
            recording?.resume()
        } catch (e: Exception) {
            Log.e(TAG, "Error reanudando grabacion", e)
        }
    }

    /**
     * Libera todos los recursos del grabador
     * Debe llamarse cuando se destruye la actividad
     */
    fun release() {
        try {
            // Detener grabacion si esta activa
            if (_isRecording.value) {
                stopRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo grabacion durante liberacion", e)
        }

        // Limpiar referencias
        recording = null
        recorder = null
        videoCapture = null
        _isRecording.value = false
    }

    companion object {
        private const val TAG = "VideoRecordingManager"
    }
}