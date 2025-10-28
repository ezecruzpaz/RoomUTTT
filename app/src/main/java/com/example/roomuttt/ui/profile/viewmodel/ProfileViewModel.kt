package com.example.roomuttt.ui.profile.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.data.repository.AuthRepository
import com.example.roomuttt.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    // Estado para verificar si es arrendatario
    private val _isRenter = MutableStateFlow(false)
    val isRenter: StateFlow<Boolean> = _isRenter.asStateFlow()

    fun getUserProfile() {
        viewModelScope.launch {
            // Fetch desde Auth (uid y email)
            val currentUser = repository.getCurrentUserFromAuth()
            _userProfile.value = currentUser

            // Verificar si es arrendatario
            checkIfUserIsRenter()
        }
    }

    // Función para verificar si el usuario existe en la colección "renters"
    private fun checkIfUserIsRenter() {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch

                val renterDoc = firestore.collection("renters")
                    .document(uid)
                    .get()
                    .await()

                _isRenter.value = renterDoc.exists()
                Log.d("ProfileViewModel", "¿Es arrendatario?: ${_isRenter.value}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error verificando arrendatario: ${e.message}")
                _isRenter.value = false
            }
        }
    }

    fun updateProfile(name: String, email: String) {
        viewModelScope.launch {
            repository.updateUserProfile(name, email)
            getUserProfile()  // Recarga
        }
    }

    fun deleteProfile() {
        viewModelScope.launch {
            repository.deleteCurrentUser()
        }
    }

    // Función para desactivar modo arrendatario (elimina documento de renters)
    suspend fun disableRenterMode() {
        try {
            val userId = auth.currentUser?.uid ?: return

            // Eliminar el documento de la colección "renters"
            firestore.collection("renters")
                .document(userId)
                .delete()
                .await()

            // Actualizar el StateFlow local
            _isRenter.value = false

            Log.d("ProfileViewModel", "Modo arrendatario desactivado correctamente")
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error al desactivar modo arrendatario: ${e.message}")
        }
    }

    suspend fun uploadProfilePhoto(imageUri: Uri): Result<String> {
        val userId = repository.getCurrentUserFromAuth()?.uid
            ?: return Result.failure(Exception("Usuario no autenticado"))

        val result = repository.uploadProfilePhoto(userId, imageUri)

        if (result.isSuccess) {
            Log.d("ProfileViewModel", "Foto subida exitosamente")
            getUserProfile() // Recargar perfil
        } else {
            Log.e("ProfileViewModel", "Error subiendo foto: ${result.exceptionOrNull()?.message}")
        }

        return result
    }
}