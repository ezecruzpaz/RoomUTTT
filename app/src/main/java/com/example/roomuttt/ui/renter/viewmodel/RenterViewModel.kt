package com.example.roomuttt.ui.renter.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RenterViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val TAG = "RenterViewModel"

    // Estado de loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Estado de creación de la cuenta (éxito/error)
    private val _renterState = MutableStateFlow<RenterState>(RenterState.Idle)
    val renterState: StateFlow<RenterState> = _renterState.asStateFlow()

    sealed class RenterState {
        object Idle : RenterState()
        object Loading : RenterState()
        data class Success(val message: String) : RenterState()
        data class Error(val message: String) : RenterState()
    }

    fun createRenterAccount(
        nombreCompleto: String,
        telefono: String,
        direccion: String
    ) {
        viewModelScope.launch {
            if (nombreCompleto.isBlank() || telefono.isBlank() || direccion.isBlank()) {
                _renterState.value = RenterState.Error("Todos los campos son requeridos")
                return@launch
            }

            try {
                _isLoading.value = true
                _renterState.value = RenterState.Loading

                val uid = auth.currentUser?.uid ?: run {
                    _renterState.value = RenterState.Error("Usuario no autenticado")
                    return@launch
                }

                val renterData = hashMapOf(
                    "uid" to uid,
                    "nombreCompleto" to nombreCompleto,
                    "telefono" to telefono,
                    "direccion" to direccion,
                    "role" to "renter",
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("renters")
                    .document(uid)
                    .set(renterData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Cuenta de arrendatario creada exitosamente")
                        _renterState.value = RenterState.Success("Cuenta creada exitosamente. Ya puedes publicar cuartos.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Error al crear cuenta de arrendatario: ${e.message}", e)
                        _renterState.value = RenterState.Error("Error al crear la cuenta: ${e.message}")
                    }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción general: ${e.message}", e)
                _renterState.value = RenterState.Error("Error inesperado: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}