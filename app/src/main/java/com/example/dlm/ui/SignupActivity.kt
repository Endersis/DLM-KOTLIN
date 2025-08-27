package com.example.dlm.ui

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dlm.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SignupActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var signupButton: MaterialButton
    private lateinit var googleSignupButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        emailInput = findViewById(R.id.emailInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        signupButton = findViewById(R.id.signupButton)
        googleSignupButton = findViewById(R.id.googleSignupButton)
    }

    private fun setupClickListeners() {
        // Botón de registro
        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (validateInput(email, username, password)) {
                performSignup(email, username, password)
            }
        }

        // Registro con Google
        googleSignupButton.setOnClickListener {
            Toast.makeText(this, "Registro con Google", Toast.LENGTH_SHORT).show()

        }

        // Si el usuario ya tiene cuenta, volver al login
        findViewById<TextView>(R.id.signupText).setOnClickListener {
            // Volver a LoginActivity
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun validateInput(email: String, username: String, password: String): Boolean {
        if (email.isEmpty()) {
            emailInput.error = "Ingresa tu email"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Email inválido"
            return false
        }

        if (username.isEmpty()) {
            usernameInput.error = "Ingresa tu nombre de usuario"
            return false
        }

        if (username.length < 3) {
            usernameInput.error = "El nombre de usuario debe tener al menos 3 caracteres"
            return false
        }

        if (password.isEmpty()) {
            passwordInput.error = "Ingresa tu contraseña"
            return false
        }

        if (password.length < 6) {
            passwordInput.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }

        return true
    }

    private fun performSignup(email: String, username: String, password: String) {
        // Aquí deberías implementar la lógica real de registro
        // Por ejemplo, con Firebase Auth o tu backend

        // Por ahora, simulamos un registro exitoso
        Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()

        // Navegar a la cámara

    }



    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}