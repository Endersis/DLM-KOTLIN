package com.example.dlm.Utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FileExportManager(private val context: Context) {

    companion object {
        private const val TAG = "FileExportManager"
        private const val EXPORT_FOLDER = "DLM_Exports"
    }

    /**
     * Copia archivos al directorio público de Descargas para fácil acceso
     */
    suspend fun exportFilesToDownloads(
        filePaths: List<String>,
        processingMode: VideoPostProcessor.ProcessingMode
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$EXPORT_FOLDER/$timestamp"
            )

            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val copiedFiles = mutableListOf<String>()

            filePaths.forEach { originalPath ->
                val originalFile = File(originalPath)
                if (originalFile.exists()) {
                    val fileName = originalFile.name
                    val destinationFile = File(exportDir, fileName)

                    copyFile(originalFile, destinationFile)
                    copiedFiles.add(destinationFile.absolutePath)
                    Log.d(TAG, "Archivo copiado: ${destinationFile.absolutePath}")
                }
            }

            // Crear archivo de información
            createInfoFile(exportDir, processingMode, copiedFiles.size)

            ExportResult(
                success = true,
                message = "Archivos exportados a: ${exportDir.absolutePath}",
                exportPath = exportDir.absolutePath,
                filesCount = copiedFiles.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exportando archivos", e)
            ExportResult(
                success = false,
                message = "Error al exportar: ${e.message}",
                exportPath = null,
                filesCount = 0
            )
        }
    }

    /**
     * Comparte archivos usando Intent para que el usuario pueda enviarlos donde quiera
     */
    fun shareFiles(filePaths: List<String>, processingMode: VideoPostProcessor.ProcessingMode) {
        try {
            if (filePaths.isEmpty()) {
                Toast.makeText(context, "No hay archivos para compartir", Toast.LENGTH_SHORT).show()
                return
            }

            val uris = mutableListOf<Uri>()

            filePaths.forEach { filePath ->
                val file = File(filePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider", // Debe coincidir con el authority de tu FileProvider en AndroidManifest.xml
                        file
                    )
                    uris.add(uri)
                }
            }

            if (uris.isEmpty()) {
                Toast.makeText(context, "No se encontraron archivos válidos", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }

                type = when (processingMode) {
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS -> "text/csv" // Cambiado de text/plain a text/csv
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES -> "image/jpeg"
                }

                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Archivos DLM - ${processingMode.name}")
            }

            val chooser = Intent.createChooser(intent, "Compartir archivos DLM")
            context.startActivity(chooser)

        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo archivos", e)
            Toast.makeText(context, "Error al compartir archivos", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Abre el explorador de archivos en la carpeta donde se guardaron los archivos
     */
    fun openFileExplorer(exportPath: String) {
        try {
            val exportDir = File(exportPath)
            // Verificar si la carpeta existe y es un directorio válido
            if (!exportDir.exists() || !exportDir.isDirectory) {
                Toast.makeText(context, "La carpeta de exportación no existe o no es válida.", Toast.LENGTH_SHORT).show()
                return
            }

            // Usar FileProvider para obtener una URI segura para la carpeta
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", // Debe coincidir con el authority de tu FileProvider en AndroidManifest.xml
                exportDir
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                // Establecer la URI y el tipo MIME para una carpeta
                // "vnd.android.document/directory" es un tipo MIME común para directorios
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Otorgar permisos de lectura temporales al explorador de archivos
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {

                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*" // Permite seleccionar cualquier tipo de archivo
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Abrir explorador de archivos"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo explorador: ${e.message}", e)
            Toast.makeText(context, "No se pudo abrir el explorador de archivos: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Lista todos los archivos DLM generados - ACTUALIZADA para incluir archivos CSV
     */
    fun listGeneratedFiles(): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()

        try {
            val appDir = context.getExternalFilesDir(null) ?: return emptyList()

            appDir.listFiles()?.forEach { file ->
                when {
                    // Archivos CSV de landmarks (nuevos)
                    (file.name.startsWith("landmarks_") ||
                            file.name.startsWith("hand_landmarks_")) &&
                            file.name.endsWith(".csv") -> {
                        files.add(
                            GeneratedFile(
                                path = file.absolutePath,
                                name = file.name,
                                type = FileType.LANDMARKS,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                    // Archivos TXT de landmarks (compatibilidad con versiones anteriores)
                    (file.name.startsWith("hand_landmarks_") ||
                            file.name.startsWith("combined_landmarks_")) &&
                            file.name.endsWith(".txt") -> {
                        files.add(
                            GeneratedFile(
                                path = file.absolutePath,
                                name = file.name,
                                type = FileType.LANDMARKS,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                    // Archivos de frames
                    file.name.startsWith("frame_") && file.name.endsWith(".jpg") -> {
                        files.add(
                            GeneratedFile(
                                path = file.absolutePath,
                                name = file.name,
                                type = FileType.FRAME,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listando archivos: ${e.message}", e)
        }

        return files.sortedByDescending { it.lastModified }
    }

    /**
     * Función auxiliar para copiar un archivo de una ubicación a otra.
     */
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Crea un archivo de texto con información sobre la exportación en el directorio de destino.
     */
    private fun createInfoFile(exportDir: File, mode: VideoPostProcessor.ProcessingMode, filesCount: Int) {
        val infoFile = File(exportDir, "info.txt")
        infoFile.writeText(
            """
            DLM Informacion de exportacion
            =====================
            Fecha de exportacion: ${Date()}
            Modo de procesamiento: ${mode.name}
            Archivos exportados: $filesCount
            
            Description:
            ${when (mode) {
                VideoPostProcessor.ProcessingMode.HAND_LANDMARKS ->
                    "Landmarks datos extraidos en formato CSV con estructura:\n" +
                            "- Datos combinados: pose (8 puntos x 4 valores) + mano_izq (21 x 3) + mano_der (21 x 3) = 158 columnas\n" +
                            "- Solo manos: mano_izq (21 x 3) + mano_der (21 x 3) = 126 columnas"
                VideoPostProcessor.ProcessingMode.VIDEO_FRAMES ->
                    "20 frames extraidos del video en formato JPG"
            }}
            
            App: DLM (Deep Learning Model)
            """.trimIndent()
        )
    }

    /**
     * Clase de datos para el resultado de una operación de exportación.
     */
    data class ExportResult(
        val success: Boolean,
        val message: String,
        val exportPath: String?,
        val filesCount: Int
    )

    /**
     * Clase de datos para representar un archivo generado por la aplicación.
     */
    data class GeneratedFile(
        val path: String,
        val name: String,
        val type: FileType,
        val size: Long,
        val lastModified: Long
    ) {
        /**
         * Retorna el tamaño del archivo en un formato legible (B, KB, MB).
         */
        fun getSizeString(): String {
            return when {
                size < 1024 -> "${size}B"
                size < 1024 * 1024 -> "${size / 1024}KB"
                else -> "${size / (1024 * 1024)}MB"
            }
        }

        /**
         * Retorna la fecha de última modificación del archivo en un formato legible.
         */
        fun getDateString(): String {
            return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(lastModified))
        }
    }

    /**
     * Enumeración para los tipos de archivos generados.
     */
    enum class FileType {
        LANDMARKS, FRAME
    }
}