package com.example.taskapplication.data.api.response

/**
 * Response model for team invitation
 */
data class TeamInvitationResponse(
    val id: Long,
    val team_id: Long,
    val team_name: String,
    val email: String,
    val role: String,
    val status: String,
    val token: String?,
    val created_at: Long,
    val expires_at: Long
)
