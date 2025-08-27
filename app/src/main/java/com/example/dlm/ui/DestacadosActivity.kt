// Archivo: DestacadosActivity.kt
package com.example.dlm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dlm.R
import com.example.dlm.api.RetrofitClient
import com.example.dlm.models.Usuario
import com.example.dlm.UsuarioDestacado
import com.example.dlm.models.UsuariosResponse
import com.example.dlm.UsuarioDestacadoAdapter // Tu adaptador
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import android.os.Build
import android.view.View


class DestacadosActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var rvDestacados: RecyclerView
    private lateinit var usuarioDestacadoAdapter: UsuarioDestacadoAdapter

    // Lista para almacenar los usuarios que se van a mostrar
    private var allUsuariosDestacados = mutableListOf<UsuarioDestacado>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destacados)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }


        // Inicializar vistas del Drawer y Toolbar
        drawerLayout = findViewById(R.id.drawer_layout_destacados)
        navigationView = findViewById(R.id.nav_view_destacados)
        toolbar = findViewById(R.id.toolbar_destacados)
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

        // Inicializar RecyclerView y su adaptador
        rvDestacados = findViewById(R.id.rv_destacados)
        rvDestacados.layoutManager = LinearLayoutManager(this)
        usuarioDestacadoAdapter = UsuarioDestacadoAdapter(allUsuariosDestacados) { usuario ->
            // Acción al hacer clic en un usuario destacado
            Toast.makeText(this, "Clic en ${usuario.nombre} - ${usuario.senasIngresadas} señas", Toast.LENGTH_SHORT).show()
        }
        rvDestacados.adapter = usuarioDestacadoAdapter

        // Llamada a la API para obtener los usuarios
        fetchUsuariosDestacados()
    }

    private fun fetchUsuariosDestacados() {
        val apiService = RetrofitClient.getCommonInstance(this)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val authToken = prefs.getString("auth_token", null)

        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Token de autorización no encontrado", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response: Response<UsuariosResponse> = apiService.getUsuarios("Bearer $authToken")

                if (response.isSuccessful && response.body() != null) {
                    val usuariosList = response.body()!!.data

                    val destacadosList = usuariosList.map {
                        UsuarioDestacado(it.correo, it.nombre, it.destacados?.conteo ?: 0)
                    }.sortedByDescending { it.senasIngresadas }

                    allUsuariosDestacados.clear()
                    allUsuariosDestacados.addAll(destacadosList)
                    usuarioDestacadoAdapter.updateData(allUsuariosDestacados)

                    Log.d("DestacadosActivity", "Usuarios destacados cargados exitosamente.")
                } else {
                    Log.e("DestacadosActivity", "Error al cargar usuarios: ${response.code()}")
                    Toast.makeText(this@DestacadosActivity, "Error al cargar usuarios destacados.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e("DestacadosActivity", "Error de conexión: ${e.message}")
                Toast.makeText(this@DestacadosActivity, "Error de conexión.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("DestacadosActivity", "Ocurrió un error inesperado: ${e.message}")
                Toast.makeText(this@DestacadosActivity, "Ocurrió un error inesperado.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ... (el resto del código del menú y onBackPressed)
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> {
                Toast.makeText(this, "Perfil clicado", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_dlm -> {
                val intent = Intent(this, CatalogoActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_favorites -> {
                Toast.makeText(this, "Ya estás en Destacados", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                Toast.makeText(this, "Salir clicado", Toast.LENGTH_SHORT).show()
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