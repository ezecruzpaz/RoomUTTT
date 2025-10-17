package com.example.roomuttt.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.ui.home.viewmodel.MainViewModel
import com.example.roomuttt.ui.profile.ProfileActivity
import com.example.roomuttt.ui.room.CreateRoomActivity  // ← Agrega este import
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView  // ← Agrega este import
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var searchView: SearchView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var cardRoom: MaterialCardView
    private lateinit var tvRoomTitle: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var btnReserve: TextView
    private lateinit var bottomNavigation: BottomNavigationView  // ← Agrega esta línea
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val TAG = "MainActivity"

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
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate iniciado")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initViews()
        setupListeners()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        observeViewModel()
        viewModel.loadRooms()
        checkAndRequestLocationPermission()

        setupBottomNavigation()  // ← Agrega esta línea
    }

    private fun initViews() {
        searchView = findViewById(R.id.search_view)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        cardRoom = findViewById(R.id.card_room)
        tvRoomTitle = findViewById(R.id.tv_room_title)
        tvCapacity = findViewById(R.id.tv_capacity)
        btnReserve = findViewById(R.id.btn_reserve)
        bottomNavigation = findViewById(R.id.bottom_navigation)  // ← Agrega esta línea

        supportActionBar?.title = "Inicio"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupListeners() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchRooms(query ?: "")
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        ivProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        ivNotifications.setOnClickListener {
            Toast.makeText(this, "Ver Notificaciones", Toast.LENGTH_SHORT).show()
        }

        btnReserve.setOnClickListener {
            Toast.makeText(this, "Reservar Sala", Toast.LENGTH_SHORT).show()
        }
    }

    // ← AGREGA ESTE MÉTODO COMPLETO
    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Ya estás en MainActivity
                    true
                }
                R.id.nav_rooms -> {
                    // Abrir CreateRoomActivity
                    val intent = Intent(this, CreateRoomActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_reservations -> {
                    Toast.makeText(this, "Reservas próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_map -> {
                    Toast.makeText(this, "Mapa próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_chat -> {
                    Toast.makeText(this, "Chat próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.rooms.collect { rooms ->
                if (rooms.isNotEmpty()) {
                    val room = rooms.first()
                    tvRoomTitle.text = room.title
                    tvCapacity.text = "Capacidad: ${room.capacity} personas"
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "Mapa listo - Inicializando")
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
        } else {
            Log.w(TAG, "Permiso de ubicación no concedido - No se habilita 'mi ubicación'")
        }
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
            viewModel.onLocationPermissionGranted()
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
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                    )
                    Log.d(TAG, "Ubicación actual: ${location.latitude}, ${location.longitude}")
                    Toast.makeText(this, "Ubicación obtenida", Toast.LENGTH_SHORT).show()
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
        val defaultLocation = LatLng(20.0910, -98.7624)
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f)
        )
        Log.d(TAG, "Usando ubicación predeterminada: UTTT")
        Toast.makeText(this, "Usando ubicación predeterminada", Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de Ubicación")
            .setMessage("Esta aplicación necesita acceso a tu ubicación para mostrar salas cercanas y ayudarte a navegar en el campus.")
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
            .setMessage("Sin acceso a la ubicación, la app mostrará una ubicación predeterminada. Puedes habilitar el permiso desde Configuración.")
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
                useDefaultLocation()
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission() && googleMap != null) {
            enableMyLocation()
        }
    }
}