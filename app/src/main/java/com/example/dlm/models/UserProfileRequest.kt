// com.example.dlm/UserProfileRequest.kt
package com.example.dlm.models

import com.google.gson.annotations.SerializedName

data class UserProfileRequest(
    @SerializedName("correo")
    val correo: String
)