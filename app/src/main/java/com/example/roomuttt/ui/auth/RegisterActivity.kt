package com.example.roomuttt.ui.auth

import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.ui.auth.viewmodel.RegisterViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    // Referencias a elementos del layout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvTerms: TextView
    private lateinit var cbTerms: CheckBox
    private lateinit var btnRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar vistas
        etEmail = findViewById(R.id.et_email)
        etFullName = findViewById(R.id.et_full_name)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        tvTerms = findViewById(R.id.tv_terms)
        cbTerms = findViewById(R.id.cb_terms)
        btnRegister = findViewById(R.id.btn_register)

        // Enlace a términos (opcional: abrir un WebView o activity con términos)
        tvTerms.setOnClickListener {
            // Aquí puedes abrir un diálogo o activity con los términos
            Toast.makeText(this, "Abrir términos y condiciones", Toast.LENGTH_SHORT).show()
        }

        // Botón de registro
        btnRegister.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val fullName = etFullName.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val termsAccepted = cbTerms.isChecked

            // Validación básica en UI
            if (!isValidInput(email, fullName, password, confirmPassword, termsAccepted)) {
                return@setOnClickListener
            }

            // Llama al ViewModel
            viewModel.registerUser(email, fullName, password)

        }

        // Observa estado del ViewModel
        lifecycleScope.launch {
            viewModel.registerState.collect { result ->
                result?.let {  // Verifica nulo primero
                    when {
                        it.isSuccess -> {
                            Toast.makeText(this@RegisterActivity, "Registro exitoso. Verifica tu email.", Toast.LENGTH_SHORT).show()
                            finish()  // Vuelve a LoginActivity
                        }
                        it.isFailure -> {
                            Toast.makeText(this@RegisterActivity, "Error: ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun isValidInput(email: String, fullName: String, password: String, confirmPassword: String, termsAccepted: Boolean): Boolean {
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"
            return false
        }
        if (TextUtils.isEmpty(fullName)) {
            etFullName.error = "Nombre requerido"
            return false
        }
        if (password.length < 6) {
            etPassword.error = "Contraseña debe tener al menos 6 caracteres"
            return false
        }
        if (password != confirmPassword) {
            etConfirmPassword.error = "Las contraseñas no coinciden"
            return false
        }
        if (!termsAccepted) {
            Toast.makeText(this, "Debes aceptar los términos", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}