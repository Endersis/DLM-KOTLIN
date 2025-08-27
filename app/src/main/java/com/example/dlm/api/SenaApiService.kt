
package com.example.dlm.api

import com.example.dlm.models.CatalogoResponse
import com.example.dlm.models.SenaDetailResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface SenaApiService {

    @GET("crud/senas")
    suspend fun getSenas(@Header("Authorization") token: String): Response<CatalogoResponse>

    @GET("crud/senas/{nombre}")
    suspend fun getSenaByName(
        @Header("Authorization") token: String,
        @Path("nombre") nombre: String
    ): Response<SenaDetailResponse>
}