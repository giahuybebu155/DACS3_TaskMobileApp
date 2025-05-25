package com.example.taskapplication.data.api.model

/**
 * Các lớp model cho đồng bộ hóa
 */

// Request models
data class InitialSyncRequest(
    val deviceId: String
)

data class QuickSyncRequest(
    val deviceId: String,
    val lastSyncedAt: String
)

data class SyncChangesRequest(
    val deviceId: String,
    val personalTasks: SyncChanges<PersonalTaskDto>,
    val teamTasks: SyncChanges<TeamTaskDto>,
    val messages: SyncChanges<MessageDto>
)

data class SyncChanges<T>(
    val created: List<T>,
    val updated: List<T>,
    val deleted: List<String>
)

data class ResolveConflictsRequest(
    val resolutions: List<ConflictResolution>
)

data class ConflictResolution(
    val id: String,
    val type: String,
    val resolution: String // "server", "client", "merge"
)

// Response models
data class InitialSyncResponse(
    val success: Boolean,
    val message: String,
    val data: SyncData,
    val meta: SyncMeta
)

data class QuickSyncResponse(
    val success: Boolean,
    val message: String,
    val data: SyncChangesData,
    val meta: SyncMeta
)

data class PushChangesResponse(
    val success: Boolean,
    val message: String,
    val syncedEntities: SyncedEntities,
    val conflicts: List<ConflictDto>
)

data class SyncData(
    val personalTasks: List<PersonalTaskDto>,
    val teamTasks: List<TeamTaskDto>,
    val teams: List<TeamDto>,
    val teamMembers: List<TeamMemberDto>,
    val messages: List<MessageDto>,
    val users: List<UserDto>
)

data class SyncChangesData(
    val personalTasks: SyncChanges<PersonalTaskDto>,
    val teamTasks: SyncChanges<TeamTaskDto>,
    val teams: SyncChanges<TeamDto>,
    val teamMembers: SyncChanges<TeamMemberDto>,
    val messages: SyncChanges<MessageDto>
)

data class SyncMeta(
    val syncTimestamp: String,
    val deviceId: String
)

data class SyncedEntities(
    val personalTasks: List<String>,
    val teamTasks: List<String>,
    val messages: List<String>
)

data class ConflictDto(
    val id: String,
    val type: String, // "CONTENT_CONFLICT", "METADATA_CONFLICT", "PERMISSION_CONFLICT"
    val entityType: String, // "personal_task", "team_task", "message", etc.
    val serverVersion: Any?, // Server version of the entity
    val clientVersion: Any? // Client version of the entity
)

data class ResolvedEntitiesDto(
    val personalTasks: List<PersonalTaskDto>,
    val teamTasks: List<TeamTaskDto>,
    val messages: List<MessageDto>
)

// DTO models
data class PersonalTaskDto(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val dueDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String
)

data class TeamTaskDto(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val dueDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val teamId: String,
    val assignedUserId: String?
)

data class TeamDto(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerId: String
)

data class TeamMemberDto(
    val id: String,
    val teamId: String,
    val userId: String,
    val role: String,
    val joinedAt: Long,
    val invitedBy: String
)

data class MessageDto(
    val id: String,
    val content: String,
    val senderId: String,
    val teamId: String?,
    val receiverId: String?,
    val timestamp: Long,
    val isRead: Boolean,
    val isDeleted: Boolean,
    val lastModified: Long
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val createdAt: Long
)

data class TeamRoleHistoryDto(
    val id: Long? = null,
    val teamId: String,
    val userId: String,
    val oldRole: String,
    val newRole: String,
    val changedByUserId: String,
    val timestamp: Long
)
