package com.roomu.app.ui.room

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var tvClearSearch: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivNotifications: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutNoResults: LinearLayout
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnFilters: MaterialButton
    private lateinit var scrollActiveFilters: HorizontalScrollView
    private lateinit var chipGroupActiveFilters: ChipGroup

    // Data
    private var allRooms: ArrayList<RoomData> = arrayListOf()
    private var filteredRooms: ArrayList<RoomData> = arrayListOf()
    private lateinit var adapter: RoomAdapter

    // Estado de filtros (SIN GÉNERO)
    private var searchQuery = ""
    private var minPrice: Double? = null
    private var maxPrice: Double? = null
    private var selectedCapacity: Int? = null
    private val selectedServices = mutableListOf<String>()

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

        Log.d(TAG, " Cuartos recibidos: ${allRooms.size}")

        updateRoomsCount()
        setupRecyclerView()
        setupBottomNavigation()
    }

    private fun initViews() {
        tvRoomsCount = findViewById(R.id.tv_rooms_count)
        tvClearSearch = findViewById(R.id.tv_clear_search)
        ivProfile = findViewById(R.id.iv_profile)
        ivNotifications = findViewById(R.id.iv_notifications)
        recyclerView = findViewById(R.id.recycler_all_rooms)
        layoutNoResults = findViewById(R.id.layout_no_results)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        etSearch = findViewById(R.id.et_search)
        btnFilters = findViewById(R.id.btn_filters)
        scrollActiveFilters = findViewById(R.id.scroll_active_filters)
        chipGroupActiveFilters = findViewById(R.id.chip_group_active_filters)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RoomAdapter(
            onRoomClick = { room ->
                Log.d(TAG, " Cuarto clickeado: ${room.nombre}")
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
            Toast.makeText(this, " Notificaciones", Toast.LENGTH_SHORT).show()
        }

        // Búsqueda en tiempo real
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
    }

    private fun showFilterDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_filter_rooms)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // Views del diálogo
        val etMinPrice = dialog.findViewById<TextInputEditText>(R.id.et_min_price)
        val etMaxPrice = dialog.findViewById<TextInputEditText>(R.id.et_max_price)
        val chipGroupCapacity = dialog.findViewById<ChipGroup>(R.id.chip_group_capacity)
        val chipGroupServices = dialog.findViewById<ChipGroup>(R.id.chip_group_services)
        val tvClearAll = dialog.findViewById<TextView>(R.id.tv_clear_all_filters)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_filter)
        val btnApply = dialog.findViewById<Button>(R.id.btn_apply_filter)

        // Cargar valores actuales
        etMinPrice.setText(minPrice?.toString() ?: "")
        etMaxPrice.setText(maxPrice?.toString() ?: "")

        // Capacidad
        chipGroupCapacity.check(
            when (selectedCapacity) {
                null -> R.id.chip_capacity_all
                1 -> R.id.chip_capacity_1
                2 -> R.id.chip_capacity_2
                3 -> R.id.chip_capacity_3
                else -> R.id.chip_capacity_4
            }
        )

        // Servicios
        val serviceChipMap = mapOf(
            R.id.chip_service_wifi to "Wi-Fi",
            R.id.chip_service_kitchen to "Cocina",
            R.id.chip_service_bathroom to "Baño privado",
            R.id.chip_service_washing to "Lavadora",
            R.id.chip_service_furniture to "Mobiliario",
            R.id.chip_service_utilities to "Servicios incluidos"
        )

        serviceChipMap.forEach { (chipId, service) ->
            dialog.findViewById<Chip>(chipId)?.isChecked = selectedServices.contains(service)
        }

        tvClearAll.setOnClickListener {
            etMinPrice.text?.clear()
            etMaxPrice.text?.clear()
            chipGroupCapacity.check(R.id.chip_capacity_all)
            chipGroupServices.clearCheck()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            minPrice = etMinPrice.text.toString().toDoubleOrNull()
            maxPrice = etMaxPrice.text.toString().toDoubleOrNull()

            selectedCapacity = when (chipGroupCapacity.checkedChipId) {
                R.id.chip_capacity_1 -> 1
                R.id.chip_capacity_2 -> 2
                R.id.chip_capacity_3 -> 3
                R.id.chip_capacity_4 -> 4
                else -> null
            }

            selectedServices.clear()
            serviceChipMap.forEach { (chipId, service) ->
                if (dialog.findViewById<Chip>(chipId)?.isChecked == true) {
                    selectedServices.add(service)
                }
            }

            applyFilters()
            updateActiveFiltersChips()
            dialog.dismiss()

            tvClearSearch.isVisible = hasActiveFilters() || searchQuery.isNotEmpty()
        }

        dialog.show()
    }

    private fun applyFilters() {
        filteredRooms.clear()

        filteredRooms.addAll(allRooms.filter { room ->
            room.matchesFilters(
                searchQuery = searchQuery,
                minPrice = minPrice,
                maxPrice = maxPrice,
                minCapacity = selectedCapacity,
                maxCapacity = if (selectedCapacity != null && selectedCapacity!! >= 4) null else selectedCapacity,
                selectedServices = selectedServices
            )
        })

        adapter.submitList(filteredRooms.toList())
        updateRoomsCount()

        recyclerView.isVisible = filteredRooms.isNotEmpty()
        layoutNoResults.isVisible = filteredRooms.isEmpty()

        Log.d(TAG, " Filtros aplicados: ${filteredRooms.size} resultados")
    }

    private fun updateActiveFiltersChips() {
        chipGroupActiveFilters.removeAllViews()
        val activeFilters = mutableListOf<Pair<String, () -> Unit>>()

        // Precio
        if (minPrice != null || maxPrice != null) {
            val priceText = buildString {
                append("Precio: ")
                if (minPrice != null) append("$${minPrice?.toInt()}")
                if (minPrice != null && maxPrice != null) append(" - ")
                if (maxPrice != null) append("$${maxPrice?.toInt()}")
            }
            activeFilters.add(priceText to {
                minPrice = null
                maxPrice = null
                applyFilters()
                updateActiveFiltersChips()
            })
        }

        // Capacidad
        if (selectedCapacity != null) {
            val capacityText = if (selectedCapacity!! >= 4) "4+ personas" else "$selectedCapacity persona${if (selectedCapacity!! > 1) "s" else ""}"
            activeFilters.add(capacityText to {
                selectedCapacity = null
                applyFilters()
                updateActiveFiltersChips()
            })
        }

        // Servicios
        selectedServices.forEach { service ->
            activeFilters.add(service to {
                selectedServices.remove(service)
                applyFilters()
                updateActiveFiltersChips()
            })
        }

        // Determinar si está en modo oscuro del sistema
        val nightModeFlags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemInDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        activeFilters.forEach { (text, onClose) ->
            val chip = Chip(this).apply {
                this.text = text
                isCloseIconVisible = true
                setOnCloseIconClickListener { onClose() }

                // Colores que contrastan correctamente
                if (isSystemInDarkMode) {
                    // Sistema en modo oscuro: chip blanco con texto negro
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                    )
                    setTextColor(android.graphics.Color.BLACK)
                    closeIconTint = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.BLACK
                    )
                } else {
                    // Sistema en modo claro: chip negro con texto blanco
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.BLACK
                    )
                    setTextColor(android.graphics.Color.WHITE)
                    closeIconTint = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                    )
                }

                // Asegurar que el chip sea visible
                chipStrokeWidth = 0f
            }
            chipGroupActiveFilters.addView(chip)
        }

        scrollActiveFilters.isVisible = activeFilters.isNotEmpty()
    }

    private fun clearAllFilters() {
        searchQuery = ""
        minPrice = null
        maxPrice = null
        selectedCapacity = null
        selectedServices.clear()

        etSearch.text?.clear()
        applyFilters()
        updateActiveFiltersChips()
        tvClearSearch.isVisible = false
    }

    private fun hasActiveFilters(): Boolean =
        minPrice != null || maxPrice != null || selectedCapacity != null || selectedServices.isNotEmpty()

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
                    applyFilters()
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