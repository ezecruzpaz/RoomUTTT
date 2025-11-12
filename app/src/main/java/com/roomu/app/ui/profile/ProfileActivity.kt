package com.roomu.app.ui.profile

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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.roomu.app.R
import com.roomu.app.ui.auth.LoginActivity
import com.roomu.app.ui.home.MainActivity
import com.roomu.app.ui.profile.viewmodel.ProfileViewModel
import com.roomu.app.ui.renter.RenterRegistrationActivity
import com.roomu.app.ui.room.AllRoomsActivity
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView

    // ✅ ViewModel compartido para obtener los cuartos
    private val mainViewModel: com.roomu.app.ui.home.viewmodel.MainViewModel by viewModels()

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
    private lateinit var layoutRenterStatus: LinearLayout
    private lateinit var switchRenterMode: SwitchMaterial

    // URI temporal para la foto de cámara
    private var tempCameraUri: Uri? = null

    // Flag para evitar mensaje de error en primera carga
    private var isFirstLoad = true

    // Flag para evitar loops en el listener del switch
    private var isUpdatingSwitch = false

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
        setupBottomNavigation()
        loadUserData()

        // ✅ Cargar cuartos - la ubicación ya se cargó en el init del ViewModel
        lifecycleScope.launch {
            mainViewModel.loadRooms()

            // ✅ Esperar a que se carguen
            mainViewModel.allRooms.first { it.isNotEmpty() }

            // ✅ Aplicar filtro con ubicación guardada
            mainViewModel.currentLocation.value?.let { location ->
                mainViewModel.updateLocationAndFilter(location.latitude, location.longitude)
                Log.d("ProfileActivity", "✅ Filtro aplicado: ${location.latitude}, ${location.longitude}")
            }
        }
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
        layoutRenterStatus = findViewById(R.id.layout_renter_status)
        switchRenterMode = findViewById(R.id.switch_renter_mode)
        bottomNavigation = findViewById(R.id.bottom_navigation) // ✅ ID correcto del XML

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

        // Observar el estado de arrendatario
        lifecycleScope.launch {
            viewModel.isRenter.collect { isRenter ->
                isUpdatingSwitch = true

                if (isRenter) {
                    // Ocultar botón y mostrar switch
                    btnCreateRenter.visibility = android.view.View.GONE
                    layoutRenterStatus.visibility = android.view.View.VISIBLE
                    switchRenterMode.isChecked = true
                } else {
                    // Mostrar botón
                    btnCreateRenter.visibility = android.view.View.VISIBLE
                    layoutRenterStatus.visibility = android.view.View.GONE
                    switchRenterMode.isChecked = false
                }

                isUpdatingSwitch = false
            }
        }

        // Listener para el switch de modo arrendatario (SOLO DESACTIVAR)
        switchRenterMode.setOnCheckedChangeListener { _, isChecked ->
            // Ignorar cambios automáticos del observer
            if (isUpdatingSwitch) return@setOnCheckedChangeListener

            if (!isChecked) {
                // Cuando se desactiva, confirmar y redirigir
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("¿Desactivar modo arrendatario?")
                    .setContentText("Dejarás de ver y publicar propiedades. Podrás reactivarlo cuando quieras desde tu perfil.")
                    .setConfirmText("Sí, desactivar")
                    .setConfirmClickListener { dialog ->
                        dialog.dismissWithAnimation()

                        // Mostrar progreso
                        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                            progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
                            titleText = "Desactivando"
                            contentText = "Eliminando cuenta de arrendatario..."
                            setCancelable(false)
                            show()
                        }

                        // Desactivar modo arrendatario en Firebase (elimina el documento)
                        lifecycleScope.launch {
                            viewModel.disableRenterMode()

                            kotlinx.coroutines.delay(1000)
                            progressDialog.dismiss()

                            // Redirigir al MainActivity
                            val intent = Intent(this@ProfileActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                    .setCancelButton("Cancelar") { dialog ->
                        // Si cancela, volver a activar el switch
                        isUpdatingSwitch = true
                        switchRenterMode.isChecked = true
                        isUpdatingSwitch = false
                        dialog.dismiss()
                    }
                    .show()
            } else {
                // Si intenta activar el switch, redirigir al registro
                isUpdatingSwitch = true
                switchRenterMode.isChecked = false
                isUpdatingSwitch = false

                SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                    .setTitleText("Crear cuenta de arrendatario")
                    .setContentText("Para activar el modo arrendatario, necesitas completar el registro")
                    .setConfirmText("Ir al registro")
                    .setConfirmClickListener {
                        it.dismiss()
                        startActivity(Intent(this, RenterRegistrationActivity::class.java))
                    }
                    .setCancelButton("Cancelar") { it.dismiss() }
                    .show()
            }
        }

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

        // Subir a Firebase Storage y ESPERAR el resultado
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
        val etNewPassword = dialogView.findViewById<EditText>(R.id.et_new_password)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.et_confirm_password)
        val layoutPasswordWarning = dialogView.findViewById<LinearLayout>(R.id.layout_password_warning)
        val btnCancel = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel)
        val btnUpdate = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_update)

        // Prellenar con datos actuales
        etName.setText(tvName.text)
        etEmail.setText(tvEmail.text)

        // Crear diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Mostrar advertencia si se está escribiendo contraseña
        val passwordWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasPassword = !etNewPassword.text.isNullOrEmpty() || !etConfirmPassword.text.isNullOrEmpty()
                layoutPasswordWarning.visibility = if (hasPassword) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        etNewPassword.addTextChangedListener(passwordWatcher)
        etConfirmPassword.addTextChangedListener(passwordWatcher)

        // Botón Cancelar
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Botón Actualizar
        btnUpdate.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newEmail = etEmail.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validaciones
            if (newName.isEmpty()) {
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Campo requerido")
                    .setContentText("El nombre no puede estar vacío")
                    .show()
                return@setOnClickListener
            }

            if (newEmail.isEmpty()) {
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Campo requerido")
                    .setContentText("El email no puede estar vacío")
                    .show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Email inválido")
                    .setContentText("Por favor ingresa un email válido")
                    .show()
                return@setOnClickListener
            }

            // Validar contraseñas si se están cambiando
            val isChangingPassword = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()

            if (isChangingPassword) {
                if (newPassword.length < 6) {
                    SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Contraseña muy corta")
                        .setContentText("La contraseña debe tener al menos 6 caracteres")
                        .show()
                    return@setOnClickListener
                }

                if (newPassword != confirmPassword) {
                    SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Contraseñas no coinciden")
                        .setContentText("Las contraseñas deben ser iguales")
                        .show()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()

            // Mostrar progreso
            val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                progressHelper.barColor = android.graphics.Color.parseColor("#A5DC86")
                titleText = "Actualizando"
                contentText = "Guardando cambios..."
                setCancelable(false)
                show()
            }

            lifecycleScope.launch {
                try {
                    // Actualizar perfil básico
                    viewModel.updateProfile(newName, newEmail)

                    // Si se está cambiando contraseña
                    if (isChangingPassword) {
                        val result = viewModel.updatePassword(newPassword)

                        progressDialog.dismiss()

                        if (result.isSuccess) {
                            // Éxito con cambio de contraseña
                            SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                                setTitleText("¡Perfil actualizado!")
                                setContentText("Tu contraseña ha sido cambiada. Por seguridad, debes iniciar sesión nuevamente.")
                                setConfirmText("Iniciar sesión")
                                setConfirmClickListener { successDialog ->
                                    successDialog.dismissWithAnimation()

                                    // Cerrar sesión
                                    auth.signOut()

                                    // Redirigir a Login
                                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                                setCancelable(false)
                                show()
                            }
                        } else {
                            // Error al cambiar contraseña
                            SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.ERROR_TYPE).apply {
                                setTitleText("Error")
                                setContentText("No se pudo cambiar la contraseña: ${result.exceptionOrNull()?.message}")
                                show()
                            }
                        }
                    } else {
                        // Solo actualización de perfil (sin contraseña)
                        kotlinx.coroutines.delay(1000)
                        progressDialog.dismiss()
                        loadUserData()

                        SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE).apply {
                            setTitleText("¡Perfecto!")
                            setContentText("Tu perfil ha sido actualizado")
                            setConfirmText("OK")
                            show()
                        }
                    }

                } catch (e: Exception) {
                    progressDialog.dismiss()
                    SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.ERROR_TYPE).apply {
                        setTitleText("Error")
                        setContentText("No se pudo actualizar el perfil: ${e.message}")
                        show()
                    }
                }
            }
        }

        dialog.show()
    }
    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // ✅ Verificar si es arrendatario para redirigir correctamente
                    lifecycleScope.launch {
                        val isRenter = viewModel.isRenter.value

                        val intent = if (isRenter) {
                            // Si es arrendatario, ir al Dashboard de Arrendatario
                            Intent(this@ProfileActivity, com.roomu.app.ui.renter.RenterDashboardActivity::class.java)
                        } else {
                            // Si es usuario normal, ir a MainActivity
                            Intent(this@ProfileActivity, MainActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    true
                }

                R.id.nav_rooms -> {
                    lifecycleScope.launch {
                        val isRenter = viewModel.isRenter.value
                        val uid = auth.currentUser?.uid

                        if (uid == null) {
                            Toast.makeText(this@ProfileActivity, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        if (isRenter) {
                            mainViewModel.loadRooms()
                            val allRooms = mainViewModel.allRooms.first { it.isNotEmpty() }
                            val myRooms = allRooms.filter { it.userId == uid }

                            if (myRooms.isNotEmpty()) {
                                val intent = Intent(this@ProfileActivity, AllRoomsActivity::class.java).apply {
                                    putExtra("allRooms", ArrayList(myRooms))
                                    putExtra("isRenterView", true)
                                    putExtra("fromRenterDashboard", true)
                                }
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@ProfileActivity, "Aún no tienes cuartos publicados", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val filteredRooms = mainViewModel.getAllRooms()
                            if (filteredRooms.isNotEmpty()) {
                                val intent = Intent(this@ProfileActivity, AllRoomsActivity::class.java).apply {
                                    putExtra("allRooms", ArrayList(filteredRooms))
                                }
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@ProfileActivity, "No hay cuartos disponibles en tu área", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }

                R.id.nav_chat -> {
                    lifecycleScope.launch {
                        val isRenter = viewModel.isRenter.value
                        val intent = Intent(this@ProfileActivity, com.roomu.app.ui.chat.ChatsListActivity::class.java).apply {
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

    private fun showDeleteDialog() {
        // Crear vista personalizada para el diálogo
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val etConfirmation = dialogView.findViewById<EditText>(R.id.et_confirmation)
        val tvWarning = dialogView.findViewById<TextView>(R.id.tv_warning)

        // Verificar si es renter para mostrar advertencia adicional
        lifecycleScope.launch {
            val isRenter = viewModel.isRenter.value

            if (isRenter) {
                tvWarning.text = """
                ⚠️ ADVERTENCIA
                
                Esta acción es PERMANENTE y NO se puede deshacer.
                
                Se eliminará:
                • Tu cuenta y perfil
                • Tu información personal
                • TODOS tus cuartos publicados
                • Todas tus fotos
                
                Para confirmar, escribe: ELIMINAR
            """.trimIndent()
            } else {
                tvWarning.text = """
                ⚠️ ADVERTENCIA
                
                Esta acción es PERMANENTE y NO se puede deshacer.
                
                Se eliminará:
                • Tu cuenta y perfil
                • Tu información personal
                • Tu foto de perfil
                
                Para confirmar, escribe: ELIMINAR
            """.trimIndent()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_confirm_delete).setOnClickListener {
            val confirmation = etConfirmation.text.toString().trim()

            if (confirmation.equals("ELIMINAR", ignoreCase = true)) {
                dialog.dismiss()
                performCompleteAccountDeletion()
            } else {
                SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Confirmación incorrecta")
                    .setContentText("Debes escribir exactamente: ELIMINAR")
                    .show()
            }
        }

        dialog.show()
    }

    private fun performCompleteAccountDeletion() {
        // Mostrar progreso
        val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            progressHelper.barColor = android.graphics.Color.parseColor("#D32F2F")
            titleText = "Eliminando cuenta"
            contentText = "Por favor espera, esto puede tardar unos segundos..."
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // Ejecutar eliminación completa
                val result = viewModel.deleteAccountCompletely()

                progressDialog.dismiss()

                if (result.isSuccess) {
                    // Éxito
                    SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("Cuenta eliminada")
                        .setContentText("Tu cuenta y todos tus datos han sido eliminados permanentemente")
                        .setConfirmText("OK")
                        .setConfirmClickListener { dialog ->
                            dialog.dismissWithAnimation()

                            // Redirigir a Login
                            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .show()
                } else {
                    // Error
                    SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText("No se pudo eliminar la cuenta: ${result.exceptionOrNull()?.message}")
                        .setConfirmText("Reintentar")
                        .setConfirmClickListener { dialog ->
                            dialog.dismissWithAnimation()
                            performCompleteAccountDeletion()
                        }
                        .setCancelButton("Cancelar") { dialog ->
                            dialog.dismissWithAnimation()
                        }
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()

                SweetAlertDialog(this@ProfileActivity, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("Error inesperado")
                    .setContentText("Ocurrió un error: ${e.message}")
                    .setConfirmText("OK")
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ No seleccionar ningún item al volver a la actividad
        bottomNavigation.menu.setGroupCheckable(0, true, false)
    }
}