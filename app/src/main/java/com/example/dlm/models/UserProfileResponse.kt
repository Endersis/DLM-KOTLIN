// Modifica tu archivo UserProfileResponse.kt
package com.example.dlm.models

import com.google.gson.annotations.SerializedName

data class UserProfileResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: UserProfileData
)