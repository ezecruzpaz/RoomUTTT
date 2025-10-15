package com.example.roomuttt.domain.model

data class User(
    val uid: String,
    val email: String? = null,
    val name: String? = null,
    val career: String? = null
)