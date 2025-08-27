package com.example.dlm.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.dlm.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Obtener referencia al logo
        val logoImage = findViewById<ImageView>(R.id.logoImage)

        // Animar el logo al aparecer
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        logoImage.startAnimation(fadeIn)

        // Configurar click en el logo
        logoImage.setOnClickListener {
            // Ir a la pantalla de login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)

            // Animación de transición suave
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

    }
}