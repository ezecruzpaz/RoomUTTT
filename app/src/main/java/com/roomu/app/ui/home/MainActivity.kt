package com.roomu.app.ui.home

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import cn.pedant.SweetAlert.SweetAlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.roomu.app.R
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.chat.ChatsListActivity
import com.roomu.app.ui.home.adapter.RoomAdapter
import com.roomu.app.ui.home.viewmodel.MainViewModel
import com.roomu.app.ui.profile.ProfileActivity
import com.roomu.app.ui.room.AllRoomsActivity
import com.roomu.app.ui.room.RoomDetailActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MainViewModel by viewModels()

    // Views
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var ivLogout: ImageView
    private lateinit var recyclerRooms: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoRooms: TextView
    private lateinit var layoutNoResults: LinearLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnFilters: MaterialButton
    private lateinit var scrollActiveFilters: HorizontalScrollView
    private lateinit var chipGroupActiveFilters: ChipGroup
    private lateinit var tvRoomsCount: TextView
    private lateinit var tvClearSearch: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private var sessionToken: AutocompleteSessionToken? = null
    private lateinit var roomAdapter: RoomAdapter

    private val TAG = "MainActivity"
    private var googleMap: GoogleMap? = null
    private var locationMessageShown = false
    private var isFirstLocationAccess = true
    private var searchLoadingDialog: SweetAlertDialog? = null

    // Filtros
    private var searchQuery = ""
    private var minPrice: Double? = null
    private var maxPrice: Double? = null
    private var selectedCapacity: Int? = null
    private val selectedServices = mutableListOf<String>()

    // Lista completa de cuartos
    private var allRoomsList: List<RoomData> = emptyList()
    private var isSearchingPlace = false
    private var searchJob: Job? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableMyLocation()
            getCurrentLocation()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        initViews()
        setupListeners()
        setupRecyclerView()
        setupBottomNavigation()

        // 🔥 INICIALIZAR MAPA EN BACKGROUND DESPUÉS DE MOSTRAR LA UI
        lifecycleScope.launch {
            // Esperar 300ms para que la UI se renderice primero
            delay(300)

            withContext(Dispatchers.Main) {
                val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment
                mapFragment?.getMapAsync(this@MainActivity)
            }
        }

        observeViewModel()

        // 🔥 CARGAR UBICACIÓN Y DATOS EN BACKGROUND
        lifecycleScope.launch {
            delay(100) // Dar tiempo a la UI
            checkAndRequestLocationPermission()
        }
    }

    private fun initViews() {
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        ivLogout = findViewById(R.id.iv_logout)
        recyclerRooms = findViewById(R.id.recycler_rooms)
        progressBar = findViewById(R.id.progress_bar)
        tvNoRooms = findViewById(R.id.tv_no_rooms)
        layoutNoResults = findViewById(R.id.layout_no_results)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        etSearch = findViewById(R.id.et_search)
        btnFilters = findViewById(R.id.btn_filters)
        scrollActiveFilters = findViewById(R.id.scroll_active_filters)
        chipGroupActiveFilters = findViewById(R.id.chip_group_active_filters)
        tvRoomsCount = findViewById(R.id.tv_rooms_count)
        tvClearSearch = findViewById(R.id.tv_clear_search)

        supportActionBar?.title = "Inicio"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupListeners() {
        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ivNotifications.setOnClickListener {
            startActivity(Intent(this, com.roomu.app.ui.notifications.NotificationsActivity::class.java))
        }

        ivLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                // Cancelar búsqueda anterior
                searchJob?.cancel()

                if (query.isEmpty()) {
                    searchQuery = ""
                    isSearchingPlace = false
                    applyLocalFilters()
                    updateMapMarkers()
                    tvClearSearch.isVisible = hasActiveFilters()
                } else {
                    searchQuery = query

                    // 🔥 PASO 1: Filtrar cuartos INMEDIATAMENTE por nombre/descripción
                    val matchingRooms = allRoomsList.filter { room ->
                        room.nombre.contains(query, ignoreCase = true) ||
                                room.descripcion?.contains(query, ignoreCase = true) == true ||
                                room.ubicacion.contains(query, ignoreCase = true)
                    }

                    // Si encuentra cuartos que coinciden, mostrarlos SIN buscar lugar
                    if (matchingRooms.isNotEmpty()) {
                        Log.d(TAG, "🏠 ${matchingRooms.size} cuartos encontrados con '$query'")

                        // Aplicar filtros sobre los cuartos encontrados
                        applyLocalFilters()

                        // NO mover el mapa, solo actualizar marcadores
                        updateMapMarkers()
                        tvClearSearch.isVisible = true

                        // 🔥 NO buscar lugar si ya encontró cuartos
                        return@afterTextChanged
                    }

                    // 🔥 PASO 2: Si NO encuentra cuartos, buscar como LUGAR
                    searchJob = lifecycleScope.launch {
                        delay(800)

                        // Buscar lugar solo si tiene más de 3 caracteres y NO encontró cuartos
                        if (query.length > 3) {
                            Log.d(TAG, "🗺️ No se encontraron cuartos, buscando lugar: '$query'")
                            searchPlaceInParallel(query)
                        } else {
                            // Búsqueda muy corta sin resultados
                            applyLocalFilters()
                            updateMapMarkers()
                        }
                    }

                    tvClearSearch.isVisible = true
                }
            }
        })

        tvClearSearch.setOnClickListener {
            clearAllFilters()
        }

        btnFilters.setOnClickListener {
            showFilterDialog()
        }
    }

    // Buscar lugar en paralelo (sin bloquear búsqueda de cuartos)
    private fun searchPlaceInParallel(query: String) {
        sessionToken = AutocompleteSessionToken.newInstance()

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions

                if (predictions.isNotEmpty()) {
                    val firstPrediction = predictions[0]
                    Log.d(TAG, "🗺️ Lugar encontrado: ${firstPrediction.getPrimaryText(null)}")

                    // Mostrar diálogo preguntando si quiere ir a ese lugar
                    SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                        .setTitleText("¿Buscar cuartos aquí?")
                        .setContentText(firstPrediction.getFullText(null).toString())
                        .setConfirmText("Sí, buscar")
                        .setCancelText("No")
                        .setConfirmClickListener { dialog ->
                            dialog.dismissWithAnimation()
                            showSearchLoading()
                            fetchPlaceDetailsAndNavigate(firstPrediction.placeId, query)
                        }
                        .setCancelClickListener {
                            it.dismissWithAnimation()
                            // Si cancela, volver a filtros normales
                            applyLocalFilters()
                            updateMapMarkers()
                        }
                        .show()
                } else {
                    Log.d(TAG, "⚠️ No se encontró lugar ni cuartos para '$query'")

                    // No encontró nada, mostrar sin resultados
                    applyLocalFilters()
                    updateMapMarkers()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error buscando lugar: ${exception.message}")

                // Si falla búsqueda de lugar, mantener filtros normales
                applyLocalFilters()
                updateMapMarkers()
            }
    }

    private fun fetchPlaceDetailsAndNavigate(placeId: String, originalQuery: String) {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val request = com.google.android.libraries.places.api.net.FetchPlaceRequest
            .builder(placeId, placeFields)
            .setSessionToken(sessionToken)
            .build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                dismissSearchLoading()

                val place = response.place
                place.latLng?.let { latLng ->
                    isSearchingPlace = true

                    // Limpiar búsqueda de texto para no interferir
                    searchQuery = ""
                    etSearch.setText("")

                    // 🔥 Actualizar ubicación y filtrar cuartos en ese lugar
                    viewModel.updateLocationAndFilter(latLng.latitude, latLng.longitude)

                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, 11f)
                    )

                    SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("Ubicación encontrada")
                        .setContentText("Mostrando cuartos cerca de ${place.name ?: "este lugar"}")
                        .show()
                }
            }
            .addOnFailureListener { exception ->
                dismissSearchLoading()
                Log.e(TAG, "Error obteniendo detalles: ${exception.message}")

                SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("Error")
                    .setContentText("No se pudo obtener la ubicación")
                    .show()
            }
    }

    private fun showFilterDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_filter_rooms)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // Views del diálogo
        val etMinPrice = dialog.findViewById<TextInputEditText>(R.id.et_min_price)
        val etMaxPrice = dialog.findViewById<TextInputEditText>(R.id.et_max_price)
        val chipGroupCapacity = dialog.findViewById<ChipGroup>(R.id.chip_group_capacity)
        val chipGroupServices = dialog.findViewById<ChipGroup>(R.id.chip_group_services)
        val tvClearAll = dialog.findViewById<TextView>(R.id.tv_clear_all_filters)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_filter)
        val btnApply = dialog.findViewById<Button>(R.id.btn_apply_filter)

        // Cargar valores actuales
        etMinPrice.setText(minPrice?.toString() ?: "")
        etMaxPrice.setText(maxPrice?.toString() ?: "")

        // Capacidad
        chipGroupCapacity.check(
            when (selectedCapacity) {
                null -> R.id.chip_capacity_all
                1 -> R.id.chip_capacity_1
                2 -> R.id.chip_capacity_2
                3 -> R.id.chip_capacity_3
                else -> R.id.chip_capacity_4
            }
        )

        // Servicios
        val serviceChipMap = mapOf(
            R.id.chip_service_wifi to "Wi-Fi",
            R.id.chip_service_kitchen to "Cocina",
            R.id.chip_service_bathroom to "Baño privado",
            R.id.chip_service_washing to "Lavadora",
            R.id.chip_service_furniture to "Mobiliario",
            R.id.chip_service_utilities to "Servicios incluidos"
        )

        serviceChipMap.forEach { (chipId, service) ->
            dialog.findViewById<Chip>(chipId)?.isChecked = selectedServices.contains(service)
        }

        tvClearAll.setOnClickListener {
            etMinPrice.text?.clear()
            etMaxPrice.text?.clear()
            chipGroupCapacity.check(R.id.chip_capacity_all)
            chipGroupServices.clearCheck()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            minPrice = etMinPrice.text.toString().toDoubleOrNull()
            maxPrice = etMaxPrice.text.toString().toDoubleOrNull()

            selectedCapacity = when (chipGroupCapacity.checkedChipId) {
                R.id.chip_capacity_1 -> 1
                R.id.chip_capacity_2 -> 2
                R.id.chip_capacity_3 -> 3
                R.id.chip_capacity_4 -> 4
                else -> null
            }

            selectedServices.clear()
            serviceChipMap.forEach { (chipId, service) ->
                if (dialog.findViewById<Chip>(chipId)?.isChecked == true) {
                    selectedServices.add(service)
                }
            }

            applyLocalFilters()
            updateMapMarkers()
            updateActiveFiltersChips()
            dialog.dismiss()

            tvClearSearch.isVisible = hasActiveFilters() || searchQuery.isNotEmpty()
        }

        dialog.show()
    }

    private fun applyLocalFilters() {
        val filtered = allRoomsList.filter { room ->
            room.matchesFilters(
                searchQuery = searchQuery,
                minPrice = minPrice,
                maxPrice = maxPrice,
                minCapacity = selectedCapacity,
                maxCapacity = if (selectedCapacity != null && selectedCapacity!! >= 4) null else selectedCapacity,
                selectedServices = selectedServices
            )
        }

        roomAdapter.submitList(filtered)
        updateRoomsCount(filtered.size)

        recyclerRooms.isVisible = filtered.isNotEmpty()
        layoutNoResults.isVisible = filtered.isEmpty()
        tvNoRooms.isVisible = false

        Log.d(TAG, "🔍 Filtros aplicados: ${filtered.size} de ${allRoomsList.size} cuartos")
    }

    private fun updateMapMarkers() {
        googleMap?.let { map ->
            // Obtener los cuartos filtrados actuales
            val filtered = allRoomsList.filter { room ->
                room.matchesFilters(
                    searchQuery = searchQuery,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    minCapacity = selectedCapacity,
                    maxCapacity = if (selectedCapacity != null && selectedCapacity!! >= 4) null else selectedCapacity,
                    selectedServices = selectedServices
                )
            }

            // Actualizar marcadores en el mapa usando el ViewModel
            viewModel.updateMapMarkersWithFilteredRooms(filtered)

            // Si hay resultados filtrados, ajustar la cámara para mostrarlos todos
            if (filtered.isNotEmpty()) {
                val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
                var hasValidLocation = false

                filtered.forEach { room ->
                    room.getLatLng()?.let { (lat, lng) ->
                        bounds.include(LatLng(lat, lng))
                        hasValidLocation = true
                    }
                }

                if (hasValidLocation) {
                    try {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds.build(),
                                100 // padding
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error ajustando cámara: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "🗺️ Mapa actualizado con ${filtered.size} cuartos")
        }
    }

    private fun updateActiveFiltersChips() {
        chipGroupActiveFilters.removeAllViews()
        val activeFilters = mutableListOf<Pair<String, () -> Unit>>()

        // Precio
        if (minPrice != null || maxPrice != null) {
            val priceText = buildString {
                append("Precio: ")
                if (minPrice != null) append("$${minPrice?.toInt()}")
                if (minPrice != null && maxPrice != null) append(" - ")
                if (maxPrice != null) append("$${maxPrice?.toInt()}")
            }
            activeFilters.add(priceText to {
                minPrice = null
                maxPrice = null
                applyLocalFilters()
                updateMapMarkers()
                updateActiveFiltersChips()
            })
        }

        // Capacidad
        if (selectedCapacity != null) {
            val capacityText = if (selectedCapacity!! >= 4) "4+ personas" else "$selectedCapacity persona${if (selectedCapacity!! > 1) "s" else ""}"
            activeFilters.add(capacityText to {
                selectedCapacity = null
                applyLocalFilters()
                updateMapMarkers()
                updateActiveFiltersChips()
            })
        }

        // Servicios
        selectedServices.forEach { service ->
            activeFilters.add(service to {
                selectedServices.remove(service)
                applyLocalFilters()
                updateMapMarkers()
                updateActiveFiltersChips()
            })
        }

        val nightModeFlags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemInDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        activeFilters.forEach { (text, onClose) ->
            val chip = Chip(this).apply {
                this.text = text
                isCloseIconVisible = true
                setOnCloseIconClickListener { onClose() }

                if (isSystemInDarkMode) {
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                    )
                    setTextColor(android.graphics.Color.BLACK)
                    closeIconTint = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.BLACK
                    )
                } else {
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.BLACK
                    )
                    setTextColor(android.graphics.Color.WHITE)
                    closeIconTint = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                    )
                }

                chipStrokeWidth = 0f
            }
            chipGroupActiveFilters.addView(chip)
        }

        scrollActiveFilters.isVisible = activeFilters.isNotEmpty()
    }

    private fun clearAllFilters() {
        searchQuery = ""
        minPrice = null
        maxPrice = null
        selectedCapacity = null
        selectedServices.clear()
        isSearchingPlace = false

        etSearch.text?.clear()
        applyLocalFilters()
        updateMapMarkers()
        updateActiveFiltersChips()
        tvClearSearch.isVisible = false

        // Restaurar vista del mapa a ubicación actual o predeterminada
        viewModel.currentLocation.value?.let { location ->
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(location, 11f)
            )
        }

        Log.d(TAG, "🧹 Todos los filtros limpiados")
    }

    private fun hasActiveFilters(): Boolean =
        minPrice != null || maxPrice != null || selectedCapacity != null || selectedServices.isNotEmpty()

    private fun updateRoomsCount(count: Int) {
        tvRoomsCount.text = "$count cuarto${if (count != 1) "s" else ""} ${if (count != 1) "encontrados" else "encontrado"}"
    }

    private fun showLogoutConfirmationDialog() {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("Cerrar Sesión")
            .setContentText("¿Estás seguro de que deseas cerrar sesión?")
            .setConfirmText("Sí, cerrar")
            .setCancelText("Cancelar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                performLogout()
            }
            .setCancelClickListener { dialog ->
                dialog.dismissWithAnimation()
            }
            .show()
    }

    private fun performLogout() {
        val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)
            .setTitleText("Cerrando sesión...")
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        try {
            FirebaseAuth.getInstance().signOut()
            isFirstLocationAccess = true
            locationMessageShown = false

            loadingDialog.dismissWithAnimation()

            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Sesión cerrada")
                .setContentText("Has cerrado sesión exitosamente")
                .setConfirmClickListener { dialog ->
                    dialog.dismissWithAnimation()

                    val intent = Intent(this, com.roomu.app.ui.auth.LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()

        } catch (e: Exception) {
            loadingDialog.dismissWithAnimation()

            SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                .setTitleText("Error")
                .setContentText("No se pudo cerrar sesión: ${e.message}")
                .show()
        }
    }

    private fun showSearchLoading() {
        dismissSearchLoading()
        searchLoadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)
            .setTitleText("Buscando ubicación...")
            .setContentText("Por favor espera")
        searchLoadingDialog?.setCancelable(false)
        searchLoadingDialog?.show()
    }

    private fun dismissSearchLoading() {
        searchLoadingDialog?.dismissWithAnimation()
        searchLoadingDialog = null
    }

    private fun setupRecyclerView() {
        recyclerRooms.layoutManager = LinearLayoutManager(this)
        recyclerRooms.setHasFixedSize(false)
        recyclerRooms.isNestedScrollingEnabled = false
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_rooms -> {
                    // 🔥 OBTENER LOS CUARTOS ACTUALMENTE FILTRADOS
                    val currentFilteredRooms = allRoomsList.filter { room ->
                        room.matchesFilters(
                            searchQuery = searchQuery,
                            minPrice = minPrice,
                            maxPrice = maxPrice,
                            minCapacity = selectedCapacity,
                            maxCapacity = if (selectedCapacity != null && selectedCapacity!! >= 4) null else selectedCapacity,
                            selectedServices = selectedServices
                        )
                    }

                    val intent = Intent(this, AllRoomsActivity::class.java)
                    // 🔥 PASAR LOS CUARTOS FILTRADOS, NO TODOS
                    intent.putExtra("allRooms", ArrayList(currentFilteredRooms))
                    startActivity(intent)
                    false
                }

                R.id.nav_chat -> {
                    val intent = Intent(this, ChatsListActivity::class.java).apply {
                        putExtra("isRenter", false)
                    }
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            launch {
                viewModel.isLoading.collect { isLoading ->
                    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }

            launch {
                viewModel.rooms.collect { rooms ->
                    allRoomsList = viewModel.getAllRooms()

                    if (allRoomsList.isEmpty()) {
                        tvNoRooms.visibility = View.VISIBLE
                        recyclerRooms.visibility = View.GONE
                        layoutNoResults.visibility = View.GONE
                    } else {
                        tvNoRooms.visibility = View.GONE

                        roomAdapter = RoomAdapter(
                            onRoomClick = { room ->
                                val intent = Intent(this@MainActivity, RoomDetailActivity::class.java)
                                intent.putExtra("room_id", room.id)
                                intent.putExtra("allRooms", ArrayList(allRoomsList))
                                startActivity(intent)

                                room.getLatLng()?.let { (lat, lng) ->
                                    googleMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f)
                                    )
                                }
                            },
                            allRooms = allRoomsList
                        )

                        recyclerRooms.adapter = roomAdapter
                        applyLocalFilters()
                        updateMapMarkers()
                    }
                }
            }
        }
    }

    // 🔥 OPTIMIZAR onMapReady - No hacer operaciones pesadas
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // 🔥 Configuración básica primero
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
        }

        // 🔥 Inicializar en el ViewModel (sin await)
        viewModel.initMap(googleMap)

        val defaultLocation = LatLng(20.0910, -98.7624)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 11f))

        if (hasLocationPermission()) {
            enableMyLocation()
        }

        Log.d(TAG, "✅ Mapa inicializado correctamente")
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
            Log.e(TAG, "Error habilitando ubicación: ${e.message}")
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModel.onLocationPermissionGranted(location.latitude, location.longitude)
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            11f
                        )
                    )

                    if (isFirstLocationAccess && !locationMessageShown) {
                        locationMessageShown = true
                        SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                            .setTitleText("Ubicación obtenida")
                            .setContentText("Tu ubicación actual ha sido detectada")
                            .show()
                    }

                    viewModel.loadRooms()
                } else {
                    Log.w(TAG, "⚠️ Ubicación null")
                    useDefaultLocation()
                }
            }.addOnFailureListener {
                Log.e(TAG, "Error obteniendo ubicación: ${it.message}")
                useDefaultLocation()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad: ${e.message}")
            useDefaultLocation()
        }
    }

    private fun useDefaultLocation() {
        val defaultLat = 20.0910
        val defaultLng = -98.7624
        viewModel.updateLocationAndFilter(defaultLat, defaultLng)

        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(defaultLat, defaultLng), 11f)
        )

        if (isFirstLocationAccess && !locationMessageShown) {
            locationMessageShown = true
            SweetAlertDialog(this)
                .setTitleText("Ubicación predeterminada")
                .setContentText("Se usará la ubicación de Tula")
                .show()
        }

        viewModel.loadRooms()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de Ubicación")
            .setMessage("Para mostrar cuartos cercanos necesitamos tu ubicación.")
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
            .setMessage("Se usará ubicación predeterminada.")
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
                useDefaultLocation()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_home

        isFirstLocationAccess = false

        if (hasLocationPermission() && googleMap != null) {
            enableMyLocation()
        }

        if (viewModel.currentLocation.value != null) {
            viewModel.loadRooms()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissSearchLoading()
        searchJob?.cancel()
    }
}