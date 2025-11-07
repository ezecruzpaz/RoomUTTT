package com.roomu.app.domain.model

import java.io.Serializable
import com.google.gson.annotations.SerializedName

// DTO para crear cuarto
data class CreateRoomDto(
    @SerializedName("Capacidad")
    val capacidad: Int,
    @SerializedName("Descripcion")
    val descripcion: String?,
    @SerializedName("Disponible")
    val disponible: Boolean = true,
    @SerializedName("Nombre")
    val nombre: String,
    @SerializedName("Precio")
    val precio: Double,
    @SerializedName("Servicios")
    val servicios: List<String> = emptyList(),
    @SerializedName("Ubicacion")
    val ubicacion: String?,
    @SerializedName("UserId")
    val userId: String
)

// 🆕 Respuesta de CREATE Room
data class RoomResponse(
    @SerializedName("result")
    val result: RoomResult?,
    @SerializedName("isSuccess")
    val isSuccess: Boolean,
    @SerializedName("message")
    val message: String?
)

data class RoomResult(
    @SerializedName("id")
    val id: String?
)

// 🆕 Respuesta de GET Rooms (lista de cuartos)
data class RoomsListResponse(
    @SerializedName("result")
    val result: List<RoomData>?,
    @SerializedName("isSuccess")
    val isSuccess: Boolean,
    @SerializedName("message")
    val message: String?
)

// 🆕 Modelo de datos de un cuarto
data class RoomData(
    @SerializedName("Id")
    val id: String,

    @SerializedName("Nombre")
    val nombre: String,

    @SerializedName("Precio")
    val precio: Double,

    @SerializedName("Descripcion")
    val descripcion: String?,

    @SerializedName("Capacidad")
    val capacidad: Int,

    @SerializedName("Disponible")
    val disponible: Boolean,

    @SerializedName("Servicios")
    val servicios: List<String>,

    @SerializedName("UserId")
    val userId: String,

    @SerializedName("Ubicacion")
    val ubicacion: String,

    @SerializedName("Imagenes")
    val imagenes: List<String>,

    @SerializedName("CreatedAt")
    val createdAt: String,

    @SerializedName("UpdatedAt")
    val updatedAt: String
) : Serializable { // Implementa Serializable

    // 🔥 Función para obtener latitud y longitud
    fun getLatLng(): Pair<Double, Double>? {
        return try {
            val parts = ubicacion.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDouble()
                val lng = parts[1].toDouble()
                Pair(lat, lng)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // 🔥 Distancia en kilómetros desde un punto
    fun distanceFrom(lat: Double, lng: Double): Double? {
        val roomLocation = getLatLng() ?: return null
        return calculateDistance(lat, lng, roomLocation.first, roomLocation.second)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}