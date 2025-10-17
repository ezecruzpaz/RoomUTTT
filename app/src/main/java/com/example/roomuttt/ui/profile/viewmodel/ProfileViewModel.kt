package com.example.roomuttt.ui.profile.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.data.repository.AuthRepository
import com.example.roomuttt.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    fun getUserProfile() {
        viewModelScope.launch {
            // Fetch desde Auth (uid y email)
            val currentUser = repository.getCurrentUserFromAuth()
            _userProfile.value = currentUser
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
    fun uploadProfilePhoto(imageUri: Uri) {
        viewModelScope.launch {
            val userId = repository.getCurrentUserFromAuth()?.uid ?: return@launch
            val result = repository.uploadProfilePhoto(userId, imageUri)

            if (result.isSuccess) {
                Log.d("ProfileViewModel", "Foto subida exitosamente")
                getUserProfile() // Recargar perfil
            } else {
                Log.e("ProfileViewModel", "Error subiendo foto: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}