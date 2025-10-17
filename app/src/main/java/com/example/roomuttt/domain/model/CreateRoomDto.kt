import com.google.gson.annotations.SerializedName

data class CreateRoomDto(
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
    val ubicacion: String?
)
