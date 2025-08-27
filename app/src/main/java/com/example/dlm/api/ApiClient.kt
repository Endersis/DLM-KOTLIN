// com.example.dlm.api/ApiClient.kt
package com.example.dlm.api

import android.content.Context
import com.example.dlm.s3.S3Manager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://3fspnqw7ij.execute-api.us-east-2.amazonaws.com/Prod/"

    // Cliente OkHttp que incluye un interceptor para el token
    private fun okHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(context))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // El objeto Retrofit para construir los servicios de la API
    private fun retrofit(context: Context): Retrofit {
        val gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // Método público para obtener la instancia del servicio
    fun getApiService(context: Context): SenaApiService {
        val retrofitInstance = retrofit(context)
        return retrofitInstance.create(SenaApiService::class.java)
    }

    // Interceptor para añadir el token de autenticación a las peticiones
    private class AuthorizationInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val token = S3Manager.loadUserToken(context)

            val newRequest = if (!token.isNullOrEmpty()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            return chain.proceed(newRequest)
        }
    }
}