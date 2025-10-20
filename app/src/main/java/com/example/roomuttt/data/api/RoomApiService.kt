package com.example.roomuttt.data.api

import com.example.roomuttt.domain.model.RoomResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface RoomApiService {

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
        @Part imagenes: List<MultipartBody.Part>? = null  // IMPORTANTE: sin nombre aquí
    ): Response<RoomResponse>
}