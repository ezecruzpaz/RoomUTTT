package com.roomu.app.ui.room

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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.roomu.app.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.databinding.ActivityRoomDetailBinding
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.chat.ChatActivity
import com.roomu.app.ui.chat.ChatsListActivity
import com.roomu.app.ui.home.MainActivity
import com.roomu.app.ui.home.viewmodel.MainViewModel
import com.roomu.app.ui.profile.ProfileActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@AndroidEntryPoint
class RoomDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "RoomDetailActivity"
    private var room: RoomData? = null
    private var googleMap: GoogleMap? = null
    private val mainViewModel: MainViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()

    // ✅ DATA BINDING
    private lateinit var binding: ActivityRoomDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomId = intent.getStringExtra("room_id")
        val allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData>

        if (roomId == null || allRooms == null) {
            finish()
            return
        }
        room = allRooms.find { it.id == roomId }
        if (room == null) {
            finish()
            return
        }

        // ✅ OBTENER NOMBRE DEL ARRENDATARIO
        lifecycleScope.launch {
            room?.let {
                fetchRenterName(it)
            }
        }

        setupMap()
        setupData()
        setupListeners()
        setupBottomNavigation()

        // Cargar cuartos (opcional)
        lifecycleScope.launch {
            try {
                mainViewModel.loadRooms()
            } catch (e: Exception) {
            }
        }
    }

    // ✅ OBTENER NOMBRE DEL ARRENDATARIO DESDE FIRESTORE
    private suspend fun fetchRenterName(roomData: RoomData) {
        try {
            val doc = firestore.collection("users").document(roomData.userId).get().await()
            // ✅ Buscar en este orden: nombreCompleto, name, displayName, nombre
            val name = doc.getString("nombreCompleto")
                ?: doc.getString("name")
                ?: doc.getString("displayName")
                ?: doc.getString("nombre")
                ?: "Usuario"
            roomData.renterName = name
        } catch (e: Exception) {
            roomData.renterName = "Usuario"
        }
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
            binding.tvRoomName.text = roomData.nombre
            binding.tvRoomPrice.text = "$${roomData.precio} MXN / mes"
            binding.tvRoomAddress.text = getReadableAddress(roomData.ubicacion)
            binding.tvDescription.text = roomData.descripcion ?: "Sin descripción disponible"

            setupImageGallery(roomData.imagenes)
            setupServices(roomData)
        }
    }

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
                        return if (addressText.isNotEmpty()) addressText else "Tula de Allende, Hidalgo"
                    }
                }
            }
            "Tula de Allende, Hidalgo"
        } catch (e: Exception) {
            "Tula de Allende, Hidalgo"
        }
    }

    private fun setupImageGallery(images: List<String>?) {
        val imageList = images?.takeIf { it.isNotEmpty() } ?: listOf(
            "https://via.placeholder.com/400x250/CCCCCC/000000?text=Sin+Imagen"
        )

        val adapter = ImagePagerAdapter(imageList)
        binding.imageViewpager.adapter = adapter
        TabLayoutMediator(binding.tabIndicator, binding.imageViewpager) { _, _ -> }.attach()
    }

    private fun setupServices(roomData: RoomData) {
        binding.servicesContainer.removeAllViews()

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
                    services.add("Aire Acondicionado" to R.drawable.ic_ac)
                servicio.contains("tv", true) || servicio.contains("televisión", true) ->
                    services.add("TV" to R.drawable.ic_tv)
                servicio.contains("lavadora", true) ->
                    services.add("Lavadora" to R.drawable.ic_washing_machine)
                servicio.contains("agua", true) ->
                    services.add("Agua Caliente" to R.drawable.ic_water)
                servicio.contains("seguridad", true) ->
                    services.add("Seguridad 24/7" to R.drawable.ic_security)
                servicio.contains("luz", true) || servicio.contains("gas", true) || servicio.contains("incluidos", true) ->
                    services.add("Servicios Incluidos" to R.drawable.ic_services)
                servicio.contains("mobiliario", true) || servicio.contains("muebles", true) ->
                    services.add("Mobiliario" to R.drawable.ic_furniture)
                else -> services.add(servicio to R.drawable.ic_service_default)
            }
        }

        if (services.isEmpty()) {
            services.add("Sin servicios" to R.drawable.ic_wifi)
        }

        var currentRow: LinearLayout? = null
        services.forEachIndexed { index, (name, icon) ->
            if (index % 3 == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8.dpToPx() }
                }
                binding.servicesContainer.addView(currentRow)
            }

            val serviceChip = LayoutInflater.from(this)
                .inflate(R.layout.item_service_chip, currentRow, false) as LinearLayout

            serviceChip.findViewById<ImageView>(R.id.iv_service_icon)?.setImageResource(icon)
            serviceChip.findViewById<TextView>(R.id.tv_service_name)?.text = name

            serviceChip.layoutParams = LinearLayout.LayoutParams(0, 55.dpToPx(), 1f).apply {
                if (index % 3 != 2) marginEnd = 6.dpToPx()
            }

            currentRow?.addView(serviceChip)
        }
    }

    private fun setupListeners() {
        // ✅ BOTÓN DE REGRESO - Navega según el rol del usuario
        binding.ivBack.setOnClickListener {
            lifecycleScope.launch {
                val isRenter = checkIfUserIsRenter()
                val intent = if (isRenter) {
                    Intent(this@RoomDetailActivity, com.roomu.app.ui.renter.RenterDashboardActivity::class.java)
                } else {
                    Intent(this@RoomDetailActivity, MainActivity::class.java)
                }
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }

        binding.btnContactContainer.setOnClickListener {
            room?.let { roomData ->
                lifecycleScope.launch {
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (currentUserId == null) {
                        Toast.makeText(this@RoomDetailActivity, "Inicia sesión para chatear", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // ✅ Evitar que el renter se escriba a sí mismo
                    if (currentUserId == roomData.userId) {
                        Toast.makeText(this@RoomDetailActivity, "Eres el dueño de este cuarto", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val predefinedMessage = crearMensajePredefinido(roomData)

                    val intent = Intent(this@RoomDetailActivity, ChatActivity::class.java).apply {
                        putExtra("roomId", roomData.id)
                        putExtra("renterId", roomData.userId)
                        putExtra("renterName", roomData.renterName ?: "Arrendatario")
                        putExtra("predefinedMessage", predefinedMessage)
                        putExtra("roomName", roomData.nombre)
                    }
                    startActivity(intent)
                }
            }
        }

        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones próximamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearMensajePredefinido(room: RoomData): String {
        return buildString {
            append("Buen día,\n\n")
            append("Solicito información del cuarto:\n")
            append("- ${room.nombre}\n")
            append("- Precio: $${room.precio} MXN/mes\n")
            append("- Ubicación: ${getReadableAddress(room.ubicacion)}\n")
            append("- Capacidad: ${room.capacidad} persona${if (room.capacidad > 1) "s" else ""}\n")
            append("- Servicios: ${room.servicios.joinToString(", ")}\n\n")
            append("¿Puede enviarme más detalles y disponibilidad?\n\n")
            append("Gracias.")
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    lifecycleScope.launch {
                        val isRenter = checkIfUserIsRenter()
                        val intent = if (isRenter) {
                            Intent(this@RoomDetailActivity, com.roomu.app.ui.renter.RenterDashboardActivity::class.java)
                        } else {
                            Intent(this@RoomDetailActivity, MainActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    true
                }
                R.id.nav_rooms -> {
                    lifecycleScope.launch {
                        try {
                            mainViewModel.loadRooms()
                            val allAvailableRooms = mainViewModel.allRooms.value
                            if (allAvailableRooms.isNotEmpty()) {
                                val intent = Intent(this@RoomDetailActivity, AllRoomsActivity::class.java)
                                intent.putExtra("allRooms", ArrayList(allAvailableRooms))
                                intent.putExtra("isRenterView", false)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@RoomDetailActivity, "No hay cuartos disponibles", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@RoomDetailActivity, "Error al cargar cuartos", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                R.id.nav_chat -> {
                    lifecycleScope.launch {
                        val isRenter = checkIfUserIsRenter()
                        val intent = Intent(this@RoomDetailActivity, ChatsListActivity::class.java).apply {
                            putExtra("isRenter", isRenter)
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private suspend fun checkIfUserIsRenter(): Boolean {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
            val doc = firestore.collection("renters").document(uid).get().await()
            doc.exists().also { Log.d(TAG, "¿Es arrendatario?: $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando rol: ${e.message}")
            false
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        room?.getLatLng()?.let { (lat, lng) ->
            val location = LatLng(lat, lng)
            googleMap?.run {
                addMarker(MarkerOptions().position(location).title(room?.nombre))
                moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                uiSettings.isZoomControlsEnabled = true
            }
            Log.d(TAG, "Mapa configurado en: $lat, $lng")
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    inner class ImagePagerAdapter(private val images: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.iv_room_image)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_image, parent, false)
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