package com.longynus.churrasco.model

/** Pedido para vincular a conta nova (Firebase) a um nome usado antes do login por e-mail. */
data class LegacyLinkRequest(
    val username: String
)

data class LegacyLinkRequestInfo(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val status: String = "",
    val reason: String? = null
)

data class LegacyLinkStatus(
    val request: LegacyLinkRequestInfo? = null,
    val requestsOpen: Boolean = false
)
