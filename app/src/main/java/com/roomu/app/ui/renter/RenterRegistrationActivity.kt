package com.roomu.app.ui.renter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.R
import com.roomu.app.ui.renter.viewmodel.RenterViewModel
import com.roomu.app.ui.room.AllRoomsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class RenterRegistrationActivity : AppCompatActivity() {

    private val viewModel: RenterViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private lateinit var etNombreCompleto: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etDireccion: EditText
    private lateinit var btnSubmit: Button
    private lateinit var ivBack: ImageView
    private lateinit var ivClose: ImageView
    private lateinit var btnGps: CardView
    private lateinit var bottomNavigation: BottomNavigationView

    private var progressDialog: SweetAlertDialog? = null

    private val mainViewModel: com.roomu.app.ui.home.viewmodel.MainViewModel by viewModels()

    // Cliente de ubicación
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Solicitud de permisos de ubicación
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Permiso preciso concedido
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Solo permiso aproximado concedido
                getCurrentLocation()
            }
            else -> {
                // Sin permisos
                Toast.makeText(
                    this,
                    "Se necesitan permisos de ubicación para usar el GPS",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renter_registration)

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initViews()
        setupListeners()
        prefillName()
        observeViewModel()
        setupBottomNavigation() // AÑADIR ESTO
    }

    private fun initViews() {
        etNombreCompleto = findViewById(R.id.et_nombre_completo)
        etTelefono = findViewById(R.id.et_telefono)
        etDireccion = findViewById(R.id.et_direccion)
        btnSubmit = findViewById(R.id.btn_submit_renter)
        ivBack = findViewById(R.id.iv_back)
        ivClose = findViewById(R.id.iv_close)
        btnGps = findViewById(R.id.btn_gps)
        bottomNavigation = findViewById(R.id.bottom_nav) // AÑADIR ESTO
    }

    private fun setupListeners() {
        ivBack.setOnClickListener { onBackPressed() }
        ivClose.setOnClickListener { finish() }

        // Limitar teléfono a exactamente 10 dígitos
        etTelefono.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                // Remover cualquier caracter que no sea número
                val filtered = s.toString().filter { it.isDigit() }

                // Si tiene más de 10 dígitos, truncar
                if (filtered.length > 10) {
                    val truncated = filtered.substring(0, 10)
                    etTelefono.setText(truncated)
                    etTelefono.setSelection(truncated.length)
                } else if (s.toString() != filtered) {
                    // Si había caracteres no numéricos, reemplazar
                    etTelefono.setText(filtered)
                    etTelefono.setSelection(filtered.length)
                }
            }
        })

        btnGps.setOnClickListener {
            checkLocationPermissionAndGetLocation()
        }

        btnSubmit.setOnClickListener {
            val nombre = etNombreCompleto.text.toString().trim()
            val telefono = etTelefono.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            // Validar campos vacíos
            if (nombre.isEmpty() || telefono.isEmpty() || direccion.isEmpty()) {
                showErrorDialog("Completa todos los campos")
                return@setOnClickListener
            }

            // Validar teléfono
            if (!isValidPhoneNumber(telefono)) {
                showErrorDialog("El número de teléfono debe tener 10 dígitos")
                return@setOnClickListener
            }

            // Validar ubicación
            if (!isValidLocation(direccion)) {
                showErrorDialog("La ubicación debe tener mínimo 10 caracteres y máximo 100")
                return@setOnClickListener
            }

            viewModel.createRenterAccount(nombre, telefono, direccion)
        }
    }
    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = -1

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, RenterDashboardActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_rooms -> {
                    lifecycleScope.launch {
                        try {
                            val uid = auth.currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(this@RenterRegistrationActivity, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            // Cargar cuartos desde MainViewModel
                            mainViewModel.loadRooms()
                            val allRooms = mainViewModel.allRooms.value ?: emptyList()

                            // Filtrar cuartos del usuario
                            val userRooms = allRooms.filter { it.userId == uid }

                            if (userRooms.isNotEmpty()) {
                                val intent = Intent(this@RenterRegistrationActivity, AllRoomsActivity::class.java).apply {
                                    putExtra("allRooms", ArrayList(userRooms))
                                    putExtra("isRenterView", true)
                                    putExtra("fromRenterDashboard", true)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@RenterRegistrationActivity, "Aún no tienes cuartos publicados", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("RenterReg", "Error cargando cuartos: ${e.message}")
                            Toast.makeText(this@RenterRegistrationActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }

                R.id.nav_chat -> {
                    val intent = Intent(this, com.roomu.app.ui.chat.ChatsListActivity::class.java).apply {
                        putExtra("isRenter", true)
                    }
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }
    }
    /**
     * Valida que el teléfono tenga 10 dígitos
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        return digitsOnly.length == 10
    }

    /**
     * Valida que la ubicación sea válida (acepta coordenadas o dirección de texto)
     */
    private fun isValidLocation(location: String): Boolean {
        // Si está vacío, no es válido
        if (location.isBlank()) return false

        // Debe tener al menos 10 caracteres para ser una ubicación válida
        if (location.length < 10) return false

        // Intentar validar como coordenadas
        val coordinatesPattern = Regex("^-?\\d+\\.\\d+\\s*,\\s*-?\\d+\\.\\d+$")

        if (coordinatesPattern.matches(location)) {
            // Es formato de coordenadas, validar rangos
            val parts = location.split(",")
            if (parts.size != 2) return false

            try {
                val lat = parts[0].trim().toDouble()
                val lon = parts[1].trim().toDouble()

                // Latitud: -90 a 90, Longitud: -180 a 180
                return lat in -90.0..90.0 && lon in -180.0..180.0
            } catch (e: NumberFormatException) {
                return false
            }
        }

        // Si no es formato de coordenadas, aceptar como dirección de texto
        // Solo validar que tenga longitud razonable
        return location.length in 10..100
    }

    private fun checkLocationPermissionAndGetLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tenemos permiso
                getCurrentLocation()
            }
            else -> {
                // Solicitar permisos
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                    titleText = "Obteniendo ubicación..."
                    contentText = "Esto puede tardar unos segundos"
                    setCancelable(false)
                    show()
                }

                // Agregar un retraso mínimo de 2 segundos para que se vea el mensaje
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location: Location? ->
                            // Retraso adicional antes de cerrar el diálogo
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                loadingDialog.dismissWithAnimation()

                                if (location != null) {
                                    val latitude = location.latitude
                                    val longitude = location.longitude
                                    val coordinates = String.format("%.6f, %.6f", latitude, longitude)

                                    etDireccion.setText(coordinates)

                                    // Mostrar mensaje de éxito con animación
                                    SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE).apply {
                                        titleText = "¡Ubicación obtenida!"
                                        contentText = "Tu ubicación GPS se ha registrado correctamente"
                                        confirmText = "Aceptar"
                                        setConfirmClickListener { it.dismissWithAnimation() }
                                        show()
                                    }

                                    Log.d("RenterRegistration", "✅ Ubicación: $coordinates")
                                } else {
                                    SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE).apply {
                                        titleText = "No se pudo obtener ubicación"
                                        contentText = "Por favor, intenta de nuevo o escribe tu ubicación manualmente"
                                        confirmText = "Aceptar"
                                        setConfirmClickListener { it.dismissWithAnimation() }
                                        show()
                                    }
                                }
                            }, 800) // 800ms después de obtener la ubicación
                        }
                        .addOnFailureListener { e ->
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                loadingDialog.dismissWithAnimation()

                                SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE).apply {
                                    titleText = "Error al obtener ubicación"
                                    contentText = "Por favor, verifica que tu GPS esté activado e intenta nuevamente"
                                    confirmText = "Aceptar"
                                    setConfirmClickListener { it.dismissWithAnimation() }
                                    show()
                                }

                                Log.e("RenterRegistration", "❌ Error GPS: ${e.message}")
                            }, 800)
                        }
                }, 1500) // 1.5 segundos de retraso inicial
            }
        } catch (e: SecurityException) {
            Log.e("RenterRegistration", "❌ Error de seguridad: ${e.message}")
            SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE).apply {
                titleText = "Error de permisos"
                contentText = "No se tienen permisos para acceder a la ubicación"
                confirmText = "Aceptar"
                setConfirmClickListener { it.dismissWithAnimation() }
                show()
            }
        }
    }

    private fun prefillName() {
        lifecycleScope.launch {
            try {
                val user = auth.currentUser
                if (user != null) {
                    val uid = user.uid

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
                            startActivity(Intent(this@RenterRegistrationActivity, RenterDashboardActivity::class.java))
                            finish()
                        }
                    }
                    is RenterViewModel.RenterState.Error -> {
                        dismissProgressDialog()
                        showErrorDialog(state.message)
                    }
                    else -> {}
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