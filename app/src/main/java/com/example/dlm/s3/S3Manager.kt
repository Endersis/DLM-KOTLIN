// com.example.dlm/s3/S3Manager.kt
package com.example.dlm.s3

import android.content.Context
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import java.net.URL
import java.util.*

object S3Manager {

    private lateinit var s3Client: AmazonS3Client
    private const val BUCKET_NAME = "proyecto-lsm-lengua-senas"
    private const val PREFS_NAME = "MySharedPrefs"
    private const val TOKEN_KEY = "user_token"

    fun initialize(accessKey: String, secretKey: String, region: Regions) {
        val credentials = BasicAWSCredentials(accessKey, secretKey)
        s3Client = AmazonS3Client(credentials)
        s3Client.setRegion(Region.getRegion(region))
    }

    fun getSignedUrlForS3Object(objectKey: String): URL {
        val expiration = Date()
        var expTimeMillis = expiration.time
        expTimeMillis += (1000 * 60 * 60) // Expira en 1 hora
        expiration.time = expTimeMillis

        return s3Client.generatePresignedUrl(BUCKET_NAME, objectKey, expiration)
    }

    // Guarda el token de usuario en SharedPreferences
    fun saveUserToken(context: Context, token: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(TOKEN_KEY, token)
            apply()
        }
    }

    // Carga el token de usuario desde SharedPreferences
    fun loadUserToken(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(TOKEN_KEY, null)
    }

    // Elimina el token (para cerrar sesión)
    fun clearUserToken(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove(TOKEN_KEY)
            apply()
        }
    }
}