package com.example.roomuttt.domain.usecase

import com.example.roomuttt.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LoginWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(account: GoogleSignInAccount): Flow<Result<String>> = flow {
        try {
            val result = repository.loginWithGoogle(account)
            emit(result)
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}