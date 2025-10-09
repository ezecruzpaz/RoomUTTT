package com.example.roomuttt.ui.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.domain.model.User
import com.example.roomuttt.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _registerState = MutableStateFlow<Result<User>?>(null)
    val registerState: StateFlow<Result<User>?> = _registerState.asStateFlow()

    fun registerUser(email: String, fullName: String, password: String) {
        viewModelScope.launch {
            registerUseCase(email, fullName, password).collect { result ->
                _registerState.value = result
            }
        }
    }
}