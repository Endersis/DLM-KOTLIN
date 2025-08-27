package com.example.dlm.api

import com.example.dlm.models.CatalogoResponse
import com.example.dlm.models.LoginRequest
import com.example.dlm.models.LoginResponse
import com.example.dlm.models.SenaDetailResponse
import com.example.dlm.models.UserProfileResponse
import com.example.dlm.models.UsuariosResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("usuarios/login")
    suspend fun login(
        @Body loginRequest: LoginRequest
    ): Response<LoginResponse>

    @GET("crud/usuarios/{correo}")
    suspend fun getUserProfile(
        @Header("Authorization") token: String,
        @Path("correo") correo: String
    ): Response<UserProfileResponse>

    @GET("crud/senas")
    suspend fun getCatalogoSenas(
        @Header("Authorization") token: String
    ): Response<CatalogoResponse>

    @GET("crud/senas/{nombre}") // Aquí es donde cambia
    suspend fun getSenaByName(
        @Header("Authorization") token: String,
        @Path("nombre") nombre: String // Se utiliza @Path en lugar de @Query
    ): Response<SenaDetailResponse>

    @GET("crud/usuarios")
    suspend fun getUsuarios(
        @Header("Authorization") token: String
    ): Response<UsuariosResponse>
}