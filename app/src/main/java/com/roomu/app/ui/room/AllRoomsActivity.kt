package com.roomu.app.ui.room

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
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

    // Views
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvRoomsCount: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutNoResults: LinearLayout

    // Data
    private var allRooms: ArrayList<RoomData> = arrayListOf()
    private var filteredRooms: ArrayList<RoomData> = arrayListOf()
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

        supportActionBar?.title = when {
            isRenterView && renterName != null -> "Cuartos de $renterName"
            isRenterView -> "Mis Cuartos"
            else -> "Todos los Cuartos"
        }

        initViews()
        setupListeners()

        allRooms = intent.getSerializableExtra("allRooms") as? ArrayList<RoomData> ?: arrayListOf()
        filteredRooms.addAll(allRooms)

        Log.d(TAG, "📦 Cuartos recibidos: ${allRooms.size}")

        updateRoomsCount()
        setupRecyclerView()
        setupBottomNavigation()
    }

    private fun initViews() {
        tvRoomsCount = findViewById(R.id.tv_rooms_count)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        recyclerView = findViewById(R.id.recycler_all_rooms)
        layoutNoResults = findViewById(R.id.layout_no_results)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        // 🔥 COMENTADO: Vistas de búsqueda y filtros
        // tvClearSearch = findViewById(R.id.tv_clear_search)
        // etSearch = findViewById(R.id.et_search)
        // btnFilters = findViewById(R.id.btn_filters)
        // scrollActiveFilters = findViewById(R.id.scroll_active_filters)
        // chipGroupActiveFilters = findViewById(R.id.chip_group_active_filters)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RoomAdapter(
            onRoomClick = { room ->
                Log.d(TAG, "🏠 Cuarto clickeado: ${room.nombre}")
                val intent = Intent(this, RoomDetailActivity::class.java).apply {
                    putExtra("room_id", room.id)
                    putExtra("allRooms", allRooms)
                }
                startActivity(intent)
            },
            allRooms = filteredRooms,
            isRenterView = isRenterView,
            onEditRoom = { editRoom(it) },
            onDeleteRoom = { confirmDeleteRoom(it) },
            onToggleAvailability = { toggleRoomAvailability(it) }
        )

        recyclerView.adapter = adapter
        adapter.submitList(filteredRooms.toList())
    }

    private fun setupListeners() {
        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ivNotifications.setOnClickListener {
            Toast.makeText(this, "🔔 Notificaciones", Toast.LENGTH_SHORT).show()
        }

        // 🔥 COMENTADO: Listeners de búsqueda y filtros
        /*
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                applyFilters()
                tvClearSearch.isVisible = searchQuery.isNotEmpty() || hasActiveFilters()
            }
        })

        tvClearSearch.setOnClickListener { clearAllFilters() }
        btnFilters.setOnClickListener { showFilterDialog() }
        */
    }

    private fun updateRoomsCount() {
        val count = filteredRooms.size
        tvRoomsCount.text = "$count cuarto${if (count != 1) "s" else ""} ${if (count != 1) "encontrados" else "encontrado"}"
    }

    private fun editRoom(room: RoomData) {
        val intent = Intent(this, EditRoomActivity::class.java).apply {
            putExtra("room", room)
        }
        startActivity(intent)
    }

    private fun confirmDeleteRoom(room: RoomData) {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("¿Eliminar cuarto?")
            .setContentText("Esta acción no se puede deshacer.")
            .setConfirmText("Sí, eliminar")
            .setConfirmClickListener { dialog ->
                dialog.dismissWithAnimation()
                deleteRoom(room)
            }
            .setCancelButton("Cancelar") { it.dismiss() }
            .show()
    }

    private fun deleteRoom(room: RoomData) {
        val progress = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Eliminando..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val response = roomApiService.deleteRoom(room.id)
                progress.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("¡Eliminado!")
                        .setContentText("El cuarto se eliminó correctamente")
                        .setConfirmClickListener { it.dismiss(); refreshRoomsList() }
                        .show()
                } else {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText("No se pudo eliminar")
                        .show()
                }
            } catch (e: Exception) {
                progress.dismiss()
                SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("Error de conexión")
                    .setContentText("Revisa tu internet")
                    .show()
            }
        }
    }

    private fun toggleRoomAvailability(room: RoomData) {
        val newStatus = !room.disponible
        val statusText = if (newStatus) "disponible" else "rentado"

        val progress = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Actualizando..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val response = roomApiService.updateRoom(
                    id = room.id,
                    nombre = room.nombre.toRequestBody("text/plain".toMediaTypeOrNull()),
                    precio = room.precio.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    descripcion = (room.descripcion ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                    capacidad = room.capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    disponible = newStatus.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    servicios = (room.servicios.firstOrNull() ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                    ubicacion = room.ubicacion.toRequestBody("text/plain".toMediaTypeOrNull()),
                    userId = room.userId.toRequestBody("text/plain".toMediaTypeOrNull()),
                    nuevasImagenes = null
                )

                progress.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("¡Actualizado!")
                        .setContentText("El cuarto ahora está $statusText")
                        .setConfirmClickListener { it.dismiss(); refreshRoomsList() }
                        .show()
                } else {
                    SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText("No se pudo actualizar")
                        .show()
                }
            } catch (e: Exception) {
                progress.dismiss()
                SweetAlertDialog(this@AllRoomsActivity, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("Error de conexión")
                    .setContentText("Revisa tu internet")
                    .show()
            }
        }
    }

    private fun refreshRoomsList() {
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val response = roomApiService.getRooms()
                if (response.isSuccessful) {
                    val list = response.body()?.result?.filter { it.userId == uid } ?: emptyList()
                    allRooms.clear()
                    allRooms.addAll(list)
                    filteredRooms.clear()
                    filteredRooms.addAll(allRooms)
                    adapter.submitList(filteredRooms.toList())
                    updateRoomsCount()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recargando lista", e)
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_rooms

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    lifecycleScope.launch {
                        val isRenter = checkIfUserIsRenter()
                        startActivity(
                            Intent(
                                this@AllRoomsActivity,
                                if (isRenter) RenterDashboardActivity::class.java else MainActivity::class.java
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                        finish()
                    }
                    true
                }
                R.id.nav_rooms -> true
                R.id.nav_chat -> {
                    lifecycleScope.launch {
                        startActivity(Intent(this@AllRoomsActivity, ChatsListActivity::class.java).apply {
                            putExtra("isRenter", checkIfUserIsRenter())
                        })
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
            val doc = FirebaseFirestore.getInstance().collection("renters").document(uid).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_rooms
    }

    override fun onBackPressed() {
        lifecycleScope.launch {
            if (checkIfUserIsRenter()) {
                startActivity(Intent(this@AllRoomsActivity, RenterDashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            } else {
                super@AllRoomsActivity.onBackPressed()
            }
        }
    }
}