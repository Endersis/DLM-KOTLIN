package com.example.dlm.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dlm.R
import com.example.dlm.api.RetrofitClient
import com.example.dlm.models.LoginRequest
import com.example.dlm.models.LoginResponse
import com.example.dlm.models.UserProfileResponse
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException


class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var googleLoginButton: MaterialButton
    private lateinit var signupButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        googleLoginButton = findViewById(R.id.googleLoginButton)
        signupButton = findViewById(R.id.signupText)
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        googleLoginButton.setOnClickListener {
            Toast.makeText(this, "Login con Google", Toast.LENGTH_SHORT).show()
        }

        signupButton.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            emailInput.error = "Ingresa tu email"
            return false
        }
        if (password.isEmpty()) {
            passwordInput.error = "Ingresa tu contraseña"
            return false
        }
        return true
    }

    private fun performLogin(email: String, password: String) {
        loginButton.isEnabled = false
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )

        val loginRequest = LoginRequest(correo = email, password = password)

        // Inicia una corrutina en el hilo principal
        lifecycleScope.launch {
            try {
                // Llama a la función suspend directamente
                val response = RetrofitClient.loginInstance.login(loginRequest)

                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit().putString("auth_token", loginResponse.token).apply()
                        Log.d("LoginActivity", "Token guardado: ${loginResponse.token}")

                        // Llama a la siguiente función de perfil
                        getUserProfile(email) // Ya no necesitas pasar el token
                    }
                } else {
                    // El servidor respondió con un error (ej. 401, 404)
                    loginButton.isEnabled = true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    Toast.makeText(this@LoginActivity, "Credenciales incorrectas", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                // Error de red (sin conexión, DNS, etc.)
                loginButton.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Otro tipo de error (ej. JSON malformado)
                loginButton.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                Toast.makeText(this@LoginActivity, "Ocurrió un error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getUserProfile(email: String) {
        // La instancia con el interceptor ya añade el token automáticamente
        val commonApiService = RetrofitClient.getCommonInstance(this)

        lifecycleScope.launch {
            try {
                // En tu ApiService, el token se añade con @Header y el correo con @Path
                // El interceptor ya pone el "Bearer ", así que no lo necesitas aquí.
                val response = commonApiService.getUserProfile("ignored_token", email)

                loginButton.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

                if (response.isSuccessful) {
                    val userProfile = response.body()
                    if (userProfile != null) {
                        // ... (resto de tu lógica para redirigir al Admin o Catálogo)
                        // ... esta parte se queda exactamente igual que la tenías
                        val userData = userProfile.data
                        val userRol = userData.rol
                        val isAdmin = userRol == "administrador"

                        Toast.makeText(this@LoginActivity, "Login exitoso. Rol: $userRol", Toast.LENGTH_SHORT).show()

                        val intent = if (isAdmin) {
                            Intent(this@LoginActivity, AdminActivity::class.java) // Reemplaza con tus Activities
                        } else {
                            Intent(this@LoginActivity, CatalogoActivity::class.java)
                        }
                        // Añade tus extras al intent aquí
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(this@LoginActivity, "Error al obtener perfil de usuario", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Error al obtener perfil. Código: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                loginButton.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                loginButton.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                Toast.makeText(this@LoginActivity, "Ocurrió un error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
