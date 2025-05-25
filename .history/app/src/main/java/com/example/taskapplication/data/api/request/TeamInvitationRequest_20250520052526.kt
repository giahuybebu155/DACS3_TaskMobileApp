package com.example.taskapplication.data.api.request

/**
 * Request model for team invitation
 */
data class TeamInvitationRequest(
    val email: String,
    val role: String,
    val userId: String? = null  // Thêm trường userId với giá trị mặc định là null
)
