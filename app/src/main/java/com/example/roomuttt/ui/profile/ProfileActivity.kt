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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        btnCreateRenter.setOnClickListener {
            Toast.makeText(this, "Crear cuenta de arrendatario", Toast.LENGTH_SHORT).show()
        }

        btnDeleteProfile.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Tomar foto", "Elegir de galería", "Cancelar")

        AlertDialog.Builder(this)
            .setTitle("Cambiar foto de perfil")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> checkGalleryPermissionAndOpen()
                    2 -> dialog.dismiss()
                }
            }
            .show()
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

        // Mostrar la imagen inmediatamente
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(ivProfilePhoto)

        // Subir a Firebase Storage
        Toast.makeText(this, "Subiendo imagen...", Toast.LENGTH_SHORT).show()
        viewModel.uploadProfilePhoto(uri)

        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            loadUserData()
            Toast.makeText(this@ProfileActivity, "Foto actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        viewModel.getUserProfile()

        lifecycleScope.launch {
            viewModel.userProfile.collect { user ->
                if (user != null) {
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
                } else {
                    Toast.makeText(this@ProfileActivity, "Error cargando perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etEmail = dialogView.findViewById<EditText>(R.id.et_email)

        etName.setText(tvName.text)
        etEmail.setText(tvEmail.text)

        AlertDialog.Builder(this)
            .setTitle("Editar Perfil")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = etName.text.toString().trim()
                val newEmail = etEmail.text.toString().trim()

                if (newName.isNotEmpty() && newEmail.isNotEmpty()) {
                    viewModel.updateProfile(newName, newEmail)
                    loadUserData()
                    Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Perfil")
            .setMessage("¿Estás seguro? Esto eliminará tu cuenta permanentemente.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteProfile()
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                Toast.makeText(this, "Perfil eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}