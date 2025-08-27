package com.example.dlm.models

data class UsuariosResponse(
    val message: String,
    val data: List<Usuario>,
    val count: Int
)

data class Usuario(
    val correo: String,
    val nombre: String,
    val fechaModificacion: String,
    val password: Any, // El tipo de dato para "password" puede ser una cadena o nulo, así que se usa "Any"
    val activo: Boolean,
    val destacados: Destacados?,
    val rol: String,
    val fechaCreacion: String
)

data class Destacados(
    val conteo: Int,
    val activo: Boolean
)