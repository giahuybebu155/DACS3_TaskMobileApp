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
    val teamUuid: String? = null, // ← THÊM team UUID field
    val email: String,
    val role: String,
    val status: String, // pending, accepted, rejected, cancelled
    val token: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val inviterId: String? = null, // Người gửi lời mời
    val inviterName: String? = null, // Tên người gửi lời mời
    val serverId: String?,
    val syncStatus: String,
    val lastModified: Long
) {
    // Constructor không có inviterId và inviterName để tương thích với code cũ
    constructor(
        id: String,
        teamId: String,
        teamName: String,
        email: String,
        role: String,
        status: String,
        token: String?,
        createdAt: Long,
        expiresAt: Long,
        serverId: String?,
        syncStatus: String,
        lastModified: Long
    ) : this(
        id = id,
        teamId = teamId,
        teamName = teamName,
        teamUuid = null, // ← THÊM teamUuid = null cho backward compatibility
        email = email,
        role = role,
        status = status,
        token = token,
        createdAt = createdAt,
        expiresAt = expiresAt,
        inviterId = null,
        inviterName = null,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified
    )
}
