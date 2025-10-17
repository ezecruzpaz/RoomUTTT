package com.example.roomuttt.data.remote

import android.net.Uri
import android.util.Log
import com.example.roomuttt.domain.model.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage  // ← Este es el importante

import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRemoteDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()

            // Guardar/actualizar datos en Firestore
            result.user?.let { firebaseUser ->
                val userDoc = firestore.collection("users").document(firebaseUser.uid)
                val docSnapshot = userDoc.get().await()

                if (!docSnapshot.exists()) {
                    // Es un nuevo usuario, guarda sus datos
                    val userData = hashMapOf(
                        "uid" to firebaseUser.uid,
                        "email" to firebaseUser.email,
                        "name" to (account.displayName ?: ""),
                        "photoUrl" to (account.photoUrl?.toString() ?: "")
                    )
                    userDoc.set(userData).await()
                    Log.d("AuthRemoteDataSource", "Nuevo usuario de Google guardado en Firestore")
                }
            }

            // Actualizar displayName en Auth
            result.user?.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(account.displayName)
                    .build()
            )?.await()

            Log.d("AuthRemoteDataSource", "Usuario Google autenticado: ${result.user?.uid}")
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

    suspend fun registerWithEmailAndPassword(
        email: String,
        password: String,
        name: String = "",

    ): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()

            // Guardar datos adicionales en Firestore
            result.user?.let { firebaseUser ->
                val userData = hashMapOf(
                    "uid" to firebaseUser.uid,
                    "email" to email,
                    "name" to name,
                    "photoUrl" to ""
                )
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(userData)
                    .await()
                Log.d("AuthRemoteDataSource", "Usuario registrado guardado en Firestore")
            }

            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Log.e("AuthRemoteDataSource", "Error en registro: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null

        return try {
            val doc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            if (doc.exists()) {
                val photoUrl = doc.getString("photoUrl")
                Log.d("AuthRemoteDataSource", "photoUrl desde Firestore: $photoUrl")

                User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    name = doc.getString("name"),
                    photoUrl = photoUrl  // ← Asegúrate de que esta línea esté
                )
            } else {
                // Si no existe documento, créalo
                val userData = hashMapOf(
                    "uid" to firebaseUser.uid,
                    "email" to firebaseUser.email,
                    "name" to (firebaseUser.displayName ?: ""),
                    "photoUrl" to (firebaseUser.photoUrl?.toString() ?: "")
                )
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(userData)
                    .await()

                User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    name = firebaseUser.displayName,
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
            }
        } catch (e: Exception) {
            Log.e("AuthRemoteDataSource", "Error obteniendo usuario: ${e.message}")
            User(
                uid = firebaseUser.uid,
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString()
            )
        }
    }

    suspend fun updateUserProfile(name: String, email: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")

        val userData = hashMapOf(
            "name" to name,
            "email" to email,

        )

        firestore.collection("users")
            .document(userId)
            .set(userData, com.google.firebase.firestore.SetOptions.merge())
            .await()

        Log.d("AuthRemoteDataSource", "Perfil actualizado en Firestore")
    }

    suspend fun deleteUser() {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")

        // Elimina documento de Firestore
        firestore.collection("users")
            .document(userId)
            .delete()
            .await()

        // Elimina cuenta de Firebase Auth
        auth.currentUser?.delete()?.await()

        Log.d("AuthRemoteDataSource", "Usuario eliminado completamente")
    }

    suspend fun uploadProfilePhoto(userId: String, imageUri: Uri): Result<String> {
        return try {
            val storageRef = FirebaseStorage.getInstance().reference
            // Cambia el path para que coincida con las reglas
            val photoRef = storageRef.child("profile_photos/$userId")

            Log.d("AuthRemoteDataSource", "Subiendo imagen para userId: $userId")

            // Subir la imagen
            val uploadTask = photoRef.putFile(imageUri).await()

            // Obtener la URL de descarga
            val downloadUrl = photoRef.downloadUrl.await().toString()

            Log.d("AuthRemoteDataSource", "URL de descarga obtenida: $downloadUrl")

            // Actualizar la URL en Firestore
            firestore.collection("users")
                .document(userId)
                .update("photoUrl", downloadUrl)
                .await()

            Log.d("AuthRemoteDataSource", "Foto de perfil actualizada en Firestore")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e("AuthRemoteDataSource", "Error subiendo foto: ${e.message}", e)
            Result.failure(e)
        }
    }
}