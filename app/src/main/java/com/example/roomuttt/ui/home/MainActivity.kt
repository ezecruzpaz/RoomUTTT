package com.example.roomuttt.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roomuttt.R
import com.example.roomuttt.ui.home.adapter.RoomAdapter
import com.example.roomuttt.ui.home.viewmodel.MainViewModel
import com.example.roomuttt.ui.profile.ProfileActivity
import com.example.roomuttt.ui.room.CreateRoomActivity
import com.example.roomuttt.ui.room.AllRoomsActivity
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var searchView: SearchView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerRooms: RecyclerView
    private lateinit var btnViewMore: Button // Cambiado a Button y corregido el ID
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoRooms: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 🔥 Places API
    private lateinit var placesClient: PlacesClient
    private var sessionToken: AutocompleteSessionToken? = null

    private lateinit var roomAdapter: RoomAdapter

    private val TAG = "MainActivity"
    private var googleMap: GoogleMap? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "✅ Permiso de ubicación concedido")
            enableMyLocation()
            getCurrentLocation()
        } else {
            Log.w(TAG, "⚠️ Permiso de ubicación denegado")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "📱 MainActivity iniciada")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 🔥 Inicializar Places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        initViews()
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
        recyclerRooms = findViewById(R.id.recycler_rooms)
        btnViewMore = findViewById(R.id.btn_view_more) // Corregido a btn_view_more
        progressBar = findViewById(R.id.progress_bar)
        tvNoRooms = findViewById(R.id.tv_no_rooms)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        supportActionBar?.title = "Inicio"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

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

        ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones", Toast.LENGTH_SHORT).show()
        }

        btnViewMore.setOnClickListener {
            val intent = Intent(this, AllRoomsActivity::class.java)
            intent.putExtra("allRooms", ArrayList(viewModel.getAllRooms()))
            startActivity(intent)
        }
    }

    private fun searchPlaceAndRooms(query: String) {
        Log.d(TAG, "🔍 Buscando lugar: '$query'")

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
                    Log.d(TAG, "📍 Lugar encontrado: ${firstPrediction.getFullText(null)}")

                    fetchPlaceDetails(firstPrediction.placeId, query)
                } else {
                    Log.w(TAG, "⚠️ No se encontraron lugares para '$query'")
                    viewModel.searchRooms(query)
                    Toast.makeText(
                        this,
                        "No se encontró el lugar. Buscando en cuartos...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Error buscando lugar: ${exception.message}")
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
                    Log.d(TAG, "🎯 Coordenadas obtenidas: ${latLng.latitude}, ${latLng.longitude}")
                    viewModel.updateLocationAndFilter(latLng.latitude, latLng.longitude)
                    Toast.makeText(
                        this,
                        "📍 ${place.name ?: "Lugar encontrado"}",
                        Toast.LENGTH_SHORT
                    ).show()
                } ?: run {
                    Log.w(TAG, "⚠️ El lugar no tiene coordenadas")
                    viewModel.searchRooms(originalQuery)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Error obteniendo detalles: ${exception.message}")
                viewModel.searchRooms(originalQuery)
            }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "🔧 Configurando RecyclerView...")

        roomAdapter = RoomAdapter { room ->
            room.getLatLng()?.let { (lat, lng) ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f)
                )
                Toast.makeText(this, "📍 ${room.nombre}", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerRooms.layoutManager = LinearLayoutManager(this)
        recyclerRooms.adapter = roomAdapter
        recyclerRooms.setHasFixedSize(true)
        // Asegurar que el RecyclerView no ocupe todo el espacio
        recyclerRooms.isNestedScrollingEnabled = true // Ya está en XML, pero lo confirmamos

        Log.d(TAG, "✅ RecyclerView configurado en modo VERTICAL")
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_rooms -> {
                    startActivity(Intent(this, CreateRoomActivity::class.java))
                    true
                }
                R.id.nav_reservations -> {
                    Toast.makeText(this, "📅 Reservas próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_map -> {
                    Toast.makeText(this, "🗺️ Ya estás en el mapa", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_chat -> {
                    Toast.makeText(this, "💬 Chat próximamente", Toast.LENGTH_SHORT).show()
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
                    Log.d(TAG, "📦 Recibidos ${rooms.size} cuartos para mostrar")

                    if (rooms.isEmpty()) {
                        tvNoRooms.visibility = View.VISIBLE
                        recyclerRooms.visibility = View.GONE
                        btnViewMore.visibility = View.GONE
                    } else {
                        tvNoRooms.visibility = View.GONE
                        recyclerRooms.visibility = View.VISIBLE
                        roomAdapter.submitList(rooms)
                        Log.d(TAG, "✅ Lista actualizada con ${rooms.size} cuartos")
                    }
                }
            }

            launch {
                viewModel.showViewMoreButton.collect { showButton ->
                    btnViewMore.visibility = if (showButton) View.VISIBLE else View.GONE
                    Log.d(TAG, "🔘 Botón 'Ver más': ${if (showButton) "VISIBLE" else "OCULTO"}")
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "🗺️ Mapa listo")
        this.googleMap = googleMap
        viewModel.initMap(googleMap)

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
        }

        if (hasLocationPermission()) {
            enableMyLocation()
            getCurrentLocation()
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
            Log.e(TAG, "❌ Error al habilitar ubicación: ${e.message}")
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "📍 Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                    viewModel.onLocationPermissionGranted(location.latitude, location.longitude)
                    Toast.makeText(this, "📍 Ubicación obtenida", Toast.LENGTH_SHORT).show()
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
        viewModel.updateLocationAndFilter(20.0910, -98.7624)
        Toast.makeText(this, "📍 Ubicación predeterminada (Tula)", Toast.LENGTH_SHORT).show()
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
        if (hasLocationPermission() && googleMap != null) {
            enableMyLocation()
        }
    }
}