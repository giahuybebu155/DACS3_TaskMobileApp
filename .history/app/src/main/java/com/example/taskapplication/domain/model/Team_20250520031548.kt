package com.example.taskapplication.domain.model

data class Team(
    val id: String,
    val name: String,
    val description: String? = null,
    val ownerId: String? = null,
    val createdBy: String? = null,
    val serverId: String? = null,
    val syncStatus: String = "synced",
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val memberCount: Int? = null,
    val isActive: Boolean? = true,
    val avatarUrl: String? = null
)