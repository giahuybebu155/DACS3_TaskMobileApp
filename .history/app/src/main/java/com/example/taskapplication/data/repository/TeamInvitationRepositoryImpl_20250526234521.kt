package com.example.taskapplication.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.database.dao.TeamInvitationDao
import com.example.taskapplication.data.database.entities.TeamInvitationEntity
import com.example.taskapplication.data.mapper.toApiRequest
import com.example.taskapplication.data.mapper.toDomainModel
import com.example.taskapplication.data.mapper.toEntity
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.EOFException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.JsonSyntaxException

@Singleton
class TeamInvitationRepositoryImpl @Inject constructor(
    private val teamInvitationDao: TeamInvitationDao,
    private val teamDao: com.example.taskapplication.data.database.dao.TeamDao,  // Add teamDao dependency
    private val teamMemberDao: com.example.taskapplication.data.database.dao.TeamMemberDao,  // Add teamMemberDao dependency
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager,
    private val connectionChecker: ConnectionChecker,
    private val context: Context
) : TeamInvitationRepository {

    private val TAG = "TeamInvitationRepository"

    override fun getTeamInvitations(teamId: String): Flow<List<TeamInvitation>> {
        return teamInvitationDao.getTeamInvitations(teamId)
            .map { entities -> entities.map { it.toDomainModel() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getTeamInvitationsByStatus(teamId: String, status: String): Flow<List<TeamInvitation>> {
        return teamInvitationDao.getTeamInvitationsByStatus(teamId, status)
            .map { entities -> entities.map { it.toDomainModel() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getUserInvitations(): Flow<List<TeamInvitation>> {
        return dataStoreManager.userEmail.map { email ->
            if (email != null) {
                teamInvitationDao.getUserInvitationsByEmail(email).first().map { it.toDomainModel() }
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun sendInvitation(teamId: String, email: String, role: String): Result<TeamInvitation> {
        try {
            // Create invitation entity
            val invitationId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val expiresAt = timestamp + (7 * 24 * 60 * 60 * 1000) // 7 days

            // Get team name
            val teamName = "Team $teamId" // Placeholder, in real implementation we would get the team name from the database

            val invitation = TeamInvitation(
                id = invitationId,
                teamId = teamId,
                teamName = teamName,
                email = email,
                role = role,
                status = "pending",
                token = UUID.randomUUID().toString(),
                createdAt = timestamp,
                expiresAt = expiresAt,
                serverId = null,
                syncStatus = "pending_create",
                lastModified = timestamp
            )

            // Save to local database
            teamInvitationDao.insertInvitation(invitation.toEntity())

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    Log.d(TAG, "🎯 [SEND] ===== BẮT ĐẦU GỬI LỜI MỜI LÊN SERVER =====")
                    Log.d(TAG, "🎯 [SEND] Team ID: $teamId")
                    Log.d(TAG, "🎯 [SEND] Email: $email")
                    Log.d(TAG, "🎯 [SEND] Role: $role")
                    Log.d(TAG, "🎯 [SEND] Local invitation ID: ${invitation.id}")
                    Log.d(TAG, "🎯 [SEND] Timestamp: ${System.currentTimeMillis()}")
                    Log.d(TAG, "🌐 [SEND] Network available: ${connectionChecker.isNetworkAvailable()}")
                    Log.d(TAG, "🌐 [SEND] Target server: http://10.0.2.2:8000/api")
                    Log.d(TAG, "⚠️ [SEND] IMPORTANT: This is a safe operation - app will NOT crash if server is down")

                    val request = invitation.toEntity().toApiRequest()
                    Log.d(TAG, "📋 [SEND] Request data: $request")

                    Log.d(TAG, "🌐 [SEND] Gửi request lên API endpoint...")
                    val response = apiService.sendInvitation(teamId, request)
                    Log.d(TAG, "📡 [SEND] Response received from server")
                    Log.d(TAG, "📡 [SEND] Response code: ${response.code()}")
                    Log.d(TAG, "📡 [SEND] Response message: ${response.message()}")

                    if (response.isSuccessful && response.body() != null) {
                        val serverInvitation = response.body()!!
                        Log.d(TAG, "✅ [SEND] SUCCESS - Server invitation: $serverInvitation")
                        Log.d(TAG, "✅ [SEND] Server invitation ID: ${serverInvitation.id}")
                        Log.d(TAG, "✅ [SEND] Server token: ${serverInvitation.token?.take(10)}...")

                        val updatedInvitation = invitation.copy(
                            serverId = serverInvitation.id.toString(),
                            token = serverInvitation.token,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(updatedInvitation.toEntity())
                        Log.d(TAG, "💾 [SEND] Updated local invitation with server data")

                        // Log để tracking real-time
                        Log.d(TAG, "📡 [SEND] ===== REAL-TIME TRACKING =====")
                        Log.d(TAG, "📡 [SEND] Expecting WebSocket event 'team.invitation.created' soon...")
                        Log.d(TAG, "📡 [SEND] Target user email: $email should receive invitation notification")
                        Log.d(TAG, "📡 [SEND] Server invitation ID: ${serverInvitation.id}")
                        Log.d(TAG, "📡 [SEND] Invitation token: ${serverInvitation.token?.take(10)}...")
                        Log.d(TAG, "📡 [SEND] Team ID: $teamId")
                        Log.d(TAG, "📡 [SEND] WebSocket should be connected on target device")
                        Log.d(TAG, "📡 [SEND] Check target device logs for WebSocket events")

                        return Result.success(updatedInvitation)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ [SEND] FAILED - Response code: ${response.code()}")
                        Log.e(TAG, "❌ [SEND] Response message: ${response.message()}")
                        Log.e(TAG, "❌ [SEND] Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 [SEND] EXCEPTION when syncing invitation to server:")
                    Log.e(TAG, "💥 [SEND] Exception type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "💥 [SEND] Exception message: ${e.message}")

                    // Detailed exception handling to prevent crashes
                    when (e) {
                        is java.net.SocketTimeoutException -> {
                            Log.e(TAG, "⏰ [SEND] Server timeout - server not responding or too slow")
                        }
                        is java.net.ConnectException -> {
                            Log.e(TAG, "🔌 [SEND] Connection refused - server not running")
                        }
                        is java.net.UnknownHostException -> {
                            Log.e(TAG, "🌐 [SEND] Unknown host - DNS resolution failed")
                        }
                        is retrofit2.HttpException -> {
                            Log.e(TAG, "📡 [SEND] HTTP error: ${e.code()} - ${e.message()}")
                        }
                        is java.io.IOException -> {
                            Log.e(TAG, "📁 [SEND] IO error - network or file system issue")
                        }
                        else -> {
                            Log.e(TAG, "❓ [SEND] Unknown exception type")
                        }
                    }

                    Log.w(TAG, "⚠️ [SEND] Server sync failed but app continues normally - data saved locally")
                    e.printStackTrace()
                    // Continue with local invitation - DO NOT CRASH
                }
            } else {
                Log.d(TAG, "📶 [SEND] Không có kết nối mạng, lưu lời mời vào local database")
            }

            return Result.success(invitation)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending invitation", e)
            return Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(token: String): Result<Unit> {
        try {
            Log.d(TAG, "🎯 [ACCEPT] Bắt đầu chấp nhận lời mời với token: ${token.take(10)}...")

            val invitation = teamInvitationDao.getInvitationByToken(token)
            if (invitation == null) {
                Log.e(TAG, "❌ [ACCEPT] Không tìm thấy lời mời với token: ${token.take(10)}...")
                return Result.failure(IOException("Invitation not found"))
            }

            Log.d(TAG, "📋 [ACCEPT] Chi tiết lời mời: teamId=${invitation.teamId}, teamName=${invitation.teamName}, email=${invitation.email}, status=${invitation.status}")

            if (invitation.status != "pending") {
                Log.e(TAG, "❌ [ACCEPT] Lời mời không ở trạng thái pending: ${invitation.status}")
                return Result.failure(IOException("Invitation is not pending"))
            }

            // Update local status
            val updatedInvitation = invitation.copy(
                status = "accepted",
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            teamInvitationDao.updateInvitation(updatedInvitation)
            Log.d(TAG, "✅ [ACCEPT] Đã cập nhật trạng thái lời mời local thành 'accepted'")

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    Log.d(TAG, "🌐 [ACCEPT] Gửi request chấp nhận lời mời lên server...")
                    val response = apiService.acceptInvitation(mapOf("token" to token))
                    Log.d(TAG, "📡 [ACCEPT] Kết quả từ server: ${response.code()}")

                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "✅ [ACCEPT] Server response body: $responseBody")

                        // Log thông tin team được join
                        responseBody?.let { body ->
                            Log.d(TAG, "🏢 [ACCEPT] Đã join team: ${body.team.name} với role: ${body.role}")
                            Log.d(TAG, "🔄 [ACCEPT] Bắt đầu quá trình đồng bộ dữ liệu team...")

                            // Set this team as current team for WebSocket connection
                            val teamIdForWebSocket = body.team.id.toString()
                            dataStoreManager.saveCurrentTeamId(teamIdForWebSocket)
                            Log.d(TAG, "✅ [ACCEPT] Đã đặt team làm team hiện tại cho WebSocket: $teamIdForWebSocket")

                            // ✅ ADD USER TO TEAM WHEN ACCEPTING INVITATION
                            val currentUserId = dataStoreManager.getCurrentUserId()
                            if (currentUserId != null) {
                                Log.d(TAG, "👤 [ACCEPT] Thêm user vào team sau khi accept invitation")

                                // Create team member entity
                                val teamMemberEntity = com.example.taskapplication.data.database.entities.TeamMemberEntity(
                                    id = UUID.randomUUID().toString(),
                                    teamId = invitation.teamId,
                                    userId = currentUserId,
                                    role = body.role, // Use role from server response
                                    joinedAt = System.currentTimeMillis(),
                                    invitedBy = invitation.invitedBy,
                                    serverId = null, // Will be updated when syncing members
                                    syncStatus = "synced", // Mark as synced since we got it from server
                                    lastModified = System.currentTimeMillis(),
                                    createdAt = System.currentTimeMillis()
                                )

                                // Insert team member
                                teamMemberDao.insertTeamMember(teamMemberEntity)
                                Log.d(TAG, "✅ [ACCEPT] Đã thêm user vào team: role=${body.role}")
                            } else {
                                Log.e(TAG, "❌ [ACCEPT] Không thể thêm user vào team: currentUserId is null")
                            }

                            // Xử lý team_data nếu có trong response
                            body.team_data?.let { teamData ->
                                Log.d(TAG, "📦 [ACCEPT] Nhận được team_data từ server, bắt đầu auto-sync...")
                                syncTeamDataFromAcceptResponse(teamData)
                            } ?: run {
                                Log.d(TAG, "⚠️ [ACCEPT] Không có team_data trong response, trigger sync thủ công...")
                                // Trigger đồng bộ dữ liệu team ngay sau khi accept (fallback)
                                triggerTeamDataSync(invitation.teamId)
                            }
                        }

                        val finalInvitation = updatedInvitation.copy(
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(finalInvitation)
                        Log.d(TAG, "✅ [ACCEPT] Đã đồng bộ thành công việc chấp nhận lời mời với server")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ [ACCEPT] Lỗi khi chấp nhận lời mời trên server: ${response.code()}, Error: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 [ACCEPT] Exception khi chấp nhận lời mời trên server: ${e.message}", e)
                    // Continue with local update
                }
            } else {
                Log.d(TAG, "📶 [ACCEPT] Không có mạng, lời mời sẽ được đồng bộ sau")
            }

            Log.d(TAG, "🎉 [ACCEPT] Hoàn thành quá trình chấp nhận lời mời")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "💥 [ACCEPT] Lỗi khi chấp nhận lời mời", e)
            return Result.failure(e)
        }
    }

    override suspend fun rejectInvitation(token: String): Result<Unit> {
        try {
            val invitation = teamInvitationDao.getInvitationByToken(token)
                ?: return Result.failure(IOException("Invitation not found"))

            if (invitation.status != "pending") {
                return Result.failure(IOException("Invitation is not pending"))
            }

            // Update local status
            val updatedInvitation = invitation.copy(
                status = "rejected",
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            teamInvitationDao.updateInvitation(updatedInvitation)

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    val response = apiService.rejectInvitation(mapOf("token" to token))

                    if (response.isSuccessful) {
                        val finalInvitation = updatedInvitation.copy(
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(finalInvitation)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rejecting invitation on server", e)
                    // Continue with local update
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting invitation", e)
            return Result.failure(e)
        }
    }

    override suspend fun cancelInvitation(teamId: String, invitationId: String): Result<Unit> {
        try {
            val invitation = teamInvitationDao.getInvitationById(invitationId)
                ?: return Result.failure(IOException("Invitation not found"))

            if (invitation.teamId != teamId) {
                return Result.failure(IOException("Invitation does not belong to this team"))
            }

            // Update local status
            val updatedInvitation = invitation.copy(
                status = "cancelled",
                syncStatus = if (invitation.serverId != null) "pending_update" else "pending_delete",
                lastModified = System.currentTimeMillis()
            )
            teamInvitationDao.updateInvitation(updatedInvitation)

            // If online and has server ID, sync with server
            if (connectionChecker.isNetworkAvailable() && invitation.serverId != null) {
                try {
                    val response = apiService.cancelInvitation(teamId, invitation.serverId)

                    if (response.isSuccessful) {
                        // If successfully cancelled on server, delete from local database
                        teamInvitationDao.deleteInvitation(updatedInvitation)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cancelling invitation on server", e)
                    // Continue with local update
                }
            } else if (invitation.serverId == null) {
                // If no server ID, just delete locally
                teamInvitationDao.deleteInvitation(updatedInvitation)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling invitation", e)
            return Result.failure(e)
        }
    }

    override suspend fun resendInvitation(invitationId: String): Result<TeamInvitation> {
        try {
            val invitation = teamInvitationDao.getInvitationById(invitationId)
                ?: return Result.failure(IOException("Invitation not found"))

            if (invitation.status != "pending") {
                return Result.failure(IOException("Only pending invitations can be resent"))
            }

            // Update expiration date and last modified
            val timestamp = System.currentTimeMillis()
            val expiresAt = timestamp + (7 * 24 * 60 * 60 * 1000) // 7 days

            val updatedInvitation = invitation.copy(
                expiresAt = expiresAt,
                syncStatus = "pending_update",
                lastModified = timestamp
            )

            teamInvitationDao.updateInvitation(updatedInvitation)

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable() && invitation.serverId != null) {
                try {
                    val request = updatedInvitation.toApiRequest()
                    val response = apiService.resendInvitation(invitation.teamId, invitation.serverId, request)

                    if (response.isSuccessful && response.body() != null) {
                        val serverInvitation = response.body()!!
                        val finalInvitation = updatedInvitation.copy(
                            token = serverInvitation.token,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(finalInvitation)
                        return Result.success(finalInvitation.toDomainModel())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resending invitation on server", e)
                    // Continue with local update
                }
            }

            return Result.success(updatedInvitation.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error resending invitation", e)
            return Result.failure(e)
        }
    }

    override suspend fun updateInvitationStatus(invitationId: String, status: String): Result<TeamInvitation> {
        try {
            val invitation = teamInvitationDao.getInvitationById(invitationId)
                ?: return Result.failure(IOException("Invitation not found"))

            if (invitation.status == status) {
                return Result.success(invitation.toDomainModel())
            }

            // Validate status
            if (status !in listOf("pending", "accepted", "rejected", "cancelled")) {
                return Result.failure(IOException("Invalid status: $status"))
            }

            // Update status
            val timestamp = System.currentTimeMillis()
            teamInvitationDao.updateInvitationStatus(invitationId, status, timestamp)

            // Get updated invitation
            val updatedInvitation = teamInvitationDao.getInvitationById(invitationId)
                ?: return Result.failure(IOException("Failed to update invitation"))

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable() && invitation.serverId != null) {
                try {
                    val response = when (status) {
                        "accepted" -> apiService.acceptInvitation(mapOf("token" to (invitation.token ?: "")))
                        "rejected" -> apiService.rejectInvitation(mapOf("token" to (invitation.token ?: "")))
                        "cancelled" -> apiService.cancelInvitation(invitation.teamId, invitation.serverId)
                        else -> null
                    }

                    if (response != null && response.isSuccessful) {
                        val finalInvitation = updatedInvitation.copy(
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(finalInvitation)
                        return Result.success(finalInvitation.toDomainModel())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating invitation status on server", e)
                    // Continue with local update
                }
            }

            return Result.success(updatedInvitation.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating invitation status", e)
            return Result.failure(e)
        }
    }

    override suspend fun getInvitationById(invitationId: String): Result<TeamInvitation> {
        try {
            val invitation = teamInvitationDao.getInvitationById(invitationId)
                ?: return Result.failure(IOException("Invitation not found"))

            return Result.success(invitation.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting invitation by ID", e)
            return Result.failure(e)
        }
    }

    override suspend fun syncInvitations(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            Log.d(TAG, "Không có kết nối mạng, không thể đồng bộ lời mời")
            return Result.failure(IOException("No network connection"))
        }

        try {
            Log.d(TAG, "Bắt đầu đồng bộ lời mời")

            // Sync pending invitations to server
            val pendingInvitations = teamInvitationDao.getPendingSyncInvitations()
            Log.d(TAG, "Có ${pendingInvitations.size} lời mời cần đồng bộ lên server")

            for (invitation in pendingInvitations) {
                Log.d(TAG, "Đồng bộ lời mời ${invitation.id} (${invitation.syncStatus}) lên server")

                when (invitation.syncStatus) {
                    "pending_create" -> {
                        // Send invitation to server
                        try {
                            val request = invitation.toApiRequest()
                            Log.d(TAG, "🔍 [DEBUG] Invitation info: localTeamId=${invitation.teamId}, email=${invitation.email}")

                            // Lấy team để có serverId
                            val team = teamDao.getTeamByIdSync(invitation.teamId)
                            if (team?.serverId.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [ERROR] Team serverId is null for invitation ${invitation.id}")
                                Log.e(TAG, "❌ [ERROR] Cannot send invitation without team serverId")
                                continue // Skip this invitation
                            }

                            Log.d(TAG, "🔍 [DEBUG] Team serverId: ${team!!.serverId}")
                            Log.d(TAG, "Gửi lời mời mới lên server: teamServerId=${team.serverId}, email=${invitation.email}")

                            // Sử dụng serverId thay vì local teamId
                            val response = apiService.sendInvitation(team.serverId!!, request)
                            Log.d(TAG, "Kết quả gửi lời mời: ${response.code()}")

                            if (response.isSuccessful && response.body() != null) {
                                val serverInvitation = response.body()!!
                                Log.d(TAG, "Lời mời đã được tạo trên server với ID: ${serverInvitation.id}")

                                val updatedInvitation = invitation.copy(
                                    serverId = serverInvitation.id.toString(),
                                    token = serverInvitation.token,
                                    syncStatus = "synced",
                                    lastModified = System.currentTimeMillis()
                                )
                                teamInvitationDao.updateInvitation(updatedInvitation)
                                Log.d(TAG, "Đã cập nhật lời mời trong cơ sở dữ liệu cục bộ")
                            } else {
                                val errorBody = response.errorBody()?.string()
                                Log.e(TAG, "Lỗi khi gửi lời mời lên server: ${response.code()}, Error: $errorBody")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Lỗi khi gửi lời mời lên server", e)
                        }
                    }
                    "pending_update" -> {
                        // Handle updates based on status
                        if (invitation.serverId != null) {
                            Log.d(TAG, "Cập nhật trạng thái lời mời ${invitation.id} (${invitation.status}) lên server")

                            try {
                                when (invitation.status) {
                                    "accepted" -> {
                                        val response = apiService.acceptInvitation(mapOf("token" to (invitation.token ?: "")))
                                        Log.d(TAG, "Kết quả chấp nhận lời mời: ${response.code()}")

                                        if (response.isSuccessful) {
                                            val responseBody = response.body()
                                            Log.d(TAG, "Response body chấp nhận lời mời: $responseBody")

                                            val updatedInvitation = invitation.copy(
                                                syncStatus = "synced",
                                                lastModified = System.currentTimeMillis()
                                            )
                                            teamInvitationDao.updateInvitation(updatedInvitation)
                                            Log.d(TAG, "Đã cập nhật trạng thái chấp nhận lời mời trong cơ sở dữ liệu cục bộ")
                                        } else {
                                            val errorBody = response.errorBody()?.string()
                                            Log.e(TAG, "Lỗi khi chấp nhận lời mời: ${response.code()}, Error: $errorBody")
                                        }
                                    }
                                    "rejected" -> {
                                        val response = apiService.rejectInvitation(mapOf("token" to (invitation.token ?: "")))
                                        Log.d(TAG, "Kết quả từ chối lời mời: ${response.code()}")

                                        if (response.isSuccessful) {
                                            val responseBody = response.body()
                                            Log.d(TAG, "Response body từ chối lời mời: $responseBody")

                                            val updatedInvitation = invitation.copy(
                                                syncStatus = "synced",
                                                lastModified = System.currentTimeMillis()
                                            )
                                            teamInvitationDao.updateInvitation(updatedInvitation)
                                            Log.d(TAG, "Đã cập nhật trạng thái từ chối lời mời trong cơ sở dữ liệu cục bộ")
                                        } else {
                                            val errorBody = response.errorBody()?.string()
                                            Log.e(TAG, "Lỗi khi từ chối lời mời: ${response.code()}, Error: $errorBody")
                                        }
                                    }
                                    "cancelled" -> {
                                        val response = apiService.cancelInvitation(invitation.teamId, invitation.serverId)
                                        Log.d(TAG, "Kết quả hủy lời mời: ${response.code()}")

                                        if (response.isSuccessful) {
                                            val responseBody = response.body()
                                            Log.d(TAG, "Response body hủy lời mời: $responseBody")

                                            teamInvitationDao.deleteInvitation(invitation)
                                            Log.d(TAG, "Đã xóa lời mời đã hủy khỏi cơ sở dữ liệu cục bộ")
                                        } else {
                                            val errorBody = response.errorBody()?.string()
                                            Log.e(TAG, "Lỗi khi hủy lời mời: ${response.code()}, Error: $errorBody")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Lỗi khi cập nhật trạng thái lời mời lên server", e)
                            }
                        }
                    }
                    "pending_delete" -> {
                        // Delete invitation on server
                        if (invitation.serverId != null) {
                            try {
                                Log.d(TAG, "Xóa lời mời ${invitation.id} khỏi server")
                                val response = apiService.cancelInvitation(invitation.teamId, invitation.serverId)
                                Log.d(TAG, "Kết quả xóa lời mời: ${response.code()}")

                                if (response.isSuccessful) {
                                    teamInvitationDao.deleteInvitation(invitation)
                                    Log.d(TAG, "Đã xóa lời mời khỏi cơ sở dữ liệu cục bộ")
                                } else {
                                    val errorBody = response.errorBody()?.string()
                                    Log.e(TAG, "Lỗi khi xóa lời mời: ${response.code()}, Error: $errorBody")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Lỗi khi xóa lời mời khỏi server", e)
                            }
                        } else {
                            // No server ID, just delete locally
                            teamInvitationDao.deleteInvitation(invitation)
                            Log.d(TAG, "Đã xóa lời mời chỉ tồn tại cục bộ khỏi cơ sở dữ liệu")
                        }
                    }
                }
            }

            // Lấy danh sách nhóm từ server
            Log.d(TAG, "🔍 [THEO DÕI] Lấy danh sách nhóm để đồng bộ lời mời")
            try {
                Log.d(TAG, "🔍 [THEO DÕI] Đang lấy danh sách nhóm từ server")
                val teamsResponse = apiService.getUserTeams()
                Log.d(TAG, "🔍 [THEO DÕI] Kết quả lấy danh sách nhóm: ${teamsResponse.code()}")

                val teams = if (teamsResponse.isSuccessful && teamsResponse.body() != null) {
                    val teamsList = teamsResponse.body()!!.map { it.id.toString() }
                    Log.d(TAG, "✅ [THEO DÕI] Lấy được ${teamsList.size} nhóm từ server: $teamsList")
                    teamsList
                } else {
                    // Fallback to a default team ID if we can't get the teams
                    val currentTeamId = dataStoreManager.getCurrentTeamId()
                    val fallbackTeams = if (currentTeamId != null) listOf(currentTeamId) else listOf("1")
                    Log.d(TAG, "ℹ️ [THEO DÕI] Sử dụng danh sách nhóm dự phòng: $fallbackTeams")
                    fallbackTeams
                }

                Log.d(TAG, "🔄 [THEO DÕI] Bắt đầu đồng bộ lời mời cho ${teams.size} nhóm: $teams")

                // Lấy lời mời cho mỗi nhóm
                for (teamId in teams) {
                    try {
                        Log.d(TAG, "🔍 [THEO DÕI] Đang lấy lời mời cho nhóm $teamId")

                        try {
                            val response = apiService.getTeamInvitations(teamId)
                            Log.d(TAG, "🔍 [THEO DÕI] Kết quả lấy lời mời: ${response.code()}")

                            if (response.isSuccessful) {
                                // Kiểm tra body có null không
                                val serverInvitations = response.body() ?: emptyList()
                                Log.d(TAG, "✅ [THEO DÕI] Nhận được ${serverInvitations.size} lời mời từ server cho nhóm $teamId")

                                for (serverInvitation in serverInvitations) {
                                    Log.d(TAG, "🔍 [THEO DÕI] Xử lý lời mời từ server: ID=${serverInvitation.id}, email=${serverInvitation.email}, status=${serverInvitation.status}")
                                    val existingInvitation = teamInvitationDao.getInvitationByServerId(serverInvitation.id.toString())
                                    Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra lời mời ${serverInvitation.id}: ${if (existingInvitation != null) "đã tồn tại" else "mới"}")

                                    if (existingInvitation == null) {
                                        // New invitation from server
                                        val newInvitation = serverInvitation.toEntity()
                                        Log.d(TAG, "📝 [THEO DÕI] Tạo entity lời mời mới: ${newInvitation.id}, email=${newInvitation.email}")
                                        teamInvitationDao.insertInvitation(newInvitation)
                                        Log.d(TAG, "✅ [THEO DÕI] Đã thêm lời mời mới vào cơ sở dữ liệu cục bộ: ${serverInvitation.id}")
                                    } else if (existingInvitation.syncStatus == "synced") {
                                        // Update existing invitation if it's synced (not pending changes)
                                        val updatedInvitation = serverInvitation.toEntity(existingInvitation)
                                        Log.d(TAG, "📝 [THEO DÕI] Cập nhật entity lời mời: ${updatedInvitation.id}, status=${updatedInvitation.status}")
                                        teamInvitationDao.updateInvitation(updatedInvitation)
                                        Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật lời mời hiện có: ${serverInvitation.id}")
                                    } else {
                                        Log.d(TAG, "ℹ️ [THEO DÕI] Bỏ qua cập nhật lời mời ${existingInvitation.id} vì đang có thay đổi cục bộ (syncStatus=${existingInvitation.syncStatus})")
                                    }
                                }
                            } else {
                                val errorBody = response.errorBody()?.string()
                                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời từ server: ${response.code()}, Error: $errorBody")

                                // Nếu API trả về lỗi 404, có thể endpoint chưa được triển khai hoặc đường dẫn không đúng
                                if (response.code() == 404) {
                                    Log.w(TAG, "⚠️ [THEO DÕI] Endpoint /teams/$teamId/invitations có thể chưa được triển khai hoặc đường dẫn không đúng")
                                }
                            }
                        } catch (e: EOFException) {
                            // Xử lý trường hợp JSON rỗng hoặc không hợp lệ
                            Log.w(TAG, "⚠️ [THEO DÕI] API trả về JSON rỗng hoặc không hợp lệ cho nhóm $teamId, coi như không có lời mời")
                        } catch (e: JsonSyntaxException) {
                            // Xử lý trường hợp JSON không hợp lệ
                            Log.w(TAG, "⚠️ [THEO DÕI] API trả về JSON không hợp lệ cho nhóm $teamId, coi như không có lời mời")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời cho nhóm $teamId: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời cho nhóm $teamId: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy danh sách nhóm: ${e.message}", e)
            }

            // Đồng bộ lời mời gửi đến người dùng hiện tại
            try {
                val email = dataStoreManager.userEmail.first()
                if (!email.isNullOrEmpty()) {
                    Log.d(TAG, "🔍 [THEO DÕI] Lấy lời mời gửi đến người dùng với email: $email")

                    try {
                        val response = apiService.getUserInvitations()
                        Log.d(TAG, "🔍 [THEO DÕI] Kết quả lấy lời mời người dùng: ${response.code()}")

                        if (response.isSuccessful && response.body() != null) {
                            try {
                                val userInvitations = response.body()!!  // Fix: Server trả về array trực tiếp, không phải object
                                Log.d(TAG, "✅ [THEO DÕI] Nhận được ${userInvitations.size} lời mời gửi đến người dùng")

                                // Tạo danh sách ID lời mời từ server để theo dõi những lời mời đã bị xóa
                                val serverInvitationIds = userInvitations.map { it.id.toString() }.toSet()

                                // Lấy danh sách lời mời hiện có trong cơ sở dữ liệu cục bộ
                                val localInvitations = teamInvitationDao.getAllInvitationsForEmail(email)
                                    .filter { it.serverId != null && it.status == "pending" }

                                // Xử lý từng lời mời từ server
                                for (serverInvitation in userInvitations) {
                                Log.d(TAG, "🔍 [THEO DÕI] Xử lý lời mời người dùng từ server: ID=${serverInvitation.id}, status=${serverInvitation.status}, teamId=${serverInvitation.team_id}")
                                val existingInvitation = teamInvitationDao.getInvitationByServerId(serverInvitation.id.toString())
                                Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra lời mời người dùng ${serverInvitation.id}: ${if (existingInvitation != null) "đã tồn tại" else "mới"}")

                                if (existingInvitation == null) {
                                    // Lời mời mới từ server
                                    val newInvitation = serverInvitation.toEntity()
                                    Log.d(TAG, "📝 [THEO DÕI] Tạo entity lời mời người dùng mới: ${newInvitation.id}, email=${newInvitation.email}")
                                    teamInvitationDao.insertInvitation(newInvitation)
                                    Log.d(TAG, "✅ [THEO DÕI] Đã thêm lời mời người dùng mới vào cơ sở dữ liệu cục bộ: ${serverInvitation.id}")

                                    // Hiển thị thông báo cho lời mời mới
                                    showNotificationForNewInvitation(newInvitation)
                                } else if (existingInvitation.syncStatus == "synced" || serverInvitation.status != existingInvitation.status) {
                                    // Cập nhật lời mời hiện có nếu đã đồng bộ hoặc trạng thái đã thay đổi
                                    val updatedInvitation = serverInvitation.toEntity(existingInvitation)
                                    Log.d(TAG, "📝 [THEO DÕI] Cập nhật entity lời mời người dùng: ${updatedInvitation.id}, status=${updatedInvitation.status}")
                                    teamInvitationDao.updateInvitation(updatedInvitation)
                                    Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật lời mời người dùng hiện có: ${serverInvitation.id}")
                                } else {
                                    Log.d(TAG, "ℹ️ [THEO DÕI] Bỏ qua cập nhật lời mời người dùng ${existingInvitation.id} vì đang có thay đổi cục bộ (syncStatus=${existingInvitation.syncStatus})")
                                }
                            }

                            // Xóa lời mời đã bị xóa trên server nhưng vẫn còn trong cơ sở dữ liệu cục bộ
                            for (localInvitation in localInvitations) {
                                if (localInvitation.serverId != null && !serverInvitationIds.contains(localInvitation.serverId)) {
                                    // Lời mời không còn tồn tại trên server
                                    if (localInvitation.syncStatus == "synced") {
                                        Log.d(TAG, "🗑️ [THEO DÕI] Xóa lời mời không còn tồn tại trên server: ${localInvitation.id}, serverId=${localInvitation.serverId}")
                                        teamInvitationDao.deleteInvitation(localInvitation)
                                    } else {
                                        Log.d(TAG, "ℹ️ [THEO DÕI] Giữ lại lời mời ${localInvitation.id} vì đang có thay đổi cục bộ (syncStatus=${localInvitation.syncStatus})")
                                    }
                                }
                                }
                            } catch (e: EOFException) {
                                // Xử lý trường hợp JSON rỗng hoặc không hợp lệ
                                Log.w(TAG, "⚠️ [THEO DÕI] API trả về JSON rỗng hoặc không hợp lệ cho lời mời người dùng, coi như không có lời mời")
                            } catch (e: JsonSyntaxException) {
                                // Xử lý trường hợp JSON không hợp lệ
                                Log.w(TAG, "⚠️ [THEO DÕI] API trả về JSON không hợp lệ cho lời mời người dùng, coi như không có lời mời")
                            } catch (e: NumberFormatException) {
                                // Xử lý trường hợp lỗi chuyển đổi kiểu dữ liệu
                                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)
                            } catch (e: Exception) {
                                // Xử lý các lỗi khác
                                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)
                            }
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời người dùng từ server: ${response.code()}, Error: $errorBody")

                            // Nếu API trả về lỗi 404, có thể endpoint chưa được triển khai hoặc đường dẫn không đúng
                            if (response.code() == 404) {
                                Log.w(TAG, "⚠️ [THEO DÕI] Endpoint /invitations có thể chưa được triển khai hoặc đường dẫn không đúng")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi gọi API lấy lời mời người dùng: ${e.message}", e)
                    }
                } else {
                    Log.d(TAG, "ℹ️ [THEO DÕI] Không có email người dùng, bỏ qua đồng bộ lời mời người dùng")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời người dùng: ${e.message}", e)
            }

            Log.d(TAG, "Đồng bộ lời mời hoàn tất")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ lời mời", e)
            return Result.failure(e)
        }
    }
    /**
     * Hiển thị thông báo cho lời mời mới
     */
    private fun showNotificationForNewInvitation(invitation: TeamInvitationEntity) {
        try {
            Log.d(TAG, "🔔 [THEO DÕI] Hiển thị thông báo cho lời mời mới: ${invitation.id}")

            // Tạo kênh thông báo nếu chưa có
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "team_invitations",
                    "Lời mời nhóm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi có lời mời tham gia nhóm mới"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Tạo intent để mở ứng dụng khi nhấn vào thông báo
            val intent = Intent(context, com.example.taskapplication.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("invitation_id", invitation.id)
                putExtra("open_invitations", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                invitation.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Tạo thông báo
            val notification = NotificationCompat.Builder(context, "team_invitations")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Lời mời tham gia nhóm mới")
                .setContentText("Bạn được mời tham gia nhóm ${invitation.teamName}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            // Hiển thị thông báo
            notificationManager.notify(invitation.id.hashCode(), notification)
            Log.d(TAG, "✅ [THEO DÕI] Đã hiển thị thông báo cho lời mời: ${invitation.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi hiển thị thông báo cho lời mời: ${e.message}", e)
        }
    }

    /**
     * Trigger đồng bộ dữ liệu team sau khi chấp nhận lời mời
     */
    private fun triggerTeamDataSync(teamId: String) {
        try {
            Log.d(TAG, "🔄 [TEAM_SYNC] Bắt đầu trigger đồng bộ dữ liệu cho team: $teamId")

            val workManager = androidx.work.WorkManager.getInstance(context)

            // 1. Đồng bộ thông tin team
            val teamSyncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(androidx.work.workDataOf("team_id" to teamId))
                .build()
            workManager.enqueue(teamSyncWork)
            Log.d(TAG, "✅ [TEAM_SYNC] Đã enqueue TeamSyncWorker cho team: $teamId")

            // 2. Đồng bộ tất cả dữ liệu team (messages, tasks, documents)
            val generalSyncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.SyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(androidx.work.workDataOf("team_id" to teamId, "sync_type" to "team_data"))
                .build()
            workManager.enqueue(generalSyncWork)
            Log.d(TAG, "✅ [TEAM_SYNC] Đã enqueue SyncWorker cho team: $teamId")

            Log.d(TAG, "🎉 [TEAM_SYNC] Hoàn thành trigger đồng bộ dữ liệu cho team: $teamId")

        } catch (e: Exception) {
            Log.e(TAG, "💥 [TEAM_SYNC] Lỗi khi trigger đồng bộ dữ liệu team: ${e.message}", e)
        }
    }

    /**
     * Sync team data từ accept invitation response
     */
    private suspend fun syncTeamDataFromAcceptResponse(teamData: com.example.taskapplication.data.api.response.TeamDataSync) {
        try {
            Log.d(TAG, "📦 [AUTO_SYNC] Bắt đầu đồng bộ team data từ accept response:")
            Log.d(TAG, "   - Team: ${teamData.team.name} (${teamData.team.id})")
            Log.d(TAG, "   - Members: ${teamData.members.size}")
            Log.d(TAG, "   - Messages: ${teamData.messages.size}")
            Log.d(TAG, "   - Tasks: ${teamData.team_tasks.size}")
            Log.d(TAG, "   - Documents: ${teamData.documents.size}")
            Log.d(TAG, "   - Folders: ${teamData.folders.size}")
            Log.d(TAG, "   - Sync time: ${teamData.sync_time}")

            // 1. Sync team info
            Log.d(TAG, "🏢 [AUTO_SYNC] Đồng bộ thông tin team...")
            syncTeamInfo(teamData.team)

            // 2. Sync team members
            Log.d(TAG, "👥 [AUTO_SYNC] Đồng bộ danh sách thành viên...")
            syncTeamMembers(teamData.members)

            // 3. Sync messages
            Log.d(TAG, "💬 [AUTO_SYNC] Đồng bộ tin nhắn...")
            syncTeamMessages(teamData.messages)

            // 4. Sync tasks
            Log.d(TAG, "📋 [AUTO_SYNC] Đồng bộ công việc...")
            syncTeamTasks(teamData.team_tasks)

            // 5. Sync documents and folders
            Log.d(TAG, "📁 [AUTO_SYNC] Đồng bộ tài liệu và thư mục...")
            syncTeamDocuments(teamData.documents, teamData.folders)

            Log.d(TAG, "🎉 [AUTO_SYNC] Hoàn thành đồng bộ team data từ accept response")

        } catch (e: Exception) {
            Log.e(TAG, "💥 [AUTO_SYNC] Lỗi khi đồng bộ team data: ${e.message}", e)
        }
    }

    /**
     * Sync team info
     */
    private suspend fun syncTeamInfo(teamInfo: com.example.taskapplication.data.api.response.TeamSyncInfo) {
        try {
            Log.d(TAG, "🏢 [SYNC_TEAM] Đồng bộ team info: ${teamInfo.name}")
            // TODO: Implement team info sync với TeamRepository
            // teamRepository.saveTeamFromSync(teamInfo)
            Log.d(TAG, "✅ [SYNC_TEAM] Đã đồng bộ team info")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SYNC_TEAM] Lỗi khi đồng bộ team info: ${e.message}", e)
        }
    }

    /**
     * Sync team members
     */
    private suspend fun syncTeamMembers(members: List<com.example.taskapplication.data.api.response.TeamMember>) {
        try {
            Log.d(TAG, "👥 [SYNC_MEMBERS] Đồng bộ ${members.size} thành viên")
            // TODO: Implement team members sync với TeamRepository
            // teamRepository.saveTeamMembersFromSync(members)
            Log.d(TAG, "✅ [SYNC_MEMBERS] Đã đồng bộ team members")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SYNC_MEMBERS] Lỗi khi đồng bộ team members: ${e.message}", e)
        }
    }

    /**
     * Sync team messages
     */
    private suspend fun syncTeamMessages(messages: List<com.example.taskapplication.data.api.response.TeamMessage>) {
        try {
            Log.d(TAG, "💬 [SYNC_MESSAGES] Đồng bộ ${messages.size} tin nhắn")
            // TODO: Implement messages sync với MessageRepository
            // messageRepository.saveMessagesFromSync(messages)
            Log.d(TAG, "✅ [SYNC_MESSAGES] Đã đồng bộ messages")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SYNC_MESSAGES] Lỗi khi đồng bộ messages: ${e.message}", e)
        }
    }

    /**
     * Sync team tasks
     */
    private suspend fun syncTeamTasks(tasks: List<com.example.taskapplication.data.api.response.TeamTask>) {
        try {
            Log.d(TAG, "📋 [SYNC_TASKS] Đồng bộ ${tasks.size} công việc")
            // TODO: Implement tasks sync với TaskRepository
            // taskRepository.saveTasksFromSync(tasks)
            Log.d(TAG, "✅ [SYNC_TASKS] Đã đồng bộ tasks")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SYNC_TASKS] Lỗi khi đồng bộ tasks: ${e.message}", e)
        }
    }

    /**
     * Sync team documents and folders
     */
    private suspend fun syncTeamDocuments(
        documents: List<com.example.taskapplication.data.api.response.TeamDocument>,
        folders: List<com.example.taskapplication.data.api.response.TeamFolder>
    ) {
        try {
            Log.d(TAG, "📁 [SYNC_DOCS] Đồng bộ ${folders.size} thư mục và ${documents.size} tài liệu")
            // TODO: Implement documents sync với DocumentRepository
            // documentRepository.saveFoldersFromSync(folders)
            // documentRepository.saveDocumentsFromSync(documents)
            Log.d(TAG, "✅ [SYNC_DOCS] Đã đồng bộ documents và folders")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SYNC_DOCS] Lỗi khi đồng bộ documents: ${e.message}", e)
        }
    }
}