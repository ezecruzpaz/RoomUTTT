package com.example.roomuttt.ui.room

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roomuttt.R
import com.example.roomuttt.ui.home.MainActivity
import com.example.roomuttt.ui.home.adapter.RoomAdapter
import com.example.roomuttt.ui.profile.ProfileActivity
import com.example.roomuttt.domain.model.RoomData
import com.example.roomuttt.ui.renter.RenterDashboardActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class AllRoomsActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvRoomsCount: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerView: RecyclerView
    private var allRooms: ArrayList<RoomData> = arrayListOf()

    // ✅ NUEVO: Variable para saber de dónde venimos
    private var fromRenterDashboard = false

    private val TAG = "AllRoomsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_rooms)

        val isRenterView = intent.getBooleanExtra("isRenterView", false)
        val renterName = intent.getStringExtra("renterName")

        // ✅ NUEVO: Detectar de dónde venimos
        fromRenterDashboard = intent.getBooleanExtra("fromRenterDashboard", false)

        // Configurar el título según el contexto
        if (isRenterView && renterName != null) {
            supportActionBar?.title = "Cuartos de $renterName"
        } else if (isRenterView) {
            supportActionBar?.title = "Mis Cuartos"
        } else {
            supportActionBar?.title = "Todos los Cuartos"
        }

        initViews()
        setupListeners()

        // ✅ Recibir la lista de cuartos
        allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData> ?: arrayListOf()

        Log.d(TAG, "📦 Cuartos recibidos: ${allRooms.size}")

        // ✅ Actualizar contador
        tvRoomsCount.text = "${allRooms.size} cuartos disponibles"

        // ✅ Configurar RecyclerView
        setupRecyclerView()
        setupBottomNavigation()
    }

    private fun initViews() {
        tvRoomsCount = findViewById(R.id.tv_rooms_count)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        recyclerView = findViewById(R.id.recycler_all_rooms)
        bottomNavigation = findViewById(R.id.bottom_navigation)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (allRooms.isEmpty()) {
            Log.w(TAG, "⚠️ No hay cuartos para mostrar")
            Toast.makeText(this, "No hay cuartos disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Crear el adapter pasando la lista completa
        val adapter = RoomAdapter(
            onRoomClick = { room ->
                Log.d(TAG, "🏠 Cuarto clickeado: ${room.nombre}")

                // ✅ Abrir detalle del cuarto
                val intent = Intent(this, RoomDetailActivity::class.java)
                intent.putExtra("room", room)
                startActivity(intent)
            },
            allRooms = allRooms // ✅ Lista completa de cuartos
        )

        recyclerView.adapter = adapter

        // ✅ Mostrar TODOS los cuartos (no solo los 2 primeros)
        adapter.submitList(allRooms)

        Log.d(TAG, "✅ RecyclerView configurado con ${allRooms.size} cuartos")
    }

    private fun setupListeners() {
        // ✅ Botón de Perfil
        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ✅ Botón de Notificaciones
        ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        // ✅ Marcar "Rooms" como seleccionado
        bottomNavigation.selectedItemId = R.id.nav_rooms

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // ✅ Regresar a la actividad correcta según de dónde venimos
                    if (fromRenterDashboard) {
                        // Venimos desde RenterDashboard, regresar ahí
                        val intent = Intent(this, RenterDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    } else {
                        // Venimos desde MainActivity normal
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    }
                    finish()
                    true
                }

                R.id.nav_rooms -> {
                    // Ya estamos aquí
                    true
                }

                R.id.nav_chat -> {
                    Toast.makeText(this, "💬 Chat próximamente", Toast.LENGTH_SHORT).show()
                    false
                }

                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ Asegurar que "Rooms" esté seleccionado cuando regresamos
        bottomNavigation.selectedItemId = R.id.nav_rooms
    }

    // ✅ NUEVO: Manejar el botón "Atrás"
    override fun onBackPressed() {
        if (fromRenterDashboard) {
            val intent = Intent(this, RenterDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        } else {
            super.onBackPressed()
        }
    }
}