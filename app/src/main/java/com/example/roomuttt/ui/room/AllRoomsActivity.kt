package com.example.roomuttt.ui.room

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.bottomnavigation.BottomNavigationView

class AllRoomsActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvRoomsCount: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private var allRooms: ArrayList<RoomData> = arrayListOf() // ✅ Guardar la lista


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_rooms)

        initViews()
        setupListeners()

        allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData> ?: arrayListOf()

        // ✅ Actualizar contador
        tvRoomsCount.text = "${allRooms.size} cuartos disponibles"

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_all_rooms)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RoomAdapter(
            onRoomClick = { room ->
                Toast.makeText(this, "Cuarto: ${room.nombre}", Toast.LENGTH_SHORT).show()
            },
            allRooms = allRooms // ✅ Pasar la lista completa al adapter
        ).apply { submitList(allRooms) }

        setupBottomNavigation()
    }

    private fun initViews() {
        tvRoomsCount = findViewById(R.id.tv_rooms_count)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        bottomNavigation = findViewById(R.id.bottom_navigation)
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
                    // ✅ Regresar a MainActivity (Home)
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish() // Cerrar AllRoomsActivity
                    true
                }

                R.id.nav_rooms -> {
                    // Ya estamos aquí
                    true
                }

                R.id.nav_reservations -> {
                    Toast.makeText(this, "📅 Reservas próximamente", Toast.LENGTH_SHORT).show()
                    false
                }

                R.id.nav_map -> {
                    Toast.makeText(this, "🗺️ Mapa próximamente", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        // ✅ Asegurar que "Rooms" esté seleccionado cuando regresamos
        bottomNavigation.selectedItemId = R.id.nav_rooms
    }
}