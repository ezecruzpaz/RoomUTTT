package com.example.roomuttt.domain.model

import android.net.Uri
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

// DTO para crear cuarto (SIN imágenes)
data class CreateRoomDto(
    @Expose
    @SerializedName("Nombre")
    val nombre: String,

    @Expose
    @SerializedName("Precio")
    val precio: Double,

    @Expose
    @SerializedName("Descripcion")
    val descripcion: String?,

    @Expose
    @SerializedName("Capacidad")
    val capacidad: Int,

    @Expose
    @SerializedName("Disponible")
    val disponible: Boolean = true,

    @Expose
    @SerializedName("Servicios")
    val servicios: List<String> = emptyList(),

    @Expose
    @SerializedName("UserId")
    val userId: String,

    @Expose
    @SerializedName("Ubicacion")
    val ubicacion: String?
)

// DTO completo con imágenes (para uso futuro)
data class RoomDto(
    @Expose
    @SerializedName("Nombre")
    val nombre: String,

    @Expose
    @SerializedName("Precio")
    val precio: Double,

    @Expose
    @SerializedName("Descripcion")
    val descripcion: String?,

    @Expose
    @SerializedName("Capacidad")
    val capacidad: Int,

    @Expose
    @SerializedName("Disponible")
    val disponible: Boolean = true,

    @Expose
    @SerializedName("Servicios")
    val servicios: List<String> = emptyList(),

    @Expose
    @SerializedName("UserId")
    val userId: String,

    @Expose
    @SerializedName("Ubicacion")
    val ubicacion: String?,

    // Sin @Expose para que NO se serialice (Uri no es compatible con JSON)
    val nuevasImagenes: List<Uri>? = null,
    val imagenesAEliminar: List<String>? = null
)

// Respuesta de la API
data class RoomResponse(
    @Expose
    @SerializedName("Id")
    val id: String?,

    @Expose
    @SerializedName("Message")
    val message: String?
)