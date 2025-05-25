package com.example.taskapplication.domain.model

/**
 * Domain model for team invitation
 */
data class TeamInvitation(
    val id: String,
    val teamId: String,
    val teamName: String,
    val email: String,
    val role: String,
    val status: String, // pending, accepted, rejected, cancelled
    val token: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val serverId: String? = null,
    val syncStatus: String = "synced", // synced, pending_create, pending_update, pending_delete
    val lastModified: Long = System.currentTimeMillis()
)
