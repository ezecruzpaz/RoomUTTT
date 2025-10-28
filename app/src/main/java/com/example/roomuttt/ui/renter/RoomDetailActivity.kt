package com.example.roomuttt.ui.room

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.roomuttt.R
import com.example.roomuttt.domain.model.RoomData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class RoomDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "RoomDetailActivity"
    private var room: RoomData? = null
    private var googleMap: GoogleMap? = null

    // Views
    private lateinit var ivBack: ImageView
    private lateinit var imageViewPager: ViewPager2
    private lateinit var tabIndicator: TabLayout
    private lateinit var tvRoomName: TextView
    private lateinit var tvRoomPrice: TextView
    private lateinit var tvRoomAddress: TextView
    private lateinit var tvDescription: TextView
    private lateinit var servicesContainer: LinearLayout
    private lateinit var btnContactContainer: LinearLayout
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_detail)

        val roomId = intent.getStringExtra("room_id")
        val allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData>

        if (roomId == null || allRooms == null) {
            Toast.makeText(this, "Error: No se encontró el cuarto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        room = allRooms.find { it.id == roomId }

        if (room == null) {
            Toast.makeText(this, "Error: Cuarto no encontrado", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ No se encontró el cuarto con ID: $roomId")
            finish()
            return
        }

        Log.d(TAG, "✅ Cuarto encontrado: ${room?.nombre}")

        initViews()
        setupMap()
        setupData()
        setupListeners()
        setupBottomNavigation()
    }

    private fun initViews() {
        ivBack = findViewById(R.id.iv_back)
        imageViewPager = findViewById(R.id.image_viewpager)
        tabIndicator = findViewById(R.id.tab_indicator)
        tvRoomName = findViewById(R.id.tv_room_name)
        tvRoomPrice = findViewById(R.id.tv_room_price)
        tvRoomAddress = findViewById(R.id.tv_room_address)
        tvDescription = findViewById(R.id.tv_description)
        servicesContainer = findViewById(R.id.services_container)
        btnContactContainer = findViewById(R.id.btn_contact_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_container) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.map_container, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)
    }

    private fun setupData() {
        room?.let { roomData ->
            tvRoomName.text = roomData.nombre
            tvRoomPrice.text = "$${roomData.precio} MXN / mes"

            // ✅ Convertir coordenadas a dirección legible
            tvRoomAddress.text = getReadableAddress(roomData.ubicacion)

            tvDescription.text = roomData.descripcion ?: "Sin descripción disponible"

            setupImageGallery(roomData.imagenes)
            setupServices(roomData)
        }
    }

    // ✅ Función mejorada para convertir coordenadas a dirección
    private fun getReadableAddress(coordinates: String): String {
        return try {
            val parts = coordinates.split(",").map { it.trim() }
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()

                if (lat != null && lng != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lng, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val addressText = buildString {
                            address.thoroughfare?.let { append(it) }
                            address.subThoroughfare?.let {
                                if (isNotEmpty()) append(" ")
                                append(it)
                            }
                            address.locality?.let {
                                if (isNotEmpty()) append(", ")
                                append(it)
                            }
                            address.adminArea?.let {
                                if (isNotEmpty()) append(", ")
                                append(it)
                            }
                        }

                        return if (addressText.isNotEmpty()) {
                            addressText
                        } else {
                            "Tula de Allende, Hidalgo"
                        }
                    }
                }
            }
            "Tula de Allende, Hidalgo"
        } catch (e: Exception) {
            Log.e(TAG, "Error al convertir coordenadas: ${e.message}")
            "Tula de Allende, Hidalgo"
        }
    }

    private fun setupImageGallery(images: List<String>?) {
        val imageList = images?.takeIf { it.isNotEmpty() } ?: listOf(
            "https://via.placeholder.com/400x250/CCCCCC/000000?text=Sin+Imagen"
        )

        val adapter = ImagePagerAdapter(imageList)
        imageViewPager.adapter = adapter

        TabLayoutMediator(tabIndicator, imageViewPager) { _, _ -> }.attach()

        Log.d(TAG, "✅ Galería configurada con ${imageList.size} imágenes")
    }

    private fun setupServices(roomData: RoomData) {
        servicesContainer.removeAllViews()

        val allServicesText = roomData.servicios.firstOrNull() ?: ""
        val servicesList = allServicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val services = mutableListOf<Pair<String, Int>>()

        servicesList.forEach { servicio ->
            when {
                servicio.contains("wifi", true) || servicio.contains("internet", true) ->
                    services.add("Wifi" to R.drawable.ic_wifi)
                servicio.contains("estacionamiento", true) || servicio.contains("parking", true) ->
                    services.add("Estacionamiento" to R.drawable.ic_parking)
                servicio.contains("cocina", true) ->
                    services.add("Cocina" to R.drawable.ic_kitchen)
                servicio.contains("baño", true) ->
                    services.add("Baño privado" to R.drawable.ic_bathroom)
                servicio.contains("aire acondicionado", true) || servicio.contains("clima", true) ->
                    services.add("Aire Acondicionado" to R.drawable.ic_wifi)
                servicio.contains("tv", true) || servicio.contains("televisión", true) ->
                    services.add("TV" to R.drawable.ic_wifi)
                servicio.contains("lavadora", true) ->
                    services.add("Lavadora" to R.drawable.ic_wifi)
                servicio.contains("agua caliente", true) ->
                    services.add("Agua Caliente" to R.drawable.ic_wifi)
                servicio.contains("seguridad", true) ->
                    services.add("Seguridad 24/7" to R.drawable.ic_wifi)
            }
        }

        if (services.isEmpty()) {
            services.add("Sin servicios" to R.drawable.ic_wifi)
        }

        Log.d(TAG, "✅ ${services.size} servicios encontrados")

        // ✅ Crear chips con TAMAÑO FIJO en filas de 3
        var currentRow: LinearLayout? = null
        services.forEachIndexed { index, (name, icon) ->
            if (index % 3 == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8.dpToPx()
                    }
                }
                servicesContainer.addView(currentRow)
            }

            val serviceChip = LayoutInflater.from(this)
                .inflate(R.layout.item_service_chip, currentRow, false) as LinearLayout

            val iconView = serviceChip.findViewById<ImageView>(R.id.iv_service_icon)
            val textView = serviceChip.findViewById<TextView>(R.id.tv_service_name)

            iconView?.setImageResource(icon)
            textView?.text = name

            // ✅ TAMAÑO FIJO: Todos los chips tienen el mismo ancho y alto
            serviceChip.layoutParams = LinearLayout.LayoutParams(
                0,
                55.dpToPx(), // ✅ Altura muy reducida a 55dp
                1f
            ).apply {
                if (index % 3 != 2) {
                    marginEnd = 6.dpToPx()
                }
            }

            currentRow?.addView(serviceChip)
        }
    }

    private fun setupListeners() {
        ivBack.setOnClickListener {
            finish()
        }

        btnContactContainer.setOnClickListener {
            room?.let { roomData ->
                val phoneNumber = "5512345678"
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()
                    true
                }
                R.id.nav_rooms -> {
                    Toast.makeText(this, "Cuartos", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_map -> {
                    Toast.makeText(this, "Mapa", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_chat -> {
                    Toast.makeText(this, "Chat próximamente", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        room?.getLatLng()?.let { (lat, lng) ->
            val location = LatLng(lat, lng)
            googleMap?.apply {
                addMarker(
                    MarkerOptions()
                        .position(location)
                        .title(room?.nombre)
                )
                moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                uiSettings.isZoomControlsEnabled = true
            }
            Log.d(TAG, "🗺️ Mapa configurado en: $lat, $lng")
        } ?: run {
            Log.w(TAG, "⚠️ Ubicación no disponible para el cuarto")
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    inner class ImagePagerAdapter(private val images: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.iv_room_image)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_room_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            Glide.with(holder.imageView.context)
                .load(images[position])
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .centerCrop()
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = images.size
    }
}