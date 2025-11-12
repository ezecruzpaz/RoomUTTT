package com.roomu.app.data.api

import com.roomu.app.domain.model.RoomResponse
import com.roomu.app.domain.model.RoomsListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface RoomApiService {

    // Obtener todos los cuartos
    @GET("api/Room")
    suspend fun getRooms(): Response<RoomsListResponse>

    // Obtener cuarto por ID
    @GET("api/Room/{id}")
    suspend fun getRoomById(@Path("id") id: String): Response<RoomResponse>

    // Crear cuarto
    @Multipart
    @POST("api/Room")
    suspend fun createRoom(
        @Part("Nombre") nombre: RequestBody,
        @Part("Precio") precio: RequestBody,
        @Part("Descripcion") descripcion: RequestBody,
        @Part("Capacidad") capacidad: RequestBody,
        @Part("Disponible") disponible: RequestBody,
        @Part("Servicios") servicios: RequestBody,
        @Part("UserId") userId: RequestBody,
        @Part("Ubicacion") ubicacion: RequestBody,
        @Part imagenes: List<MultipartBody.Part>? = null
    ): Response<RoomResponse>

    // ✅ Eliminar cuarto
    @DELETE("api/Room/{id}")
    suspend fun deleteRoom(@Path("id") id: String): Response<RoomResponse>

    // ✅ Actualizar cuarto completo (incluye disponibilidad)
    @Multipart
    @PUT("api/Room/{id}")
    suspend fun updateRoom(
        @Path("id") id: String,
        @Part("Nombre") nombre: RequestBody,
        @Part("Precio") precio: RequestBody,
        @Part("Descripcion") descripcion: RequestBody,
        @Part("Capacidad") capacidad: RequestBody,
        @Part("Disponible") disponible: RequestBody,
        @Part("Servicios") servicios: RequestBody,
        @Part("Ubicacion") ubicacion: RequestBody,
        @Part("UserId") userId: RequestBody,
        @Part nuevasImagenes: List<MultipartBody.Part>? = null
    ): Response<RoomResponse>
}