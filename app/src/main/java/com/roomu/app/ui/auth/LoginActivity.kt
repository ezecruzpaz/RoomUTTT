package com.roomu.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import cn.pedant.SweetAlert.SweetAlertDialog
import com.roomu.app.R
import com.roomu.app.ui.auth.viewmodel.LoginViewModel
import com.roomu.app.ui.auth.viewmodel.RegisterViewModel
import com.roomu.app.ui.home.MainActivity
import com.roomu.app.ui.renter.RenterDashboardActivity
import com.roomu.app.ui.profile.ProfileActivity
import com.datadog.android.Datadog
import com.roomu.app.utils.ProfanityFilter
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private val registerViewModel: RegisterViewModel by viewModels() // ✅ Para verificar nombres
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 9001
    private val TAG = "LoginActivity"
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private var progressDialog: SweetAlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // ✅ VERIFICACIÓN INVISIBLE - Si hay sesión, redirigir INMEDIATAMENTE
        if (auth.currentUser != null) {
            Log.d(TAG, "⚡ Usuario ya autenticado, redirigiendo instantáneamente...")
            // ✅ DATADOG: Actualizar user info si ya hay sesión
            updateDatadogUserInfo(auth.currentUser!!)
            // ✅ Verificar nombre inapropiado antes de redirigir
            checkInappropriateNameAndNavigateQuickly()
            return
        }

        // Solo si NO hay sesión, mostrar UI de login
        setContentView(R.layout.activity_login)
        Log.d(TAG, "📱 Mostrando pantalla de login")

        // Inicializar vistas
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)

        // Google Setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Botón de Google
        val btnGoogleLogin = findViewById<ImageView>(R.id.btn_google_login)
        btnGoogleLogin.setOnClickListener {
            Log.d(TAG, "Botón Google clicado")
            showProgressDialog("Iniciando sesión con Google...")

            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
                }
            }
        }

        // Login con Email/Password
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateLoginInput(email, password)) {
                return@setOnClickListener
            }

            Log.d(TAG, "Iniciando login con email: $email")
            showProgressDialog("Iniciando sesión...")
            viewModel.loginWithEmailPassword(email, password)
        }

        // Navegación a Registro
        val tvRegister = findViewById<TextView>(R.id.tv_register)
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Observa estado del ViewModel
        lifecycleScope.launch {
            viewModel.loginState.collect { result ->
                Log.d(TAG, "loginState emitido: $result")
                result?.let {
                    dismissProgressDialog()

                    if (it.isSuccess == true) {
                        Log.d(TAG, "Login exitoso - Verificando rol y nombre...")

                        // ✅ DATADOG: Actualizar user info después del login
                        auth.currentUser?.let { firebaseUser ->
                            updateDatadogUserInfo(firebaseUser)
                        }

                        val userName = auth.currentUser?.displayName
                            ?: auth.currentUser?.email?.substringBefore("@")
                            ?: "usuario"

                        showSuccessDialog(
                            "¡Bienvenido!",
                            "Inicio de sesión exitoso\n¡Hola $userName!"
                        ) {
                            showProgressDialog("Verificando perfil...")
                            lifecycleScope.launch {
                                kotlinx.coroutines.delay(500)
                                // ✅ Verificar nombre inapropiado antes de navegar
                                checkInappropriateNameAndNavigate()
                            }
                        }
                    } else if (it.isFailure == true) {
                        Log.e(TAG, "Login falló: ${it.exceptionOrNull()?.message}")
                        val errorMsg = getErrorMessage(it.exceptionOrNull()?.message)
                        showErrorDialog("Error de inicio de sesión", errorMsg)
                    }
                }
            }
        }
    }

    // ✅ NUEVO: Función para actualizar user info en Datadog
    private fun updateDatadogUserInfo(firebaseUser: FirebaseUser) {
        try {
            Datadog.setUserInfo(
                id = firebaseUser.uid,
                name = firebaseUser.displayName ?: "Usuario",
                email = firebaseUser.email ?: "",
                extraInfo = mapOf(
                    "provider" to (firebaseUser.providerId ?: "unknown"),
                    "email_verified" to firebaseUser.isEmailVerified.toString(),
                    "phone_number" to (firebaseUser.phoneNumber ?: "none")
                )
            )
            Log.d(TAG, "✅ Datadog user info actualizado para: ${firebaseUser.email}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando Datadog user info", e)
        }
    }

    // ✅ NUEVA: Verificar nombre inapropiado y navegar rápidamente (para sesión existente)
    private fun checkInappropriateNameAndNavigateQuickly() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Verificar y censurar si es necesario
                val hadInappropriateName = registerViewModel.checkAndCensorExistingUserName(userId)

                if (hadInappropriateName) {
                    Log.d(TAG, "⚠️ Nombre inapropiado detectado al iniciar sesión")
                    // Redirigir directamente al perfil
                    val intent = Intent(this@LoginActivity, ProfileActivity::class.java).apply {
                        putExtra("force_name_update", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    // Continuar con navegación normal
                    navigateBasedOnRole(userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando nombre: ${e.message}")
                // En caso de error, continuar con navegación normal
                navigateBasedOnRole(userId)
            }
        }
    }

    // ✅ NUEVA: Verificar nombre inapropiado y navegar (para login nuevo)
    private fun checkInappropriateNameAndNavigate() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Verificar y censurar si es necesario
                val hadInappropriateName = registerViewModel.checkAndCensorExistingUserName(userId)

                dismissProgressDialog()

                if (hadInappropriateName) {
                    Log.d(TAG, "⚠️ Nombre inapropiado detectado")

                    // Mostrar advertencia
                    SweetAlertDialog(this@LoginActivity, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("⚠️ Actualización de perfil requerida")
                        .setContentText("Tu nombre de perfil contiene contenido inapropiado y ha sido censurado. Por favor actualiza tu perfil con un nombre apropiado.")
                        .setConfirmText("Ir a perfil")
                        .setConfirmClickListener { dialog ->
                            dialog.dismiss()
                            val intent = Intent(this@LoginActivity, ProfileActivity::class.java).apply {
                                putExtra("force_name_update", true)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                        .setCancelButton("Después") { dialog ->
                            dialog.dismiss()
                            navigateBasedOnRole(userId)
                        }
                        .show()
                } else {
                    // Continuar con navegación normal
                    navigateBasedOnRole(userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando nombre: ${e.message}")
                dismissProgressDialog()
                // En caso de error, continuar con navegación normal
                navigateBasedOnRole(userId)
            }
        }
    }

    // ✅ NUEVA: Función auxiliar para navegar según el rol
    private fun navigateBasedOnRole(userId: String) {
        firestore.collection("renters")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val intent = if (document.exists()) {
                    Log.d(TAG, "👤 Arrendatario - Navegando a RenterDashboard")
                    Intent(this, RenterDashboardActivity::class.java)
                } else {
                    Log.d(TAG, "👤 Cliente - Navegando a MainActivity")
                    Intent(this, MainActivity::class.java)
                }

                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error verificando rol: ${e.message}")
                // Por defecto, ir a MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
    }

    private fun validateLoginInput(email: String, password: String): Boolean {
        if (TextUtils.isEmpty(email)) {
            etEmail.error = "El email es requerido"
            etEmail.requestFocus()
            showWarningDialog("Campo requerido", "Por favor ingresa tu email")
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"
            etEmail.requestFocus()
            showWarningDialog("Email inválido", "Por favor ingresa un email válido")
            return false
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.error = "La contraseña es requerida"
            etPassword.requestFocus()
            showWarningDialog("Campo requerido", "Por favor ingresa tu contraseña")
            return false
        }

        if (password.length < 6) {
            etPassword.error = "Contraseña debe tener al menos 6 caracteres"
            etPassword.requestFocus()
            showWarningDialog(
                "Contraseña muy corta",
                "La contraseña debe tener al menos 6 caracteres"
            )
            return false
        }

        return true
    }

    private fun getErrorMessage(error: String?): String {
        return when {
            error?.contains("credential is incorrect", ignoreCase = true) == true ||
                    error?.contains("malformed", ignoreCase = true) == true ||
                    error?.contains("has expired", ignoreCase = true) == true ->
                "Email o contraseña incorrectos. Por favor verifica tus datos"

            error?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ||
                    error?.contains("invalid-credential", ignoreCase = true) == true ->
                "Credenciales inválidas. Verifica tu email y contraseña"

            error?.contains("wrong-password", ignoreCase = true) == true ||
                    error?.contains("password", ignoreCase = true) == true ->
                "La contraseña es incorrecta"

            error?.contains("user-not-found", ignoreCase = true) == true ||
                    error?.contains("user", ignoreCase = true) == true ->
                "No existe una cuenta con este email"

            error?.contains("invalid-email", ignoreCase = true) == true ||
                    error?.contains("email", ignoreCase = true) == true ->
                "El formato del email no es válido"

            error?.contains("user-disabled", ignoreCase = true) == true ->
                "Esta cuenta ha sido deshabilitada"

            error?.contains("too-many-requests", ignoreCase = true) == true ->
                "Demasiados intentos fallidos. Intenta más tarde"

            error?.contains("network", ignoreCase = true) == true ||
                    error?.contains("connection", ignoreCase = true) == true ->
                "Sin conexión a internet. Verifica tu red"

            error?.contains("timeout", ignoreCase = true) == true ->
                "La conexión tardó demasiado. Intenta nuevamente"

            else -> "No se pudo iniciar sesión. Verifica tus datos e intenta nuevamente"
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
            .setConfirmText("Continuar")
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult llamado con requestCode: $requestCode")

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Cuenta Google obtenida: ${account.email}")
                viewModel.loginWithGoogle(account)
            } catch (e: ApiException) {
                dismissProgressDialog()
                Log.e(TAG, "Error en Google Sign-In: ${e.statusCode} - ${e.message}")
                showErrorDialog(
                    "Error de autenticación",
                    "No se pudo iniciar sesión con Google. Intenta nuevamente"
                )
            }
        }
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
    }
}