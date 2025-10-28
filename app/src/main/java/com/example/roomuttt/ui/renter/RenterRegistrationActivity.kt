package com.example.roomuttt.ui.renter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.ui.renter.viewmodel.RenterViewModel
import com.google.firebase.auth.FirebaseAuth
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class RenterRegistrationActivity : AppCompatActivity() {

    private val viewModel: RenterViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance() // 🆕


    private lateinit var etNombreCompleto: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etDireccion: EditText
    private lateinit var btnSubmit: Button
    private lateinit var ivBack: ImageView
    private lateinit var ivClose: ImageView

    private var progressDialog: SweetAlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renter_registration)

        initViews()
        setupListeners()
        prefillName() // Prefill nombre del usuario autenticado
        observeViewModel()
    }

    private fun initViews() {
        etNombreCompleto = findViewById(R.id.et_nombre_completo)
        etTelefono = findViewById(R.id.et_telefono)
        etDireccion = findViewById(R.id.et_direccion)
        btnSubmit = findViewById(R.id.btn_submit_renter)
        ivBack = findViewById(R.id.iv_back)
        ivClose = findViewById(R.id.iv_close)
    }

    private fun setupListeners() {
        ivBack.setOnClickListener { onBackPressed() }
        ivClose.setOnClickListener { finish() }

        btnSubmit.setOnClickListener {
            val nombre = etNombreCompleto.text.toString().trim()
            val telefono = etTelefono.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            if (nombre.isEmpty() || telefono.isEmpty() || direccion.isEmpty()) {
                showErrorDialog("Completa todos los campos")
                return@setOnClickListener
            }

            viewModel.createRenterAccount(nombre, telefono, direccion)
        }
    }

    private fun prefillName() {
        lifecycleScope.launch {
            try {
                val user = auth.currentUser
                if (user != null) {
                    val uid = user.uid

                    // 🔥 Obtener nombre desde Firestore
                    val userDoc = firestore.collection("users")
                        .document(uid)
                        .get()
                        .await()

                    val nameFromFirestore = userDoc.getString("name")

                    val finalName = if (!nameFromFirestore.isNullOrBlank()) {
                        nameFromFirestore
                    } else {
                        user.displayName ?: user.email?.split("@")?.first() ?: "Usuario"
                    }

                    etNombreCompleto.setText(finalName)
                    Log.d("RenterRegistration", "✅ Nombre cargado: $finalName")
                }
            } catch (e: Exception) {
                Log.e("RenterRegistration", "❌ Error: ${e.message}")
                auth.currentUser?.let {
                    etNombreCompleto.setText(it.displayName ?: it.email?.split("@")?.first() ?: "Usuario")
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.renterState.collect { state ->
                when (state) {
                    is RenterViewModel.RenterState.Loading -> {
                        showProgressDialog("Creando cuenta...")
                    }
                    is RenterViewModel.RenterState.Success -> {
                        dismissProgressDialog()
                        showSuccessDialog(state.message) {
                            // Redirigir a RenterDashboardActivity en lugar de solo finish()
                            startActivity(Intent(this@RenterRegistrationActivity, RenterDashboardActivity::class.java))
                            finish() // Cierra la actividad actual después de redirigir
                        }
                    }
                    is RenterViewModel.RenterState.Error -> {
                        dismissProgressDialog()
                        showErrorDialog(state.message)
                    }
                    else -> {} // Idle no necesita acción
                }
            }
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

    private fun showSuccessDialog(message: String, onConfirm: () -> Unit) {
        SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "Éxito"
            contentText = message
            confirmText = "Aceptar"
            setConfirmClickListener { sDialog ->
                sDialog.dismissWithAnimation()
                onConfirm()
            }
            show()
        }
    }

    private fun showErrorDialog(message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE).apply {
            titleText = "Error"
            contentText = message
            confirmText = "Aceptar"
            setConfirmClickListener { it.dismissWithAnimation() }
            show()
        }
    }
}