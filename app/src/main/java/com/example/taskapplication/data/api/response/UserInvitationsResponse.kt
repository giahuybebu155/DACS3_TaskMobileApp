package com.example.taskapplication.data.api.response

/**
 * Response wrapper for user invitations
 * This class is used to handle the response from the /invitations endpoint
 * which returns a JSON object with a "data" field containing an array of invitations
 * following the JSON:API specification
 */
data class UserInvitationsResponse(
    val data: List<TeamInvitationResponse> = emptyList()
)
