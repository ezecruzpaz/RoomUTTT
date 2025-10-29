package com.example.roomuttt.data.preferences

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object UserRoleHelper {

    suspend fun isRenter(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        return try {
            val db = FirebaseFirestore.getInstance()
            val renterDoc = db.collection("renters").document(uid).get().await()
            renterDoc.exists()
        } catch (e: Exception) {
            false
        }
    }
}