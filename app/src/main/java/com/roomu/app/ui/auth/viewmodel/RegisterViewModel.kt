package com.roomu.app.ui.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.roomu.app.data.repository.AuthRepository
import com.roomu.app.utils.ProfanityFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _registerState = MutableStateFlow<Result<String>?>(null)
    val registerState: StateFlow<Result<String>?> = _registerState.asStateFlow()

    private val _needsNameUpdate = MutableStateFlow(false)
    val needsNameUpdate: StateFlow<Boolean> = _needsNameUpdate.asStateFlow()

    fun registerUser(email: String, fullName: String, password: String) {
        viewModelScope.launch {
            try {
                // ✅ Validar lenguaje inapropiado antes de registrar
                if (ProfanityFilter.containsInappropriateLanguage(fullName)) {
                    _registerState.value = Result.failure(
                        Exception("El nombre contiene lenguaje inapropiado")
                    )
                    return@launch
                }

                // Proceder con el registro normal
                val result = repository.registerWithEmailAndPassword(email, password, fullName)
                _registerState.value = result
            } catch (e: Exception) {
                _registerState.value = Result.failure(e)
            }
        }
    }

    suspend fun checkAndCensorExistingUserName(userId: String): Boolean {
        return try {
            val userDoc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()

            val currentName = userDoc.getString("name")

            if (currentName.isNullOrEmpty()) {
                Log.w("RegisterViewModel", "Usuario sin nombre: $userId")
                return false
            }

            // Verificar si contiene lenguaje inapropiado
            if (ProfanityFilter.containsInappropriateLanguage(currentName)) {
                Log.d("RegisterViewModel", "Nombre inapropiado detectado: $currentName")

                // Censurar el nombre
                val censoredName = ProfanityFilter.censorText(currentName)

                // Actualizar en Firestore
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update("name", censoredName)
                    .await()

                Log.d("RegisterViewModel", "Nombre censurado actualizado: $censoredName")

                // Marcar que necesita actualización
                _needsNameUpdate.value = true

                true // Se detectó y censuró
            } else {
                false // No se detectó contenido inapropiado
            }

        } catch (e: Exception) {
            Log.e("RegisterViewModel", "Error verificando nombre de usuario", e)
            false
        }
    }

    /**
     * ✅ Verifica si el nombre contiene lenguaje inapropiado
     * Útil para validaciones en tiempo real
     */
    fun validateNameForProfanity(name: String): Boolean {
        return ProfanityFilter.containsInappropriateLanguage(name)
    }

    /**
     * ✅ Obtiene el nombre censurado de un texto
     */
    fun getCensoredName(name: String): String {
        return ProfanityFilter.censorText(name)
    }

    /**
     * ✅ Resetea el estado de necesidad de actualización de nombre
     */
    fun resetNameUpdateFlag() {
        _needsNameUpdate.value = false
    }

    /**
     * ✅ Verifica múltiples usuarios y censura si es necesario (útil para migraciones)
     */
    suspend fun checkAndCensorMultipleUsers(userIds: List<String>): Int {
        var censoredCount = 0

        userIds.forEach { userId ->
            try {
                val wasCensored = checkAndCensorExistingUserName(userId)
                if (wasCensored) {
                    censoredCount++
                }
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Error procesando usuario: $userId", e)
            }
        }

        Log.d("RegisterViewModel", "Usuarios censurados: $censoredCount de ${userIds.size}")
        return censoredCount
    }
}