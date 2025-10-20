package com.example.roomuttt.domain.model

import android.net.Uri
import com.google.gson.annotations.SerializedName

// DTO para crear cuarto - Orden alfabético para .NET
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

// DTO completo con imágenes (para uso futuro)
data class RoomDto(
    @SerializedName("Nombre")
    val nombre: String,

    @SerializedName("Precio")
    val precio: Double,

    @SerializedName("Descripcion")
    val descripcion: String?,

    @SerializedName("Capacidad")
    val capacidad: Int,

    @SerializedName("Disponible")
    val disponible: Boolean = true,

    @SerializedName("Servicios")
    val servicios: List<String> = emptyList(),

    @SerializedName("UserId")
    val userId: String,

    @SerializedName("Ubicacion")
    val ubicacion: String?,

    // Estos NO se serializan (sin @SerializedName los ignora Gson por defecto)
    val nuevasImagenes: List<Uri>? = null,
    val imagenesAEliminar: List<String>? = null
)

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