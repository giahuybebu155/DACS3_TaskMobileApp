package com.example.taskapplication.data.mapper

import com.example.taskapplication.data.api.model.*
import com.example.taskapplication.data.database.entities.*
import com.example.taskapplication.data.local.entity.TeamRoleHistoryEntity

/**
 * Extension functions to convert between entities and DTOs for sync operations
 */

// PersonalTask conversions
fun PersonalTaskEntity.toApiModel(): PersonalTaskDto {
    return PersonalTaskDto(
        id = this.id,
        title = this.title,
        description = this.description,
        status = this.status,
        priority = this.priority,
        dueDate = this.dueDate,
        createdAt = this.createdAt,
        updatedAt = this.lastModified,
        userId = this.userId ?: ""
    )
}

fun PersonalTaskDto.toEntity(): PersonalTaskEntity {
    return PersonalTaskEntity(
        id = this.id,
        title = this.title,
        description = this.description,
        status = this.status,
        priority = this.priority,
        dueDate = this.dueDate,
        createdAt = this.createdAt,
        lastModified = this.updatedAt,
        userId = this.userId,
        syncStatus = "synced",
        serverId = this.id,
        order = 0, // Default order, should be updated later
        updatedAt = this.updatedAt
    )
}

// TeamTask conversions
fun TeamTaskEntity.toApiModel(): TeamTaskDto {
    return TeamTaskDto(
        id = this.id,
        title = this.title,
        description = this.description,
        status = if (this.isCompleted) "completed" else "pending",
        priority = this.priority.toString(),
        dueDate = this.dueDate,
        createdAt = this.createdAt,
        updatedAt = this.lastModified,
        teamId = this.teamId,
        assignedUserId = this.assignedUserId
    )
}

fun TeamTaskDto.toEntity(): TeamTaskEntity {
    return TeamTaskEntity(
        id = this.id,
        title = this.title,
        description = this.description,
        isCompleted = this.status == "completed",
        priority = this.priority.toIntOrNull() ?: 0,
        dueDate = this.dueDate,
        createdAt = this.createdAt,
        lastModified = this.updatedAt,
        teamId = this.teamId,
        assignedUserId = this.assignedUserId,
        syncStatus = "synced",
        serverId = this.id
    )
}

// Team conversions
fun TeamEntity.toApiModel(): TeamDto {
    return TeamDto(
        id = this.id,
        name = this.name,
        description = this.description,
        createdAt = this.createdAt,
        updatedAt = this.lastModified,
        ownerId = this.ownerId
    )
}

fun TeamDto.toEntity(): TeamEntity {
    return TeamEntity(
        id = this.id,
        name = this.name,
        description = this.description,
        createdAt = this.createdAt,
        lastModified = this.updatedAt,
        ownerId = this.ownerId,
        syncStatus = "synced",
        serverId = this.id,
        createdBy = this.ownerId
    )
}

// TeamMember conversions
fun TeamMemberEntity.toApiModel(): TeamMemberDto {
    return TeamMemberDto(
        id = this.id,
        teamId = this.teamId,
        userId = this.userId,
        role = this.role,
        joinedAt = this.joinedAt,
        invitedBy = this.invitedBy
    )
}

fun TeamMemberDto.toEntity(): TeamMemberEntity {
    return TeamMemberEntity(
        id = this.id,
        teamId = this.teamId,
        userId = this.userId,
        role = this.role,
        joinedAt = this.joinedAt,
        invitedBy = this.invitedBy,
        syncStatus = "synced",
        serverId = this.id,
        lastModified = System.currentTimeMillis(),
        createdAt = this.joinedAt
    )
}

// Message conversions
fun MessageEntity.toApiModel(): MessageDto {
    return MessageDto(
        id = this.id,
        content = this.content,
        senderId = this.senderId,
        teamId = this.teamId,
        receiverId = this.receiverId,
        timestamp = this.timestamp,
        isRead = this.isRead,
        isDeleted = this.isDeleted,
        lastModified = this.lastModified
    )
}

fun MessageDto.toEntity(): MessageEntity {
    return MessageEntity(
        id = this.id,
        content = this.content,
        senderId = this.senderId,
        teamId = this.teamId,
        receiverId = this.receiverId,
        timestamp = this.timestamp,
        isRead = this.isRead,
        isDeleted = this.isDeleted,
        lastModified = this.lastModified,
        syncStatus = "synced",
        serverId = this.id,
        clientTempId = null,
        createdAt = this.timestamp
    )
}

// TeamRoleHistory conversions
fun TeamRoleHistoryEntity.toApiModel(): TeamRoleHistoryDto {
    return TeamRoleHistoryDto(
        id = this.id,
        teamId = this.teamId,
        userId = this.userId,
        oldRole = this.oldRole,
        newRole = this.newRole,
        changedByUserId = this.changedByUserId,
        timestamp = this.timestamp
    )
}

fun TeamRoleHistoryDto.toEntity(): TeamRoleHistoryEntity {
    return TeamRoleHistoryEntity(
        id = this.id,
        teamId = this.teamId,
        userId = this.userId,
        oldRole = this.oldRole,
        newRole = this.newRole,
        changedByUserId = this.changedByUserId,
        timestamp = this.timestamp,
        syncStatus = "synced"
    )
}
