// com.example.dlm/UserProfileData.kt
package com.example.dlm.models

import com.google.gson.annotations.SerializedName

data class UserProfileData(
    @SerializedName("correo")
    val correo: String,
    @SerializedName("nombre")
    val nombre: String,
    @SerializedName("rol")
    val rol: String,
    @SerializedName("destacados")
    val destacados: DestacadosData
)