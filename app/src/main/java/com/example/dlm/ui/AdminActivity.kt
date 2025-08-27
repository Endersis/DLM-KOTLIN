// com.example.dlm/AdminActivity.kt
package com.example.dlm.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.example.dlm.R


class AdminActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    private lateinit var etNewSena: TextInputEditText
    private lateinit var etDescripcion: TextInputEditText
    private lateinit var etHashtags: TextInputEditText
    private lateinit var btnUploadVideo: MaterialButton
    private lateinit var btnComenzar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) y superior
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Versiones anteriores a Android 11 (API 30)
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }



        // Inicializar vistas del Drawer y Toolbar
        drawerLayout = findViewById(R.id.drawer_layout_admin)
        navigationView = findViewById(R.id.nav_view_admin)
        toolbar = findViewById(R.id.toolbar_admin)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)

        // Inicializar vistas del formulario
        etNewSena = findViewById(R.id.et_new_sena)
        etDescripcion = findViewById(R.id.et_descripcion)
        etHashtags = findViewById(R.id.et_hashtags)
        btnUploadVideo = findViewById(R.id.btn_upload_video)
        btnComenzar = findViewById(R.id.btn_comenzar)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnUploadVideo.setOnClickListener {
            // Lógica para abrir el selector de archivos de video
            Toast.makeText(this, "Simulando subir un video", Toast.LENGTH_SHORT).show()
            // Aquí puedes implementar el Intent para seleccionar un video:
            // val intent = Intent(Intent.ACTION_GET_CONTENT)
            // intent.type = "video/*"
            // startActivityForResult(intent, REQUEST_VIDEO_PICK)
        }

        btnComenzar.setOnClickListener {
            val nuevaSena = etNewSena.text.toString().trim()
            val descripcion = etDescripcion.text.toString().trim()
            val hashtags = etHashtags.text.toString().trim()

            // Validación simple
            if (nuevaSena.isEmpty() || descripcion.isEmpty() || hashtags.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Aquí iría la lógica para enviar los datos a la base de datos
            Toast.makeText(this, "Nueva seña enviada: $nuevaSena", Toast.LENGTH_LONG).show()
            // Simulación de "subida exitosa"
            etNewSena.text?.clear()
            etDescripcion.text?.clear()
            etHashtags.text?.clear()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> {
                Toast.makeText(this, "Perfil clicado", Toast.LENGTH_SHORT).show()
                // Navegar a la pantalla de Perfil
            }
            R.id.nav_dlm -> {
                val intent = Intent(this, CatalogoActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_favorites -> {
                val intent = Intent(this, DestacadosActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_logout -> {
                Toast.makeText(this, "Salir clicado", Toast.LENGTH_SHORT).show()
                // Lógica de cierre de sesión
            }
        }
        drawerLayout.closeDrawer(navigationView)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }
}