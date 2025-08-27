package com.example.dlm.models
// com.example.dlm/Sena.kt
import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Sena(
    @SerializedName("_id")
    val id: String,
    @SerializedName("nombre")
    val nombre: String,
    @SerializedName("fechaModificacion")
    val fechaModificacion: String,
    @SerializedName("descripcion")
    val descripcion: String,
    @SerializedName("activo")
    val activo: Boolean,
    @SerializedName("imagenesUrl")
    val imagenesUrl: List<String>,
    @SerializedName("fechaCreacion")
    val fechaCreacion: String,
    @SerializedName("hashtag")
    val hashtag: String
) : Parcelable