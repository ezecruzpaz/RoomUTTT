package com.example.roomuttt.ui.room

import CreateRoomDto
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.data.api.RoomApiService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateRoomActivity : AppCompatActivity() {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var auth: FirebaseAuth
    private lateinit var etNombre: EditText
    private lateinit var etPrecio: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var etCapacidad: EditText
    private lateinit var spinnerServicios: Spinner
    private lateinit var etUbicacion: EditText
    private lateinit var btnAddImage: Button
    private lateinit var ivPreview: ImageView
    private lateinit var btnCreate: Button

    private var selectedImageUri: Uri? = null
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val TAG = "CreateRoomActivity"

    // Launcher para seleccionar imagen
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            ivPreview.setImageURI(it)
            Log.d(TAG, "Imagen seleccionada: $it")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_room)

        auth = FirebaseAuth.getInstance()

        initViews()
        setupSpinner()
        setupListeners()
    }

    private fun initViews() {
        etNombre = findViewById(R.id.et_nombre)
        etPrecio = findViewById(R.id.et_precio)
        etDescripcion = findViewById(R.id.et_descripcion)
        etCapacidad = findViewById(R.id.et_capacidad)
        spinnerServicios = findViewById(R.id.spinner_servicios)
        etUbicacion = findViewById(R.id.et_ubicacion)
        btnAddImage = findViewById(R.id.btn_add_image)
        ivPreview = findViewById(R.id.iv_preview)
        btnCreate = findViewById(R.id.btn_create)
    }

    private fun setupSpinner() {
        val servicios = arrayOf("Internet", "Aire Acondicionado", "Cocina", "Estacionamiento")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, servicios)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServicios.adapter = adapter
    }

    private fun setupListeners() {
        btnAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnCreate.setOnClickListener {
            createRoom()
        }
    }

    private fun createRoom() {
        val nombre = etNombre.text.toString().trim()
        val precioStr = etPrecio.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()
        val capacidadStr = etCapacidad.text.toString().trim()
        val ubicacion = etUbicacion.text.toString().trim()
        val servicios = listOf(spinnerServicios.selectedItem.toString())

        // Validación mejorada
        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es requerido"
            etNombre.requestFocus()
            return
        }

        if (precioStr.isEmpty()) {
            etPrecio.error = "El precio es requerido"
            etPrecio.requestFocus()
            return
        }

        val precio = precioStr.toDoubleOrNull()
        if (precio == null || precio <= 0) {
            etPrecio.error = "El precio debe ser mayor a 0"
            etPrecio.requestFocus()
            return
        }

        if (capacidadStr.isEmpty()) {
            etCapacidad.error = "La capacidad es requerida"
            etCapacidad.requestFocus()
            return
        }

        val capacidad = capacidadStr.toIntOrNull()
        if (capacidad == null || capacidad < 1) {
            etCapacidad.error = "La capacidad debe ser al menos 1"
            etCapacidad.requestFocus()
            return
        }

        if (ubicacion.isEmpty()) {
            etUbicacion.error = "La ubicación es requerida"
            etUbicacion.requestFocus()
            return
        }

        // ⭐ CAMBIO IMPORTANTE: Usar CreateRoomDto en lugar de RoomDto
        val createRoomDto = CreateRoomDto(
            nombre = nombre,
            precio = precio,
            descripcion = if (descripcion.isEmpty()) null else descripcion,
            capacidad = capacidad,
            disponible = true,
            servicios = servicios,
            userId = uid,
            ubicacion = ubicacion
        )

        Log.d(TAG, "Creando cuarto con UID: $uid")
        Log.d(TAG, "Datos: nombre=$nombre, precio=$precio, capacidad=$capacidad, ubicacion=$ubicacion")

        lifecycleScope.launch {
            try {
                val response = roomApiService.createRoom(createRoomDto)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(
                        this@CreateRoomActivity,
                        "Cuarto creado exitosamente",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                    Log.e(TAG, "Error API (${response.code()}): $errorMsg")
                    Toast.makeText(
                        this@CreateRoomActivity,
                        "Error al crear el cuarto",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de red: ${e.message}", e)
                Toast.makeText(
                    this@CreateRoomActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}