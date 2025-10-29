package com.example.roomuttt.ui.renter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roomuttt.R
import com.example.roomuttt.data.api.RoomApiService
import com.example.roomuttt.domain.model.RoomData
import com.example.roomuttt.ui.home.adapter.RoomAdapter
import com.example.roomuttt.ui.profile.ProfileActivity
import com.example.roomuttt.ui.renter.adapter.RenterRoomAdapter
import com.example.roomuttt.ui.room.AllRoomsActivity
import com.example.roomuttt.ui.room.CreateRoomActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RenterDashboardActivity : AppCompatActivity() {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var adapter: RenterRoomAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRooms: TextView
    private lateinit var tvOccupiedCount: TextView
    private lateinit var tvAvailableCount: TextView
    private lateinit var btnAddRoom: Button
    private lateinit var btnFilters: Button
    private lateinit var filterAll: LinearLayout
    private lateinit var filterOccupied: LinearLayout
    private lateinit var filterAvailable: LinearLayout
    private lateinit var layoutStatusFilters: androidx.cardview.widget.CardView
    private lateinit var bottomNavigation: BottomNavigationView

    private var allRooms = mutableListOf<RoomData>()
    private var filteredRooms = mutableListOf<RoomData>()
    private var currentFilter: RoomFilter = RoomFilter.ALL
    private var isFiltersVisible = false

    enum class RoomFilter {
        ALL, OCCUPIED, AVAILABLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renter_dashboard)

        initializeViews()
        setupRecyclerView()
        setupListeners()
        loadRooms()
    }

    override fun onResume() {
        super.onResume()
        loadRooms()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_rooms)
        tvNoRooms = findViewById(R.id.tv_no_rooms)
        tvOccupiedCount = findViewById(R.id.tv_occupied_count)
        tvAvailableCount = findViewById(R.id.tv_available_count)
        btnAddRoom = findViewById(R.id.btn_add_room)
        btnFilters = findViewById(R.id.btn_filters)
        filterOccupied = findViewById(R.id.filter_occupied)
        filterAvailable = findViewById(R.id.filter_available)
        layoutStatusFilters = findViewById(R.id.layout_status_filters)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        layoutStatusFilters.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RenterRoomAdapter(filteredRooms)
        recyclerView.adapter = adapter

    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.iv_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnAddRoom.setOnClickListener {
            startActivity(Intent(this, CreateRoomActivity::class.java))
        }

        btnFilters.setOnClickListener {
            isFiltersVisible = !isFiltersVisible
            layoutStatusFilters.visibility = if (isFiltersVisible) View.VISIBLE else View.GONE
            btnFilters.text = if (isFiltersVisible) "Filtros ▲" else "Filtros ▼"
        }

        filterOccupied.setOnClickListener {
            currentFilter = if (currentFilter == RoomFilter.OCCUPIED) {
                RoomFilter.ALL
            } else {
                RoomFilter.OCCUPIED
            }
            applyFilter()
        }

        filterAvailable.setOnClickListener {
            currentFilter = if (currentFilter == RoomFilter.AVAILABLE) {
                RoomFilter.ALL
            } else {
                RoomFilter.AVAILABLE
            }
            applyFilter()
        }

        // ✅ NUEVO: Setup del Bottom Navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Ya estamos en home (Dashboard)
                    true
                }

                R.id.nav_rooms -> {
                    // Navegar a AllRoomsActivity con los cuartos del arrendatario
                    val intent = Intent(this, AllRoomsActivity::class.java)

                    // Pasar los cuartos del arrendatario actual
                    val currentRooms = ArrayList<RoomData>()
                    currentRooms.addAll(allRooms)
                    intent.putExtra("allRooms", currentRooms)

                    // ✅ Flag para indicar que son cuartos del arrendatario
                    intent.putExtra("isRenterView", true)

                    // ✅ NUEVO: Indicar que venimos desde RenterDashboard
                    intent.putExtra("fromRenterDashboard", true)

                    startActivity(intent)
                    false
                }

                R.id.nav_chat -> {
                    Toast.makeText(this, "💬 Chat próximamente", Toast.LENGTH_SHORT).show()
                    false
                }

                else -> false
            }
        }
    }

    private fun loadRooms() {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid

        if (uid == null) {
            println("⚠️ Usuario no autenticado")
            showEmptyState()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("🔄 Cargando cuartos desde API...")
                println("🔑 UID del usuario: $uid")

                // ✅ USAR LA API REST EN LUGAR DE FIRESTORE
                val response = roomApiService.getRooms()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        val roomsList = apiResponse?.result

                        if (roomsList != null) {
                            println("📦 Total de cuartos en API: ${roomsList.size}")

                            // ✅ FILTRAR por UserId (con trim para evitar problemas de espacios)
                            allRooms = roomsList.filter { room ->
                                val roomUserId = room.userId.trim()
                                val matches = roomUserId.equals(uid.trim(), ignoreCase = true)

                                // Debug detallado
                                println("🏠 Cuarto: ${room.nombre}")
                                println("   UserId del cuarto: '$roomUserId'")
                                println("   UserId buscado: '$uid'")
                                println("   ¿Coincide?: $matches")

                                matches
                            }.toMutableList()

                            println("✅ Cuartos del usuario: ${allRooms.size}")

                            if (allRooms.isEmpty()) {
                                showEmptyState()
                            } else {
                                showRoomsList()
                                updateStatusCounts()
                                applyFilter()
                            }
                        } else {
                            println("❌ Respuesta vacía del servidor")
                            showEmptyState()
                            Toast.makeText(
                                this@RenterDashboardActivity,
                                "No se pudieron cargar los cuartos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        println("❌ Error en respuesta: ${response.code()} - ${response.message()}")
                        showEmptyState()
                        Toast.makeText(
                            this@RenterDashboardActivity,
                            "Error al cargar cuartos: ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                println("❌ Error cargando cuartos: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    showEmptyState()
                    Toast.makeText(
                        this@RenterDashboardActivity,
                        "Error de conexión: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyFilter() {
        filteredRooms.clear()

        when (currentFilter) {
            RoomFilter.ALL -> filteredRooms.addAll(allRooms)
            RoomFilter.OCCUPIED -> filteredRooms.addAll(allRooms.filter { !it.disponible })
            RoomFilter.AVAILABLE -> filteredRooms.addAll(allRooms.filter { it.disponible })
        }

        adapter.notifyDataSetChanged()
        updateFilterUI()
    }

    private fun updateFilterUI() {
        val selectedAlpha = 1.0f
        val normalAlpha = 0.6f

        filterOccupied.alpha = if (currentFilter == RoomFilter.OCCUPIED) selectedAlpha else normalAlpha
        filterAvailable.alpha = if (currentFilter == RoomFilter.AVAILABLE) selectedAlpha else normalAlpha
    }

    private fun updateStatusCounts() {
        val occupiedCount = allRooms.count { !it.disponible }
        val availableCount = allRooms.count { it.disponible }

        tvOccupiedCount.text = occupiedCount.toString()
        tvAvailableCount.text = availableCount.toString()
    }

    private fun showEmptyState() {
        tvNoRooms.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvOccupiedCount.text = "0"
        tvAvailableCount.text = "0"
    }

    private fun showRoomsList() {
        tvNoRooms.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}