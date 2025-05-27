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

/**
 * Team data sync structure from accept invitation response
 */
data class TeamDataSync(
    val team: TeamSyncInfo,
    val members: List<TeamMember>,
    val messages: List<TeamMessage>,
    val team_tasks: List<TeamTask>,
    val documents: List<TeamDocument>,
    val folders: List<TeamFolder>,
    val sync_time: String
)

/**
 * Team info for sync (renamed to avoid conflict)
 */
data class TeamSyncInfo(
    val id: Long,
    val name: String,
    val description: String,
    val uuid: String,
    val created_at: String
)

/**
 * Team member for sync
 */
data class TeamMember(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val joined_at: String
)

/**
 * Team message for sync
 */
data class TeamMessage(
    val id: Long,
    val team_id: Long,
    val sender: MessageSender,
    val message: String,
    val file_url: String?,
    val created_at: String,
    val read_statuses: List<MessageReadStatus>
)

/**
 * Message sender info
 */
data class MessageSender(
    val id: Long,
    val name: String,
    val email: String
)

/**
 * Message read status
 */
data class MessageReadStatus(
    val user_id: Long,
    val read_at: String
)

/**
 * Team task for sync
 */
data class TeamTask(
    val id: Long,
    val team_id: Long,
    val title: String,
    val description: String,
    val status: String,
    val priority: Int,
    val deadline: String?,
    val created_at: String,
    val assignments: List<TaskAssignment>
)

/**
 * Task assignment info
 */
data class TaskAssignment(
    val user_id: Long,
    val user_name: String,
    val assigned_at: String
)

/**
 * Team document for sync
 */
data class TeamDocument(
    val id: Long,
    val team_id: Long,
    val name: String,
    val file_path: String,
    val file_size: Long,
    val file_type: String,
    val folder_id: Long?,
    val folder_name: String?,
    val uploaded_by: Long,
    val created_at: String
)

/**
 * Team folder for sync
 */
data class TeamFolder(
    val id: Long,
    val team_id: Long,
    val name: String,
    val parent_id: Long?,
    val created_at: String
)
