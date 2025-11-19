package com.roomu.app.domain.model

import java.io.Serializable
import com.google.gson.annotations.SerializedName
import java.lang.Math.toRadians
import kotlin.math.*

data class CreateRoomDto(
    @SerializedName("Capacidad") val capacidad: Int,
    @SerializedName("Descripcion") val descripcion: String?,
    @SerializedName("Disponible") val disponible: Boolean = true,
    @SerializedName("Nombre") val nombre: String,
    @SerializedName("Precio") val precio: Double,
    @SerializedName("Servicios") val servicios: List<String> = emptyList(),
    @SerializedName("Ubicacion") val ubicacion: String?,
    @SerializedName("UserId") val userId: String
    // → Genero eliminado completamente
)

data class RoomResponse(
    @SerializedName("result") val result: RoomResult?,
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("message") val message: String?
)

data class RoomResult(
    @SerializedName("id") val id: String?
)

data class RoomsListResponse(
    @SerializedName("result") val result: List<RoomData>?,
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("message") val message: String?
)

data class RoomData(
    @SerializedName("Id") val id: String,
    @SerializedName("Nombre") val nombre: String,
    @SerializedName("Precio") val precio: Double,
    @SerializedName("Descripcion") val descripcion: String?,
    @SerializedName("Capacidad") val capacidad: Int,
    @SerializedName("Disponible") val disponible: Boolean,
    @SerializedName("Servicios") val servicios: List<String>,
    @SerializedName("UserId") val userId: String,
    @SerializedName("Ubicacion") val ubicacion: String,
    @SerializedName("Imagenes") val imagenes: List<String>,
    @SerializedName("CreatedAt") val createdAt: String,
    @SerializedName("UpdatedAt") val updatedAt: String,

    var renterId: String? = null,
    var renterName: String? = null
) : Serializable {

    init {
        if (renterId.isNullOrEmpty()) renterId = userId
        if (renterName == null) renterName = ""
    }

    companion object {
        fun fromJson(
            id: String,
            nombre: String,
            precio: Double,
            descripcion: String?,
            capacidad: Int,
            disponible: Boolean,
            servicios: List<String>,
            userId: String,
            ubicacion: String,
            imagenes: List<String>,
            createdAt: String,
            updatedAt: String
        ): RoomData {
            return RoomData(
                id = id,
                nombre = nombre,
                precio = precio,
                descripcion = descripcion,
                capacidad = capacidad,
                disponible = disponible,
                servicios = servicios,
                userId = userId,
                ubicacion = ubicacion,
                imagenes = imagenes,
                createdAt = createdAt,
                updatedAt = updatedAt,
                renterId = userId,
                renterName = ""
            )
        }
    }

    fun getLatLng(): Pair<Double, Double>? {
        return try {
            val parts = ubicacion.split(",")
            if (parts.size == 2) {
                Pair(parts[0].trim().toDouble(), parts[1].trim().toDouble())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun distanceFrom(lat: Double, lng: Double): Double? {
        val roomLoc = getLatLng() ?: return null
        return calculateDistance(lat, lng, roomLoc.first, roomLoc.second)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // km
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun matchesFilters(
        searchQuery: String = "",
        minPrice: Double? = null,
        maxPrice: Double? = null,
        minCapacity: Int? = null,
        maxCapacity: Int? = null,
        selectedServices: List<String> = emptyList(),
        maxDistance: Double? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        onlyAvailable: Boolean = false
    ): Boolean {
        if (onlyAvailable && !disponible) return false

        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            val matches = nombre.lowercase().contains(q) ||
                    descripcion?.lowercase()?.contains(q) == true ||
                    servicios.any { it.lowercase().contains(q) } ||
                    ubicacion.lowercase().contains(q)
            if (!matches) return false
        }

        if (minPrice != null && precio < minPrice) return false
        if (maxPrice != null && precio > maxPrice) return false
        if (minCapacity != null && capacidad < minCapacity) return false
        if (maxCapacity != null && capacidad > maxCapacity) return false

        if (selectedServices.isNotEmpty()) {
            val roomServicesLower = servicios.joinToString(",").lowercase()
            val hasAll = selectedServices.all { roomServicesLower.contains(it.lowercase()) }
            if (!hasAll) return false
        }

        if (maxDistance != null && userLat != null && userLng != null) {
            val distance = distanceFrom(userLat, userLng) ?: return false
            if (distance > maxDistance) return false
        }

        return true
    }

    fun getDistanceText(userLat: Double, userLng: Double): String {
        val distance = distanceFrom(userLat, userLng) ?: return "Distancia no disponible"
        return when {
            distance < 1.0 -> "${(distance * 1000).toInt()} m"
            distance < 10.0 -> String.format("%.1f km", distance)
            else -> "${distance.toInt()} km"
        }
    }

    fun hasService(serviceName: String): Boolean {
        return servicios.joinToString(",").lowercase().contains(serviceName.lowercase())
    }

    fun getCleanServicesList(): List<String> {
        val set = mutableSetOf<String>()
        servicios.forEach { group ->
            group.split(",").forEach { s ->
                val clean = s.trim()
                if (clean.isNotEmpty()) set.add(clean)
            }
        }
        return set.toList().sorted()
    }

    fun getAvailabilityStatus(): Pair<Boolean, String> =
        if (disponible) true to "Disponible" else false to "Rentado"

    fun getFormattedPrice(): String = "$${precio.toInt()} MXN/mes"

    fun getFormattedCapacity(): String =
        "$capacidad persona${if (capacidad > 1) "s" else ""}"

    fun isValid(): Boolean =
        id.isNotEmpty() &&
                nombre.isNotEmpty() &&
                precio > 0 &&
                capacidad > 0 &&
                userId.isNotEmpty() &&
                ubicacion.isNotEmpty() &&
                getLatLng() != null

    fun getSummary(): String = buildString {
        append("$nombre - ")
        append(getFormattedPrice())
        append(" - ")
        append(getFormattedCapacity())
        if (!disponible) append(" (Rentado)")
    }
}