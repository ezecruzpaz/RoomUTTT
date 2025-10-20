package com.example.roomuttt.ui.room

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.data.api.RoomApiService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
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
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: LatLng? = null

    private val selectedImageUris = mutableListOf<Uri>()
    private val TAG = "CreateRoomActivity"

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
            Toast.makeText(
                this,
                "${uris.size} imagen(es) seleccionada(s)",
                Toast.LENGTH_SHORT
            ).show()
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

    private fun setupAutocomplete() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.place_autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setHint("Buscar ubicación...")

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let { latLng ->
                    currentLocation = latLng
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    updateLocationMarker(latLng)
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
                Toast.makeText(
                    this@CreateRoomActivity,
                    "Error al buscar ubicación",
                    Toast.LENGTH_SHORT
                ).show()
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
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val nombre = etNombre.text.toString().trim()
        val precioStr = etPrecio.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()
        val capacidadStr = etCapacidad.text.toString().trim()
        val serviciosStr = getServiciosString()

        // Validaciones
        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es requerido"
            etNombre.requestFocus()
            return
        }

        if (precioStr.isEmpty()) {
            etPrecio.error = "El precio es requerido"
            etPrecio.requestFocus()
            return
        }

        val precio = precioStr.toDoubleOrNull()
        if (precio == null || precio <= 0) {
            etPrecio.error = "El precio debe ser mayor a 0"
            etPrecio.requestFocus()
            return
        }

        if (capacidadStr.isEmpty()) {
            etCapacidad.error = "La capacidad es requerida"
            etCapacidad.requestFocus()
            return
        }

        val capacidad = capacidadStr.toIntOrNull()
        if (capacidad == null || capacidad < 1) {
            etCapacidad.error = "La capacidad debe ser al menos 1"
            etCapacidad.requestFocus()
            return
        }

        if (serviciosStr.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un servicio", Toast.LENGTH_SHORT).show()
            return
        }

        val ubicacion = currentLocation?.let { "${it.latitude},${it.longitude}" } ?: "20.0910,-98.7624"

        lifecycleScope.launch {
            try {
                Toast.makeText(this@CreateRoomActivity, "Subiendo cuarto...", Toast.LENGTH_SHORT).show()

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

                Log.d(TAG, "Response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()
                    Log.d(TAG, "✅ Éxito: ${body?.message}")
                    Toast.makeText(
                        this@CreateRoomActivity,
                        body?.message ?: "Cuarto creado exitosamente",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                    Log.e(TAG, "❌ Error API (${response.code()}): $errorMsg")
                    Toast.makeText(
                        this@CreateRoomActivity,
                        "Error: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error de red: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(
                    this@CreateRoomActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
                Log.d(TAG, "Imagen preparada: $fileName (${tempFile.length()} bytes)")
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

    private var centerMarker: Marker? = null

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Configurar UI del mapa
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        // 🔥 LISTENER PRINCIPAL: Actualiza la ubicación cuando el usuario mueve el mapa
        googleMap.setOnCameraIdleListener {
            val center = googleMap.cameraPosition.target
            currentLocation = center
            updateLocationMarker(center)

            Log.d(TAG, "📍 Ubicación ajustada: $center")
        }

        // Listener para cuando el usuario empieza a mover el mapa
        googleMap.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                // Usuario está moviendo el mapa manualmente
                centerMarker?.remove()
            }
        }

        if (hasLocationPermission()) {
            enableMyLocation()
            getCurrentLocation()
        } else {
            useDefaultLocation()
        }
    }

    // 🎯 Método para actualizar el marcador central
    private fun updateLocationMarker(location: LatLng) {
        centerMarker?.remove()
        centerMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(location)
                .title("Ubicación seleccionada")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun checkAndRequestLocationPermission() {
        when {
            hasLocationPermission() -> {
                Log.d(TAG, "Permiso de ubicación ya concedido")
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
        if (!hasLocationPermission()) {
            Log.w(TAG, "No se puede habilitar ubicación sin permiso")
            return
        }
        try {
            googleMap?.isMyLocationEnabled = true
            Log.d(TAG, "Ubicación habilitada en mapa")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al habilitar ubicación: ${e.message}")
            Toast.makeText(this, "Error al habilitar ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No se puede obtener ubicación sin permiso")
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
                    updateLocationMarker(currentLocation!!)
                    Log.d(TAG, "Ubicación actual: ${location.latitude}, ${location.longitude}")
                    Toast.makeText(this, "📍 Ubicación obtenida", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Ubicación es null - usando ubicación predeterminada")
                    useDefaultLocation()
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener ubicación: ${e.message}")
                useDefaultLocation()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al obtener ubicación: ${e.message}")
            useDefaultLocation()
        }
    }

    private fun useDefaultLocation() {
        currentLocation = LatLng(20.0910, -98.7624) // UTTT
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 12f))
        updateLocationMarker(currentLocation!!)
        Log.d(TAG, "Usando ubicación predeterminada: UTTT")
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
}