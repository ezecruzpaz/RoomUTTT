package com.example.roomuttt.ui.renter

// Data class para Room (si no la tienes ya)
data class Room(
    val id: String = "",
    val name: String = "",
    val renterId: String = "",
    val isOccupied: Boolean = false,
    val status: String = "",
    val imageUrl: String = "",
    val price: Double = 0.0,
    val description: String = ""
)