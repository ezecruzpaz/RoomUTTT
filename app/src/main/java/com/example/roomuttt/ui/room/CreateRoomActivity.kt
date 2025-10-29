package com.example.roomuttt.ui.room

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.data.api.RoomApiService
import com.example.roomuttt.ui.renter.RenterDashboardActivity
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
import com.example.roomuttt.domain.model.RoomData

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

    private val serviciosDisponibles = arrayOf(
        "Internet",
        "Aire Acondicionado",
        "Cocina",
        "Estacionamiento",
        "TV",
        "Lavadora",
        "Agua Caliente",
        "Seguridad 24/7"
    )

    private val serviciosSeleccionados = BooleanArray(serviciosDisponibles.size)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris)
            ivPreview.setImageURI(uris[0])
            tvImageCount.text = "${uris.size} imagen(es) seleccionada(s)"
            Log.d(TAG, "Imágenes seleccionadas: ${uris.size}")
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
        btnAddImage = findViewById(R.id.btn_add_image)
        ivPreview = findViewById(R.id.iv_preview)
        tvImageCount = findViewById(R.id.tv_image_count)
        btnCreate = findViewById(R.id.btn_create)
        centerMarkerView = findViewById(R.id.center_marker)
        scrollView = findViewById(R.id.scroll_view)
        bottomNavigation = findViewById(R.id.bottom_nav)
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
    }

    private fun setupBottomNavigation() {
        // No seleccionar ningún ítem por defecto en esta pantalla
        bottomNavigation.menu.findItem(R.id.nav_home)?.isChecked = false
        bottomNavigation.menu.findItem(R.id.nav_rooms)?.isChecked = false
        bottomNavigation.menu.findItem(R.id.nav_chat)?.isChecked = false

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Ir a RenterDashboardActivity
                    val intent = Intent(this, RenterDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }

                R.id.nav_rooms -> {
                    // Navegar a AllRoomsActivity con los cuartos del arrendatario
                    val intent = Intent(this, AllRoomsActivity::class.java)

                    // Pasar los cuartos del arrendatario actual
                    val currentRooms = ArrayList<RoomData>()
                    currentRooms.addAll(allRooms)
                    intent.putExtra("allRooms", currentRooms)

                    // ✅ Flag para indicar que son cuartos del arrendatario
                    intent.putExtra("isRenterView", true)

                    // ✅ NUEVO: Indicar que venimos desde RenterDashboard
                    intent.putExtra("fromRenterDashboard", true)

                    startActivity(intent)
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
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecciona Servicios")

        builder.setMultiChoiceItems(
            serviciosDisponibles,
            serviciosSeleccionados
        ) { _, which, isChecked ->
            serviciosSeleccionados[which] = isChecked
        }

        builder.setPositiveButton("Aceptar") { dialog, _ ->
            updateServiciosText()
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
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

        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es requerido"
            etNombre.requestFocus()
            showWarningDialog("Campo Requerido", "El nombre es requerido")
            return
        }

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

        if (serviciosStr.isEmpty()) {
            showWarningDialog("Servicios", "Selecciona al menos un servicio")
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

                val imageParts = if (selectedImageUris.isNotEmpty()) {
                    prepareImageParts(selectedImageUris)
                } else {
                    null
                }

                Log.d(TAG, "Enviando ${imageParts?.size ?: 0} imagen(es)")

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
                        // Redirigir al dashboard después de crear el cuarto
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