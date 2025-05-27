package com.example.taskapplication.data.mapper

import com.example.taskapplication.data.api.request.TeamInvitationRequest
import com.example.taskapplication.data.api.response.TeamInvitationResponse
import com.example.taskapplication.data.database.entities.TeamInvitationEntity
import com.example.taskapplication.domain.model.TeamInvitation
import java.util.*

/**
 * Convert TeamInvitationEntity to TeamInvitation domain model
 */
fun TeamInvitationEntity.toDomainModel(): TeamInvitation {
    return TeamInvitation(
        id = id,
        teamId = teamId,
        teamName = teamName,
        teamUuid = teamUuid, // ← THÊM teamUuid mapping
        email = email,
        role = role,
        status = status,
        token = token,
        createdAt = createdAt,
        expiresAt = expiresAt,
        inviterId = inviterId,
        inviterName = inviterName,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified
    )
}

/**
 * Convert TeamInvitation domain model to TeamInvitationEntity
 */
fun TeamInvitation.toEntity(): TeamInvitationEntity {
    return TeamInvitationEntity(
        id = id,
        teamId = teamId,
        teamName = teamName,
        teamUuid = teamUuid, // ← THÊM teamUuid mapping
        email = email,
        role = role,
        status = status,
        token = token,
        createdAt = createdAt,
        expiresAt = expiresAt,
        inviterId = inviterId,
        inviterName = inviterName,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified
    )
}

/**
 * Convert TeamInvitationResponse to TeamInvitationEntity
 */
fun TeamInvitationResponse.toEntity(existingInvitation: TeamInvitationEntity? = null): TeamInvitationEntity {
    return TeamInvitationEntity(
        id = existingInvitation?.id ?: UUID.randomUUID().toString(),
        teamId = team_id.toString(),
        teamName = team_name ?: "Nhóm không xác định", // Cung cấp giá trị mặc định nếu team_name là null
        teamUuid = null, // ← THÊM teamUuid (API response chưa có field này)
        email = email,
        role = role,
        status = status,
        token = token,
        createdAt = created_at,
        expiresAt = expires_at,
        inviterId = inviter_id?.toString(),
        inviterName = inviter_name,
        serverId = id.toString(),
        syncStatus = "synced",
        lastModified = System.currentTimeMillis()
    )
}

/**
 * Convert TeamInvitationEntity to TeamInvitationRequest
 */
fun TeamInvitationEntity.toApiRequest(): TeamInvitationRequest {
    return TeamInvitationRequest(
        email = email,
        role = role
    )
}
