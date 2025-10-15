package com.example.roomuttt.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomuttt.R
import com.example.roomuttt.ui.auth.LoginActivity
import com.example.roomuttt.ui.profile.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect  // Import para collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var auth: FirebaseAuth
    private lateinit var ivBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var ivClose: ImageView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var tvName: TextView

    private lateinit var tvCareer: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnCreateRenter: Button
    private lateinit var btnDeleteProfile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        // Inicializar vistas
        ivBack = findViewById(R.id.iv_back)
        tvTitle = findViewById(R.id.tv_title)
        ivClose = findViewById(R.id.iv_close)
        ivProfilePhoto = findViewById(R.id.iv_profile_photo)
        tvName = findViewById(R.id.tv_name)

        tvEmail = findViewById(R.id.tv_email)
        btnEdit = findViewById(R.id.btn_edit)
        btnCreateRenter = findViewById(R.id.btn_create_renter)
        btnDeleteProfile = findViewById(R.id.btn_delete_profile)

        tvTitle.text = "Perfil"

        // Cargar datos del usuario (llama getUserProfile y collect en userProfile)
        loadUserData()

        // Back button
        ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Close button
        ivClose.setOnClickListener {
            finish()
        }

        // Editar perfil
        btnEdit.setOnClickListener {
            showEditDialog()
        }

        // Crear cuenta de arrendatario
        btnCreateRenter.setOnClickListener {
            Toast.makeText(this, "Crear cuenta de arrendatario (implementar formulario)", Toast.LENGTH_SHORT).show()
        }

        // Eliminar perfil
        btnDeleteProfile.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun loadUserData() {
        // Llama getUserProfile para iniciar carga
        viewModel.getUserProfile()

        lifecycleScope.launch {
            // Collect en userProfile (StateFlow<User?>) – ahora resuelve el error
            viewModel.userProfile.collect { user ->
                if (user != null) {
                    tvName.text = user.name ?: "Nombre no disponible"  // Placeholder si null

                    tvEmail.text = user.email ?: "Email no disponible"
                    // Carga foto si tienes URL (e.g., Glide)
                    // Glide.with(this@ProfileActivity).load(user.photoUrl).into(ivProfilePhoto)
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

        // Prellena con datos actuales (usa valores actuales del TextView)
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
                    loadUserData()  // Recarga
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