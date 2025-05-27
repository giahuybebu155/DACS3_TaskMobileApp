package com.example.taskapplication.data.database.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class for joining TeamMemberEntity with UserEntity
 * This allows us to get team member information along with user details in a single query
 */
data class TeamMemberWithUser(
    @Embedded val teamMember: TeamMemberEntity,
    
    @Relation(
        parentColumn = "userId",
        entityColumn = "id"
    )
    val user: UserEntity?
)
