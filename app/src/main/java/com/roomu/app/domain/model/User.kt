package com.roomu.app.domain.model

data class User(
    val uid: String,
    val email: String? = null,
    val name: String? = null,
    val photoUrl: String? = null  // ← Agrega esta línea
)