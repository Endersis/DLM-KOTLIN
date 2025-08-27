package com.example.dlm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dlm.Utils.Mediapipe.CameraActivity
import com.example.dlm.R
import com.example.dlm.SignupActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.example.dlm.profile.ProfileActivity

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
                // Por ahora, simulamos un login exitoso
                performLogin(email, password)
            }
        }

        googleLoginButton.setOnClickListener {

            // Verificar permisos primero
            if (checkCameraPermissions()) {
                navigateToCamera()
            } else {
                requestCameraPermissions()
            }
        }

        // Click en Signup
        signupButton.setOnClickListener {
            // Navegar a SignupActivity
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

        if (password.length < 6) {
            passwordInput.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }

        return true
    }

    private fun performLogin(email: String, password: String) {
        // Aquí deberías implementar la lógica real de autenticación
        // Por ejemplo, con Firebase Auth o tu backend

        // Por ahora, simulamos un login exitoso
        Toast.makeText(this, "Login exitoso", Toast.LENGTH_SHORT).show()

        // Después del login exitoso, podrías ir a la cámara:
        // navigateToCamera()
    }

    private fun checkCameraPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            CAMERA_PERMISSION_REQUEST
        )
    }

    private fun navigateToCamera() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                navigateToCamera()
            } else {
                Toast.makeText(this, "Se necesitan permisos de cámara y audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}