package com.example.dlm.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dlm.R
import com.example.dlm.s3.S3Manager
import com.example.dlm.api.RetrofitClient
import com.example.dlm.models.Sena
import com.example.dlm.models.SenaDetailResponse
import com.example.dlm.ui.ImageAdapter // Asegúrate de que este import sea correcto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class SenaDetailActivity : AppCompatActivity() {

    private lateinit var senaName: TextView
    private lateinit var senaHashtags: TextView
    private lateinit var senaDescription: TextView
    private lateinit var btnComenzar: Button
    private lateinit var shareIconCard: ImageView
    private lateinit var rvSenaImages: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sena_detail)

        val toolbar: Toolbar = findViewById(R.id.toolbar_detail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        senaName = findViewById(R.id.sena_name)
        senaHashtags = findViewById(R.id.sena_hashtags)
        senaDescription = findViewById(R.id.sena_description)
        btnComenzar = findViewById(R.id.btn_comenzar)
        shareIconCard = findViewById(R.id.share_icon_card)

        rvSenaImages = findViewById(R.id.rv_sena_images)
        rvSenaImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val senaNombre = intent.getStringExtra("sena_nombre")
        Log.d("SenaDetailActivity", "Seña recibida: $senaNombre")
        if (senaNombre != null) {
            fetchSenaDetails(senaNombre)
        } else {
            Toast.makeText(this, "Error: No se encontró el nombre de la seña.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun fetchSenaDetails(nombre: String) {
        val apiService = RetrofitClient.getCommonInstance(this)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "No se pudo autenticar. Por favor, reinicia la app.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response: Response<SenaDetailResponse> = apiService.getSenaByName("Bearer $token", nombre)

                if (response.isSuccessful && response.body() != null) {
                    val sena = response.body()!!.data
                    updateUI(sena)
                } else {
                    Log.e("SenaDetailActivity", "Error al obtener los detalles de la seña: ${response.code()}")
                    Toast.makeText(this@SenaDetailActivity, "Error al cargar la seña.", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Log.e("SenaDetailActivity", "Error de conexión: ${e.message}")
                Toast.makeText(this@SenaDetailActivity, "Error de conexión.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("SenaDetailActivity", "Ocurrió un error inesperado: ${e.message}")
                Toast.makeText(this@SenaDetailActivity, "Ocurrió un error inesperado.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI(sena: Sena) {
        senaName.text = sena.nombre
        senaHashtags.text = sena.hashtag
        senaDescription.text = sena.descripcion

        // **Lógica corregida para el RecyclerView**
        lifecycleScope.launch {
            try {
                if (sena.imagenesUrl.isNotEmpty()) {
                    val signedUrls = withContext(Dispatchers.IO) {
                        sena.imagenesUrl.map { imageUrl ->
                            val s3Key = imageUrl.substringAfter(".com/")
                            S3Manager.getSignedUrlForS3Object(s3Key).toString()
                        }
                    }
                    rvSenaImages.adapter = ImageAdapter(signedUrls)
                } else {
                    Toast.makeText(this@SenaDetailActivity, "La seña no tiene imágenes asociadas.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SenaDetailActivity", "Error al generar URLs firmadas: ${e.message}")
                Toast.makeText(this@SenaDetailActivity, "Error al cargar las imágenes.", Toast.LENGTH_SHORT).show()
            }
        }

        btnComenzar.setOnClickListener {
            Toast.makeText(this, "Botón COMENZAR clicado para: ${sena.nombre}", Toast.LENGTH_SHORT).show()
        }
        shareIconCard.setOnClickListener {
            Toast.makeText(this, "Compartir (Tarjeta) clicado: ${sena.nombre}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}