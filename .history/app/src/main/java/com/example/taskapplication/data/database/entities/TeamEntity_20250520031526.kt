package com.example.taskapplication.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String, // UUID String
    val name: String,
    val description: String?,
    val ownerId: String?,
    val createdBy: String?,
    val serverId: String?,
    val syncStatus: String,
    val lastModified: Long,
    val createdAt: Long
)