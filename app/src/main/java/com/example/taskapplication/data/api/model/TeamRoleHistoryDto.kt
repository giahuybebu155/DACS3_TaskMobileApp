package com.example.taskapplication.data.api.model

import com.google.gson.annotations.SerializedName

/**
 * DTO for team role history
 */
data class TeamRoleHistoryDto(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("team_id")
    val teamId: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("old_role")
    val oldRole: String,
    
    @SerializedName("new_role")
    val newRole: String,
    
    @SerializedName("changed_by_user_id")
    val changedByUserId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long
)
