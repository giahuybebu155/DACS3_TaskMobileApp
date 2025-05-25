package com.example.taskapplication.data.api.response

/**
 * Response wrapper for user invitations
 * This class is used to handle the response from the /invitations endpoint
 * which returns a JSON object with an "invitations" field containing an array of invitations
 */
data class UserInvitationsResponse(
    val invitations: List<TeamInvitationResponse> = emptyList(),
    val message: String? = null
)
