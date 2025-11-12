package com.roomu.app.ui.room

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.R
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.chat.ChatsListActivity
import com.roomu.app.ui.home.MainActivity
import com.roomu.app.ui.home.adapter.RoomAdapter
import com.roomu.app.ui.profile.ProfileActivity
import com.roomu.app.ui.renter.RenterDashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@AndroidEntryPoint
class AllRoomsActivity : AppCompatActivity() {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvRoomsCount: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerView: RecyclerView
    private var allRooms: ArrayList<RoomData> = arrayListOf()
    private lateinit var adapter: RoomAdapter

    private var fromRenterDashboard = false
    private var isRenterView = false

    private val TAG = "AllRoomsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_rooms)

        isRenterView = intent.getBooleanExtra("isRenterView", false)
        val renterName = intent.getStringExtra("renterName")
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

        // Recibir la lista de cuartos
        allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData> ?: arrayListOf()

        Log.d(TAG, "📦 Cuartos recibidos: ${allRooms.size}")

        // Actualizar contador
        tvRoomsCount.text = "${allRooms.size} cuartos disponibles"

        // Configurar RecyclerView
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

        // ✅ Crear adapter con callbacks para las acciones
        adapter = RoomAdapter(
            onRoomClick = { room ->
                Log.d(TAG, "🏠 Cuarto clickeado: ${room.nombre}")
                val intent = Intent(this, RoomDetailActivity::class.java)
                intent.putExtra("room_id", room.id)
                intent.putExtra("allRooms", allRooms)
                startActivity(intent)
            },
            allRooms = allRooms,
            isRenterView = isRenterView,
            onEditRoom = { room ->
                editRoom(room)
            },
            onDeleteRoom = { room ->
                confirmDeleteRoom(room)
            },
            onToggleAvailability = { room ->
                toggleRoomAvailability(room)
            }
        )

        recyclerView.adapter = adapter
        adapter.submitList(allRooms)

        Log.d(TAG, "✅ RecyclerView configurado con ${allRooms.size} cuartos")
    }

    // ✅ Editar cuarto
    private fun editRoom(room: RoomData) {
        val intent = Intent(this, EditRoomActivity::class.java)
        intent.putExtra("room", room)
        startActivity(intent)
    }

    // ✅ Confirmar eliminación de cuarto
    private fun confirmDeleteRoom(room: RoomData) {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("¿Eliminar cuarto?")
            .setContentText("Esta acción no se puede deshacer. Se eliminará ${room.nombre}")
            .setConfirmText("Sí, eliminar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                deleteRoom(room)
            }
            .setCancelButton("Cancelar") { dialog ->
                dialog.dismiss()
            }
            .show()
    }

    // ✅ Eliminar cuarto
    private fun deleteRoom(room: RoomData) {
        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#D32F2F")
            titleText = "Eliminando"
            contentText = "Eliminando cuarto..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val response = roomApiService.deleteRoom(room.id)

                progressDialog.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                        setTitleText("¡Eliminado!")
                        setContentText("El cuarto ha sido eliminado correctamente")
                        setConfirmText("OK")
                        setConfirmClickListener {
                            it.dismiss()
                            // Recargar lista
                            refreshRoomsList()
                        }
                        show()
                    }
                } else {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE).apply {
                        setTitleText("Error")
                        setContentText("No se pudo eliminar el cuarto: ${response.code()}")
                        show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error eliminando cuarto: ${e.message}", e)
                SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE).apply {
                    setTitleText("Error de conexión")
                    setContentText("No se pudo eliminar el cuarto")
                    show()
                }
            }
        }
    }

    // ✅ Cambiar disponibilidad del cuarto (CORREGIDO)
    private fun toggleRoomAvailability(room: RoomData) {
        val newStatus = !room.disponible
        val statusText = if (newStatus) "disponible" else "rentado"

        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
            titleText = "Actualizando"
            contentText = "Cambiando estado..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // ✅ Preparar todos los datos del cuarto (incluyendo el nuevo estado)
                val nombreBody = room.nombre.toRequestBody("text/plain".toMediaTypeOrNull())
                val precioBody = room.precio.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionBody = (room.descripcion ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                val capacidadBody = room.capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val disponibleBody = newStatus.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val serviciosBody = (room.servicios.firstOrNull() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                val ubicacionBody = room.ubicacion.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody = room.userId.toRequestBody("text/plain".toMediaTypeOrNull())

                // ✅ Llamar al endpoint PUT correcto
                val response = roomApiService.updateRoom(
                    id = room.id,
                    nombre = nombreBody,
                    precio = precioBody,
                    descripcion = descripcionBody,
                    capacidad = capacidadBody,
                    disponible = disponibleBody,
                    servicios = serviciosBody,
                    ubicacion = ubicacionBody,
                    userId = userIdBody,
                    nuevasImagenes = null // No se cambian las imágenes
                )

                progressDialog.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                        setTitleText("¡Actualizado!")
                        setContentText("El cuarto ahora está marcado como $statusText")
                        setConfirmText("OK")
                        setConfirmClickListener {
                            it.dismiss()
                            // Recargar lista
                            refreshRoomsList()
                        }
                        show()
                    }
                } else {
                    Log.e(TAG, "❌ Error ${response.code()}: ${response.errorBody()?.string()}")
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE).apply {
                        setTitleText("Error")
                        setContentText("No se pudo actualizar el estado: ${response.code()}")
                        show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error actualizando disponibilidad: ${e.message}", e)
                SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE).apply {
                    setTitleText("Error de conexión")
                    setContentText("No se pudo actualizar el estado")
                    show()
                }
            }
        }
    }
    // ✅ Recargar lista de cuartos
    private fun refreshRoomsList() {
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

                val response = roomApiService.getRooms()

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    val allRoomsList = apiResponse?.result ?: emptyList()

                    allRooms.clear()
                    allRooms.addAll(allRoomsList.filter { it.userId == uid })

                    tvRoomsCount.text = "${allRooms.size} cuartos disponibles"
                    adapter.submitList(allRooms.toList())
                    adapter.notifyDataSetChanged()

                    Log.d(TAG, "✅ Lista recargada: ${allRooms.size} cuartos")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recargando lista: ${e.message}", e)
            }
        }
    }

    private fun setupListeners() {
        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_rooms

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    lifecycleScope.launch {
                        val isRenter = checkIfUserIsRenter()

                        val intent = if (isRenter) {
                            Intent(this@AllRoomsActivity, RenterDashboardActivity::class.java)
                        } else {
                            Intent(this@AllRoomsActivity, MainActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    true
                }

                R.id.nav_rooms -> {
                    true
                }

                R.id.nav_chat -> {
                    lifecycleScope.launch {
                        val isRenter = checkIfUserIsRenter()
                        val intent = Intent(this@AllRoomsActivity, ChatsListActivity::class.java).apply {
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
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "⚠️ Usuario no autenticado")
                return false
            }

            val uid = currentUser.uid
            val firestore = FirebaseFirestore.getInstance()

            val renterDoc = firestore.collection("renters")
                .document(uid)
                .get()
                .await()

            val isRenter = renterDoc.exists()
            Log.d(TAG, "✅ ¿Es arrendatario?: $isRenter")

            isRenter
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando rol de usuario: ${e.message}")
            false
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_rooms
    }

    override fun onBackPressed() {
        lifecycleScope.launch {
            val isRenter = checkIfUserIsRenter()

            if (isRenter) {
                val intent = Intent(this@AllRoomsActivity, RenterDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            } else {
                super.onBackPressed()
            }
        }
    }
}