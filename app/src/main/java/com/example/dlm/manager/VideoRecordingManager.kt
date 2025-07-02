import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class VideoRecordingManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: Executor
) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    // Callbacks
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: ((String) -> Unit)? = null // Retorna la ruta del video
    var onRecordingError: ((String) -> Unit)? = null

    fun setupCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: androidx.camera.view.PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Configurar Preview
        val preview = androidx.camera.core.Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // Configurar VideoCapture
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        // Seleccionar cámara frontal o trasera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Desvincular casos de uso anteriores
            cameraProvider.unbindAll()

            // Vincular casos de uso
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
            onRecordingError?.invoke("Error al configurar la cámara: ${e.message}")
        }
    }

    fun startRecording() {
        if (_isRecording.value) return

        val videoCapture = videoCapture ?: return

        // Crear nombre de archivo con timestamp
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        // Configurar ContentValues para guardar el video
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HandDetectionVideos")
            }
        }

        // Configurar opciones de salida
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // Iniciar grabación
        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                // Grabar audio si tienes permisos
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(executor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        onRecordingStarted?.invoke()
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        if (!recordEvent.hasError()) {
                            val uri = recordEvent.outputResults.outputUri
                            onRecordingStopped?.invoke(uri.toString())
                        } else {
                            onRecordingError?.invoke(
                                "Error en la grabación: ${recordEvent.error}"
                            )
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun release() {
        recording?.close()
        cameraProvider?.unbindAll()
    }
}