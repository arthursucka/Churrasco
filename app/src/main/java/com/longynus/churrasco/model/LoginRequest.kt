package com.longynus.churrasco.model

data class LoginRequest(
    val username: String,
    val fcmToken: String
)
