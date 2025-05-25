package com.example.taskapplication.data.api.response

data class TeamResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val ownerId: String,
    val createdBy: String,
    val lastModified: Long,
    val createdAt: Long
)
