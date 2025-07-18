package com.example.dlm

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout // Importa LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dlm.Utils.FileExportManager
import com.example.dlm.Utils.VideoPostProcessor
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class FilesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnExportAll: MaterialButton
    private lateinit var btnShareAll: MaterialButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvNoFiles: LinearLayout // CORRECCIÓN: Cambiado de TextView a LinearLayout
    private lateinit var tvFilesCount: TextView

    private lateinit var fileExportManager: FileExportManager
    private lateinit var filesAdapter: FilesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        initViews()
        setupRecyclerView()
        loadFiles()
        setupClickListeners()
    }

    /**
     * Initializes all the views from the layout.
     */
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewFiles)
        btnExportAll = findViewById(R.id.btnExportAll)
        btnShareAll = findViewById(R.id.btnShareAll)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnBack = findViewById(R.id.btnBack)
        tvNoFiles = findViewById(R.id.tvNoFiles) // Now the type matches the XML
        tvFilesCount = findViewById(R.id.tvFilesCount)

        fileExportManager = FileExportManager(this)
    }

    /**
     * Sets up the RecyclerView with its adapter and layout manager.
     */
    private fun setupRecyclerView() {
        filesAdapter = FilesAdapter { file ->
            showFileOptions(file)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = filesAdapter
    }

    /**
     * Sets up click listeners for various buttons.
     */
    private fun setupClickListeners() {
        btnRefresh.setOnClickListener {
            loadFiles()
        }

        btnExportAll.setOnClickListener {
            exportAllFiles()
        }

        btnShareAll.setOnClickListener {
            shareAllFiles()
        }

        // Back button
        btnBack.setOnClickListener {
            finish()
        }
    }

    /**
     * Loads generated files and updates the UI accordingly.
     */
    private fun loadFiles() {
        val files = fileExportManager.listGeneratedFiles()

        if (files.isEmpty()) {
            tvNoFiles.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvFilesCount.text = "No hay archivos generados"
            btnExportAll.isEnabled = false
            btnShareAll.isEnabled = false
        } else {
            tvNoFiles.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            val landmarksCount = files.count { it.type == FileExportManager.FileType.LANDMARKS }
            val framesCount = files.count { it.type == FileExportManager.FileType.FRAME }

            tvFilesCount.text = "Total: ${files.size} archivos " +
                    "($landmarksCount landmarks, $framesCount frames)"

            btnExportAll.isEnabled = true
            btnShareAll.isEnabled = true

            filesAdapter.updateFiles(files)
        }
    }

    /**
     * Shows a dialog with options for a selected file.
     * @param file The file for which to show options.
     */
    private fun showFileOptions(file: FileExportManager.GeneratedFile) {
        val options = arrayOf(
            "Ver información",
            "Compartir archivo",
            "Exportar a Descargas",
            "Eliminar archivo"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFileInfo(file)
                    1 -> shareFile(file)
                    2 -> exportFile(file)
                    3 -> deleteFile(file)
                }
            }
            .show()
    }

    /**
     * Displays detailed information about a file in a dialog.
     * @param file The file whose information is to be displayed.
     */
    private fun showFileInfo(file: FileExportManager.GeneratedFile) {
        val info = """
            Nombre: ${file.name}
            Tipo: ${if (file.type == FileExportManager.FileType.LANDMARKS) "Hand Landmarks" else "Video Frame"}
            Tamaño: ${file.getSizeString()}
            Fecha: ${file.getDateString()}
            Ruta: ${file.path}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Información del archivo")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Shares a single file using FileExportManager.
     * @param file The file to be shared.
     */
    private fun shareFile(file: FileExportManager.GeneratedFile) {
        val mode = if (file.type == FileExportManager.FileType.LANDMARKS) {
            VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
        } else {
            VideoPostProcessor.ProcessingMode.VIDEO_FRAMES
        }

        fileExportManager.shareFiles(listOf(file.path), mode)
    }

    /**
     * Exports a single file to the Downloads directory.
     * @param file The file to be exported.
     */
    private fun exportFile(file: FileExportManager.GeneratedFile) {
        lifecycleScope.launch {
            val mode = if (file.type == FileExportManager.FileType.LANDMARKS) {
                VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
            } else {
                VideoPostProcessor.ProcessingMode.VIDEO_FRAMES
            }

            val result = fileExportManager.exportFilesToDownloads(listOf(file.path), mode)

            if (result.success) {
                Toast.makeText(this@FilesActivity, "✅ ${result.message}", Toast.LENGTH_LONG).show()

                // Ask if the user wants to open the file explorer
                MaterialAlertDialogBuilder(this@FilesActivity)
                    .setTitle("Archivo exportado")
                    .setMessage("¿Quieres abrir el explorador de archivos?")
                    .setPositiveButton("Sí") { _, _ ->
                        result.exportPath?.let { path ->
                            fileExportManager.openFileExplorer(path)
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                Toast.makeText(this@FilesActivity, "❌ ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Deletes a specified file.
     * @param file The file to be deleted.
     */
    private fun deleteFile(file: FileExportManager.GeneratedFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar archivo")
            .setMessage("¿Estás seguro de que quieres eliminar ${file.name}?")
            .setPositiveButton("Eliminar") { _, _ ->
                try {
                    val deleted = java.io.File(file.path).delete()
                    if (deleted) {
                        Toast.makeText(this, "Archivo eliminado", Toast.LENGTH_SHORT).show()
                        loadFiles() // Reload the list
                    } else {
                        Toast.makeText(this, "No se pudo eliminar el archivo", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Exports all generated files to the Downloads directory.
     */
    private fun exportAllFiles() {
        val files = fileExportManager.listGeneratedFiles()
        if (files.isEmpty()) {
            Toast.makeText(this, "No hay archivos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // Group by type for separate export
            val landmarkFiles = files.filter { it.type == FileExportManager.FileType.LANDMARKS }
            val frameFiles = files.filter { it.type == FileExportManager.FileType.FRAME }

            var successCount = 0

            if (landmarkFiles.isNotEmpty()) {
                val result = fileExportManager.exportFilesToDownloads(
                    landmarkFiles.map { it.path },
                    VideoPostProcessor.ProcessingMode.HAND_LANDMARKS
                )
                if (result.success) successCount++
            }

            if (frameFiles.isNotEmpty()) {
                val result = fileExportManager.exportFilesToDownloads(
                    frameFiles.map { it.path },
                    VideoPostProcessor.ProcessingMode.VIDEO_FRAMES
                )
                if (result.success) successCount++
            }

            Toast.makeText(
                this@FilesActivity,
                if (successCount > 0) "✅ Archivos exportados exitosamente"
                else "❌ Error al exportar archivos",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Shares all generated files.
     */
    private fun shareAllFiles() {
        val files = fileExportManager.listGeneratedFiles()
        if (files.isEmpty()) {
            Toast.makeText(this, "No hay archivos para compartir", Toast.LENGTH_SHORT).show()
            return
        }

        // For simplicity, share as mixed files
        fileExportManager.shareFiles(
            files.map { it.path },
            VideoPostProcessor.ProcessingMode.HAND_LANDMARKS // Default type
        )
    }
}

/**
 * RecyclerView adapter for displaying a list of generated files.
 * @param onFileClick Lambda function to be called when a file item is clicked.
 */
class FilesAdapter(
    private val onFileClick: (FileExportManager.GeneratedFile) -> Unit
) : RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {

    private var files = listOf<FileExportManager.GeneratedFile>()

    /**
     * Updates the list of files and notifies the adapter of the change.
     * @param newFiles The new list of generated files.
     */
    fun updateFiles(newFiles: List<FileExportManager.GeneratedFile>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    /**
     * ViewHolder for a single file item in the RecyclerView.
     * @param itemView The view for the file item.
     */
    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
        private val tvFileType: TextView = itemView.findViewById(R.id.tvFileType)

        /**
         * Binds data from a GeneratedFile object to the views in the ViewHolder.
         * @param file The GeneratedFile object to bind.
         */
        fun bind(file: FileExportManager.GeneratedFile) {
            tvFileName.text = file.name
            tvFileInfo.text = "${file.getSizeString()} • ${file.getDateString()}"
            tvFileType.text = when (file.type) {
                FileExportManager.FileType.LANDMARKS -> "📝 Landmarks"
                FileExportManager.FileType.FRAME -> "🖼️ Frame"
            }

            itemView.setOnClickListener {
                onFileClick(file)
            }
        }
    }
}
