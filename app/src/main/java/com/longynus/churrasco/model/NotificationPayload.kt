package com.longynus.churrasco.model

data class NotificationPayload(
    val to: String,
    val notification: NotificationContent,
    val data: Map<String, String>
)

data class NotificationContent(
    val title: String,
    val body: String
)
