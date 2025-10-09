package com.example.roomuttt.data.repository

import com.example.roomuttt.data.remote.AuthRemoteDataSource
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val remote: AuthRemoteDataSource
) {
    suspend fun loginWithGoogle(account: GoogleSignInAccount): Result<String> = remote.signInWithGoogle(account)
    suspend fun loginWithEmailAndPassword(email: String, password: String): Result<String> = remote.signInWithEmailAndPassword(email, password)
    suspend fun registerWithEmailAndPassword(email: String, password: String): Result<String> = remote.registerWithEmailAndPassword(email, password)  // Nuevo método
}