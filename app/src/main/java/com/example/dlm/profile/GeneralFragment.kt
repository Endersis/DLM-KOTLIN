package com.example.dlm.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.dlm.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class GeneralFragment : Fragment() {

    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSaveChanges: MaterialButton

    private var currentName = "Juan Pérez"
    private var currentEmail = "juan.perez@gmail.com"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_general, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadUserData()
        setupListeners()
    }

    private fun initViews(view: View) {
        etName = view.findViewById(R.id.etName)
        etEmail = view.findViewById(R.id.etEmail)
        btnSaveChanges = view.findViewById(R.id.btnSaveChanges)
    }

    private fun loadUserData() {
        etName.setText(currentName)
        etEmail.setText(currentEmail)
    }

    private fun setupListeners() {
        btnSaveChanges.setOnClickListener {
            saveChanges()
        }
    }

    private fun saveChanges() {
        val newName = etName.text.toString().trim()
        val newEmail = etEmail.text.toString().trim()

        if (newName.isEmpty()) {
            etName.error = "El nombre es requerido"
            return
        }

        if (newEmail.isEmpty()) {
            etEmail.error = "El email es requerido"
            return
        }

        if (!isValidEmail(newEmail)) {
            etEmail.error = "Email inválido"
            return
        }

        currentName = newName
        currentEmail = newEmail

        Toast.makeText(context, "✅ Cambios guardados exitosamente", Toast.LENGTH_SHORT).show()
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}