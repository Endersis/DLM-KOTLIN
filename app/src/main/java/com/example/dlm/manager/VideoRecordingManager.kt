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

class VideoRecordingManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: ExecutorService
) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recorder: Recorder? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    // Callbacks
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: ((String) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    init {
        Log.d(TAG, "🎬 VideoRecordingManager initialized")
        setupRecorder()
    }

    private fun setupRecorder() {
        try {
            recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .setExecutor(executor)
                .build()

            videoCapture = VideoCapture.withOutput(recorder!!)

            Log.d(TAG, "✅ Recorder setup completed")
            Log.d(TAG, "- Recorder: $recorder")
            Log.d(TAG, "- VideoCapture: $videoCapture")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up recorder", e)
            onRecordingError?.invoke("Error setting up recorder: ${e.message}")
        }
    }

    fun getVideoCapture(): VideoCapture<Recorder> {
        return videoCapture ?: throw IllegalStateException("VideoCapture not initialized")
    }

    fun startRecording() {
        Log.d(TAG, "🎬 startRecording() called")
        Log.d(TAG, "- Current state: ${_isRecording.value}")
        Log.d(TAG, "- Recorder: $recorder")
        Log.d(TAG, "- Active recording: $recording")

        if (_isRecording.value) {
            Log.w(TAG, "⚠️ Already recording, ignoring start request")
            return
        }

        if (recorder == null) {
            Log.e(TAG, "❌ Recorder is null!")
            onRecordingError?.invoke("Recorder not initialized")
            return
        }

        try {
            // Create output file
            val fileName = "recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val videoFile = File(context.getExternalFilesDir(null), fileName)

            Log.d(TAG, "📁 Video will be saved to: ${videoFile.absolutePath}")

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            // Check audio permission
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🎤 Audio permission: $hasAudioPermission")

            // Prepare recording
            val pendingRecording = recorder!!.prepareRecording(context, outputOptions)
                .apply {
                    if (hasAudioPermission) {
                        withAudioEnabled()
                    }
                }

            // Start recording
            recording = pendingRecording.start(executor) { recordEvent ->
                Log.d(TAG, "📹 Record event: ${recordEvent.javaClass.simpleName}")

                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "✅ Recording STARTED")
                        _isRecording.value = true
                        onRecordingStarted?.invoke()
                    }

                    is VideoRecordEvent.Pause -> {
                        Log.d(TAG, "⏸️ Recording PAUSED")
                    }

                    is VideoRecordEvent.Resume -> {
                        Log.d(TAG, "▶️ Recording RESUMED")
                    }

                    is VideoRecordEvent.Finalize -> {
                        Log.d(TAG, "🏁 Recording FINALIZED")
                        _isRecording.value = false

                        if (!recordEvent.hasError()) {
                            val outputUri = recordEvent.outputResults.outputUri
                            Log.d(TAG, "✅ Recording saved successfully: $outputUri")
                            onRecordingStopped?.invoke(videoFile.absolutePath)
                        } else {
                            val error = when (recordEvent.error) {
                                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "Encoding failed"
                                VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "File size limit reached"
                                VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Insufficient storage"
                                VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "Invalid output options"
                                VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "No valid data"
                                VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "Recorder error"
                                VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Source inactive"
                                else -> "Unknown error: ${recordEvent.error}"
                            }
                            Log.e(TAG, "❌ Recording error: $error")
                            onRecordingError?.invoke(error)
                        }

                        recording = null
                    }

                    is VideoRecordEvent.Status -> {
                        val stats = recordEvent.recordingStats
                        Log.v(TAG, "📊 Recording stats - Duration: ${stats.recordedDurationNanos / 1_000_000}ms, Size: ${stats.numBytesRecorded} bytes")
                    }
                }
            }

            Log.d(TAG, "🎬 Recording started successfully, recording object: $recording")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording", e)
            _isRecording.value = false
            onRecordingError?.invoke("Failed to start recording: ${e.message}")
        }
    }

    fun stopRecording() {
        Log.d(TAG, "🛑 stopRecording() called")
        Log.d(TAG, "- Current state: ${_isRecording.value}")
        Log.d(TAG, "- Active recording: $recording")

        if (!_isRecording.value || recording == null) {
            Log.w(TAG, "⚠️ No active recording to stop")
            return
        }

        try {
            recording?.stop()
            Log.d(TAG, "✅ Stop recording called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
            onRecordingError?.invoke("Error stopping recording: ${e.message}")
        }

        recording = null
    }

    fun pauseRecording() {
        Log.d(TAG, "⏸️ pauseRecording() called")

        if (!_isRecording.value || recording == null) {
            Log.w(TAG, "⚠️ No active recording to pause")
            return
        }

        try {
            recording?.pause()
            Log.d(TAG, "✅ Recording paused")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error pausing recording", e)
        }
    }

    fun resumeRecording() {
        Log.d(TAG, "▶️ resumeRecording() called")

        if (!_isRecording.value || recording == null) {
            Log.w(TAG, "⚠️ No active recording to resume")
            return
        }

        try {
            recording?.resume()
            Log.d(TAG, "✅ Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error resuming recording", e)
        }
    }

    fun release() {
        Log.d(TAG, "🧹 Releasing VideoRecordingManager")

        try {
            if (_isRecording.value) {
                stopRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording during release", e)
        }

        recording = null
        recorder = null
        videoCapture = null
        _isRecording.value = false

        Log.d(TAG, "✅ VideoRecordingManager released")
    }

    companion object {
        private const val TAG = "VideoRecordingManager"
    }
}