package com.roomu.app.ui.room

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.roomu.app.R

class FullscreenMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var centerMarkerView: ImageView
    private lateinit var tvCoordinates: TextView
    private lateinit var btnConfirm: Button
    private lateinit var btnClose: ImageButton
    private var currentLocation: LatLng? = null
    private val TAG = "FullscreenMapActivity"

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableMyLocation()
            getCurrentLocation()
        } else {
            useDefaultLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_fullscreen_map)
            Log.d(TAG, "✅ Layout inflado correctamente")

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            // Obtener ubicación actual pasada desde CreateRoomActivity
            val lat = intent.getDoubleExtra("latitude", 20.0910)
            val lng = intent.getDoubleExtra("longitude", -98.7624)
            currentLocation = LatLng(lat, lng)

            Log.d(TAG, "📍 Ubicación recibida: $lat, $lng")

            initViews()
            setupListeners()

            val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment

            if (mapFragment == null) {
                Log.e(TAG, "❌ Error: MapFragment es null")
                Toast.makeText(this, "Error al cargar el mapa", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            mapFragment.getMapAsync(this)
            Log.d(TAG, "🗺️ Solicitando mapa...")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        try {
            centerMarkerView = findViewById(R.id.center_marker)
            tvCoordinates = findViewById(R.id.tv_coordinates)
            btnConfirm = findViewById(R.id.btn_confirm_location)
            btnClose = findViewById(R.id.btn_close)
            Log.d(TAG, "✅ Views inicializadas")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al inicializar views: ${e.message}", e)
            throw e
        }
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            Log.d(TAG, "❌ Cerrando sin seleccionar ubicación")
            finish()
        }

        btnConfirm.setOnClickListener {
            currentLocation?.let { location ->
                Log.d(TAG, "✅ Confirmando ubicación: ${location.latitude}, ${location.longitude}")
                val resultIntent = Intent()
                resultIntent.putExtra("latitude", location.latitude)
                resultIntent.putExtra("longitude", location.longitude)
                setResult(RESULT_OK, resultIntent)
                finish()
            } ?: run {
                Toast.makeText(this, "No hay ubicación seleccionada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        try {
            Log.d(TAG, "🗺️ Mapa listo")
            googleMap = map

            googleMap?.uiSettings?.apply {
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isMyLocationButtonEnabled = true
                isCompassEnabled = true
            }

            // Centrar en la ubicación actual
            currentLocation?.let {
                Log.d(TAG, "📍 Centrando mapa en: ${it.latitude}, ${it.longitude}")
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
                updateCoordinates(it)
            }

            // Listener para cuando el usuario mueve el mapa
            googleMap?.setOnCameraMoveStartedListener {
                centerMarkerView.animate()
                    .translationY(-50f)
                    .setDuration(200)
                    .start()
            }

            googleMap?.setOnCameraIdleListener {
                val center = googleMap?.cameraPosition?.target
                center?.let {
                    currentLocation = it
                    updateCoordinates(it)

                    centerMarkerView.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                }
            }

            if (hasLocationPermission()) {
                enableMyLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onMapReady: ${e.message}", e)
            Toast.makeText(this, "Error al configurar el mapa", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCoordinates(latLng: LatLng) {
        try {
            tvCoordinates.text = String.format(
                "Lat: %.4f, Lng: %.4f",
                latLng.latitude,
                latLng.longitude
            )
            Log.d(TAG, "📍 Nueva ubicación: ${latLng.latitude}, ${latLng.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando coordenadas: ${e.message}")
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
            Log.d(TAG, "✅ Mi ubicación habilitada")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Error al habilitar ubicación: ${e.message}")
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 17f))
                    updateCoordinates(currentLocation!!)
                    Log.d(TAG, "✅ Ubicación actual obtenida")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Error obteniendo ubicación: ${e.message}")
        }
    }

    private fun useDefaultLocation() {
        currentLocation = LatLng(20.0910, -98.7624)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 12f))
        updateCoordinates(currentLocation!!)
        Log.d(TAG, "📍 Usando ubicación predeterminada")
    }

    override fun onDestroy() {
        super.onDestroy()
        googleMap = null
        Log.d(TAG, "🧹 Activity destruida")
    }
}