package com.example.taskapplication.data.api

import com.example.taskapplication.data.api.request.*
import com.example.taskapplication.data.api.response.*
import com.example.taskapplication.data.api.response.SearchUsersResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Auth
    @POST("auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>

    @POST("auth/google")
    suspend fun loginWithGoogle(@Body googleAuthRequest: GoogleAuthRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body logoutRequest: LogoutRequest): Response<MessageResponse>

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<MessageResponse>

    @GET("user")
    suspend fun getCurrentUser(): Response<UserDataResponse>

    @GET("settings")
    suspend fun getUserSettings(): Response<UserSettingsResponse>

    @PUT("settings")
    suspend fun updateUserSettings(@Body request: UserSettingsRequest): Response<UserSettingsResponse>

    // Personal Tasks
    @GET("personal-tasks")
    suspend fun getPersonalTasks(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("due_date_start") dueDateStart: String? = null,
        @Query("due_date_end") dueDateEnd: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null
    ): Response<PersonalTaskListResponse>

    @GET("personal-tasks/{id}")
    suspend fun getPersonalTask(@Path("id") id: String): Response<PersonalTaskResponse>

    @POST("personal-tasks")
    suspend fun createPersonalTask(@Body request: PersonalTaskRequest): Response<PersonalTaskResponse>

    @PUT("personal-tasks/{id}")
    suspend fun updatePersonalTask(
        @Path("id") id: String,
        @Body request: PersonalTaskRequest
    ): Response<PersonalTaskResponse>

    @DELETE("personal-tasks/{id}")
    suspend fun deletePersonalTask(@Path("id") id: String): Response<Unit>

    @POST("personal-tasks/order")
    suspend fun updateTaskOrder(@Body request: TaskOrderRequest): Response<Unit>

    // Các endpoint filter và search đã được hợp nhất vào endpoint getPersonalTasks

    // Subtasks - Đã loại bỏ vì không còn sử dụng

    // Team Tasks
    @GET("team-tasks")
    suspend fun getTeamTasks(): Response<List<TeamTaskResponse>>

    @GET("team-tasks/{id}")
    suspend fun getTeamTask(@Path("id") id: String): Response<TeamTaskResponse>

    @POST("team-tasks")
    suspend fun createTeamTask(@Body request: TeamTaskRequest): Response<TeamTaskResponse>

    @PUT("team-tasks/{id}")
    suspend fun updateTeamTask(
        @Path("id") id: String,
        @Body request: TeamTaskRequest
    ): Response<TeamTaskResponse>

    @DELETE("team-tasks/{id}")
    suspend fun deleteTeamTask(@Path("id") id: String): Response<Unit>

    // Messages
    @GET("messages")
    suspend fun getMessages(): Response<List<MessageResponse>>

    @GET("messages/{id}")
    suspend fun getMessage(@Path("id") id: String): Response<MessageResponse>

    @POST("messages")
    suspend fun createMessage(@Body request: MessageRequest): Response<MessageResponse>

    @PUT("messages/{id}")
    suspend fun updateMessage(
        @Path("id") id: String,
        @Body request: MessageRequest
    ): Response<MessageResponse>

    @DELETE("messages/{id}")
    suspend fun deleteMessage(@Path("id") id: String): Response<Unit>

    @POST("messages/{id}/read")
    suspend fun markMessageAsRead(@Path("id") id: String, @Query("userId") userId: String): Response<Unit>

    @POST("messages/{id}/reactions")
    suspend fun addReaction(@Path("id") id: String, @Query("userId") userId: String, @Query("reaction") reaction: String): Response<Unit>

    @DELETE("messages/{id}/reactions")
    suspend fun removeReaction(@Path("id") id: String, @Query("userId") userId: String, @Query("reaction") reaction: String): Response<Unit>

    // Teams
    @GET("teams")
    suspend fun getTeams(): Response<List<TeamResponse>>

    @GET("teams/{id}")
    suspend fun getTeam(@Path("id") id: String): Response<TeamResponse>

    @POST("teams")
    suspend fun createTeam(@Body request: TeamRequest): Response<TeamResponse>

    @PUT("teams/{id}")
    suspend fun updateTeam(
        @Path("id") id: String,
        @Body request: TeamRequest
    ): Response<TeamResponse>

    @DELETE("teams/{id}")
    suspend fun deleteTeam(@Path("id") id: String): Response<Unit>

    // Team Members
    @GET("teams/{teamId}/members")
    suspend fun getTeamMembers(@Path("teamId") teamId: String): Response<List<TeamMemberResponse>>

    @POST("teams/{teamId}/members")
    suspend fun addTeamMember(@Path("teamId") teamId: String, @Query("userId") userId: String, @Query("role") role: String): Response<TeamMemberResponse>

    @DELETE("teams/{teamId}/members/{userId}")
    suspend fun removeTeamMember(@Path("teamId") teamId: String, @Path("userId") userId: String): Response<Unit>

    @PUT("teams/{teamId}/members/{userId}/role")
    suspend fun updateTeamMemberRole(@Path("teamId") teamId: String, @Path("userId") userId: String, @Query("role") role: String): Response<TeamMemberResponse>

    // Users
    @GET("users")
    suspend fun getUsers(): Response<List<UserResponse>>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): Response<UserResponse>

    @POST("users")
    suspend fun createUser(@Body request: UserRequest): Response<UserResponse>

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body request: UserRequest
    ): Response<UserResponse>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("query") query: String? = null,
        @Query("name") name: String? = null,
        @Query("email") email: String? = null,
        @Query("exclude_team") excludeTeam: Int? = null,
        @Query("per_page") perPage: Int? = null
    ): Response<SearchUsersResponse>

    // Kanban
    @GET("teams/{team_id}/kanban")
    suspend fun getKanbanBoard(@Path("team_id") teamId: String): Response<KanbanResponse>

    @PATCH("teams/{team_id}/kanban/tasks/{task_id}/move")
    suspend fun moveKanbanTask(
        @Path("team_id") teamId: String,
        @Path("task_id") taskId: String,
        @Body request: MoveTaskRequest
    ): Response<KanbanTaskResponse>

    // Chat
    @GET("teams/{team_id}/chat")
    suspend fun getTeamChat(
        @Path("team_id") teamId: Long,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ChatResponse>

    @POST("teams/{team_id}/chat")
    @Multipart
    suspend fun sendTeamMessage(
        @Path("team_id") teamId: Long,
        @Part("content") content: String,
        @Part attachments: List<MultipartBody.Part>? = null
    ): Response<MessageResponse>

    @PUT("teams/{team_id}/chat/{message_id}")
    suspend fun updateMessage(
        @Path("team_id") teamId: Long,
        @Path("message_id") messageId: Long,
        @Body message: MessageRequest
    ): Response<MessageResponse>

    @DELETE("teams/{team_id}/chat/{message_id}")
    suspend fun deleteMessage(
        @Path("team_id") teamId: Long,
        @Path("message_id") messageId: Long
    ): Response<Unit>

    // Sync
    @GET("sync")
    suspend fun getSyncData(
        @Query("last_sync_at") lastSyncAt: String,
        @Query("device_id") deviceId: String
    ): Response<SyncResponse>

    @POST("sync")
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>

    @POST("sync/initial")
    suspend fun initialSync(@Query("deviceId") deviceId: String): Response<com.example.taskapplication.data.api.model.InitialSyncResponse>

    @POST("sync/quick")
    suspend fun quickSync(
        @Query("deviceId") deviceId: String,
        @Query("lastSyncedAt") lastSyncedAt: String
    ): Response<com.example.taskapplication.data.api.model.QuickSyncResponse>

    @POST("sync/push")
    suspend fun pushChanges(@Body changes: com.example.taskapplication.data.api.model.SyncChangesRequest): Response<com.example.taskapplication.data.api.model.PushChangesResponse>

    @POST("sync/conflicts")
    suspend fun resolveConflicts(@Body request: com.example.taskapplication.data.api.model.ResolveConflictsRequest): Response<com.example.taskapplication.data.api.model.PushChangesResponse>

    @GET("sync/teams")
    suspend fun getTeamsChanges(@Query("lastSyncedAt") lastSyncedAt: String): Response<com.example.taskapplication.data.api.model.QuickSyncResponse>

    @GET("sync/team-members")
    suspend fun getTeamMembersChanges(@Query("lastSyncedAt") lastSyncedAt: String): Response<com.example.taskapplication.data.api.model.QuickSyncResponse>

    @GET("sync/team-tasks")
    suspend fun getTeamTasksChanges(@Query("lastSyncedAt") lastSyncedAt: String): Response<com.example.taskapplication.data.api.model.QuickSyncResponse>

    @GET("sync/teams/{teamId}/tasks")
    suspend fun getTeamTasksChangesByTeam(@Path("teamId") teamId: String, @Query("lastSyncedAt") lastSyncedAt: String): Response<com.example.taskapplication.data.api.model.QuickSyncResponse>

    @POST("teams/tasks/{taskId}/assign")
    suspend fun assignTeamTask(@Path("taskId") taskId: String, @Query("userId") userId: String): Response<com.example.taskapplication.data.api.response.TeamTaskResponse>

    @POST("teams/{teamId}/members")
    suspend fun inviteUserToTeam(
        @Path("teamId") teamId: String,
        @Query("userId") userId: String,
        @Query("role") role: String
    ): Response<com.example.taskapplication.data.api.model.TeamMemberDto>

    @PUT("teams/{teamId}/members/{memberId}")
    suspend fun updateTeamMember(
        @Path("teamId") teamId: String,
        @Path("memberId") memberId: String,
        @Query("role") role: String
    ): Response<com.example.taskapplication.data.api.model.TeamMemberDto>

    @DELETE("teams/{teamId}/members/{memberId}")
    suspend fun removeUserFromTeam(
        @Path("teamId") teamId: String,
        @Path("memberId") memberId: String
    ): Response<Unit>

    @POST("sync/role-history")
    suspend fun syncRoleHistory(@Body roleHistory: List<com.example.taskapplication.data.api.model.TeamRoleHistoryDto>): Response<com.example.taskapplication.data.api.model.PushChangesResponse>

    // Notifications
    @GET("notifications")
    suspend fun getNotifications(@Query("since") since: Long? = null): Response<List<NotificationResponse>>

    @POST("notifications/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<Unit>

    @DELETE("notifications/register/{device_id}")
    suspend fun unregisterDevice(@Path("device_id") deviceId: String): Response<Unit>

    @GET("notifications/settings")
    suspend fun getNotificationSettings(): Response<NotificationSettingsResponse>

    @PATCH("notifications/settings")
    suspend fun updateNotificationSettings(@Body request: NotificationSettingsRequest): Response<NotificationSettingsResponse>

    // Analytics
    @GET("analytics/tasks")
    suspend fun getTaskAnalytics(
        @Query("team_id") teamId: Long? = null,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<TaskAnalyticsResponse>

    @GET("analytics/teams/{team_id}/performance")
    suspend fun getTeamPerformance(
        @Path("team_id") teamId: Long,
        @Query("period") period: String,
        @Query("start_date") startDate: String
    ): Response<TeamPerformanceResponse>

    // Calendar
    @GET("calendar/events")
    suspend fun getCalendarEvents(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("team_id") teamId: Long? = null
    ): Response<List<CalendarEventResponse>>

    @POST("calendar/events")
    suspend fun createCalendarEvent(@Body request: CalendarEventRequest): Response<CalendarEventResponse>

    // Team Invitations
    @GET("teams/{team_id}/invitations")
    suspend fun getTeamInvitations(@Path("team_id") teamId: String): Response<List<TeamInvitationResponse>>

    @POST("teams/{team_id}/invitations")
    suspend fun sendInvitation(
        @Path("team_id") teamId: String,
        @Body request: TeamInvitationRequest
    ): Response<TeamInvitationResponse>

    @PUT("teams/{team_id}/invitations/{invitation_id}")
    suspend fun resendInvitation(
        @Path("team_id") teamId: String,
        @Path("invitation_id") invitationId: String,
        @Body request: TeamInvitationRequest
    ): Response<TeamInvitationResponse>

    @POST("invitations/accept")
    suspend fun acceptInvitation(@Body request: Map<String, String>): Response<Unit>

    @POST("invitations/reject")
    suspend fun rejectInvitation(@Body request: Map<String, String>): Response<Unit>

    @DELETE("teams/{team_id}/invitations/{invitation_id}")
    suspend fun cancelInvitation(
        @Path("team_id") teamId: String,
        @Path("invitation_id") invitationId: String
    ): Response<Unit>
}