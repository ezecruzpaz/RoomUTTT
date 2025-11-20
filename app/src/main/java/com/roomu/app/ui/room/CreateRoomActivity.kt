package com.roomu.app.ui.room

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.core.content.FileProvider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var btnExpandMap: com.google.android.material.floatingactionbutton.FloatingActionButton

    // ✅ Variables para la cámara
    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null

    private val selectedImageUris = mutableListOf<Uri>()
    private val TAG = "CreateRoomActivity"
    private var progressDialog: SweetAlertDialog? = null
    private var allRooms = mutableListOf<RoomData>()

    // Límites de caracteres
    private val MAX_NOMBRE = 50
    private val MAX_PRECIO = 10
    private val MAX_DESCRIPCION = 300
    private val MAX_CAPACIDAD = 1
    private val MAX_IMAGES = 10
    private val MAX_PERSONAS = 4

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

    // ✅ Launcher para galería
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            handleSelectedImages(uris)
        }
    }

    // ✅ Launcher para cámara
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            // Verificar que no exceda el máximo de imágenes
            if (selectedImageUris.size >= MAX_IMAGES) {
                showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes seleccionadas")
                // Eliminar el archivo si no se va a usar
                currentPhotoFile?.delete()
            } else {
                selectedImageUris.add(currentPhotoUri!!)
                updateImagePreview()
                Log.d(TAG, "Foto capturada y agregada: ${currentPhotoUri}")
            }
        } else {
            // Si falla, eliminar el archivo temporal
            currentPhotoFile?.delete()
            Log.w(TAG, "Captura de foto cancelada o fallida")
        }
    }

    // ✅ Launcher para permisos de cámara
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permiso de cámara concedido")
            openCamera()
        } else {
            Log.w(TAG, "Permiso de cámara denegado")
            showPermissionDeniedDialog("Cámara", "Sin este permiso no se pueden tomar fotos")
        }
    }

    // ✅ Launcher para permisos de almacenamiento (READ_EXTERNAL_STORAGE)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permiso de almacenamiento concedido")
            openGallery()
        } else {
            Log.w(TAG, "Permiso de almacenamiento denegado")
            // En Android 13+ no se necesita este permiso para la galería
            openGallery()
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
            showPermissionDeniedDialog("Ubicación", "Se usará la ubicación predeterminada")
        }
    }

    private val fullscreenMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val lat = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val lng = data?.getDoubleExtra("longitude", 0.0) ?: 0.0

            if (lat != 0.0 && lng != 0.0) {
                currentLocation = LatLng(lat, lng)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 17f))



                Log.d(TAG, "📍 Ubicación desde pantalla completa: $lat, $lng")
            }
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
        btnExpandMap = findViewById(R.id.btn_expand_map)

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

        // ✅ Mostrar diálogo para elegir entre Cámara o Galería
        btnAddImage.setOnClickListener {
            showImageSourceDialog()
        }

        btnCreate.setOnClickListener {
            createRoom()
        }

        findViewById<ImageView>(R.id.iv_profile).setOnClickListener {
            startActivity(Intent(this, com.roomu.app.ui.profile.ProfileActivity::class.java))
        }

        findViewById<ImageView>(R.id.iv_notifications).setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones próximamente", Toast.LENGTH_SHORT).show()
        }

        btnExpandMap.setOnClickListener {
            val intent = Intent(this, FullscreenMapActivity::class.java)
            intent.putExtra("latitude", currentLocation?.latitude ?: 20.0910)
            intent.putExtra("longitude", currentLocation?.longitude ?: -98.7624)
            fullscreenMapLauncher.launch(intent)
        }
    }

    // ✅ Diálogo para elegir entre Cámara o Galería
    private fun showImageSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_source, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Fondo transparente para mostrar las esquinas redondeadas
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Opción: Tomar foto
        dialogView.findViewById<LinearLayout>(R.id.option_take_photo).setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }

        // Opción: Elegir de galería
        dialogView.findViewById<LinearLayout>(R.id.option_gallery).setOnClickListener {
            dialog.dismiss()
            checkStoragePermissionAndOpenGallery()
        }

        // Botón cancelar
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ✅ Verificar permiso de cámara
    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog(
                    "Cámara",
                    "Necesitamos acceso a la cámara para tomar fotos del cuarto"
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // ✅ Verificar permiso de almacenamiento (solo para Android 12 o inferior)
    private fun checkStoragePermissionAndOpenGallery() {
        // En Android 13+ (API 33+) no se necesita READ_EXTERNAL_STORAGE para la galería
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            openGallery()
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showPermissionRationaleDialog(
                        "Almacenamiento",
                        "Necesitamos acceso a tu galería para seleccionar fotos"
                    ) {
                        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    // ✅ Abrir cámara
    private fun openCamera() {
        if (selectedImageUris.size >= MAX_IMAGES) {
            showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes seleccionadas")
            return
        }

        try {
            // Crear archivo temporal para la foto
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            val photoFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )

            currentPhotoFile = photoFile

            // Obtener URI usando FileProvider
            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )

            currentPhotoUri = photoUri

            // Lanzar la cámara con la variable local
            takePictureLauncher.launch(photoUri)
            Log.d(TAG, "Cámara abierta, archivo: ${photoFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir cámara: ${e.message}", e)
        }
    }

    // ✅ Abrir galería
    private fun openGallery() {
        val remainingSlots = MAX_IMAGES - selectedImageUris.size
        if (remainingSlots <= 0) {
            showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes seleccionadas")
            return
        }

        pickImagesLauncher.launch("image/*")
    }

    // ✅ Manejar imágenes seleccionadas de la galería
    private fun handleSelectedImages(uris: List<Uri>) {
        val remainingSlots = MAX_IMAGES - selectedImageUris.size

        if (uris.size > remainingSlots) {
            showWarningDialog(
                "Límite de imágenes",
                "Solo puedes agregar $remainingSlots imagen(es) más. Se tomarán las primeras $remainingSlots."
            )
            selectedImageUris.addAll(uris.take(remainingSlots))
        } else {
            selectedImageUris.addAll(uris)
        }

        updateImagePreview()
        Log.d(TAG, "Imágenes agregadas desde galería: ${uris.size}, Total: ${selectedImageUris.size}")
    }

    // ✅ Actualizar vista previa de imágenes
    private fun updateImagePreview() {
        if (selectedImageUris.isNotEmpty()) {
            ivPreview.setImageURI(selectedImageUris.last())
            tvImageCount.text = "${selectedImageUris.size}/$MAX_IMAGES imagen(es) seleccionada(s)"
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
                    lifecycleScope.launch {
                        try {
                            val uid = auth.currentUser?.uid

                            if (uid == null) {

                                return@launch
                            }

                            val response = roomApiService.getRooms()

                            if (response.isSuccessful) {
                                val apiResponse = response.body()
                                val allRoomsList = apiResponse?.result ?: emptyList()

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

                                }
                            } else {

                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cargando cuartos: ${e.message}")

                        }
                    }
                    false
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

    private fun setupAutocomplete() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.place_autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setHint("Buscar ubicación...")

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let { latLng ->
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    Log.d(TAG, "📍 Lugar seleccionado: ${place.name}, $latLng")

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

        // Validaciones
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

        if (descripcion.length > MAX_DESCRIPCION) {
            etDescripcion.error = "La descripción no puede exceder $MAX_DESCRIPCION caracteres"
            etDescripcion.requestFocus()
            showWarningDialog("Descripción muy larga", "La descripción no puede exceder $MAX_DESCRIPCION caracteres")
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
        if (capacidad > MAX_PERSONAS) {
            etCapacidad.error = "La capacidad máxima es $MAX_PERSONAS personas"
            etCapacidad.requestFocus()
            showWarningDialog("Capacidad Inválida", "La capacidad máxima es $MAX_PERSONAS personas")
            return
        }

        if (serviciosStr.isEmpty()) {
            showWarningDialog("Servicios", "Selecciona al menos un servicio")
            return
        }

        if (selectedImageUris.isEmpty()) {
            showWarningDialog("Imágenes", "Debes agregar al menos 1 imagen del cuarto")
            return
        }

        val ubicacion = currentLocation?.let { "${it.latitude},${it.longitude}" } ?: "20.0910,-98.7624"

        // ✅ PROCESO DE CREACIÓN CON VALIDACIÓN CORRECTA
        lifecycleScope.launch {
            try {
                showProgressDialog("Subiendo cuarto...")

                // Preparar datos del cuarto
                val nombreBody = nombre.toRequestBody("text/plain".toMediaTypeOrNull())
                val precioBody = precio.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionBody = (descripcion.ifEmpty { "Sin descripción" })
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val capacidadBody = capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val disponibleBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())
                val serviciosBody = serviciosStr.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())
                val ubicacionBody = ubicacion.toRequestBody("text/plain".toMediaTypeOrNull())

                // Preparar imágenes
                val imageParts = prepareImageParts(selectedImageUris)
                Log.d(TAG, "📤 Enviando ${imageParts.size} imagen(es)")

                // Hacer la petición al servidor
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

                // ✅ VALIDACIÓN CORRECTA USANDO TU MODELO RoomResponse
                if (response.isSuccessful) {
                    val roomResponse = response.body()

                    if (roomResponse != null) {
                        Log.d(TAG, "📥 Respuesta recibida:")
                        Log.d(TAG, "   - isSuccess: ${roomResponse.isSuccess}")
                        Log.d(TAG, "   - message: ${roomResponse.message}")
                        Log.d(TAG, "   - result: ${roomResponse.result}")

                        // ✅ Verificar si el cuarto fue creado exitosamente
                        if (roomResponse.isSuccess && roomResponse.result != null) {
                            // ✅ ÉXITO - El cuarto SÍ fue creado
                            Log.d(TAG, "✅ Cuarto creado exitosamente con ID: ${roomResponse.result.id}")

                            showSuccessDialog(
                                title = "¡Éxito!",
                                message = "Cuarto creado exitosamente"
                            ) {
                                limpiarFormulario()

                                val intent = Intent(this@CreateRoomActivity, RenterDashboardActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            // ❌ ERROR - La API rechazó el cuarto (contenido inapropiado, etc.)
                            Log.w(TAG, "⚠️ El cuarto NO fue creado")
                            Log.w(TAG, "   Razón: ${roomResponse.message}")

                            showErrorDialog(
                                "Contenido No Permitido",
                                roomResponse.message ?: "El cuarto no pudo ser creado. Por favor, revisa el contenido e imágenes."
                            )
                        }
                    } else {
                        // Response body es null (muy raro)
                        Log.e(TAG, "❌ Response body es null")
                        showErrorDialog(
                            "Error",
                            "No se recibió respuesta del servidor"
                        )
                    }
                } else {
                    // ❌ Error HTTP (400, 500, etc.)
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e(TAG, "❌ Error HTTP ${response.code()}")
                    Log.e(TAG, "   Error body: $errorBody")

                    // Intentar extraer el mensaje de error si viene en JSON
                    val errorMessage = try {
                        when {
                            errorBody.contains("inapropiado", ignoreCase = true) ||
                                    errorBody.contains("inappropriate", ignoreCase = true) ->
                                "El contenido fue rechazado por contener información o imágenes inapropiadas"

                            errorBody.contains("message", ignoreCase = true) -> {
                                // Intentar parsear el JSON de error
                                val messageRegex = """"message"\s*:\s*"([^"]+)"""".toRegex()
                                messageRegex.find(errorBody)?.groupValues?.get(1)
                                    ?: "Error del servidor: ${response.code()}"
                            }

                            else -> "Error al crear el cuarto. Código: ${response.code()}"
                        }
                    } catch (e: Exception) {
                        "Error al crear el cuarto. Código: ${response.code()}"
                    }

                    showErrorDialog("Error al crear cuarto", errorMessage)
                }

            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e(TAG, "❌ Excepción al crear cuarto: ${e.message}", e)
                showErrorDialog(
                    "Error de Conexión",
                    "No se pudo conectar con el servidor. Verifica tu conexión a internet."
                )
            }
        }
    }

    // ✅ NUEVA FUNCIÓN: Limpiar formulario después de crear el cuarto
    private fun limpiarFormulario() {
        etNombre.text.clear()
        etPrecio.text.clear()
        etDescripcion.text.clear()
        etCapacidad.text.clear()
        selectedImageUris.clear()
        serviciosSeleccionados.fill(false)

        tvServiciosSeleccionados.text = "Seleccionar servicios"
        tvServiciosSeleccionados.setTextColor(getColor(android.R.color.darker_gray))
        tvImageCount.text = "0/$MAX_IMAGES imagen(es) seleccionada(s)"
    }

    // ✅ FUNCIÓN prepareImageParts optimizada (sin cambios, pero la incluyo para referencia)
    private fun prepareImageParts(uris: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        uris.forEachIndexed { index, uri ->
            try {
                val fileName = getFileName(uri) ?: "imagen_cuarto_${System.currentTimeMillis()}_$index.jpg"
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

                Log.d(TAG, "✅ Imagen preparada: $fileName (${tempFile.length() / 1024}KB)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error preparando imagen $index: ${e.message}", e)
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
                showPermissionRationaleDialog(
                    "Ubicación",
                    "Esta aplicación necesita acceso a tu ubicación para mostrar cuartos cercanos"
                ) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
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
    }

    private fun showPermissionRationaleDialog(title: String, message: String, onAccept: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permiso de $title")
            .setMessage(message)
            .setPositiveButton("Aceptar") { _, _ ->
                onAccept()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                if (title == "Ubicación") {
                    useDefaultLocation()
                }
            }
            .show()
    }

    private fun showPermissionDeniedDialog(permissionName: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permiso Denegado")
            .setMessage(message)
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
                if (permissionName == "Ubicación") {
                    useDefaultLocation()
                }
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