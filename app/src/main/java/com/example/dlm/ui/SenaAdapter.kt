// com.example.dlm.ui/SenaAdapter.kt
package com.example.dlm.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dlm.R
import com.example.dlm.s3.S3Manager
import com.example.dlm.models.Sena
import kotlinx.coroutines.*

class SenaAdapter(
    private var senas: List<Sena>,
    private val clickListener: (Sena) -> Unit
) : RecyclerView.Adapter<SenaAdapter.SenaViewHolder>() {

    // Se crea un CoroutineScope para el ciclo de vida del adaptador
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())

    class SenaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewNombre: TextView = view.findViewById(R.id.sena_name)
        val textViewDescripcion: TextView = view.findViewById(R.id.sena_description)
        val imageViewSena: ImageView = view.findViewById(R.id.sena_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SenaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sena, parent, false)
        return SenaViewHolder(view)
    }

    override fun onBindViewHolder(holder: SenaViewHolder, position: Int) {
        val sena = senas[position]
        holder.textViewNombre.text = sena.nombre
        holder.textViewDescripcion.text = sena.hashtag

        holder.itemView.setOnClickListener {
            Log.d("SenaAdapter", "Seña clickeada: ${sena.nombre}")
            clickListener(sena)
        }

        // Cargar la primera imagen de la lista de forma asíncrona
        if (sena.imagenesUrl.isNotEmpty()) {
            val imageUrl = sena.imagenesUrl[0]

            // Limpia la vista para evitar que se carguen imágenes viejas
            Glide.with(holder.itemView.context).clear(holder.imageViewSena)
            holder.imageViewSena.setImageResource(R.drawable.ic_user_placeholder1)

            // Usa una corrutina para obtener la URL firmada en el hilo de IO
            adapterScope.launch {
                try {
                    val s3Key = imageUrl.substringAfter(".com/")
                    val signedUrl = withContext(Dispatchers.IO) {
                        S3Manager.getSignedUrlForS3Object(s3Key).toString()
                    }
                    // Una vez que tenemos la URL, cargamos la imagen en el hilo principal
                    Glide.with(holder.itemView.context)
                        .load(signedUrl)
                        .into(holder.imageViewSena)
                } catch (e: Exception) {
                    // Manejar errores si no se puede obtener la URL o cargar la imagen
                    e.printStackTrace()
                    holder.imageViewSena.setImageResource(R.drawable.ic_user_placeholder1)
                }
            }
        } else {
            // Si la lista de URLs está vacía, carga una imagen de placeholder
            holder.imageViewSena.setImageResource(R.drawable.ic_user_placeholder1)
        }
    }

    override fun getItemCount(): Int {
        return senas.size
    }

    fun updateData(newSenas: List<Sena>) {
        this.senas = newSenas
        notifyDataSetChanged()
    }

    // Método para cancelar las corrutinas cuando el adaptador ya no sea necesario
    fun destroy() {
        adapterScope.cancel()
    }
}