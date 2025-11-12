package com.roomu.app.ui.room

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.roomu.app.R
import com.roomu.app.data.api.RoomApiService
import com.roomu.app.domain.model.RoomData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class EditRoomActivity : AppCompatActivity() {

    @Inject
    lateinit var roomApiService: RoomApiService

    private lateinit var auth: FirebaseAuth
    private lateinit var etNombre: EditText
    private lateinit var etPrecio: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var etCapacidad: EditText
    private lateinit var tvServiciosSeleccionados: TextView
    private lateinit var btnAddImage: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var recyclerImages: RecyclerView
    private lateinit var btnUpdate: Button
    private lateinit var btnCancel: Button

    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private val existingImageUrls = mutableListOf<String>()
    private val newImageUris = mutableListOf<Uri>()
    private lateinit var imageAdapter: EditRoomImageAdapter

    private lateinit var room: RoomData

    private val MAX_NOMBRE = 50
    private val MAX_DESCRIPCION = 300
    private val MAX_IMAGES = 10
    private val MAX_PERSONAS = 4

    private val serviciosDisponibles = arrayOf(
        "Agua, luz y gas incluidos",
        "Wi-Fi",
        "Baño privado",
        "Baño compartido",
        "Acceso a cocina",
        "Mobiliario básico",
        "Lavadora",
    )

    private val serviciosSeleccionados = BooleanArray(serviciosDisponibles.size)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            handleSelectedImages(uris)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            val totalImages = existingImageUrls.size + newImageUris.size
            if (totalImages >= MAX_IMAGES) {
                showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes")
                currentPhotoFile?.delete()
            } else {
                newImageUris.add(currentPhotoUri!!)
                updateImageGrid()
            }
        } else {
            currentPhotoFile?.delete()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            openGallery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_room)

        auth = FirebaseAuth.getInstance()

        // Recibir el cuarto a editar
        room = intent.getSerializableExtra("room") as? RoomData ?: run {
            Toast.makeText(this, "Error: No se pudo cargar el cuarto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadRoomData()
        setupListeners()
    }

    private fun initViews() {
        etNombre = findViewById(R.id.et_nombre)
        etPrecio = findViewById(R.id.et_precio)
        etDescripcion = findViewById(R.id.et_descripcion)
        etCapacidad = findViewById(R.id.et_capacidad)
        tvServiciosSeleccionados = findViewById(R.id.tv_servicios_seleccionados)
        btnAddImage = findViewById(R.id.btn_add_image)
        recyclerImages = findViewById(R.id.recycler_images)
        btnUpdate = findViewById(R.id.btn_update)
        btnCancel = findViewById(R.id.btn_cancel)

        // Configurar RecyclerView de imágenes
        recyclerImages.layoutManager = GridLayoutManager(this, 3)
        imageAdapter = EditRoomImageAdapter(
            existingImages = existingImageUrls,
            newImages = newImageUris,
            onRemoveExisting = { url ->
                existingImageUrls.remove(url)
                updateImageGrid()
            },
            onRemoveNew = { uri ->
                newImageUris.remove(uri)
                updateImageGrid()
            }
        )
        recyclerImages.adapter = imageAdapter
    }

    private fun loadRoomData() {
        // Cargar datos del cuarto
        etNombre.setText(room.nombre)
        etPrecio.setText(room.precio.toString())
        etDescripcion.setText(room.descripcion)
        etCapacidad.setText(room.capacidad.toString())

        // Cargar imágenes existentes
        existingImageUrls.addAll(room.imagenes)
        updateImageGrid()

        // Cargar servicios
        val serviciosActuales = room.servicios.firstOrNull()?.split(",") ?: emptyList()
        serviciosDisponibles.forEachIndexed { index, servicio ->
            serviciosSeleccionados[index] = serviciosActuales.any { it.trim() == servicio }
        }
        updateServiciosText()

        supportActionBar?.title = "Editar: ${room.nombre}"
    }

    private fun setupListeners() {
        btnAddImage.setOnClickListener {
            showImageSourceDialog()
        }

        tvServiciosSeleccionados.setOnClickListener {
            showServiciosDialog()
        }

        btnUpdate.setOnClickListener {
            updateRoom()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun showImageSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_source, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<LinearLayout>(R.id.option_take_photo).setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }

        dialogView.findViewById<LinearLayout>(R.id.option_gallery).setOnClickListener {
            dialog.dismiss()
            checkStoragePermissionAndOpenGallery()
        }

        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> openCamera()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermissionAndOpenGallery() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            openGallery()
        } else {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED -> openGallery()
                else -> storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun openCamera() {
        val totalImages = existingImageUrls.size + newImageUris.size
        if (totalImages >= MAX_IMAGES) {
            showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes")
            return
        }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            val photoFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)

            currentPhotoFile = photoFile
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )

            takePictureLauncher.launch(currentPhotoUri!!)
        } catch (e: Exception) {
            Log.e("EditRoomActivity", "Error al abrir cámara: ${e.message}", e)
            Toast.makeText(this, "Error al abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val totalImages = existingImageUrls.size + newImageUris.size
        val remainingSlots = MAX_IMAGES - totalImages
        if (remainingSlots <= 0) {
            showWarningDialog("Límite alcanzado", "Ya tienes $MAX_IMAGES imágenes")
            return
        }

        pickImagesLauncher.launch("image/*")
    }

    private fun handleSelectedImages(uris: List<Uri>) {
        val totalImages = existingImageUrls.size + newImageUris.size
        val remainingSlots = MAX_IMAGES - totalImages

        if (uris.size > remainingSlots) {
            showWarningDialog(
                "Límite de imágenes",
                "Solo puedes agregar $remainingSlots imagen(es) más"
            )
            newImageUris.addAll(uris.take(remainingSlots))
        } else {
            newImageUris.addAll(uris)
        }

        updateImageGrid()
    }

    private fun updateImageGrid() {
        imageAdapter.notifyDataSetChanged()
    }

    private fun showServiciosDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_servicios_selection, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnAccept = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_accept)

        checkboxContainer.removeAllViews()

        serviciosDisponibles.forEachIndexed { index, servicio ->
            val checkbox = layoutInflater.inflate(R.layout.item_checkbox_servicio, checkboxContainer, false) as CheckBox
            checkbox.text = servicio
            checkbox.isChecked = serviciosSeleccionados[index]
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                serviciosSeleccionados[index] = isChecked
            }
            checkboxContainer.addView(checkbox)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            updateServiciosText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateServiciosText() {
        val seleccionados = mutableListOf<String>()
        for (i in serviciosDisponibles.indices) {
            if (serviciosSeleccionados[i]) {
                seleccionados.add(serviciosDisponibles[i])
            }
        }

        if (seleccionados.isEmpty()) {
            tvServiciosSeleccionados.text = "Seleccionar servicios"
            tvServiciosSeleccionados.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            tvServiciosSeleccionados.text = seleccionados.joinToString(", ")
            tvServiciosSeleccionados.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun getServiciosString(): String {
        val seleccionados = mutableListOf<String>()
        for (i in serviciosDisponibles.indices) {
            if (serviciosSeleccionados[i]) {
                seleccionados.add(serviciosDisponibles[i])
            }
        }
        return seleccionados.joinToString(",")
    }

    private fun updateRoom() {
        val nombre = etNombre.text.toString().trim()
        val precioStr = etPrecio.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()
        val capacidadStr = etCapacidad.text.toString().trim()
        val serviciosStr = getServiciosString()

        // Validaciones
        if (nombre.isEmpty() || nombre.length < 5) {
            showWarningDialog("Nombre inválido", "El nombre debe tener al menos 5 caracteres")
            return
        }

        val precio = precioStr.toDoubleOrNull()
        if (precio == null || precio <= 0) {
            showWarningDialog("Precio inválido", "El precio debe ser mayor a 0")
            return
        }

        val capacidad = capacidadStr.toIntOrNull()
        if (capacidad == null || capacidad < 1 || capacidad > MAX_PERSONAS) {
            showWarningDialog("Capacidad inválida", "La capacidad debe estar entre 1 y $MAX_PERSONAS")
            return
        }

        if (serviciosStr.isEmpty()) {
            showWarningDialog("Servicios", "Selecciona al menos un servicio")
            return
        }

        val totalImages = existingImageUrls.size + newImageUris.size
        if (totalImages == 0) {
            showWarningDialog("Imágenes", "Debes tener al menos 1 imagen")
            return
        }

        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
            titleText = "Actualizando"
            contentText = "Guardando cambios..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // ✅ Preparar todos los campos obligatorios
                val nombreBody = nombre.toRequestBody("text/plain".toMediaTypeOrNull())
                val precioBody = precio.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionBody = descripcion.toRequestBody("text/plain".toMediaTypeOrNull())
                val capacidadBody = capacidad.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val disponibleBody = room.disponible.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val serviciosBody = serviciosStr.toRequestBody("text/plain".toMediaTypeOrNull())
                val ubicacionBody = room.ubicacion.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody = room.userId.toRequestBody("text/plain".toMediaTypeOrNull())

                // Preparar nuevas imágenes (si las hay)
                val imageParts = if (newImageUris.isNotEmpty()) {
                    prepareImageParts(newImageUris)
                } else {
                    null
                }

                Log.d("EditRoomActivity", "📤 Actualizando cuarto con ${imageParts?.size ?: 0} imágenes nuevas")

                // ✅ Llamar a la API con TODOS los campos
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
                    nuevasImagenes = imageParts
                )

                progressDialog.dismiss()

                if (response.isSuccessful) {
                    SweetAlertDialog(this@EditRoomActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                        setTitleText("¡Actualizado!")
                        setContentText("El cuarto ha sido actualizado correctamente")
                        setConfirmText("OK")
                        setConfirmClickListener {
                            it.dismiss()
                            setResult(RESULT_OK) // ✅ Notificar que se actualizó
                            finish()
                        }
                        show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("EditRoomActivity", "❌ Error ${response.code()}: $errorBody")
                    showErrorDialog("Error", "No se pudo actualizar: ${response.code()}")
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("EditRoomActivity", "Error: ${e.message}", e)
                showErrorDialog("Error de conexión", "No se pudo actualizar el cuarto")
            }
        }
    }

    private fun prepareImageParts(uris: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        uris.forEachIndexed { index, uri ->
            try {
                val fileName = getFileName(uri) ?: "imagen_$index.jpg"
                val tempFile = File(cacheDir, fileName)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("NuevasImagenes", fileName, requestBody)
                parts.add(part)
            } catch (e: Exception) {
                Log.e("EditRoomActivity", "Error preparando imagen: ${e.message}", e)
            }
        }
        return parts
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun showWarningDialog(title: String, message: String) {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .show()
    }
}