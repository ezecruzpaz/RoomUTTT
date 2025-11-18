package com.roomu.app.ui.renter

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.roomu.app.R
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.home.adapter.RoomAdapter
import com.roomu.app.ui.profile.ProfileActivity
import com.roomu.app.ui.room.CreateRoomActivity
import com.roomu.app.ui.room.AllRoomsActivity
import com.roomu.app.ui.room.EditRoomActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@AndroidEntryPoint
class RenterDashboardActivity : AppCompatActivity() {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var adapter: RoomAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRooms: TextView
    private lateinit var tvOccupiedCount: TextView
    private lateinit var tvAvailableCount: TextView
    private lateinit var btnAddRoom: Button
    private lateinit var btnFilters: Button
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var ivLogout: ImageView
    private lateinit var ivNotifications: ImageView

    private var allRooms = mutableListOf<RoomData>()
    private var filteredRooms = mutableListOf<RoomData>()
    private var currentFilter: RoomFilter = RoomFilter.ALL

    private val TAG = "RenterDashboard"

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
        bottomNavigation = findViewById(R.id.bottom_navigation)
        ivLogout = findViewById(R.id.iv_logout)
        ivNotifications = findViewById(R.id.iv_notifications)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        // ✅ Usar RoomAdapter con callbacks para acciones
        adapter = RoomAdapter(
            onRoomClick = { room ->
                val intent = Intent(this, com.roomu.app.ui.room.RoomDetailActivity::class.java)
                intent.putExtra("room_id", room.id)
                intent.putExtra("allRooms", ArrayList(allRooms))
                startActivity(intent)
            },
            allRooms = allRooms,
            isRenterView = true, // ✅ Mostrar menú de opciones
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
    }

    // ✅ Editar cuarto
    private fun editRoom(room: RoomData) {
        val intent = Intent(this, EditRoomActivity::class.java)
        intent.putExtra("room", room)
        startActivity(intent)
    }

    // ✅ Confirmar eliminación
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
                    SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                        setTitleText("¡Eliminado!")
                        setContentText("El cuarto ha sido eliminado correctamente")
                        setConfirmText("OK")
                        setConfirmClickListener {
                            it.dismiss()
                            loadRooms() // Recargar lista
                        }
                        show()
                    }
                } else {
                    SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.ERROR_TYPE).apply {
                        setTitleText("Error")
                        setContentText("No se pudo eliminar el cuarto: ${response.code()}")
                        show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error eliminando cuarto: ${e.message}", e)
                SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.ERROR_TYPE).apply {
                    setTitleText("Error de conexión")
                    setContentText("No se pudo eliminar el cuarto")
                    show()
                }
            }
        }
    }

    // ✅ Cambiar disponibilidad
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
                // Preparar todos los datos del cuarto
                val nombreBody = room.nombre.toRequestBody("text/plain".toMediaTypeOrNull())
                val precioBody = room.precio.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionBody = (room.descripcion ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                val capacidadBody = room.capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val disponibleBody = newStatus.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val serviciosBody = (room.servicios.firstOrNull() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                val ubicacionBody = room.ubicacion.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody = room.userId.toRequestBody("text/plain".toMediaTypeOrNull())

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
                    nuevasImagenes = null
                )

                progressDialog.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                        setTitleText("¡Actualizado!")
                        setContentText("El cuarto ahora está marcado como $statusText")
                        setConfirmText("OK")
                        setConfirmClickListener {
                            it.dismiss()
                            loadRooms() // Recargar lista
                        }
                        show()
                    }
                } else {
                    Log.e(TAG, "❌ Error ${response.code()}: ${response.errorBody()?.string()}")
                    SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.ERROR_TYPE).apply {
                        setTitleText("Error")
                        setContentText("No se pudo actualizar el estado: ${response.code()}")
                        show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error actualizando disponibilidad: ${e.message}", e)
                SweetAlertDialog(this@RenterDashboardActivity, SweetAlertDialog.ERROR_TYPE).apply {
                    setTitleText("Error de conexión")
                    setContentText("No se pudo actualizar el estado")
                    show()
                }
            }
        }
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.iv_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones próximamente", Toast.LENGTH_SHORT).show()
        }

        ivLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        btnAddRoom.setOnClickListener {
            startActivity(Intent(this, CreateRoomActivity::class.java))
        }

        btnFilters.setOnClickListener {
            showFilterDialog()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_rooms -> {
                    val intent = Intent(this, AllRoomsActivity::class.java)
                    intent.putExtra("allRooms", ArrayList(allRooms))
                    intent.putExtra("isRenterView", true)
                    intent.putExtra("fromRenterDashboard", true)
                    startActivity(intent)
                    false
                }

                R.id.nav_chat -> {
                    startActivity(Intent(this, RenterChatsActivity::class.java))
                    false
                }

                else -> false
            }
        }
    }

    private fun showFilterDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_filter_cuartos)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val chipGroupStatus = dialog.findViewById<ChipGroup>(R.id.chip_group_status)
        val chipTodos = dialog.findViewById<Chip>(R.id.chip_todos)
        val chipOcupados = dialog.findViewById<Chip>(R.id.chip_ocupados)
        val chipDisponibles = dialog.findViewById<Chip>(R.id.chip_disponibles)
        val tvClearFilters = dialog.findViewById<TextView>(R.id.tv_clear_filters)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_filter)
        val btnApply = dialog.findViewById<Button>(R.id.btn_apply_filter)

        when(currentFilter) {
            RoomFilter.ALL -> chipTodos.isChecked = true
            RoomFilter.OCCUPIED -> chipOcupados.isChecked = true
            RoomFilter.AVAILABLE -> chipDisponibles.isChecked = true
        }

        tvClearFilters.setOnClickListener {
            chipTodos.isChecked = true
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            val selectedChipId = chipGroupStatus.checkedChipId

            currentFilter = when(selectedChipId) {
                R.id.chip_todos -> RoomFilter.ALL
                R.id.chip_ocupados -> RoomFilter.OCCUPIED
                R.id.chip_disponibles -> RoomFilter.AVAILABLE
                else -> RoomFilter.ALL
            }

            applyFilter()
            updateFilterButtonText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateFilterButtonText() {
        val text = when(currentFilter) {
            RoomFilter.ALL -> "Filtros ▼"
            RoomFilter.OCCUPIED -> "🔴 Ocupados ▼"
            RoomFilter.AVAILABLE -> "🟢 Disponibles ▼"
        }
        btnFilters.text = text
    }

    // ✅ Agregar estas funciones en RenterDashboardActivity.kt

    private fun showLogoutConfirmationDialog() {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("Cerrar Sesión")
            .setContentText("¿Estás seguro de que deseas cerrar sesión?")
            .setConfirmText("Sí, cerrar")
            .setCancelText("Cancelar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                performLogout()
            }
            .setCancelClickListener { dialog ->
                dialog.dismissWithAnimation()
            }
            .show()
    }

    private fun performLogout() {
        val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)
            .setTitleText("Cerrando sesión...")
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        try {
            // ✅ Cerrar sesión de Firebase
            FirebaseAuth.getInstance().signOut()
            loadingDialog.dismissWithAnimation()

            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Sesión cerrada")
                .setContentText("Has cerrado sesión exitosamente")
                .setConfirmClickListener { dialog ->
                    dialog.dismissWithAnimation()

                    // ✅ Ir directamente a LoginActivity
                    val intent = Intent(this, com.roomu.app.ui.auth.LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()

        } catch (e: Exception) {
            loadingDialog.dismissWithAnimation()

            SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                .setTitleText("Error")
                .setContentText("No se pudo cerrar sesión: ${e.message}")
                .show()
        }
    }

// ✅ En el onCreate() o donde tengas el botón de logout, cambia a:
// ivLogout.setOnClickListener {
//     showLogoutConfirmationDialog()
// }


    private fun loadRooms() {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid

        if (uid == null) {
            showEmptyState()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = roomApiService.getRooms()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        val roomsList = apiResponse?.result

                        if (roomsList != null) {
                            allRooms = roomsList.filter { room ->
                                room.userId.trim().equals(uid.trim(), ignoreCase = true)
                            }.toMutableList()

                            if (allRooms.isEmpty()) {
                                showEmptyState()
                            } else {
                                showRoomsList()
                                updateStatusCounts()
                                applyFilter()
                            }
                        } else {
                            showEmptyState()
                            Toast.makeText(
                                this@RenterDashboardActivity,
                                "No se pudieron cargar los cuartos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        showEmptyState()
                        Toast.makeText(
                            this@RenterDashboardActivity,
                            "Error al cargar cuartos: ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
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

        // ✅ Actualizar lista del adapter
        adapter.submitList(filteredRooms.toList())
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