package com.example.roomuttt.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.ui.auth.viewmodel.LoginViewModel
import com.example.roomuttt.ui.home.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth  // Para email/password
    private val RC_SIGN_IN = 9001
    private val TAG = "LoginActivity"

    // Vistas para email/password
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d(TAG, "onCreate iniciado")

        auth = FirebaseAuth.getInstance()  // Inicializa Firebase Auth

        // Inicializar vistas para email/password
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)

        // Google Setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Botón de Google (con signOut para forzar selector de cuenta)
        val btnGoogleLogin = findViewById<ImageView>(R.id.btn_google_login)
        btnGoogleLogin.setOnClickListener {
            Log.d(TAG, "Botón Google clicado - Cerrando sesión anterior para forzar selector")
            googleSignInClient.signOut().addOnCompleteListener {  // Fuerza selector cada vez
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }
        }

        // Login con Email/Password
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validación básica
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Email inválido"
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Contraseña debe tener al menos 6 caracteres"
                return@setOnClickListener
            }

            Log.d(TAG, "Iniciando login con email: $email")
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
                result?.let {  // Verifica nulo
                    if (it.isSuccess == true) {
                        Log.d(TAG, "Login exitoso - Navegando a MainActivity")
                        Toast.makeText(this@LoginActivity, "Login exitoso", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else if (it.isFailure == true) {
                        Log.e(TAG, "Login falló: ${it.exceptionOrNull()?.message}")
                        val errorMsg = it.exceptionOrNull()?.message ?: "Error desconocido"
                        Toast.makeText(this@LoginActivity, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
                Log.e(TAG, "Error en Google Sign-In: ${e.statusCode} - ${e.message}")
                Toast.makeText(this, "Error en Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}