package com.example.roomuttt.data.remote

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRemoteDataSource(private val auth: FirebaseAuth) {

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<String> {
        return try {
            // Crea la credencial de Google
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            // Enlaza con Firebase Auth
            val result = auth.signInWithCredential(credential).await()

            // Actualiza profile (opcional, para displayName)
            result.user?.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(account.displayName)
                    .build()
            )?.await()

            Log.d("AuthRemoteDataSource", "Usuario Google creado/enlazado: ${result.user?.uid}")
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Log.e("AuthRemoteDataSource", "Error en signInWithGoogle: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<String> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(auth.currentUser?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmailAndPassword(email: String, password: String): Result<String> {  // Nuevo método para registro
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(auth.currentUser?.uid ?: "")  // Retorna UID del nuevo usuario
        } catch (e: Exception) {
            Result.failure(e)  // e.g., "email already exists" o "weak password"
        }
    }
}