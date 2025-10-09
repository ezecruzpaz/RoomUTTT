package com.example.roomuttt.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.domain.model.Room
import com.example.roomuttt.ui.home.viewmodel.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
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
    private var googleMap: GoogleMap? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate iniciado")

        // Inicializar vistas
        searchView = findViewById(R.id.search_view)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        cardRoom = findViewById(R.id.card_room)
        tvRoomTitle = findViewById(R.id.tv_room_title)
        tvCapacity = findViewById(R.id.tv_capacity)
        btnReserve = findViewById(R.id.btn_reserve)

        // Configurar Toolbar
        supportActionBar?.title = "Inicio"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // Configurar SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchRooms(query ?: "")
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // Icono de Perfil
        ivProfile.setOnClickListener {
            Toast.makeText(this, "Ir a Perfil", Toast.LENGTH_SHORT).show()
        }

        // Icono de Notificaciones
        ivNotifications.setOnClickListener {
            Toast.makeText(this, "Ver Notificaciones", Toast.LENGTH_SHORT).show()
        }

        // Botón Reservar
        btnReserve.setOnClickListener {
            Toast.makeText(this, "Reservar Sala", Toast.LENGTH_SHORT).show()
        }

        // Inicializar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Solicitar permisos de ubicación
        requestLocationPermission()

        // Observa datos del ViewModel
        lifecycleScope.launch {
            viewModel.rooms.collect { rooms ->
                if (rooms.isNotEmpty()) {
                    val room = rooms.first()
                    tvRoomTitle.text = room.title
                    tvCapacity.text = "Capacidad: ${room.capacity} personas"
                }
            }
        }

        // Carga inicial de cuartos
        viewModel.loadRooms()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "Mapa listo - Inicializando")
        this.googleMap = googleMap
        viewModel.initMap(googleMap)

        // Verifica permisos ANTES de habilitar "mi ubicación" (resuelve warnings)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            Log.d(TAG, "Ubicación habilitada en mapa")
        } else {
            Log.w(TAG, "Permiso de ubicación no concedido - No se habilita 'mi ubicación'")
            // Opcional: Muestra Toast
            Toast.makeText(this, "Habilita ubicación para ver tu posición", Toast.LENGTH_SHORT).show()
        }
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

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Solicitando permiso de ubicación")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "Permiso de ubicación ya concedido")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permiso de ubicación concedido - Habilitando mi ubicación")
                viewModel.onLocationPermissionGranted()
                // Habilita en mapa si ya cargó
                googleMap?.isMyLocationEnabled = true
            } else {
                Log.w(TAG, "Permiso de ubicación denegado")
                Toast.makeText(this, "Permiso de ubicación requerido para el mapa", Toast.LENGTH_SHORT).show()
            }
        }
    }
}