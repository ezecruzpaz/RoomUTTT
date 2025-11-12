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
        val updatedAt: String,

        // ✅ Campos que se llenan después (nullable para evitar problemas con Gson)
        var renterId: String? = null,
        var renterName: String? = null

    ) : Serializable {

        init {
            // ✅ Si renterId es null o vacío, llenarlo con userId
            if (renterId.isNullOrEmpty()) {
                renterId = userId
            }
            // ✅ Si renterName es null, usar string vacío
            if (renterName == null) {
                renterName = ""
            }
        }

        companion object {
            // ✅ Factory function para crear RoomData con renterId y renterName
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
                    renterId = userId, // ✅ Llenar aquí directamente
                    renterName = ""
                )
            }
        }

        // 🔥 Función para obtener latitud y longitud
        fun getLatLng(): Pair<Double, Double>? {
            return try {
                val parts = ubicacion.split(",")
                if (parts.size == 2) {
                    val lat = parts[0].trim().toDouble()
                    val lng = parts[1].trim().toDouble()
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