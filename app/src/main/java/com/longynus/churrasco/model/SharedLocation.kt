package com.longynus.churrasco.model

data class SharedLocation(
    val username: String = "",
    val displayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L,
    val expiresAt: Long = 0L
)
