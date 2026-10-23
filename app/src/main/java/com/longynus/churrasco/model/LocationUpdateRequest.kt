package com.longynus.churrasco.model

data class LocationUpdateRequest(
    val latitude: Double,
    val longitude: Double,
    val sessionId: String
)

data class LocationSessionResponse(
    val success: Boolean,
    val message: String? = null,
    val sessionId: String? = null,
    val expiresAt: Long = 0L
)
