package com.example.roomuttt.data.repository

import android.net.Uri
import com.example.roomuttt.data.remote.AuthRemoteDataSource
import com.example.roomuttt.domain.model.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val remote: AuthRemoteDataSource
) {
    suspend fun loginWithGoogle(account: GoogleSignInAccount): Result<String> =
        remote.signInWithGoogle(account)

    suspend fun loginWithEmailAndPassword(email: String, password: String): Result<String> =
        remote.signInWithEmailAndPassword(email, password)

    suspend fun registerWithEmailAndPassword(
        email: String,
        password: String,
        name: String = ""
    ): Result<String> =
        remote.registerWithEmailAndPassword(email, password, name)

    // Métodos nuevos para el perfil
    suspend fun getCurrentUserFromAuth(): User? {
        return remote.getCurrentUser()
    }

    suspend fun updateUserProfile(name: String, email: String) {
        remote.updateUserProfile(name, email)
    }

    suspend fun deleteCurrentUser() {
        remote.deleteUser()
    }
    suspend fun uploadProfilePhoto(userId: String, imageUri: Uri): Result<String> {
        return remote.uploadProfilePhoto(userId, imageUri)
    }
}