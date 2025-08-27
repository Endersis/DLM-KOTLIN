package com.example.dlm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dlm.R
import com.google.android.material.navigation.NavigationView
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.example.dlm.api.RetrofitClient
import com.example.dlm.models.CatalogoResponse
import com.example.dlm.models.Sena
import retrofit2.Response
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException

class CatalogoActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var etSearch: EditText
    private lateinit var rvSenas: RecyclerView
    private lateinit var senaAdapter: SenaAdapter
    private var allSenas = mutableListOf<Sena>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalogo)

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

        // Inicializar vistas del Drawer
        drawerLayout = findViewById(R.id.drawer_layout_catalogo)
        navigationView = findViewById(R.id.nav_view_catalogo)
        toolbar = findViewById(R.id.toolbar_catalogo)

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

        // Inicializar vistas del Catálogo
        etSearch = findViewById(R.id.et_search)
        rvSenas = findViewById(R.id.rv_senas)

        // Configurar RecyclerView
        rvSenas.layoutManager = GridLayoutManager(this, 2)
        senaAdapter = SenaAdapter(allSenas) { sena ->
            val intent = Intent(this, SenaDetailActivity::class.java).apply {
                putExtra("sena_nombre", sena.nombre)
            }
            startActivity(intent)
        }
        rvSenas.adapter = senaAdapter

        fetchCatalogo()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSenas(s.toString())
            }
        })
    }

    private fun fetchCatalogo() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val authToken = prefs.getString("auth_token", null)

        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Token de autorización no encontrado", Toast.LENGTH_LONG).show()
            return
        }

        // Llamamos a la instancia de la API que ya tiene el interceptor de autenticación
        val apiService = RetrofitClient.getCommonInstance(this)

        // Usamos una corrutina para la llamada a la API
        lifecycleScope.launch {
            try {
                // Realizamos la llamada suspend fun directamente
                val response: Response<CatalogoResponse> = apiService.getCatalogoSenas("Bearer $authToken")

                if (response.isSuccessful) {
                    val catalogoResponse = response.body()
                    val senasList = catalogoResponse?.data
                    if (!senasList.isNullOrEmpty()) {
                        allSenas.clear()
                        allSenas.addAll(senasList)
                        senaAdapter.updateData(allSenas)
                        Toast.makeText(this@CatalogoActivity, "Catálogo cargado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@CatalogoActivity, "No se encontraron señas", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // El servidor respondió con un error (ej. 401, 404)
                    Toast.makeText(this@CatalogoActivity, "Error al cargar el catálogo. Código: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                // Error de red (sin conexión, DNS, etc.)
                Toast.makeText(this@CatalogoActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Otro tipo de error (ej. JSON malformado)
                Toast.makeText(this@CatalogoActivity, "Ocurrió un error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filterSenas(query: String) {
        val filteredList = if (query.isEmpty()) {
            allSenas
        } else {
            allSenas.filter { sena ->
                sena.nombre.contains(query, ignoreCase = true) ||
                        sena.hashtag.contains(query, ignoreCase = true) ||
                        sena.descripcion.contains(query, ignoreCase = true)
            }
        }
        senaAdapter.updateData(filteredList)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> {
                Toast.makeText(this, "Perfil clicado", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_dlm -> {
                Toast.makeText(this, "Ya estás en Catálogo (DLM)", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_favorites -> {
                Toast.makeText(this, "Destacados clicado", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DestacadosActivity::class.java)
                startActivity(intent)
                finish()
            }
            R.id.nav_logout -> {
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

                // Borrar el token de autenticación
                prefs.edit().remove("auth_token").apply()

                // Redirigir a la pantalla de inicio de sesión
                val intent = Intent(this, LoginActivity::class.java).apply {
                    // Limpia todas las actividades de la pila para evitar que el usuario vuelva atrás
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)

                // Mostrar un mensaje de éxito
                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
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
