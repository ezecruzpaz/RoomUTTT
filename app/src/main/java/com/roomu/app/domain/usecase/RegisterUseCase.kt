package com.roomu.app.domain.usecase


import com.roomu.app.data.repository.AuthRepository
import com.roomu.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, fullName: String, password: String): Flow<Result<User>> = flow {
        if (password.length < 6) {
            emit(Result.failure(Exception("Contraseña debe tener al menos 6 caracteres")))
            return@flow
        }
        try {
            val result = repository.registerWithEmailAndPassword(email, password)
            emit(result.map { User(it, fullName) })
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}