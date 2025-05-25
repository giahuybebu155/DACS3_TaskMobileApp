package com.example.taskapplication.data.api.response

/**
 * Response wrapper for team invitations
 * This class is used to handle the response from the /teams/{team_id}/invitations endpoint
 * which returns a JSON object with a "data" field containing an array of invitations
 * following the JSON:API specification
 */
data class TeamInvitationsResponse(
    val data: List<TeamInvitationResponse> = emptyList()
)
