package com.example.roomuttt.ui.profile

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.roomuttt.R
import com.example.roomuttt.ui.auth.LoginActivity
import com.example.roomuttt.ui.profile.viewmodel.ProfileViewModel
import com.example.roomuttt.ui.renter.RenterRegistrationActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import cn.pedant.SweetAlert.SweetAlertDialog

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var auth: FirebaseAuth

    // Vistas
    private lateinit var ivBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var ivClose: ImageView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnCreateRenter: Button
    private lateinit var btnDeleteProfile: Button

    // URI temporal para la foto de cámara
    private var tempCameraUri: Uri? = null

    // Flag para evitar mensaje de error en primera carga
    private var isFirstLoad = true

    // Launcher para seleccionar imagen de galería
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadProfileImage(it)
        }
    }

    // Launcher para tomar foto con cámara
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            uploadProfileImage(tempCameraUri!!)
        }
    }

    // Launcher para permisos de cámara
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para permisos de galería (Android 13+)
    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "Permiso de galería denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        initViews()
        setupListeners()
        loadUserData()
    }

    private fun initViews() {
        ivBack = findViewById(R.id.iv_back)
        tvTitle = findViewById(R.id.tv_title)
        ivClose = findViewById(R.id.iv_close)
        ivProfilePhoto = findViewById(R.id.iv_profile_photo)
        fabChangePhoto = findViewById(R.id.fab_change_photo)
        tvName = findViewById(R.id.tv_name)
        tvEmail = findViewById(R.id.tv_email)
        btnEdit = findViewById(R.id.btn_edit)
        btnCreateRenter = findViewById(R.id.btn_create_renter)
        btnDeleteProfile = findViewById(R.id.btn_delete_profile)

        tvTitle.text = "Perfil"
    }

    private fun setupListeners() {
        ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        ivClose.setOnClickListener {
            finish()
        }

        // Mostrar diálogo para elegir entre cámara o galería
        fabChangePhoto.setOnClickListener {
            showImageSourceDialog()
        }

        btnEdit.setOnClickListener {
            showEditDialog()
        }

        val btnCreateRenter = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_create_renter)
        btnCreateRenter.setOnClickListener {
            startActivity(Intent(this, RenterRegistrationActivity::class.java))
        }

        btnDeleteProfile.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showImageSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_photo, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Hacer el fondo transparente para que se vean los bordes redondeados
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Configurar listeners
        dialogView.findViewById<LinearLayout>(R.id.option_take_photo).setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }

        dialogView.findViewById<LinearLayout>(R.id.option_gallery).setOnClickListener {
            dialog.dismiss()
            checkGalleryPermissionAndOpen()
        }

        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkGalleryPermissionAndOpen() {
        // Android 13+ requiere READ_MEDIA_IMAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                else -> {
                    galleryPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            // Android 12 y anteriores
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                else -> {
                    galleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun openCamera() {
        // Crear URI temporal para guardar la foto
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "Foto de perfil")
            put(MediaStore.Images.Media.DESCRIPTION, "Tomada desde RoomUTTT")
        }

        tempCameraUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        tempCameraUri?.let {
            takePictureLauncher.launch(it)
        } ?: run {
            Toast.makeText(this, "Error al crear archivo temporal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun uploadProfileImage(uri: Uri) {
        Log.d("ProfileActivity", "Imagen seleccionada: $uri")

        // Mostrar la imagen inmediatamente (vista previa)
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(ivProfilePhoto)

        // Mostrar diálogo de progreso
        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
            titleText = "Subiendo imagen"
            contentText = "Actualizando tu foto de perfil..."
            setCancelable(false)
            show()
        }

        // 🔥 Subir a Firebase Storage y ESPERAR el resultado
        lifecycleScope.launch {
            val result = viewModel.uploadProfilePhoto(uri)

            // Cerrar el diálogo de progreso
            progressDialog.dismiss()

            if (result.isSuccess) {
                // Recargar los datos del usuario para obtener la URL actualizada
                loadUserData()

                // Pequeño delay para asegurar que la imagen se cargue
                kotlinx.coroutines.delay(700)

                // Mensaje de éxito
                SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE)
                    .setTitleText("¡Listo!")
                    .setContentText("Foto de perfil actualizada correctamente")
                    .setConfirmText("OK")
                    .show()
            } else {
                // Mensaje de error
                SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("Error")
                    .setContentText("No se pudo actualizar la foto. Intenta de nuevo.")
                    .setConfirmText("OK")
                    .show()
            }
        }
    }

    private fun loadUserData() {
        viewModel.getUserProfile()

        lifecycleScope.launch {
            viewModel.userProfile.collect { user ->
                // Solo mostrar error si no es la primera carga y el usuario sigue siendo null
                if (user != null) {
                    isFirstLoad = false
                    Log.d("ProfileActivity", "Usuario: photoUrl=${user.photoUrl}")

                    tvName.text = user.name ?: "Nombre no disponible"
                    tvEmail.text = user.email ?: "Email no disponible"

                    // Cargar foto de perfil
                    if (!user.photoUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(user.photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(ivProfilePhoto)
                    } else {
                        ivProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder)
                    }
                } else if (!isFirstLoad) {
                    // Solo mostrar error si ya intentamos cargar antes
                    Toast.makeText(this@ProfileActivity, "Error cargando perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etEmail = dialogView.findViewById<EditText>(R.id.et_email)
        val btnUpdate = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_update)

        etName.setText(tvName.text)
        etEmail.setText(tvEmail.text)

        // Crear diálogo sin título (ya que el layout tiene su propio título)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Configurar el botón de actualizar
        btnUpdate.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newEmail = etEmail.text.toString().trim()

            if (newName.isNotEmpty() && newEmail.isNotEmpty()) {
                dialog.dismiss()

                // Mostrar progreso
                val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                    progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
                    titleText = "Actualizando"
                    contentText = "Guardando cambios..."
                    setCancelable(false)
                    show()
                }

                viewModel.updateProfile(newName, newEmail)

                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    progressDialog.dismiss()
                    loadUserData()

                    // Mensaje de éxito
                    SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("¡Perfecto!")
                        .setContentText("Tu perfil ha sido actualizado")
                        .setConfirmText("OK")
                        .show()
                }
            } else {
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Campos vacíos")
                    .setContentText("Por favor completa todos los campos")
                    .setConfirmText("OK")
                    .show()
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog() {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("¿Eliminar perfil?")
            .setContentText("Esta acción eliminará tu cuenta permanentemente y no se puede deshacer")
            .setConfirmText("Sí, eliminar")
            .setConfirmClickListener { sDialog ->
                sDialog.dismissWithAnimation()

                // Mostrar progreso
                val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                    progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
                    titleText = "Eliminando"
                    contentText = "Eliminando tu cuenta..."
                    setCancelable(false)
                    show()
                }

                viewModel.deleteProfile()

                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    progressDialog.dismiss()
                    auth.signOut()

                    // Mensaje de confirmación
                    SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("Cuenta eliminada")
                        .setContentText("Tu perfil ha sido eliminado correctamente")
                        .setConfirmText("OK")
                        .setConfirmClickListener {
                            it.dismiss()
                            startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                            finish()
                        }
                        .show()
                }
            }
            .setCancelButton("Cancelar") { it.dismiss() }
            .show()
    }
}