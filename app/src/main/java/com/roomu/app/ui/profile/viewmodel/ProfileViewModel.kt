package com.roomu.app.ui.profile.viewmodel

import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.roomu.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore

) : ViewModel() {

    private val _userProfile = MutableStateFlow<com.roomu.app.domain.model.User?>(null)
    val userProfile: StateFlow<com.roomu.app.domain.model.User?> = _userProfile.asStateFlow()
    private val storage = FirebaseStorage.getInstance()


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

    suspend fun deleteAccountCompletely(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            Log.d(TAG, "🗑️ Iniciando eliminación completa de cuenta: $uid")

            // 1. Verificar si es arrendatario
            val isRenterUser = firestore.collection("renters")
                .document(uid)
                .get()
                .await()
                .exists()

            if (isRenterUser) {
                Log.d(TAG, "👤 Usuario es arrendatario, eliminando cuartos...")

                // 2. Obtener todos los cuartos del usuario
                val userRooms = firestore.collection("cuartos")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()

                Log.d(TAG, "🏠 Encontrados ${userRooms.size()} cuartos para eliminar")

                // 3. Eliminar cada cuarto y sus imágenes
                userRooms.documents.forEach { roomDoc ->
                    try {
                        val roomId = roomDoc.id
                        val images = roomDoc.get("imagenes") as? List<String> ?: emptyList()

                        // Eliminar imágenes de Storage
                        images.forEach { imageUrl ->
                            try {
                                val imageRef = storage.getReferenceFromUrl(imageUrl)
                                imageRef.delete().await()
                                Log.d(TAG, "✅ Imagen eliminada: $imageUrl")
                            } catch (e: Exception) {
                                Log.e(TAG, "⚠️ Error eliminando imagen: ${e.message}")
                            }
                        }

                        // Eliminar documento del cuarto
                        firestore.collection("cuartos").document(roomId).delete().await()
                        Log.d(TAG, "✅ Cuarto eliminado: $roomId")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error eliminando cuarto: ${e.message}")
                    }
                }

                // 4. Eliminar documento de renters
                firestore.collection("renters").document(uid).delete().await()
                Log.d(TAG, "✅ Documento de renter eliminado")
            }

            // 5. Eliminar foto de perfil del usuario (si existe)
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val photoUrl = userDoc.getString("photoUrl")

                if (!photoUrl.isNullOrEmpty() && photoUrl.startsWith("https://firebasestorage")) {
                    val photoRef = storage.getReferenceFromUrl(photoUrl)
                    photoRef.delete().await()
                    Log.d(TAG, "✅ Foto de perfil eliminada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error eliminando foto de perfil: ${e.message}")
            }

            // 6. Eliminar documento de users
            firestore.collection("users").document(uid).delete().await()
            Log.d(TAG, "✅ Documento de usuario eliminado")

            // 7. Eliminar cuenta de Authentication (ÚLTIMO PASO)
            auth.currentUser?.delete()?.await()
            Log.d(TAG, "✅ Cuenta de Authentication eliminada")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en eliminación completa: ${e.message}")
            Result.failure(e)
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

    // Agregar esta función en ProfileViewModel.kt

    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            // Actualizar contraseña en Firebase Authentication
            user.updatePassword(newPassword).await()

            Log.d(TAG, "✅ Contraseña actualizada exitosamente")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando contraseña: ${e.message}")

            // Mensajes de error más específicos
            val errorMessage = when {
                e.message?.contains("REQUIRES_RECENT_LOGIN", ignoreCase = true) == true ->
                    "Por seguridad, debes cerrar sesión y volver a iniciar para cambiar tu contraseña"
                e.message?.contains("WEAK_PASSWORD", ignoreCase = true) == true ->
                    "La contraseña es muy débil. Usa al menos 6 caracteres"
                else -> e.message ?: "Error desconocido"
            }

            Result.failure(Exception(errorMessage))
        }
    }
}