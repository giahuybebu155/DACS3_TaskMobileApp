package com.example.taskapplication.data.api.request

data class TeamRequest(
    val name: String,
    val description: String? = null,
    val ownerId: String
)
