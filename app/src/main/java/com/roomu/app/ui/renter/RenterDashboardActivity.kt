package com.roomu.app.ui.renter

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.roomu.app.R
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.domain.model.RoomData
import com.roomu.app.ui.profile.ProfileActivity
import com.roomu.app.ui.renter.adapter.RenterRoomAdapter
import com.roomu.app.ui.room.CreateRoomActivity
import com.roomu.app.ui.room.AllRoomsActivity
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
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var ivLogout: ImageView
    private lateinit var ivNotifications: ImageView

    private var allRooms = mutableListOf<RoomData>()
    private var filteredRooms = mutableListOf<RoomData>()
    private var currentFilter: RoomFilter = RoomFilter.ALL

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

        adapter = RenterRoomAdapter(
            rooms = filteredRooms,
            onRoomClick = { room ->
                val intent = Intent(this, com.roomu.app.ui.room.RoomDetailActivity::class.java)
                intent.putExtra("room_id", room.id)
                intent.putExtra("allRooms", ArrayList(allRooms))
                startActivity(intent)
            }
        )

        recyclerView.adapter = adapter
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

        // ✅ NUEVO: Abrir diálogo de filtros
        btnFilters.setOnClickListener {
            showFilterDialog()
        }

        // ✅ BOTTOM NAVIGATION CON CHATS
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

    // ✅ NUEVO: Mostrar diálogo de filtros con chips
    private fun showFilterDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_filter_cuartos)

        // Configurar el ancho del diálogo (90% del ancho de pantalla)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Hacer el fondo transparente para que se vean las esquinas redondeadas
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Referencias a las vistas
        val chipGroupStatus = dialog.findViewById<ChipGroup>(R.id.chip_group_status)
        val chipTodos = dialog.findViewById<Chip>(R.id.chip_todos)
        val chipOcupados = dialog.findViewById<Chip>(R.id.chip_ocupados)
        val chipDisponibles = dialog.findViewById<Chip>(R.id.chip_disponibles)
        val tvClearFilters = dialog.findViewById<TextView>(R.id.tv_clear_filters)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_filter)
        val btnApply = dialog.findViewById<Button>(R.id.btn_apply_filter)

        // Establecer el filtro actual seleccionado
        when(currentFilter) {
            RoomFilter.ALL -> chipTodos.isChecked = true
            RoomFilter.OCCUPIED -> chipOcupados.isChecked = true
            RoomFilter.AVAILABLE -> chipDisponibles.isChecked = true
        }

        // Botón "Limpiar" - vuelve a "Todos"
        tvClearFilters.setOnClickListener {
            chipTodos.isChecked = true
        }

        // Botón Cancelar - cierra el diálogo sin aplicar cambios
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Botón Aplicar - aplica el filtro seleccionado
        btnApply.setOnClickListener {
            val selectedChipId = chipGroupStatus.checkedChipId

            currentFilter = when(selectedChipId) {
                R.id.chip_todos -> RoomFilter.ALL
                R.id.chip_ocupados -> RoomFilter.OCCUPIED
                R.id.chip_disponibles -> RoomFilter.AVAILABLE
                else -> RoomFilter.ALL
            }

            // Aplicar el filtro a la lista
            applyFilter()

            // Actualizar el texto del botón
            updateFilterButtonText()

            // Cerrar el diálogo
            dialog.dismiss()
        }

        dialog.show()
    }

    // ✅ NUEVO: Actualizar texto del botón de filtros
    private fun updateFilterButtonText() {
        val text = when(currentFilter) {
            RoomFilter.ALL -> "Filtros ▼"
            RoomFilter.OCCUPIED -> "🔴 Ocupados ▼"
            RoomFilter.AVAILABLE -> "🟢 Disponibles ▼"
        }
        btnFilters.text = text
    }

    private fun showLogoutConfirmationDialog() {
        cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.WARNING_TYPE)
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
        val loadingDialog = cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.PROGRESS_TYPE)
            .setTitleText("Cerrando sesión...")
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        try {
            FirebaseAuth.getInstance().signOut()
            loadingDialog.dismissWithAnimation()

            cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Sesión cerrada")
                .setContentText("Has cerrado sesión exitosamente")
                .setConfirmClickListener { dialog ->
                    dialog.dismissWithAnimation()

                    val intent = Intent(this, com.roomu.app.ui.auth.LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        } catch (e: Exception) {
            loadingDialog.dismissWithAnimation()

            cn.pedant.SweetAlert.SweetAlertDialog(this, cn.pedant.SweetAlert.SweetAlertDialog.ERROR_TYPE)
                .setTitleText("Error")
                .setContentText("No se pudo cerrar sesión: ${e.message}")
                .show()
        }
    }

    private fun loadRooms() {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid

        if (uid == null) {
            showEmptyState()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
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

        adapter.notifyDataSetChanged()
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