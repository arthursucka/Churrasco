package com.longynus.churrasco

/**
 * Modelo genérico para capturar respostas do seu backend.
 *
 * @param T o tipo do dado que vem no campo adicional (por ex. id, lista de churrascos etc.)
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val id: String? = null,
    val churrasco: T? = null,
    val churrascos: List<T>? = null,
    val invites: List<T>? = null,
    val payload: T? = null
)

