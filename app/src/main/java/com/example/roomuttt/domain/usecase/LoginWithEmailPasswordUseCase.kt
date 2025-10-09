package com.example.roomuttt.domain.usecase

import com.example.roomuttt.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LoginWithEmailPasswordUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Flow<Result<String>> = flow {
        try {
            val result = repository.loginWithEmailAndPassword(email, password)
            emit(result)
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}