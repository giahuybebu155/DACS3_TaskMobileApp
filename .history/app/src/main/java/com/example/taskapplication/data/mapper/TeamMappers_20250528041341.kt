package com.example.taskapplication.data.mapper

import com.example.taskapplication.data.api.request.TeamRequest
import com.example.taskapplication.data.api.response.TeamResponse
import com.example.taskapplication.data.database.entities.TeamEntity
import com.example.taskapplication.domain.model.Team
import java.util.*

// Entity to Domain
fun TeamEntity.toDomainModel(): Team {
    return Team(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        createdBy = createdBy,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified,
        createdAt = createdAt
    )
}

// Domain to Entity
fun Team.toEntity(): TeamEntity {
    return TeamEntity(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        createdBy = createdBy,
        serverId = serverId,
        syncStatus = syncStatus,
        lastModified = lastModified,
        createdAt = createdAt
    )
}

// API Response to Entity
fun TeamResponse.toEntity(existingTeam: TeamEntity? = null): TeamEntity {
    return TeamEntity(
        id = existingTeam?.id ?: uuid, // Sử dụng UUID từ API
        name = name,
        description = description,
        ownerId = creator?.id?.toString(), // Từ creator object
        createdBy = created_by.toString(), // Từ created_by field
        serverId = id, // Numeric ID từ server
        syncStatus = "synced",
        lastModified = System.currentTimeMillis(),
        createdAt = existingTeam?.createdAt ?: parseTimestamp(created_at) ?: System.currentTimeMillis()
    )
}

/**
 * Parse timestamp từ string
 */
private fun parseTimestamp(timestamp: String?): Long? {
    return try {
        if (timestamp.isNullOrEmpty()) return null
        // Parse ISO 8601 timestamp
        val formatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME
        val dateTime = java.time.LocalDateTime.parse(timestamp.replace("Z", ""), formatter)
        dateTime.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

// Entity to API Request
fun TeamEntity.toApiRequest(): TeamRequest {
    return TeamRequest(
        name = name,
        description = description,
        ownerId = ownerId
    )
}

// Domain to API Request
fun Team.toApiRequest(): TeamRequest {
    return TeamRequest(
        name = name,
        description = description,
        ownerId = ownerId
    )
}