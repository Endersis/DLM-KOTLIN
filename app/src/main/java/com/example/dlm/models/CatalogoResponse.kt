// com.example.dlm/CatalogoResponse.kt
package com.example.dlm.models

import com.google.gson.annotations.SerializedName

data class CatalogoResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<Sena>
)