package com.example.dlm.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL_LOGIN = "https://x4wnwj9fkc.execute-api.us-east-2.amazonaws.com/Prod/"
    private const val BASE_URL_COMMON = "https://3fspnqw7ij.execute-api.us-east-2.amazonaws.com/Prod/"

    // Interceptor para registrar la información de la red
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Instancia de OkHttpClient para la llamada de login (sin autenticación)
    private val loginHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // El loginInstance usa el cliente sin autenticación
    val loginInstance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_LOGIN)
            .addConverterFactory(GsonConverterFactory.create())
            .client(loginHttpClient) // Usar el cliente sin autenticación
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Devuelve una instancia de ApiService con un interceptor de autenticación.
     * Es una función (no un 'by lazy') porque necesita un contexto.
     * @param context El contexto de la aplicación para acceder a SharedPreferences.
     */
    fun getCommonInstance(context: Context): ApiService {
        // Interceptor de autenticación que usa el contexto para obtener el token
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)

            // Construir la nueva solicitud solo si el token existe
            val newRequest = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            chain.proceed(newRequest)
        }

        // Instancia de OkHttpClient para las llamadas con autenticación
        val commonHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor) // Añadir el interceptor de autenticación
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL_COMMON)
            .addConverterFactory(GsonConverterFactory.create())
            .client(commonHttpClient) // Usar el cliente con autenticación
            .build()
            .create(ApiService::class.java)
    }
}
