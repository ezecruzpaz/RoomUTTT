package com.roomu.app.ui.room

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import cn.pedant.SweetAlert.SweetAlertDialog
import com.roomu.app.R
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.renter.RenterDashboardActivity

@AndroidEntryPoint
class CreateRoomActivity : AppCompatActivity(), OnMapReadyCallback {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var auth: FirebaseAuth
    private lateinit var etNombre: EditText
    private lateinit var etPrecio: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var etCapacidad: EditText
    private lateinit var tvServiciosSeleccionados: TextView
    private lateinit var tvNombreCounter: TextView
    private lateinit var tvPrecioCounter: TextView
    private lateinit var tvDescripcionCounter: TextView
    private lateinit var tvCapacidadCounter: TextView
    private lateinit var btnAddImage: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var ivPreview: ImageView
    private lateinit var tvImageCount: TextView
    private lateinit var btnCreate: Button
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var centerMarkerView: ImageView
    private lateinit var scrollView: ScrollView
    private lateinit var bottomNavigation: BottomNavigationView
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: LatLng? = null

    private val selectedImageUris = mutableListOf<Uri>()
    private val TAG = "CreateRoomActivity"
    private var progressDialog: SweetAlertDialog? = null
    private var allRooms = mutableListOf<RoomData>()

    // Límites de caracteres
    private val MAX_NOMBRE = 50
    private val MAX_PRECIO = 10
    private val MAX_DESCRIPCION = 300
    private val MAX_CAPACIDAD = 1  // Solo 1 dígito
    private val MAX_IMAGES = 10
    private val MAX_PERSONAS = 4   // Máximo 4 personas

    private val serviciosDisponibles = arrayOf(
        "Agua, luz y gas incluidos",
        "Wi-Fi",
        "Baño privado",
        "Baño compartido",
        "Acceso a cocina",
        "Mobiliario básico",
        "Lavadora",
    )

    private val serviciosSeleccionados = BooleanArray(serviciosDisponibles.size)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > MAX_IMAGES) {
                showWarningDialog("Límite de imágenes", "Puedes seleccionar máximo $MAX_IMAGES imágenes. Se tomarán las primeras $MAX_IMAGES.")
                selectedImageUris.clear()
                selectedImageUris.addAll(uris.take(MAX_IMAGES))
            } else {
                selectedImageUris.clear()
                selectedImageUris.addAll(uris)
            }
            ivPreview.setImageURI(selectedImageUris[0])
            tvImageCount.text = "${selectedImageUris.size}/$MAX_IMAGES imagen(es) seleccionada(s)"
            Log.d(TAG, "Imágenes seleccionadas: ${selectedImageUris.size}")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permiso de ubicación concedido")
            enableMyLocation()
            getCurrentLocation()
        } else {
            Log.w(TAG, "Permiso de ubicación denegado")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_room)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initViews()
        setupTextWatchers()
        setupListeners()
        setupBottomNavigation()

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupAutocomplete()
        checkAndRequestLocationPermission()
    }

    private fun initViews() {
        etNombre = findViewById(R.id.et_nombre)
        etPrecio = findViewById(R.id.et_precio)
        etDescripcion = findViewById(R.id.et_descripcion)
        etCapacidad = findViewById(R.id.et_capacidad)
        tvServiciosSeleccionados = findViewById(R.id.tv_servicios_seleccionados)
        tvNombreCounter = findViewById(R.id.tv_nombre_counter)
        tvPrecioCounter = findViewById(R.id.tv_precio_counter)
        tvDescripcionCounter = findViewById(R.id.tv_descripcion_counter)
        tvCapacidadCounter = findViewById(R.id.tv_capacidad_counter)
        btnAddImage = findViewById(R.id.btn_add_image)
        ivPreview = findViewById(R.id.iv_preview)
        tvImageCount = findViewById(R.id.tv_image_count)
        btnCreate = findViewById(R.id.btn_create)
        centerMarkerView = findViewById(R.id.center_marker)
        scrollView = findViewById(R.id.scroll_view)
        bottomNavigation = findViewById(R.id.bottom_nav)

        // Inicializar contadores
        tvImageCount.text = "0/$MAX_IMAGES imagen(es) seleccionada(s)"
    }

    private fun setupTextWatchers() {
        // TextWatcher para Nombre - SOLO LETRAS
        etNombre.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvNombreCounter.text = "$length/$MAX_NOMBRE (solo letras)"
                tvNombreCounter.setTextColor(
                    if (length > MAX_NOMBRE * 0.9) getColor(android.R.color.holo_red_dark)
                    else getColor(R.color.darker_gray)
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // TextWatcher para Precio (sin cambios)
        etPrecio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvPrecioCounter.text = "$length/$MAX_PRECIO"
                tvPrecioCounter.setTextColor(
                    if (length > MAX_PRECIO * 0.9) getColor(android.R.color.holo_red_dark)
                    else getColor(R.color.darker_gray)
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // TextWatcher para Descripción - SOLO LETRAS
        etDescripcion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvDescripcionCounter.text = "$length/$MAX_DESCRIPCION (solo letras)"
                tvDescripcionCounter.setTextColor(
                    if (length > MAX_DESCRIPCION * 0.9) getColor(android.R.color.holo_red_dark)
                    else getColor(R.color.darker_gray)
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // TextWatcher para Capacidad - MÁXIMO 4 PERSONAS
        etCapacidad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                val valor = s.toString().toIntOrNull() ?: 0

                tvCapacidadCounter.text = "$length/$MAX_CAPACIDAD (máx. $MAX_PERSONAS personas)"
                tvCapacidadCounter.setTextColor(
                    if (valor > MAX_PERSONAS) getColor(android.R.color.holo_red_dark)
                    else getColor(R.color.darker_gray)
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListeners() {
        tvServiciosSeleccionados.setOnClickListener {
            showServiciosDialog()
        }

        btnAddImage.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        btnCreate.setOnClickListener {
            createRoom()
        }

        // ✅ Listener para el icono de perfil
        findViewById<ImageView>(R.id.iv_profile).setOnClickListener {
            startActivity(Intent(this, com.roomu.app.ui.profile.ProfileActivity::class.java))
        }

// ✅ Listener para el icono de notificaciones
        findViewById<ImageView>(R.id.iv_notifications).setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones próximamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.menu.findItem(R.id.nav_home)?.isChecked = false
        bottomNavigation.menu.findItem(R.id.nav_rooms)?.isChecked = false
        bottomNavigation.menu.findItem(R.id.nav_chat)?.isChecked = false

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, RenterDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }

                R.id.nav_rooms -> {
                    // ✅ CORREGIDO: Cargar cuartos del API antes de navegar
                    lifecycleScope.launch {
                        try {
                            // Obtener el UID del usuario actual
                            val uid = auth.currentUser?.uid

                            if (uid == null) {
                                Toast.makeText(
                                    this@CreateRoomActivity,
                                    "Error: Usuario no autenticado",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            // Cargar todos los cuartos desde el API
                            val response = roomApiService.getRooms()

                            if (response.isSuccessful) {
                                val apiResponse = response.body()
                                val allRoomsList = apiResponse?.result ?: emptyList()

                                // Filtrar solo los cuartos del usuario actual
                                val userRooms = allRoomsList.filter { room ->
                                    room.userId.trim().equals(uid.trim(), ignoreCase = true)
                                }

                                Log.d(TAG, "📦 Cuartos del usuario: ${userRooms.size}")

                                if (userRooms.isNotEmpty()) {
                                    val intent = Intent(this@CreateRoomActivity, AllRoomsActivity::class.java)
                                    intent.putExtra("allRooms", ArrayList(userRooms))
                                    intent.putExtra("isRenterView", true)
                                    intent.putExtra("fromRenterDashboard", true)
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        this@CreateRoomActivity,
                                        "Aún no tienes cuartos publicados",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    this@CreateRoomActivity,
                                    "Error al cargar cuartos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cargando cuartos: ${e.message}")
                            Toast.makeText(
                                this@CreateRoomActivity,
                                "Error de conexión",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    false
                }

                R.id.nav_chat -> {
                    Toast.makeText(this, "💬 Chat próximamente", Toast.LENGTH_SHORT).show()
                    false
                }

                else -> false
            }
        }
    }

    private fun setupAutocomplete() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.place_autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setHint("Buscar ubicación...")

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let { latLng ->
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    Log.d(TAG, "📍 Lugar seleccionado: ${place.name}, $latLng")
                    Toast.makeText(
                        this@CreateRoomActivity,
                        "📍 ${place.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Log.e(TAG, "Error al seleccionar lugar: ${status.statusMessage}")
            }
        })
    }

    private fun showServiciosDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_servicios_selection, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnAccept = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_accept)

        checkboxContainer.removeAllViews()

        serviciosDisponibles.forEachIndexed { index, servicio ->
            val checkbox = layoutInflater.inflate(R.layout.item_checkbox_servicio, checkboxContainer, false) as CheckBox

            checkbox.text = servicio
            checkbox.isChecked = serviciosSeleccionados[index]

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                serviciosSeleccionados[index] = isChecked
            }

            checkboxContainer.addView(checkbox)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnAccept.setOnClickListener {
            updateServiciosText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateServiciosText() {
        val seleccionados = mutableListOf<String>()
        for (i in serviciosDisponibles.indices) {
            if (serviciosSeleccionados[i]) {
                seleccionados.add(serviciosDisponibles[i])
            }
        }

        if (seleccionados.isEmpty()) {
            tvServiciosSeleccionados.text = "Seleccionar servicios"
            tvServiciosSeleccionados.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            tvServiciosSeleccionados.text = seleccionados.joinToString(", ")
            tvServiciosSeleccionados.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun getServiciosString(): String {
        val seleccionados = mutableListOf<String>()
        for (i in serviciosDisponibles.indices) {
            if (serviciosSeleccionados[i]) {
                seleccionados.add(serviciosDisponibles[i])
            }
        }
        return seleccionados.joinToString(",")
    }

    private fun createRoom() {
        val uid = auth.currentUser?.uid

        if (uid.isNullOrEmpty()) {
            showWarningDialog("Inicio de Sesión", "Debes iniciar sesión")
            return
        }

        val nombre = etNombre.text.toString().trim()
        val precioStr = etPrecio.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()
        val capacidadStr = etCapacidad.text.toString().trim()
        val serviciosStr = getServiciosString()

        // Validación de nombre
        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es requerido"
            etNombre.requestFocus()
            showWarningDialog("Campo Requerido", "El nombre es requerido")
            return
        }
        if (nombre.length < 5) {
            etNombre.error = "El nombre debe tener al menos 5 caracteres"
            etNombre.requestFocus()
            showWarningDialog("Nombre muy corto", "El nombre debe tener al menos 5 caracteres")
            return
        }
        if (nombre.length > MAX_NOMBRE) {
            etNombre.error = "El nombre no puede exceder $MAX_NOMBRE caracteres"
            etNombre.requestFocus()
            showWarningDialog("Nombre muy largo", "El nombre no puede exceder $MAX_NOMBRE caracteres")
            return
        }

        // Validación de precio
        if (precioStr.isEmpty()) {
            etPrecio.error = "El precio es requerido"
            etPrecio.requestFocus()
            showWarningDialog("Campo Requerido", "El precio es requerido")
            return
        }

        val precio = precioStr.toDoubleOrNull()
        if (precio == null || precio <= 0) {
            etPrecio.error = "El precio debe ser mayor a 0"
            etPrecio.requestFocus()
            showWarningDialog("Precio Inválido", "El precio debe ser mayor a 0")
            return
        }
        if (precio > 999999999) {
            etPrecio.error = "El precio es demasiado alto"
            etPrecio.requestFocus()
            showWarningDialog("Precio Inválido", "El precio ingresado es demasiado alto")
            return
        }

        // Validación de descripción
        if (descripcion.length > MAX_DESCRIPCION) {
            etDescripcion.error = "La descripción no puede exceder $MAX_DESCRIPCION caracteres"
            etDescripcion.requestFocus()
            showWarningDialog("Descripción muy larga", "La descripción no puede exceder $MAX_DESCRIPCION caracteres")
            return
        }

        // Validación de capacidad
        if (capacidadStr.isEmpty()) {
            etCapacidad.error = "La capacidad es requerida"
            etCapacidad.requestFocus()
            showWarningDialog("Campo Requerido", "La capacidad es requerida")
            return
        }

        val capacidad = capacidadStr.toIntOrNull()
        if (capacidad == null || capacidad < 1) {
            etCapacidad.error = "La capacidad debe ser al menos 1"
            etCapacidad.requestFocus()
            showWarningDialog("Capacidad Inválida", "La capacidad debe ser al menos 1")
            return
        }
        if (capacidad > 99) {
            etCapacidad.error = "La capacidad máxima es 4 personas"
            etCapacidad.requestFocus()
            showWarningDialog("Capacidad Inválida", "La capacidad máxima es 4 personas")
            return
        }

        // Validación de servicios
        if (serviciosStr.isEmpty()) {
            showWarningDialog("Servicios", "Selecciona al menos un servicio")
            return
        }

        // Validación de imágenes
        if (selectedImageUris.isEmpty()) {
            showWarningDialog("Imágenes", "Debes agregar al menos 1 imagen del cuarto")
            return
        }

        val ubicacion = currentLocation?.let { "${it.latitude},${it.longitude}" } ?: "20.0910,-98.7624"

        lifecycleScope.launch {
            try {
                showProgressDialog("Subiendo cuarto...")
                val nombreBody = nombre.toRequestBody("text/plain".toMediaTypeOrNull())
                val precioBody = precio.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionBody = (descripcion.ifEmpty { "Sin descripción" })
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val capacidadBody = capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val disponibleBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())
                val serviciosBody = serviciosStr.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())
                val ubicacionBody = ubicacion.toRequestBody("text/plain".toMediaTypeOrNull())

                val imageParts = prepareImageParts(selectedImageUris)

                Log.d(TAG, "Enviando ${imageParts.size} imagen(es)")

                val response = roomApiService.createRoom(
                    nombre = nombreBody,
                    precio = precioBody,
                    descripcion = descripcionBody,
                    capacidad = capacidadBody,
                    disponible = disponibleBody,
                    servicios = serviciosBody,
                    userId = userIdBody,
                    ubicacion = ubicacionBody,
                    imagenes = imageParts
                )

                dismissProgressDialog()

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()
                    showSuccessDialog(
                        title = "¡Éxito!",
                        message = body?.message ?: "Cuarto creado exitosamente"
                    ) {
                        val intent = Intent(this@CreateRoomActivity, RenterDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    showErrorDialog("Error", "Error: ${response.code()}")
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e(TAG, "❌ Error de red: ${e.message}", e)
                showErrorDialog("Error de Conexión", "Error de conexión: ${e.message}")
            }
        }
    }

    private fun prepareImageParts(uris: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        uris.forEach { uri ->
            try {
                val fileName = getFileName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(
                    "NuevasImagenes",
                    fileName,
                    requestBody
                )
                parts.add(part)
            } catch (e: Exception) {
                Log.e(TAG, "Error preparando imagen: ${e.message}", e)
            }
        }
        return parts
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.let { it.substring((cut ?: 0) + 1) }
            }
        }
        return result
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        val mapView = mapFragment.view

        mapView?.setOnTouchListener { v, event ->
            val action = event.actionMasked

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollView.requestDisallowInterceptTouchEvent(true)
                    v.parent?.requestDisallowInterceptTouchEvent(true)

                    centerMarkerView.animate()
                        .translationY(-50f)
                        .setDuration(200)
                        .start()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollView.requestDisallowInterceptTouchEvent(true)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    scrollView.requestDisallowInterceptTouchEvent(true)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.pointerCount <= 1) {
                        scrollView.requestDisallowInterceptTouchEvent(false)
                        v.parent?.requestDisallowInterceptTouchEvent(false)

                        centerMarkerView.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()

                        val center = googleMap.cameraPosition.target
                        currentLocation = center
                        Log.d(TAG, "📍 Ubicación seleccionada: ${center.latitude}, ${center.longitude}")
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount > 1) {
                        scrollView.requestDisallowInterceptTouchEvent(true)
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            false
        }

        googleMap.setOnCameraIdleListener {
            val center = googleMap.cameraPosition.target
            currentLocation = center
        }

        if (hasLocationPermission()) {
            enableMyLocation()
            getCurrentLocation()
        } else {
            useDefaultLocation()
        }
    }

    private fun checkAndRequestLocationPermission() {
        when {
            hasLocationPermission() -> {
                enableMyLocation()
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableMyLocation() {
        if (!hasLocationPermission()) return
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al habilitar ubicación: ${e.message}")
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
                    Toast.makeText(this, "📍 Ubicación obtenida", Toast.LENGTH_SHORT).show()
                } else {
                    useDefaultLocation()
                }
            }.addOnFailureListener {
                useDefaultLocation()
            }
        } catch (e: SecurityException) {
            useDefaultLocation()
        }
    }

    private fun useDefaultLocation() {
        currentLocation = LatLng(20.0910, -98.7624)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 12f))
        Toast.makeText(this, "Usando ubicación predeterminada", Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de Ubicación")
            .setMessage("Esta aplicación necesita acceso a tu ubicación para mostrar cuartos cercanos.")
            .setPositiveButton("Aceptar") { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                useDefaultLocation()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso Denegado")
            .setMessage("Sin acceso a la ubicación, se usará una predeterminada.")
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
                useDefaultLocation()
            }
            .show()
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
}