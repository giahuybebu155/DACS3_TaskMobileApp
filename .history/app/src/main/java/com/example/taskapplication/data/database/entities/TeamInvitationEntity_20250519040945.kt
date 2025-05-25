package com.example.taskapplication.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for team invitation
 */
@Entity(
    tableName = "team_invitations",
    indices = [
        Index("teamId"),
        Index("email")
    ]
)
data class TeamInvitationEntity(
    @PrimaryKey
    val id: String,
    val teamId: String,
    val teamName: String,
    val email: String,
    val role: String,
    val status: String, // pending, accepted, rejected, cancelled
    val token: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val serverId: String?,
    val syncStatus: String,
    val lastModified: Long
)
