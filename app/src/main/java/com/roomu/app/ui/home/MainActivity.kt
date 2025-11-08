package com.roomu.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import cn.pedant.SweetAlert.SweetAlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.R
import com.roomu.app.ui.chat.ChatsListActivity
import com.roomu.app.ui.home.adapter.RoomAdapter
import com.roomu.app.ui.home.viewmodel.MainViewModel
import com.roomu.app.ui.profile.ProfileActivity
import com.roomu.app.ui.room.AllRoomsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var searchView: SearchView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerRooms: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoRooms: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var ivLogout: ImageView

    private lateinit var placesClient: PlacesClient
    private var sessionToken: AutocompleteSessionToken? = null

    private lateinit var roomAdapter: RoomAdapter

    private val TAG = "MainActivity"
    private var googleMap: GoogleMap? = null

    // ✅ Variable para controlar que el mensaje solo aparezca una vez
    private var locationMessageShown = false

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

        // ✅ NUEVO: Verificar si el usuario es arrendatario
        lifecycleScope.launch {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid != null) {
                try {
                    val renterDoc = db.collection("renters").document(uid).get().await()

                    if (renterDoc.exists()) {
                        // Es arrendatario, redirigir a RenterDashboard
                        val intent = Intent(this@MainActivity, com.roomu.app.ui.renter.RenterDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                        return@launch
                    }
                } catch (e: Exception) {
                }
            }
        }

        // ✅ Una sola llamada a setContentView
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        initViews()
        setupSearchViewStyle()
        setupListeners()
        setupRecyclerView()
        setupBottomNavigation()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        observeViewModel()
        viewModel.loadRooms()
        checkAndRequestLocationPermission()
    }

    private fun initViews() {
        searchView = findViewById(R.id.search_view)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        ivLogout = findViewById(R.id.iv_logout) // ✅ NUEVO
        recyclerRooms = findViewById(R.id.recycler_rooms)
        progressBar = findViewById(R.id.progress_bar)
        tvNoRooms = findViewById(R.id.tv_no_rooms)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        supportActionBar?.title = "Inicio"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
    private fun setupSearchViewStyle() {
        try {
            val searchEditTextId = searchView.context.resources.getIdentifier(
                "search_src_text",
                "id",
                searchView.context.packageName
            )

            if (searchEditTextId != 0) {
                val searchEditText = searchView.findViewById<EditText>(searchEditTextId)
                searchEditText?.apply {
                    setTextColor(android.graphics.Color.BLACK)
                    setHintTextColor(android.graphics.Color.GRAY)
                    textSize = 14f
                }
            } else {
                findSearchEditText(searchView)?.apply {
                    setTextColor(android.graphics.Color.BLACK)
                    setHintTextColor(android.graphics.Color.GRAY)
                    textSize = 14f
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun findSearchEditText(view: View): EditText? {
        if (view is EditText) {
            return view
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findSearchEditText(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    // ✅ Agregar en setupListeners()
    private fun setupListeners() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchPlaceAndRooms(query)
                    searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    viewModel.searchRooms("")
                }
                return true
            }
        })

        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ✅ ACTUALIZADO: Abre ChatsListActivity en lugar de mostrar Toast
        ivNotifications.setOnClickListener {
            val intent = Intent(this, ChatsListActivity::class.java)
            startActivity(intent)
        }

        ivLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
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
    // ✅ NUEVO: Función para cerrar sesión
    private fun performLogout() {
        // Mostrar diálogo de carga
        val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)
            .setTitleText("Cerrando sesión...")
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        try {
            // Cerrar sesión en Firebase
            FirebaseAuth.getInstance().signOut()

            loadingDialog.dismissWithAnimation()

            // Mostrar mensaje de éxito
            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Sesión cerrada")
                .setContentText("Has cerrado sesión exitosamente")
                .setConfirmClickListener { dialog ->
                    dialog.dismissWithAnimation()

                    // Redirigir a LoginActivity
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

    private fun searchPlaceAndRooms(query: String) {

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
                    fetchPlaceDetails(firstPrediction.placeId, query)
                } else {
                    viewModel.searchRooms(query)

                    SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Lugar no encontrado")
                        .setContentText("No se encontró el lugar. Buscando en cuartos...")
                        .show()
                }
            }
            .addOnFailureListener { exception ->
                viewModel.searchRooms(query)
            }
    }

    private fun fetchPlaceDetails(placeId: String, originalQuery: String) {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val request = com.google.android.libraries.places.api.net.FetchPlaceRequest
            .builder(placeId, placeFields)
            .setSessionToken(sessionToken)
            .build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                place.latLng?.let { latLng ->
                    viewModel.updateLocationAndFilter(latLng.latitude, latLng.longitude)

                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, 11f)
                    )

                    SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("Ubicación encontrada")
                        .setContentText(place.name ?: "Lugar encontrado")
                        .show()
                } ?: run {
                    viewModel.searchRooms(originalQuery)
                }
            }
            .addOnFailureListener { exception ->
                viewModel.searchRooms(originalQuery)
            }
    }

    private fun setupRecyclerView() {

        recyclerRooms.layoutManager = LinearLayoutManager(this)
        recyclerRooms.setHasFixedSize(false)  // ✅ Cambiar a false
        recyclerRooms.isNestedScrollingEnabled = false  // ✅ Cambiar a false
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_rooms -> {
                    val intent = Intent(this, AllRoomsActivity::class.java)
                    intent.putExtra("allRooms", ArrayList(viewModel.getAllRooms()))
                    startActivity(intent)
                    false
                }

                R.id.nav_chat -> {
                    val intent = Intent(this, ChatsListActivity::class.java).apply {
                        putExtra("isRenter", false) // Es inquilino
                    }
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }
    }


    private suspend fun checkIfUserIsRenter(): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) return false

            val uid = currentUser.uid
            val firestore = FirebaseFirestore.getInstance()

            val renterDoc = firestore.collection("renters")
                .document(uid)
                .get()
                .await()

            renterDoc.exists()
        } catch (e: Exception) {
            Log.e("CheckRenter", "Error: ${e.message}")
            false
        }
    }

    // ✅ LÍNEA ~391-405 EN observeViewModel() - ELIMINAR O MODIFICAR:
    private fun observeViewModel() {
        lifecycleScope.launch {
            launch {
                viewModel.isLoading.collect { isLoading ->
                    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }

            launch {
                viewModel.rooms.collect { rooms ->
                    if (rooms.isEmpty()) {
                        tvNoRooms.visibility = View.VISIBLE
                        recyclerRooms.visibility = View.GONE
                    } else {
                        tvNoRooms.visibility = View.GONE
                        recyclerRooms.visibility = View.VISIBLE

                        val allRooms = viewModel.getAllRooms()

                        roomAdapter = RoomAdapter(
                            onRoomClick = { room ->
                                room.getLatLng()?.let { (lat, lng) ->
                                    googleMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f)
                                    )
                                    Toast.makeText(this@MainActivity, "📍 ${room.nombre}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            allRooms = allRooms
                        )

                        recyclerRooms.adapter = roomAdapter
                        roomAdapter.submitList(rooms)
                    }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        viewModel.initMap(googleMap)

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
        }

        val defaultLocation = LatLng(20.0910, -98.7624)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 11f))

        // ✅ No llamar a getCurrentLocation() aquí, solo habilitar el botón
        if (hasLocationPermission()) {
            enableMyLocation()
        }
    }

    private fun checkAndRequestLocationPermission() {
        when {
            hasLocationPermission() -> {
                enableMyLocation()
                getCurrentLocation() // ✅ Solo se llama una vez desde aquí
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

                    // ✅ Mostrar mensaje solo la primera vez
                    if (!locationMessageShown) {
                        locationMessageShown = true
                        SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                            .setTitleText("Ubicación obtenida")
                            .setContentText("Tu ubicación actual ha sido detectada")
                            .show()
                    }
                } else {
                    Log.w(TAG, "⚠️ Ubicación null")
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
        val defaultLat = 20.0910
        val defaultLng = -98.7624
        viewModel.updateLocationAndFilter(defaultLat, defaultLng)

        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(defaultLat, defaultLng), 11f)
        )

        // ✅ Solo mostrar si no se ha mostrado ya el mensaje de ubicación
        if (!locationMessageShown) {
            locationMessageShown = true
            SweetAlertDialog(this)
                .setTitleText("Ubicación predeterminada")
                .setContentText("Se usará la ubicación de Tula")
                .show()
        }
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

        if (hasLocationPermission() && googleMap != null) {
            enableMyLocation()
        }
        // ✅ NUEVO: Recargar cuartos cada vez que la actividad vuelve a primer plano
        viewModel.loadRooms()
    }

}