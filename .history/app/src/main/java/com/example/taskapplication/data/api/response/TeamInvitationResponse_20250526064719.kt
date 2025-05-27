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
    val expires_at: Long,
    val inviter_id: Long? = null,
    val inviter_name: String? = null
)

/**
 * Response for accept invitation
 */
data class AcceptInvitationResponse(
    val message: String,
    val team: InvitationTeamInfo,
    val role: String
)

/**
 * Response for reject invitation
 */
data class RejectInvitationResponse(
    val message: String
)

/**
 * Response for cancel invitation
 */
data class CancelInvitationResponse(
    val message: String
)

/**
 * Team info in accept invitation response
 */
data class InvitationTeamInfo(
    val id: Long,
    val name: String,
    val description: String,
    val created_at: String
)
