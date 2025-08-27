// com.example.dlm/UsuarioDestacadoAdapter.kt
package com.example.dlm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dlm.R

// Asegúrate de que este modelo de datos esté en el mismo archivo o sea accesible
data class UsuarioDestacado(
    val id: String,
    val nombre: String,
    val senasIngresadas: Int
)

class UsuarioDestacadoAdapter(
    // CAMBIO IMPORTANTE: La lista debe ser var para poder ser actualizada
    private var usuarios: List<UsuarioDestacado>,
    private val clickListener: (UsuarioDestacado) -> Unit
) : RecyclerView.Adapter<UsuarioDestacadoAdapter.UsuarioDestacadoViewHolder>() {

    class UsuarioDestacadoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTextView: TextView = itemView.findViewById(R.id.tv_user_name)
        val starsContainer: LinearLayout = itemView.findViewById(R.id.ll_stars_container)

        fun bind(usuario: UsuarioDestacado, clickListener: (UsuarioDestacado) -> Unit) {
            userNameTextView.text = usuario.nombre
            starsContainer.removeAllViews()

            val numStars = when {
                usuario.senasIngresadas >= 100 -> 3
                usuario.senasIngresadas >= 60 -> 2
                usuario.senasIngresadas >= 25 -> 1
                else -> 0
            }

            for (i in 1..3) {
                val star = ImageView(itemView.context)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.width = (24 * itemView.context.resources.displayMetrics.density + 0.5f).toInt()
                params.height = (24 * itemView.context.resources.displayMetrics.density + 0.5f).toInt()
                star.layoutParams = params
                star.setImageResource(
                    if (i <= numStars) R.drawable.ic_star_filled
                    else R.drawable.ic_star_empty
                )
                starsContainer.addView(star)
            }

            itemView.setOnClickListener { clickListener(usuario) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioDestacadoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_destacado, parent, false)
        return UsuarioDestacadoViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioDestacadoViewHolder, position: Int) {
        holder.bind(usuarios[position], clickListener)
    }

    override fun getItemCount(): Int = usuarios.size

    // ¡AÑADE ESTA FUNCIÓN! 🚀
    fun updateData(newUsuarios: List<UsuarioDestacado>) {
        this.usuarios = newUsuarios
        notifyDataSetChanged()
    }
}