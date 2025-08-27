package com.example.dlm.models

import com.google.gson.annotations.SerializedName

data class SenaDetailResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: Sena // Aquí se usa tu clase Sena existente
)