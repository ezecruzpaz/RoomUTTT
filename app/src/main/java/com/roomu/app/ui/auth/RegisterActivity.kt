package com.roomu.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.TextUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import cn.pedant.SweetAlert.SweetAlertDialog
import com.roomu.app.R
import com.roomu.app.ui.auth.viewmodel.RegisterViewModel
import com.roomu.app.ui.terms.TermsActivity

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    private lateinit var etEmail: TextInputEditText
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvTerms: TextView
    private lateinit var cbTerms: CheckBox
    private lateinit var btnRegister: Button
    private var progressDialog: SweetAlertDialog? = null

    companion object {
        private const val MAX_NAME_LENGTH = 50
    }

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

        // Establecer límite de 50 caracteres para nombre completo
        etFullName.filters = arrayOf(InputFilter.LengthFilter(MAX_NAME_LENGTH))

        // Enlace a términos
        tvTerms.setOnClickListener {
            startActivity(Intent(this@RegisterActivity, TermsActivity::class.java))
        }

        // Botón de registro
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val fullName = etFullName.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val termsAccepted = cbTerms.isChecked

            // Validación
            if (!isValidInput(email, fullName, password, confirmPassword, termsAccepted)) {
                return@setOnClickListener
            }

            // Llama al ViewModel
            showProgressDialog("Registrando usuario...")
            viewModel.registerUser(email, fullName, password)
        }

        // Observa estado del ViewModel
        lifecycleScope.launch {
            viewModel.registerState.collect { result ->
                result?.let {
                    dismissProgressDialog()

                    when {
                        it.isSuccess -> {
                            showSuccessDialog(
                                title = "¡Registro Exitoso!",
                                message = "Tu cuenta ha sido creada."
                            ) {
                                finish()
                            }
                        }
                        it.isFailure -> {
                            val errorMsg = getErrorMessage(it.exceptionOrNull()?.message)
                            showErrorDialog("Error en el registro", errorMsg)
                        }
                    }
                }
            }
        }
    }

    private fun isValidInput(
        email: String,
        fullName: String,
        password: String,
        confirmPassword: String,
        termsAccepted: Boolean
    ): Boolean {
        // Validar email vacío
        if (TextUtils.isEmpty(email)) {
            etEmail.error = "El email es requerido"
            etEmail.requestFocus()
            showWarningDialog("Campo requerido", "Por favor ingresa tu email")
            return false
        }

        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"
            etEmail.requestFocus()
            showWarningDialog("Email inválido", "Por favor ingresa un email válido")
            return false
        }

        // Validar nombre completo vacío
        if (TextUtils.isEmpty(fullName)) {
            etFullName.error = "El nombre es requerido"
            etFullName.requestFocus()
            showWarningDialog("Campo requerido", "Por favor ingresa tu nombre completo")
            return false
        }

        // Validar longitud mínima del nombre
        if (fullName.length < 3) {
            etFullName.error = "Nombre muy corto"
            etFullName.requestFocus()
            showWarningDialog(
                "Nombre muy corto",
                "El nombre debe tener al menos 3 caracteres"
            )
            return false
        }

        // Validar que el nombre solo contenga letras y espacios
        if (!fullName.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$"))) {
            etFullName.error = "Solo se permiten letras y espacios"
            etFullName.requestFocus()
            showWarningDialog(
                "Formato inválido",
                "El nombre solo puede contener letras y espacios"
            )
            return false
        }

        // Validar contraseña vacía
        if (TextUtils.isEmpty(password)) {
            etPassword.error = "La contraseña es requerida"
            etPassword.requestFocus()
            showWarningDialog("Campo requerido", "Por favor ingresa una contraseña")
            return false
        }

        // Validar longitud mínima de contraseña
        if (password.length < 6) {
            etPassword.error = "Contraseña debe tener al menos 6 caracteres"
            etPassword.requestFocus()
            showWarningDialog(
                "Contraseña muy corta",
                "La contraseña debe tener al menos 6 caracteres"
            )
            return false
        }

        // Validar confirmación de contraseña
        if (password != confirmPassword) {
            etConfirmPassword.error = "Las contraseñas no coinciden"
            etConfirmPassword.requestFocus()
            showWarningDialog(
                "Contraseñas diferentes",
                "Las contraseñas ingresadas no coinciden"
            )
            return false
        }

        // Validar aceptación de términos
        if (!termsAccepted) {
            showWarningDialog(
                "Términos y condiciones",
                "Debes aceptar los términos y condiciones para continuar"
            )
            return false
        }

        return true
    }

    private fun getErrorMessage(error: String?): String {
        return when {
            error?.contains("already in use", ignoreCase = true) == true ->
                "Este email ya está registrado. Intenta iniciar sesión"
            error?.contains("network", ignoreCase = true) == true ->
                "Error de conexión. Verifica tu internet"
            error?.contains("weak password", ignoreCase = true) == true ->
                "La contraseña es muy débil. Usa al menos 6 caracteres"
            error?.contains("invalid email", ignoreCase = true) == true ->
                "El formato del email es inválido"
            else -> error ?: "Error desconocido. Intenta nuevamente"
        }
    }

    private fun showProgressDialog(message: String) {
        progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
            titleText = "Cargando"
            contentText = message
            setCancelable(false)
            show()
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showSuccessDialog(title: String, message: String, onConfirm: () -> Unit) {
        SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .setConfirmText("Entendido")
            .setConfirmClickListener {
                it.dismiss()
                onConfirm()
            }
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .setConfirmText("Entendido")
            .show()
    }

    private fun showWarningDialog(title: String, message: String) {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .setConfirmText("OK")
            .show()
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences("RoomUPrefs", MODE_PRIVATE)
        val politicasAceptadas = sharedPreferences.getBoolean("politicas_aceptadas", false)
        if (politicasAceptadas) {
            cbTerms.isChecked = true
        }
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
    }
}