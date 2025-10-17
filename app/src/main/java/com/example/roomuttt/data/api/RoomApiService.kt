package com.example.roomuttt.data.api

import CreateRoomDto
import com.example.roomuttt.domain.model.RoomResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

interface RoomApiService {
    @POST("api/Room")  // Ajusta tu endpoint
    suspend fun createRoom(@Body roomDto: CreateRoomDto): Response<RoomResponse>

    @Multipart
    @POST("api/Room")  // Si subes imágenes
    suspend fun createRoomWithImages(
        @PartMap map: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part imagenes: List<MultipartBody.Part>? = null
    ): Response<RoomResponse>
}