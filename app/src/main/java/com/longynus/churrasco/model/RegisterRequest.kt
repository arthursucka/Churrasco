package com.longynus.churrasco.model

/**
 * Payload para registrar um novo usuário no backend.
 *
 * @param username       Identificador único escolhido (sem espaços).
 * @param displayName    Nome de exibição que aparecerá no app.
 */
data class RegisterRequest(
    val username: String,
    val displayName: String
)
