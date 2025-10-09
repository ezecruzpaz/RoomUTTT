package com.example.roomuttt.ui.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomuttt.domain.usecase.LoginWithEmailPasswordUseCase
import com.example.roomuttt.domain.usecase.LoginWithGoogleUseCase
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleUseCase: LoginWithGoogleUseCase,
    private val emailUseCase: LoginWithEmailPasswordUseCase  // Inyecta el nuevo use case
) : ViewModel() {

    private val _loginState = MutableStateFlow<Result<String>?>(null)
    val loginState: StateFlow<Result<String>?> = _loginState.asStateFlow()

    private val TAG = "LoginViewModel"

    fun loginWithGoogle(account: GoogleSignInAccount) {
        Log.d(TAG, "loginWithGoogle llamado con cuenta: ${account.email}")
        viewModelScope.launch {
            googleUseCase(account).collect { result ->
                Log.d(TAG, "googleUseCase emitió: $result")
                _loginState.value = result
            }
        }
    }

    fun loginWithEmailPassword(email: String, password: String) {
        Log.d(TAG, "loginWithEmailPassword llamado con email: $email")
        viewModelScope.launch {
            emailUseCase(email, password).collect { result ->
                Log.d(TAG, "emailUseCase emitió: $result")
                _loginState.value = result
            }
        }
    }
}