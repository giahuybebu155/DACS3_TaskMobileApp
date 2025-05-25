package com.example.taskapplication.data.repository

import android.util.Log
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.database.dao.TeamInvitationDao
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
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamInvitationRepositoryImpl @Inject constructor(
    private val teamInvitationDao: TeamInvitationDao,
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager,
    private val connectionChecker: ConnectionChecker
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
                    Log.d(TAG, "Đang gửi lời mời lên server: teamId=$teamId, email=$email, role=$role")
                    val request = invitation.toEntity().toApiRequest()
                    Log.d(TAG, "Request data: $request")

                    val response = apiService.sendInvitation(teamId, request)
                    Log.d(TAG, "Response code: ${response.code()}, message: ${response.message()}")

                    if (response.isSuccessful && response.body() != null) {
                        val serverInvitation = response.body()!!
                        Log.d(TAG, "Gửi lời mời thành công: $serverInvitation")

                        val updatedInvitation = invitation.copy(
                            serverId = serverInvitation.id.toString(),
                            token = serverInvitation.token,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(updatedInvitation.toEntity())
                        return Result.success(updatedInvitation)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Lỗi khi gửi lời mời lên server: ${response.code()} - ${response.message()}, Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing invitation to server", e)
                    e.printStackTrace()
                    // Continue with local invitation
                }
            } else {
                Log.d(TAG, "Không có kết nối mạng, lưu lời mời vào local database")
            }

            return Result.success(invitation)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending invitation", e)
            return Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(token: String): Result<Unit> {
        try {
            val invitation = teamInvitationDao.getInvitationByToken(token)
                ?: return Result.failure(IOException("Invitation not found"))

            if (invitation.status != "pending") {
                return Result.failure(IOException("Invitation is not pending"))
            }

            // Update local status
            val updatedInvitation = invitation.copy(
                status = "accepted",
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            teamInvitationDao.updateInvitation(updatedInvitation)

            // If online, sync with server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    val response = apiService.acceptInvitation(mapOf("token" to token))

                    if (response.isSuccessful) {
                        val finalInvitation = updatedInvitation.copy(
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamInvitationDao.updateInvitation(finalInvitation)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accepting invitation on server", e)
                    // Continue with local update
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting invitation", e)
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
                            Log.d(TAG, "Gửi lời mời mới lên server: teamId=${invitation.teamId}, email=${invitation.email}")

                            val response = apiService.sendInvitation(invitation.teamId, request)
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
                        val response = apiService.getTeamInvitations(teamId)
                        Log.d(TAG, "🔍 [THEO DÕI] Kết quả lấy lời mời: ${response.code()}")

                        if (response.isSuccessful && response.body() != null) {
                            val serverInvitations = response.body()!!
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
                    val response = apiService.getUserInvitations()
                    Log.d(TAG, "🔍 [THEO DÕI] Kết quả lấy lời mời người dùng: ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        val userInvitations = response.body()!!
                        Log.d(TAG, "✅ [THEO DÕI] Nhận được ${userInvitations.size} lời mời gửi đến người dùng")

                        for (serverInvitation in userInvitations) {
                            Log.d(TAG, "🔍 [THEO DÕI] Xử lý lời mời người dùng từ server: ID=${serverInvitation.id}, status=${serverInvitation.status}")
                            val existingInvitation = teamInvitationDao.getInvitationByServerId(serverInvitation.id.toString())
                            Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra lời mời người dùng ${serverInvitation.id}: ${if (existingInvitation != null) "đã tồn tại" else "mới"}")

                            if (existingInvitation == null) {
                                // New invitation from server
                                val newInvitation = serverInvitation.toEntity()
                                Log.d(TAG, "📝 [THEO DÕI] Tạo entity lời mời người dùng mới: ${newInvitation.id}, email=${newInvitation.email}")
                                teamInvitationDao.insertInvitation(newInvitation)
                                Log.d(TAG, "✅ [THEO DÕI] Đã thêm lời mời người dùng mới vào cơ sở dữ liệu cục bộ: ${serverInvitation.id}")
                            } else if (existingInvitation.syncStatus == "synced") {
                                // Update existing invitation if it's synced (not pending changes)
                                val updatedInvitation = serverInvitation.toEntity(existingInvitation)
                                Log.d(TAG, "📝 [THEO DÕI] Cập nhật entity lời mời người dùng: ${updatedInvitation.id}, status=${updatedInvitation.status}")
                                teamInvitationDao.updateInvitation(updatedInvitation)
                                Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật lời mời người dùng hiện có: ${serverInvitation.id}")
                            } else {
                                Log.d(TAG, "ℹ️ [THEO DÕI] Bỏ qua cập nhật lời mời người dùng ${existingInvitation.id} vì đang có thay đổi cục bộ (syncStatus=${existingInvitation.syncStatus})")
                            }
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy lời mời người dùng từ server: ${response.code()}, Error: $errorBody")
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
}
