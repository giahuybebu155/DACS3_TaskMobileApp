package com.example.taskapplication.domain.model

data class TeamMember(
    val id: String,
    val teamId: String,
    val userId: String,
    val role: String, // "admin", "member", etc.
    val joinedAt: Long,
    val invitedBy: String? = null,
    val serverId: String? = null,
    val syncStatus: String = "synced",
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    // User information
    val userName: String? = null,
    val userEmail: String? = null,
    val userAvatar: String? = null
)
