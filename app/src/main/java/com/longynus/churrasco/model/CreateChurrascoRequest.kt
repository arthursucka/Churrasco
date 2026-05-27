package com.longynus.churrasco.model

data class CreateChurrascoRequest(
    val churrascoDate: String,
    val hora: String,
    val local: String,
    val fornecidos: List<String>,
    val userName: String,
    val invitedUsers: List<String>
)
