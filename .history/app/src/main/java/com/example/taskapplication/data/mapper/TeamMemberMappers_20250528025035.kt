package com.example.taskapplication.data.mapper

import com.example.taskapplication.data.api.response.TeamMemberResponse
import com.example.taskapplication.data.database.entities.TeamMemberEntity
import com.example.taskapplication.data.database.entities.TeamMemberWithUser
import com.example.taskapplication.domain.model.TeamMember
import java.util.*

// Entity to Domain
fun TeamMemberEntity.toDomainModel(): TeamMember {
    return TeamMember(
        id = id,
        teamId = teamId,
        userId = userId,
        role = role,
        joinedAt = joinedAt,
        invitedBy = invitedBy,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified,
        createdAt = createdAt
    )
}

// TeamMemberWithUser to Domain (with user information)
fun TeamMemberWithUser.toDomainModel(): TeamMember {
    return TeamMember(
        id = teamMember.id,
        teamId = teamMember.teamId,
        userId = teamMember.userId,
        role = teamMember.role,
        joinedAt = teamMember.joinedAt,
        invitedBy = teamMember.invitedBy,
        serverId = teamMember.serverId,
        syncStatus = teamMember.syncStatus,
        lastModified = teamMember.lastModified,
        createdAt = teamMember.createdAt,
        // User information from joined user entity
        userName = user?.name,
        userEmail = user?.email,
        userAvatar = user?.avatar
    )
}

// Domain to Entity
fun TeamMember.toEntity(): TeamMemberEntity {
    return TeamMemberEntity(
        id = id,
        teamId = teamId,
        userId = userId,
        role = role,
        joinedAt = joinedAt,
        invitedBy = invitedBy,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified,
        createdAt = createdAt
    )
}

// API Response to Entity
fun TeamMemberResponse.toEntity(teamId: String, existingMember: TeamMemberEntity? = null): TeamMemberEntity {
    return TeamMemberEntity(
        id = existingMember?.id ?: UUID.randomUUID().toString(),
        teamId = teamId,
        userId = id.toString(),
        role = role,
        joinedAt = System.currentTimeMillis(), // Joined time is not provided in response
        invitedBy = existingMember?.invitedBy,
        serverId = id.toString(),
        syncStatus = "synced",
        lastModified = System.currentTimeMillis(),
        createdAt = existingMember?.createdAt ?: System.currentTimeMillis()
    )
}
