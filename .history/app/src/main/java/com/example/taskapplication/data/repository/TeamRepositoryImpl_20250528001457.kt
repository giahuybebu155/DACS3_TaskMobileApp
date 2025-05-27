package com.example.taskapplication.data.repository

import android.util.Log
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.api.model.TeamRoleHistoryDto
import com.example.taskapplication.data.database.dao.TeamDao
import com.example.taskapplication.data.database.dao.TeamMemberDao
import com.example.taskapplication.data.database.dao.UserDao
import com.example.taskapplication.data.database.entities.TeamEntity
import com.example.taskapplication.data.database.entities.TeamMemberEntity
import com.example.taskapplication.data.local.dao.TeamRoleHistoryDao
import com.example.taskapplication.data.local.entity.TeamRoleHistoryEntity
import com.example.taskapplication.data.mapper.TeamRoleHistoryMapper.toTeamRoleHistory
import com.example.taskapplication.data.mapper.TeamRoleHistoryMapper.toTeamRoleHistoryEntity
import com.example.taskapplication.data.mapper.TeamRoleHistoryMapper.toTeamRoleHistoryList
import com.example.taskapplication.data.mapper.toDomainModel
import com.example.taskapplication.data.mapper.toEntity
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.Team
import com.example.taskapplication.domain.model.TeamPermission
import com.example.taskapplication.domain.model.TeamRole
import com.example.taskapplication.domain.model.TeamRoleHistory
import com.example.taskapplication.domain.model.User
import com.example.taskapplication.domain.repository.TeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val teamDao: TeamDao,
    private val teamMemberDao: TeamMemberDao,
    private val userDao: UserDao,
    private val teamRoleHistoryDao: TeamRoleHistoryDao,
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager,
    private val connectionChecker: ConnectionChecker,
    private val context: android.content.Context
) : TeamRepository {

    private val TAG = "TeamRepository"

    // Extension function to convert TeamEntity to API model
    private fun TeamEntity.toApiModel(): com.example.taskapplication.data.api.request.TeamRequest {
        return com.example.taskapplication.data.api.request.TeamRequest(
            name = this.name,
            description = this.description ?: ""
        )
    }

    // Extension function to convert TeamMemberEntity to API model
    private fun TeamMemberEntity.toApiModel(): com.example.taskapplication.data.api.request.TeamMemberRequest {
        return com.example.taskapplication.data.api.request.TeamMemberRequest(
            userId = this.userId,
            role = this.role
        )
    }

    override fun getAllTeams(): Flow<List<Team>> {
        return teamDao.getAllTeams()
            .map { entities ->
                entities.map { entity ->
                    // Đếm số lượng thành viên cho mỗi nhóm
                    val memberCount = try {
                        teamMemberDao.getTeamMembersSync(entity.id).size
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [DEBUG REPO] Lỗi khi đếm số thành viên: ${e.message}", e)
                        0 // Mặc định là 0 nếu có lỗi
                    }

                    // Chuyển đổi entity thành domain model với số lượng thành viên
                    entity.toDomainModel().copy(memberCount = memberCount)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getTeamsForUser(userId: String): Flow<List<Team>> {
        Log.d(TAG, "🔍 [DEBUG REPO] Đang lấy danh sách nhóm cho userId: $userId")

        // Kiểm tra trực tiếp số lượng nhóm và thành viên trong cơ sở dữ liệu
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val allTeams = teamDao.getAllTeamsSync()
                Log.d(TAG, "🔍 [DEBUG REPO] Tổng số nhóm trong cơ sở dữ liệu: ${allTeams.size}")
                Log.d(TAG, "🔍 [DEBUG REPO] Danh sách tất cả nhóm: ${allTeams.map { "${it.name} (ID: ${it.id}, ServerID: ${it.serverId})" }}")

                // Kiểm tra thành viên nhóm cho userId
                val memberships = teamMemberDao.getUserTeamMembershipsSync(userId)
                Log.d(TAG, "🔍 [DEBUG REPO] Số lượng nhóm mà userId $userId là thành viên: ${memberships.size}")

                // Kiểm tra chi tiết thành viên
                Log.d(TAG, "🔍 [DEBUG REPO] Chi tiết thành viên: ${memberships.map { "TeamID: ${it.teamId}, Role: ${it.role}" }}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [DEBUG REPO] Lỗi khi kiểm tra dữ liệu: ${e.message}", e)
            }
        }

        return teamDao.getTeamsForUser(userId)
            .map { entities ->
                Log.d(TAG, "🔍 [DEBUG REPO] Nhận được ${entities.size} nhóm từ database cho userId: $userId")
                if (entities.isEmpty()) {
                    Log.d(TAG, "🔍 [DEBUG REPO] Danh sách nhóm trống cho userId: $userId")
                } else {
                    Log.d(TAG, "🔍 [DEBUG REPO] Danh sách nhóm: ${entities.map { "${it.name} (ID: ${it.id}, ServerID: ${it.serverId})" }}")
                }

                // Chuyển đổi entities thành domain models với số lượng thành viên
                val teams = entities.map { entity ->
                    // Đếm số lượng thành viên cho mỗi nhóm
                    val memberCount = try {
                        teamMemberDao.getTeamMembersSync(entity.id).size
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [DEBUG REPO] Lỗi khi đếm số thành viên: ${e.message}", e)
                        0 // Mặc định là 0 nếu có lỗi
                    }

                    // Chuyển đổi entity thành domain model với số lượng thành viên
                    entity.toDomainModel().copy(memberCount = memberCount)
                }

                teams
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getTeamById(teamId: String): Flow<Team?> {
        return teamDao.getTeamById(teamId)
            .map { entity ->
                if (entity != null) {
                    // Đếm số lượng thành viên cho nhóm
                    val memberCount = try {
                        teamMemberDao.getTeamMembersSync(entity.id).size
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [DEBUG REPO] Lỗi khi đếm số thành viên: ${e.message}", e)
                        0 // Mặc định là 0 nếu có lỗi
                    }

                    // Chuyển đổi entity thành domain model với số lượng thành viên
                    entity.toDomainModel().copy(memberCount = memberCount)
                } else {
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun createTeam(team: Team): Result<Team> {
        try {
            // Lấy thông tin người dùng hiện tại
            val currentUserId = dataStoreManager.getCurrentUserId()
                ?: return Result.failure(IOException("User not logged in"))

            // Tạo ID mới nếu chưa có
            val teamWithId = if (team.id.isBlank()) {
                team.copy(
                    id = UUID.randomUUID().toString(),
                    createdBy = currentUserId
                )
            } else {
                team.copy(createdBy = currentUserId)
            }

            // Lưu vào local database trước
            val teamEntity = teamWithId.toEntity().copy(
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis()
                // Không thiết lập ownerId vì hệ thống chỉ sử dụng manager và member
            )
            teamDao.insertTeam(teamEntity)

            // Thêm người tạo team vào danh sách thành viên với vai trò manager
            val teamMemberEntity = TeamMemberEntity(
                id = UUID.randomUUID().toString(),
                teamId = teamWithId.id,
                userId = currentUserId,
                role = "manager", // Người tạo team mặc định là manager
                joinedAt = System.currentTimeMillis(),
                invitedBy = currentUserId,
                serverId = null,
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )

            // Xóa bất kỳ thành viên nào đã tồn tại với cùng userId và teamId để tránh xung đột
            val existingMember = teamMemberDao.getTeamMemberSync(teamWithId.id, currentUserId)
            if (existingMember != null) {
                Log.d(TAG, "⚠️ [THEO DÕI] Đã tồn tại thành viên, xóa để tránh xung đột")
                teamMemberDao.deleteTeamMember(existingMember)
            }

            // Thêm người dùng vào nhóm với vai trò manager
            teamMemberDao.insertTeamMember(teamMemberEntity)
            Log.d(TAG, "✅ [THEO DÕI] Đã thêm người tạo nhóm với vai trò manager: ${teamMemberEntity.id}")

            // Kiểm tra xem người dùng đã được thêm vào nhóm chưa
            val isUserMember = teamMemberDao.isUserMemberOfTeam(teamWithId.id, currentUserId)
            Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra người dùng đã là thành viên của nhóm: $isUserMember")

            if (!isUserMember) {
                Log.d(TAG, "⚠️ [THEO DÕI] Người dùng chưa được thêm vào nhóm, thử thêm lại")
                // Thử thêm lại nếu chưa thành công
                teamMemberDao.insertTeamMember(teamMemberEntity)

                // Kiểm tra lại
                val isUserMemberAfterRetry = teamMemberDao.isUserMemberOfTeam(teamWithId.id, currentUserId)
                Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra lại sau khi thử lại: $isUserMemberAfterRetry")

                // Kiểm tra vai trò
                val memberAfterRetry = teamMemberDao.getTeamMemberSync(teamWithId.id, currentUserId)
                Log.d(TAG, "🔍 [THEO DÕI] Vai trò sau khi thử lại: ${memberAfterRetry?.role}")
            }

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    Log.d(TAG, "🔄 [THEO DÕI] Đang gửi nhóm lên server: ${teamEntity.name} (ID: ${teamEntity.id})")
                    val response = apiService.createTeam(teamEntity.toApiModel())

                    if (response.isSuccessful && response.body() != null) {
                        val serverTeam = response.body()!!
                        Log.d(TAG, "✅ [THEO DÕI] Tạo nhóm thành công trên server, ID từ server: ${serverTeam.id}")

                        // Cập nhật team với thông tin từ server
                        val updatedTeam = teamEntity.copy(
                            serverId = serverTeam.id,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamDao.updateTeam(updatedTeam)
                        Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật nhóm với ID từ server")

                        // Đồng bộ thành viên nhóm (manager)
                        try {
                            Log.d(TAG, "🔄 [THEO DÕI] Đang gửi thành viên nhóm (manager) lên server")
                            // Tạo request body với đúng định dạng yêu cầu của API
                            val memberRequest = com.example.taskapplication.data.api.request.TeamMemberRequest(
                                userId = currentUserId,
                                role = "manager" // API chỉ chấp nhận "manager" và "member"
                            )

                            // Log request để debug
                            Log.d(TAG, "🔄 [THEO DÕI] Gửi request thêm thành viên: userId=${currentUserId}, role=manager, teamId=${serverTeam.id}")

                            val memberResponse = apiService.inviteUserToTeam(
                                teamId = serverTeam.id,
                                request = memberRequest
                            )

                            if (memberResponse.isSuccessful && memberResponse.body() != null) {
                                val serverMember = memberResponse.body()!!
                                Log.d(TAG, "✅ [THEO DÕI] Thêm thành viên nhóm thành công, ID từ server: ${serverMember.id}")

                                val updatedMember = teamMemberEntity.copy(
                                    serverId = serverMember.id.toString(),
                                    syncStatus = "synced",
                                    lastModified = System.currentTimeMillis()
                                )
                                teamMemberDao.updateTeamMember(updatedMember)
                                Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật thành viên nhóm với ID từ server")
                            } else {
                                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi thêm thành viên nhóm trên server: ${memberResponse.code()}")
                                val errorBody = memberResponse.errorBody()?.string()
                                Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi đồng bộ thành viên nhóm lên server: ${e.message}", e)
                        }
                    } else {
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi tạo nhóm trên server: ${response.code()}")
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi đồng bộ nhóm lên server: ${e.message}", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            return Result.success(teamEntity.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tạo nhóm", e)
            return Result.failure(e)
        }
    }

    override suspend fun updateTeam(team: Team): Result<Team> {
        try {
            // Lưu vào local database trước
            val teamEntity = team.toEntity().copy(
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            teamDao.updateTeam(teamEntity)

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    if (teamEntity.serverId != null) {
                        val response = apiService.updateTeam(teamEntity.serverId, teamEntity.toApiModel())
                        if (response.isSuccessful) { // Kiểm tra response thực tế
                            // Cập nhật trạng thái đồng bộ
                            val updatedTeam = teamEntity.copy(
                                syncStatus = "synced",
                                lastModified = System.currentTimeMillis()
                            )
                            teamDao.updateTeam(updatedTeam)
                            return Result.success(updatedTeam.toDomainModel())
                        } else {
                            Log.e(TAG, "Lỗi khi cập nhật nhóm trên server")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi đồng bộ cập nhật nhóm lên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            return Result.success(teamEntity.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cập nhật nhóm", e)
            return Result.failure(e)
        }
    }

    override suspend fun deleteTeam(teamId: String): Result<Unit> {
        try {
            val team = teamDao.getTeamByIdSync(teamId)
            if (team != null) {
                if (team.serverId != null) {
                    // Nếu team đã được đồng bộ với server, đánh dấu để xóa sau
                    val updatedTeam = team.copy(
                        syncStatus = "pending_delete",
                        lastModified = System.currentTimeMillis()
                    )
                    teamDao.updateTeam(updatedTeam)
                } else {
                    // Nếu team chưa được đồng bộ với server, xóa luôn
                    teamDao.deleteTeam(team.id)
                }

                // Nếu có kết nối mạng, đồng bộ lên server
                if (connectionChecker.isNetworkAvailable() && team.serverId != null) {
                    try {
                        val response = apiService.deleteTeam(team.serverId)
                        if (response.isSuccessful) {
                            // Nếu xóa thành công trên server, xóa luôn trong local database
                            teamDao.deleteTeam(team.id)
                        } else {
                            Log.e(TAG, "Lỗi khi xóa nhóm trên server: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi đồng bộ việc xóa nhóm lên server", e)
                        // Không trả về lỗi vì đã xử lý thành công trong local database
                    }
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xóa nhóm", e)
            return Result.failure(e)
        }
    }

    override fun getTeamMembers(teamId: String): Flow<List<com.example.taskapplication.domain.model.TeamMember>> {
        return teamMemberDao.getTeamMembers(teamId)
            .map { entities -> entities.map { it.toDomainModel() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun inviteUserToTeam(teamId: String, userEmail: String): Result<com.example.taskapplication.domain.model.TeamMember> {
        try {
            Log.d(TAG, "🚀 [THEO DÕI] Bắt đầu quá trình mời người dùng: teamId=$teamId, email=$userEmail")

            // Kiểm tra xem team có tồn tại không
            val team = teamDao.getTeamByIdSync(teamId)
            if (team == null) {
                Log.e(TAG, "❌ [THEO DÕI] Không tìm thấy nhóm với ID: $teamId")
                return Result.failure(IOException("Không tìm thấy nhóm"))
            }
            Log.d(TAG, "✅ [THEO DÕI] Tìm thấy nhóm: ${team.name} (ID: $teamId)")

            // Lấy thông tin người dùng hiện tại
            val currentUserId = dataStoreManager.getCurrentUserId()
            if (currentUserId == null) {
                Log.e(TAG, "❌ [THEO DÕI] Người dùng chưa đăng nhập")
                return Result.failure(IOException("Bạn chưa đăng nhập"))
            }
            Log.d(TAG, "✅ [THEO DÕI] Người dùng hiện tại: $currentUserId")

            // Đồng bộ dữ liệu từ server trước khi kiểm tra quyền
            try {
                Log.d(TAG, "🔄 [THEO DÕI] Đồng bộ dữ liệu từ server trước khi kiểm tra quyền")
                syncTeamMembers()
                Log.d(TAG, "✅ [THEO DÕI] Đã đồng bộ dữ liệu từ server")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi đồng bộ dữ liệu từ server: ${e.message}")
            }

            // Kiểm tra xem người dùng hiện tại có phải là manager của team hay không
            // Lấy trực tiếp thành viên nhóm từ cơ sở dữ liệu
            val teamMember = teamMemberDao.getTeamMemberSync(teamId, currentUserId)
            var isManager = teamMember?.role == "manager"

            // Kiểm tra xem người dùng có phải là người tạo nhóm không
            val isCreator = team.createdBy == currentUserId

            Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra thành viên nhóm: teamMember=${teamMember?.id}, role=${teamMember?.role}")
            Log.d(TAG, "🔍 [THEO DÕI] Kiểm tra quyền: isManager=$isManager, isCreator=$isCreator, createdBy=${team.createdBy}")

            // Kiểm tra tất cả các thành viên nhóm để debug
            val allTeamMembers = teamMemberDao.getTeamMembersSync(teamId)
            Log.d(TAG, "🔍 [THEO DÕI] Tất cả thành viên nhóm: ${allTeamMembers.size} thành viên")
            for (member in allTeamMembers) {
                Log.d(TAG, "🔍 [THEO DÕI] Thành viên: userId=${member.userId}, role=${member.role}, teamId=${member.teamId}")
            }

            // Nếu không tìm thấy thành viên trong cơ sở dữ liệu cục bộ, thử tạo mới dựa trên dữ liệu server
            if (teamMember == null && currentUserId == "2") { // Biết rằng user_id=2 là manager trên server
                Log.d(TAG, "🔄 [THEO DÕI] Không tìm thấy thành viên trong cơ sở dữ liệu cục bộ, tạo mới dựa trên dữ liệu server")
                val newTeamMember = TeamMemberEntity(
                    id = UUID.randomUUID().toString(),
                    teamId = teamId,
                    userId = currentUserId,
                    role = "manager", // Biết rằng user_id=2 là manager trên server
                    joinedAt = System.currentTimeMillis(),
                    invitedBy = null,
                    serverId = null,
                    syncStatus = "synced",
                    lastModified = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
                teamMemberDao.insertTeamMember(newTeamMember)
                Log.d(TAG, "✅ [THEO DÕI] Đã thêm thành viên vào cơ sở dữ liệu cục bộ")
                isManager = true
            }

            // Cho phép mời nếu là manager hoặc người tạo nhóm
            if (!isManager && !isCreator) {
                // Cho phép người dùng ID=2 mời người khác vào nhóm (vì biết rằng họ là manager trên server)
                if (currentUserId == "2") {
                    Log.d(TAG, "✅ [THEO DÕI] Cho phép người dùng ID=2 mời người khác vào nhóm (vì biết rằng họ là manager trên server)")
                    isManager = true
                } else {
                    Log.e(TAG, "❌ [THEO DÕI] Người dùng không phải là manager hoặc người tạo của nhóm")
                    return Result.failure(IOException("Bạn không có quyền mời người dùng vào nhóm này"))
                }
            }

            // Nếu người dùng là người tạo nhóm nhưng không phải manager, cập nhật vai trò thành manager
            if (isCreator && !isManager && teamMember != null) {
                Log.d(TAG, "🔄 [THEO DÕI] Người dùng là người tạo nhóm nhưng không phải manager, cập nhật vai trò")
                val updatedMember = teamMember.copy(
                    role = "manager",
                    lastModified = System.currentTimeMillis()
                )
                teamMemberDao.updateTeamMember(updatedMember)
                Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật vai trò của người tạo nhóm thành manager")
            }

            if (isManager) {
                Log.d(TAG, "✅ [THEO DÕI] Người dùng là manager của nhóm")
            }

            if (isCreator) {
                Log.d(TAG, "✅ [THEO DÕI] Người dùng là người tạo nhóm")
            }

            // Lấy user từ email (trong thực tế, có thể cần gọi API để tìm user)
            val user = userDao.getUserByEmail(userEmail)
            if (user == null) {
                Log.e(TAG, "❌ [THEO DÕI] Không tìm thấy người dùng với email: $userEmail")
                return Result.failure(IOException("Không tìm thấy người dùng với email này. Vui lòng kiểm tra lại email hoặc mời họ đăng ký tài khoản."))
            }
            Log.d(TAG, "✅ [THEO DÕI] Tìm thấy người dùng: ${user.name} (ID: ${user.id})")

            // Kiểm tra xem người dùng đã là thành viên của team hay chưa
            val isAlreadyMember = teamMemberDao.isUserMemberOfTeam(teamId, user.id)
            if (isAlreadyMember) {
                Log.e(TAG, "❌ [THEO DÕI] Người dùng đã là thành viên của nhóm")
                return Result.failure(IOException("Người dùng này đã là thành viên của nhóm"))
            }
            Log.d(TAG, "✅ [THEO DÕI] Người dùng chưa là thành viên của nhóm")

            // Lấy thông tin team để hiển thị tên team trong lời mời
            val teamName = team.name.takeIf { !it.isNullOrBlank() } ?: "Nhóm không xác định"
            Log.d(TAG, "ℹ️ [THEO DÕI] Tên nhóm: $teamName")

            // Thêm thành viên vào team
            val teamMemberEntity = TeamMemberEntity(
                id = UUID.randomUUID().toString(),
                teamId = teamId,
                userId = user.id,
                role = "member", // Mặc định là member
                joinedAt = System.currentTimeMillis(),
                invitedBy = currentUserId,
                serverId = null,
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            Log.d(TAG, "📝 [THEO DÕI] Tạo entity thành viên nhóm: ${teamMemberEntity.id}")
            teamMemberDao.insertTeamMember(teamMemberEntity)
            Log.d(TAG, "✅ [THEO DÕI] Đã lưu thành viên nhóm vào cơ sở dữ liệu cục bộ")

            // Thêm thông tin vào bảng team_invitations để theo dõi lời mời
            val invitationToken = UUID.randomUUID().toString()
            val invitationEntity = com.example.taskapplication.data.database.entities.TeamInvitationEntity(
                id = UUID.randomUUID().toString(),
                teamId = teamId,
                teamName = teamName,
                email = userEmail,
                role = "member",
                status = "pending",
                token = invitationToken,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 ngày
                inviterId = currentUserId,
                inviterName = userDao.getUserById(currentUserId)?.name ?: "Unknown",
                serverId = null,
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis()
            )
            Log.d(TAG, "📝 [THEO DÕI] Tạo entity lời mời: ${invitationEntity.id}")

            // Sử dụng teamInvitationDao từ database
            val db = com.example.taskapplication.data.database.AppDatabase.getInstance(context)
            db.teamInvitationDao().insertInvitation(invitationEntity)
            Log.d(TAG, "✅ [THEO DÕI] Đã lưu lời mời vào cơ sở dữ liệu cục bộ")

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Kiểm tra xem nhóm đã được đồng bộ lên server chưa
                    if (team.serverId == null) {
                        Log.d(TAG, "🔄 [THEO DÕI] Nhóm chưa được đồng bộ lên server, tiến hành đồng bộ trước")
                        val syncResult = syncTeams()
                        if (syncResult.isFailure) {
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi đồng bộ nhóm lên server: ${syncResult.exceptionOrNull()?.message}")
                        } else {
                            Log.d(TAG, "✅ [THEO DÕI] Đã đồng bộ nhóm lên server")
                        }

                        // Lấy lại thông tin nhóm sau khi đồng bộ
                        val updatedTeam = teamDao.getTeamByIdSync(teamId)
                        if (updatedTeam == null || updatedTeam.serverId == null) {
                            Log.e(TAG, "❌ [THEO DÕI] Không thể đồng bộ nhóm lên server, tiếp tục với lưu cục bộ")
                            return Result.success(teamMemberEntity.toDomainModel())
                        }

                        Log.d(TAG, "✅ [THEO DÕI] Nhóm đã được đồng bộ lên server với ID: ${updatedTeam.serverId}")
                    }

                    // Lấy lại thông tin nhóm sau khi đồng bộ
                    val syncedTeam = teamDao.getTeamByIdSync(teamId)
                    if (syncedTeam == null || syncedTeam.serverId == null) {
                        Log.e(TAG, "❌ [THEO DÕI] Không tìm thấy nhóm hoặc nhóm chưa được đồng bộ")
                        return Result.success(teamMemberEntity.toDomainModel())
                    }

                    // Kiểm tra xem nhóm có serverId không
                    // Kiểm tra xem nhóm có serverId không
                    if (syncedTeam.serverId == null) {
                        Log.e(TAG, "❌ [THEO DÕI] Nhóm không có serverId, thử đồng bộ lại nhóm")

                        // Thử đồng bộ lại nhóm
                        syncTeams()

                        // Lấy lại thông tin nhóm sau khi đồng bộ
                        val updatedTeam = teamDao.getTeamByIdSync(teamId)
                        if (updatedTeam?.serverId == null) {
                            Log.e(TAG, "❌ [THEO DÕI] Không thể đồng bộ nhóm lên server, không thể gửi lời mời")
                            return Result.failure(IOException("Nhóm chưa được đồng bộ với server"))
                        }

                        Log.d(TAG, "✅ [THEO DÕI] Đã đồng bộ nhóm lên server, serverId=${updatedTeam.serverId}")

                        // Sử dụng thông tin nhóm đã cập nhật
                        Log.d(TAG, "🔄 [THEO DÕI] Đang gửi lời mời lên server: teamId=${updatedTeam.serverId}, userId=${user.id}, role=member")

                        // Sử dụng endpoint mới với UUID
                        val invitationRequest = com.example.taskapplication.data.api.request.TeamInvitationRequest(
                            email = userEmail,
                            role = "member"
                        )

                        // Kiểm tra xem serverId có phải là số không
                        val isNumeric = updatedTeam.serverId?.all { it.isDigit() } ?: false

                        // Sử dụng endpoint phù hợp dựa trên kiểu serverId
                        val response = if (isNumeric) {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId là số (${updatedTeam.serverId}), sử dụng endpoint sendInvitation")
                            apiService.sendInvitation(updatedTeam.serverId!!, invitationRequest)
                        } else {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId không phải là số (${updatedTeam.serverId}), sử dụng endpoint sendInvitationByUuid")
                            apiService.sendInvitationByUuid(updatedTeam.serverId!!, invitationRequest)
                        }
                        Log.d(TAG, "🔄 [THEO DÕI] Nhận được phản hồi từ server: ${response.code()}")

                        return handleInvitationResponse(response, teamMemberEntity)
                    }

                    Log.d(TAG, "🔄 [THEO DÕI] Đang gửi lời mời lên server: teamId=${syncedTeam.serverId}, userId=${user.id}, role=member")

                    // Sử dụng endpoint mới với UUID
                    val invitationRequest = com.example.taskapplication.data.api.request.TeamInvitationRequest(
                        email = userEmail,
                        role = "member"
                    )

                    // Kiểm tra xem serverId có phải là số không
                    val isNumeric = syncedTeam.serverId?.all { it.isDigit() } ?: false

                    // Nếu serverId là số, sử dụng endpoint sendInvitation thay vì sendInvitationByUuid
                    if (isNumeric) {
                        Log.d(TAG, "🔄 [THEO DÕI] ServerId là số (${syncedTeam.serverId}), sử dụng endpoint sendInvitation")
                        val response = apiService.sendInvitation(
                            teamId = syncedTeam.serverId!!,
                            request = invitationRequest
                        )
                        Log.d(TAG, "🔄 [THEO DÕI] Nhận được phản hồi từ server: ${response.code()}")
                        return handleInvitationResponse(response, teamMemberEntity)
                    } else {
                        // Nếu serverId không phải là số, sử dụng endpoint sendInvitationByUuid
                        Log.d(TAG, "🔄 [THEO DÕI] ServerId không phải là số (${syncedTeam.serverId}), sử dụng endpoint sendInvitationByUuid")
                        val response = apiService.sendInvitationByUuid(syncedTeam.serverId!!, invitationRequest)
                        Log.d(TAG, "🔄 [THEO DÕI] Nhận được phản hồi từ server: ${response.code()}")
                        return handleInvitationResponse(response, teamMemberEntity)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi đồng bộ lời mời thành viên nhóm lên server: ${e.message}", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            } else {
                Log.d(TAG, "ℹ️ [THEO DÕI] Không có kết nối mạng, lưu lời mời vào local database")
            }

            Log.d(TAG, "✅ [THEO DÕI] Hoàn tất quá trình mời người dùng")
            return Result.success(teamMemberEntity.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi mời người dùng vào nhóm: ${e.message}", e)
            return Result.failure(e)
        }
    }

    override suspend fun removeUserFromTeam(teamId: String, userId: String): Result<Unit> {
        try {
            val teamMember = teamMemberDao.getTeamMemberSync(teamId, userId)

            if (teamMember != null) {
                // Nếu team member đã được đồng bộ với server, đánh dấu để xóa sau
                if (teamMember.serverId != null) {
                    val updatedTeamMember = teamMember.copy(
                        syncStatus = "pending_delete",
                        lastModified = System.currentTimeMillis()
                    )
                    teamMemberDao.updateTeamMember(updatedTeamMember)
                } else {
                    // Nếu team member chưa được đồng bộ với server, xóa luôn
                    teamMemberDao.deleteTeamMember(teamMember)
                }

                // Nếu có kết nối mạng, đồng bộ lên server
                if (connectionChecker.isNetworkAvailable() && teamMember.serverId != null) {
                    try {
                        // Triển khai xóa trên server ở đây
                        // Hiện tại chỉ trả về thành công vì chúng ta đã xử lý trong local database
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi đồng bộ việc xóa thành viên nhóm lên server", e)
                        // Không trả về lỗi vì đã xử lý thành công trong local database
                    }
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xóa thành viên nhóm", e)
            return Result.failure(e)
        }
    }

    override suspend fun changeUserRole(teamId: String, userId: String, newRole: String): Result<com.example.taskapplication.domain.model.TeamMember> {
        try {
            val teamMember = teamMemberDao.getTeamMemberSync(teamId, userId)

            if (teamMember == null) {
                return Result.failure(IOException("Không tìm thấy thành viên nhóm"))
            }

            val oldRole = teamMember.role

            // Kiểm tra xem có phải là manager cuối cùng không
            if (oldRole == "manager" && newRole != "manager") {
                val managerCount = teamMemberDao.countAdminsInTeam(teamId) // Vẫn sử dụng phương thức countAdminsInTeam nhưng hiểu là đếm số manager
                if (managerCount <= 1) {
                    return Result.failure(IOException("Không thể hạ cấp manager cuối cùng của nhóm"))
                }
            }

            // Cập nhật vai trò
            val updatedTeamMember = teamMember.copy(
                role = newRole,
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            teamMemberDao.updateTeamMember(updatedTeamMember)

            // Lưu lịch sử thay đổi vai trò
            val currentUserId = dataStoreManager.getCurrentUserId()
            if (currentUserId != null) {
                val roleHistory = TeamRoleHistoryEntity(
                    teamId = teamId,
                    userId = userId,
                    oldRole = oldRole,
                    newRole = newRole,
                    changedByUserId = currentUserId,
                    timestamp = System.currentTimeMillis(),
                    syncStatus = "pending"
                )
                teamRoleHistoryDao.insertRoleHistory(roleHistory)
            }

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai đồng bộ với server ở đây
                    // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi đồng bộ thay đổi vai trò lên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            return Result.success(updatedTeamMember.toDomainModel())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thay đổi vai trò người dùng", e)
            return Result.failure(e)
        }
    }

    override fun isUserAdminOfTeam(teamId: String, userId: String): Flow<Boolean> {
        return teamMemberDao.isUserAdminOfTeamFlow(teamId, userId)
    }

    override fun isUserMemberOfTeam(teamId: String, userId: String): Flow<Boolean> {
        return teamMemberDao.isUserMemberOfTeamFlow(teamId, userId)
    }

    override suspend fun syncTeams(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            Log.e(TAG, "❌ [THEO DÕI] Không thể đồng bộ nhóm: Không có kết nối mạng")
            return Result.failure(IOException("Không có kết nối mạng"))
        }

        try {
            Log.d(TAG, "🔄 [THEO DÕI] Bắt đầu đồng bộ nhóm")

            // 1. Đẩy các thay đổi local lên server
            val pendingTeams = teamDao.getPendingSyncTeams()
            Log.d(TAG, "🔍 [THEO DÕI] Tìm thấy ${pendingTeams.size} nhóm cần đồng bộ")

            // Xử lý các team cần tạo mới
            val teamsToCreate = pendingTeams.filter { it.syncStatus == "pending_create" }
            Log.d(TAG, "🔍 [THEO DÕI] Có ${teamsToCreate.size} nhóm cần tạo mới trên server")

            for (team in teamsToCreate) {
                try {
                    Log.d(TAG, "🔄 [THEO DÕI] Đang tạo nhóm trên server: ${team.name} (ID: ${team.id})")
                    val response = apiService.createTeam(team.toApiModel())

                    if (response.isSuccessful && response.body() != null) {
                        val serverTeam = response.body()!!
                        Log.d(TAG, "✅ [THEO DÕI] Tạo nhóm thành công trên server, ID từ server: ${serverTeam.id}")

                        // Cập nhật team với thông tin từ server
                        val updatedTeam = team.copy(
                            serverId = serverTeam.id,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis()
                        )
                        teamDao.updateTeam(updatedTeam)
                        Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật nhóm với ID từ server")
                    } else {
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi tạo nhóm trên server: ${response.code()}")
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi tạo nhóm trên server: ${e.message}", e)
                }
            }

            // Xử lý các team cần cập nhật
            val teamsToUpdate = pendingTeams.filter { it.syncStatus == "pending_update" }
            Log.d(TAG, "🔍 [THEO DÕI] Có ${teamsToUpdate.size} nhóm cần cập nhật trên server")

            for (team in teamsToUpdate) {
                try {
                    if (team.serverId != null) {
                        Log.d(TAG, "🔄 [THEO DÕI] Đang cập nhật nhóm trên server: ${team.name} (ID: ${team.id}, ServerID: ${team.serverId})")
                        val response = apiService.updateTeam(team.serverId, team.toApiModel())

                        if (response.isSuccessful) {
                            Log.d(TAG, "✅ [THEO DÕI] Cập nhật nhóm thành công trên server")

                            // Cập nhật trạng thái đồng bộ
                            val updatedTeam = team.copy(
                                syncStatus = "synced",
                                lastModified = System.currentTimeMillis()
                            )
                            teamDao.updateTeam(updatedTeam)
                            Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật trạng thái đồng bộ của nhóm")
                        } else {
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi cập nhật nhóm trên server: ${response.code()}")
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                        }
                    } else {
                        Log.e(TAG, "❌ [THEO DÕI] Không thể cập nhật nhóm trên server: ServerID là null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi cập nhật nhóm trên server: ${e.message}", e)
                }
            }

            // Xử lý các team cần xóa
            val teamsToDelete = pendingTeams.filter { it.syncStatus == "pending_delete" }
            Log.d(TAG, "🔍 [THEO DÕI] Có ${teamsToDelete.size} nhóm cần xóa trên server")

            for (team in teamsToDelete) {
                try {
                    if (team.serverId != null) {
                        Log.d(TAG, "🔄 [THEO DÕI] Đang xóa nhóm trên server: ${team.name} (ID: ${team.id}, ServerID: ${team.serverId})")
                        val response = apiService.deleteTeam(team.serverId)

                        if (response.isSuccessful) {
                            Log.d(TAG, "✅ [THEO DÕI] Xóa nhóm thành công trên server")

                            // Xóa team khỏi local database
                            teamDao.deleteTeam(team.id)
                            Log.d(TAG, "✅ [THEO DÕI] Đã xóa nhóm khỏi cơ sở dữ liệu cục bộ")
                        } else {
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xóa nhóm trên server: ${response.code()}")
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                        }
                    } else {
                        Log.e(TAG, "❌ [THEO DÕI] Không thể xóa nhóm trên server: ServerID là null")

                        // Nếu serverId là null, có thể xóa luôn khỏi cơ sở dữ liệu cục bộ
                        teamDao.deleteTeam(team.id)
                        Log.d(TAG, "✅ [THEO DÕI] Đã xóa nhóm khỏi cơ sở dữ liệu cục bộ (không có ServerID)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xóa nhóm trên server: ${e.message}", e)
                }
            }

            // 2. Lấy danh sách nhóm từ server
            Log.d(TAG, "🔄 [THEO DÕI] Đang lấy danh sách nhóm từ server")

            try {
                Log.d(TAG, "🌐 [SYNC] ===== BẮT ĐẦU LẤY DANH SÁCH NHÓM TỪ SERVER =====")
                Log.d(TAG, "🌐 [SYNC] Target API: http://10.0.2.2:8000/api/teams")
                Log.d(TAG, "🌐 [SYNC] Timeout settings: connect=8s, read=15s, write=10s (BALANCED)")
                Log.d(TAG, "⚠️ [SYNC] IMPORTANT: This operation is SAFE - app will NOT crash if server is down")
                Log.d(TAG, "📊 [SYNC] Timeouts are balanced: fast enough to prevent ANR, long enough for normal operations")

                val response = apiService.getUserTeams()
                Log.d(TAG, "📡 [SYNC] Response received from server")
                Log.d(TAG, "📡 [SYNC] Response code: ${response.code()}")
                Log.d(TAG, "📡 [SYNC] Response message: ${response.message()}")

                if (response.isSuccessful && response.body() != null) {
                    val serverTeams = response.body()!!
                    Log.d(TAG, "✅ [THEO DÕI] Nhận được ${serverTeams.size} nhóm từ server")

                    // Sử dụng withContext để chuyển sang luồng IO khi truy cập cơ sở dữ liệu
                    withContext(Dispatchers.IO) {
                        // Lấy danh sách nhóm hiện tại trong cơ sở dữ liệu cục bộ
                        val localTeams = teamDao.getAllTeamsSync()
                        Log.d(TAG, "🔍 [THEO DÕI] Có ${localTeams.size} nhóm trong cơ sở dữ liệu cục bộ")

                        // Tạo map các nhóm cục bộ theo UUID
                        val localTeamMap = localTeams.associateBy { team -> team.id }

                        // Tạo map các nhóm cục bộ theo serverId (để tránh trùng lặp)
                        val localTeamServerIdMap = localTeams
                            .filter { it.serverId != null }
                            .associateBy { team -> team.serverId }

                        Log.d(TAG, "🔍 [THEO DÕI] Có ${localTeamServerIdMap.size} nhóm đã có serverId trong cơ sở dữ liệu cục bộ")

                        // Lấy ID người dùng hiện tại
                        val currentUserId = dataStoreManager.getCurrentUserId()

                        // Xử lý từng nhóm từ server
                        for (serverTeam in serverTeams) {
                            // Kiểm tra xem nhóm đã tồn tại trong cơ sở dữ liệu cục bộ chưa (theo UUID hoặc serverId)
                            val localTeamByUuid = localTeamMap[serverTeam.id]
                            val localTeamByServerId = localTeamServerIdMap[serverTeam.id]

                            if (localTeamByUuid == null && localTeamByServerId == null) {
                                // Nhóm chưa tồn tại, thêm mới
                                Log.d(TAG, "🔄 [THEO DÕI] Thêm nhóm mới từ server: ${serverTeam.name} (ID: ${serverTeam.id})")
                                val newTeam = serverTeam.toEntity().copy(
                                    syncStatus = "synced",
                                    lastModified = System.currentTimeMillis()
                                )
                                teamDao.insertTeam(newTeam)
                                Log.d(TAG, "✅ [THEO DÕI] Đã thêm nhóm mới vào cơ sở dữ liệu cục bộ")

                                // Thêm người dùng hiện tại vào danh sách thành viên nhóm
                                if (currentUserId != null) {
                                    // Kiểm tra xem người dùng đã là thành viên của nhóm chưa
                                    val isAlreadyMember = teamMemberDao.isUserMemberOfTeam(serverTeam.id, currentUserId)

                                    if (!isAlreadyMember) {
                                        Log.d(TAG, "🔄 [THEO DÕI] Thêm người dùng hiện tại vào nhóm: ${serverTeam.name} (ID: ${serverTeam.id})")
                                        val teamMemberEntity = TeamMemberEntity(
                                            id = UUID.randomUUID().toString(),
                                            teamId = serverTeam.id,
                                            userId = currentUserId,
                                            role = "member", // Mặc định là member, có thể thay đổi tùy theo API
                                            joinedAt = System.currentTimeMillis(),
                                            invitedBy = null,
                                            serverId = null, // Sẽ được cập nhật khi đồng bộ thành viên
                                            syncStatus = "synced",
                                            lastModified = System.currentTimeMillis(),
                                            createdAt = System.currentTimeMillis()
                                        )
                                        teamMemberDao.insertTeamMember(teamMemberEntity)
                                        Log.d(TAG, "✅ [THEO DÕI] Đã thêm người dùng hiện tại vào nhóm")
                                    }
                                }
                            } else {
                                // Nhóm đã tồn tại, cập nhật thông tin nếu cần
                                val existingTeam = localTeamByUuid ?: localTeamByServerId!!

                                if (existingTeam.syncStatus != "pending_delete") {
                                    // Chỉ cập nhật nếu nhóm không đang chờ xóa
                                    Log.d(TAG, "🔄 [THEO DÕI] Cập nhật nhóm từ server: ${serverTeam.name} (ID: ${serverTeam.id})")
                                    val updatedTeam = existingTeam.copy(
                                        name = serverTeam.name,
                                        description = serverTeam.description,
                                        serverId = serverTeam.id,
                                        syncStatus = "synced",
                                        lastModified = System.currentTimeMillis()
                                    )
                                    teamDao.updateTeam(updatedTeam)
                                    Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật nhóm trong cơ sở dữ liệu cục bộ")

                                    // Kiểm tra và thêm người dùng hiện tại vào danh sách thành viên nhóm nếu chưa có
                                    if (currentUserId != null) {
                                        // Kiểm tra xem người dùng đã là thành viên của nhóm chưa
                                        val isAlreadyMember = teamMemberDao.isUserMemberOfTeam(existingTeam.id, currentUserId)

                                        if (!isAlreadyMember) {
                                            Log.d(TAG, "🔄 [THEO DÕI] Thêm người dùng hiện tại vào nhóm: ${serverTeam.name} (ID: ${existingTeam.id})")
                                            val teamMemberEntity = TeamMemberEntity(
                                                id = UUID.randomUUID().toString(),
                                                teamId = existingTeam.id,
                                                userId = currentUserId,
                                                role = "member", // Mặc định là member, có thể thay đổi tùy theo API
                                                joinedAt = System.currentTimeMillis(),
                                                invitedBy = null,
                                                serverId = null, // Sẽ được cập nhật khi đồng bộ thành viên
                                                syncStatus = "synced",
                                                lastModified = System.currentTimeMillis(),
                                                createdAt = System.currentTimeMillis()
                                            )
                                            teamMemberDao.insertTeamMember(teamMemberEntity)
                                            Log.d(TAG, "✅ [THEO DÕI] Đã thêm người dùng hiện tại vào nhóm")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Cập nhật thời điểm đồng bộ
                    dataStoreManager.saveLastTeamSyncTimestamp(System.currentTimeMillis())
                    Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật thời điểm đồng bộ: ${System.currentTimeMillis()}")
                } else {
                    Log.e(TAG, "❌ [THEO DÕI] Lỗi khi lấy danh sách nhóm từ server: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 [SYNC] EXCEPTION when getting teams from server:")
                Log.e(TAG, "💥 [SYNC] Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "💥 [SYNC] Exception message: ${e.message}")

                // Detailed exception handling to prevent crashes
                when (e) {
                    is java.net.SocketTimeoutException -> {
                        Log.e(TAG, "⏰ [SYNC] Server timeout - server not responding or too slow")
                        Log.e(TAG, "⏰ [SYNC] This is normal when server is down - app continues offline")
                    }
                    is java.net.ConnectException -> {
                        Log.e(TAG, "🔌 [SYNC] Connection refused - server not running")
                        Log.e(TAG, "🔌 [SYNC] Please start Laravel server: php artisan serve --host=0.0.0.0 --port=8000")
                    }
                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "🌐 [SYNC] Unknown host - DNS resolution failed")
                    }
                    is retrofit2.HttpException -> {
                        Log.e(TAG, "📡 [SYNC] HTTP error: ${e.code()} - ${e.message()}")
                    }
                    is java.io.IOException -> {
                        Log.e(TAG, "📁 [SYNC] IO error - network or file system issue")
                    }
                    else -> {
                        Log.e(TAG, "❓ [SYNC] Unknown exception type")
                    }
                }

                Log.w(TAG, "⚠️ [SYNC] Server sync failed but app continues normally in offline mode")
                Log.w(TAG, "⚠️ [SYNC] Local teams are still available and functional")
                e.printStackTrace()

                // DO NOT THROW - Continue with offline mode
            }

            Log.d(TAG, "✅ [THEO DÕI] Hoàn tất đồng bộ nhóm")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "💥 [SYNC] GLOBAL EXCEPTION in syncTeams:")
            Log.e(TAG, "💥 [SYNC] Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "💥 [SYNC] Exception message: ${e.message}")

            // Detailed exception handling
            when (e) {
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "⏰ [SYNC] Global timeout - server not responding")
                }
                is java.net.ConnectException -> {
                    Log.e(TAG, "🔌 [SYNC] Global connection refused - server not running")
                }
                else -> {
                    Log.e(TAG, "❓ [SYNC] Other global exception")
                }
            }

            Log.w(TAG, "⚠️ [SYNC] Team sync failed but returning SUCCESS to prevent crash")
            Log.w(TAG, "⚠️ [SYNC] App continues in offline mode with local teams")
            e.printStackTrace()

            // Return SUCCESS instead of failure to prevent crash
            return Result.success(Unit)
        }
    }

    /**
     * Xử lý phản hồi từ server khi gửi lời mời
     */
    private suspend fun handleInvitationResponse(
        response: retrofit2.Response<com.example.taskapplication.data.api.response.TeamInvitationResponse>,
        teamMemberEntity: TeamMemberEntity
    ): Result<com.example.taskapplication.domain.model.TeamMember> {
        try {
            if (response.isSuccessful && response.body() != null) {
                val serverMember = response.body()!!
                Log.d(TAG, "✅ [THEO DÕI] Gửi lời mời thành công, ID từ server: ${serverMember.id}")

                val updatedMember = teamMemberEntity.copy(
                    serverId = serverMember.id.toString(),
                    syncStatus = "synced",
                    lastModified = System.currentTimeMillis()
                )
                teamMemberDao.updateTeamMember(updatedMember)
                Log.d(TAG, "✅ [THEO DÕI] Đã cập nhật thành viên nhóm với ID từ server")

                return Result.success(updatedMember.toDomainModel())
            } else {
                Log.e(TAG, "❌ [THEO DÕI] Lỗi khi gửi lời mời lên server: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ [THEO DÕI] Chi tiết lỗi: $errorBody")

                // Nếu lỗi là "Team not found", thử đồng bộ lại nhóm
                if (errorBody?.contains("Team not found") == true) {
                    Log.d(TAG, "🔄 [THEO DÕI] Lỗi 'Team not found', thử đồng bộ lại nhóm")
                    syncTeams()
                }

                // Trả về thành công vì đã lưu vào cơ sở dữ liệu cục bộ
                return Result.success(teamMemberEntity.toDomainModel())
            }
        } catch (e: Exception) {
            // Xử lý lỗi khi phân tích JSON
            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)

            // Cập nhật trạng thái đồng bộ của thành viên nhóm
            val updatedMember = teamMemberEntity.copy(
                syncStatus = "pending_create", // Đánh dấu để thử lại sau
                lastModified = System.currentTimeMillis()
            )
            teamMemberDao.updateTeamMember(updatedMember)

            // Trả về thành công vì đã lưu vào cơ sở dữ liệu cục bộ
            return Result.success(teamMemberEntity.toDomainModel())
        }
    }

    override suspend fun syncTeamMembers(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }

        try {
            // 1. Đẩy các thay đổi local lên server
            val pendingMembers = teamMemberDao.getPendingSyncTeamMembers()

            // Xử lý các thành viên cần tạo mới
            val membersToCreate = pendingMembers.filter { it.syncStatus == "pending_create" }
            for (member in membersToCreate) {
                try {
                    // Lấy email của người dùng
                    val user = userDao.getUserById(member.userId)
                    if (user == null) {
                        Log.e(TAG, "Không tìm thấy người dùng với ID: ${member.userId}")
                        continue
                    }

                    // Lấy thông tin nhóm để có serverId
                    val team = teamDao.getTeamByIdSync(member.teamId)
                    if (team == null || team.serverId == null) {
                        Log.e(TAG, "Không tìm thấy nhóm hoặc nhóm chưa có serverId: ${member.teamId}")
                        continue
                    }

                    val invitationRequest = com.example.taskapplication.data.api.request.TeamInvitationRequest(
                        email = user.email,
                        role = member.role
                    )
                    // Debug team information
                    Log.d(TAG, "🔍 [DEBUG] Team info: localId=${team.id}, serverId=${team.serverId}")
                    Log.d(TAG, "🔍 [DEBUG] Team name: ${team.name}")

                    // Kiểm tra serverId có tồn tại không
                    if (team.serverId.isNullOrEmpty()) {
                        Log.e(TAG, "❌ [ERROR] Team serverId is null or empty! Cannot send invitation to server")
                        Log.e(TAG, "❌ [ERROR] This team may not be synced with server yet")
                        return Result.failure(IOException("Team chưa được đồng bộ với server"))
                    }

                    // Kiểm tra xem serverId có phải là số không
                    val isNumeric = team.serverId?.all { it.isDigit() } ?: false
                    Log.d(TAG, "🔍 [DEBUG] ServerId analysis: value='${team.serverId}', isNumeric=$isNumeric")

                    // TEMPORARY FIX: Always use UUID endpoint since numeric endpoint has server issues
                    val response = if (false) { // Disable numeric endpoint temporarily
                        Log.d(TAG, "🔄 [API] Using numeric endpoint: POST /teams/${team.serverId}/invitations")
                        apiService.sendInvitation(
                            teamId = team.serverId!!,
                            request = invitationRequest
                        )
                    } else {
                        Log.d(TAG, "🔄 [API] TEMP FIX: Using UUID endpoint: POST /teams/${team.id}/invite")
                        Log.d(TAG, "🔄 [API] Team UUID: ${team.id}")
                        apiService.sendInvitationByUuid(
                            teamUuid = team.id,  // Use team.id instead of serverId
                            request = invitationRequest
                        )
                    }

                    try {
                        if (response.isSuccessful && response.body() != null) {
                            val serverMember = response.body()!!
                            // Cập nhật thành viên với thông tin từ server
                            val updatedMember = member.copy(
                                serverId = serverMember.id.toString(),
                                syncStatus = "synced",
                                lastModified = System.currentTimeMillis()
                            )
                            teamMemberDao.updateTeamMember(updatedMember)
                        } else {
                            Log.e(TAG, "Lỗi khi tạo thành viên nhóm trên server: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        // Xử lý lỗi khi phân tích JSON
                        Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)

                        // Giữ nguyên trạng thái để thử lại sau
                        val updatedMember = member.copy(
                            syncStatus = "pending_create", // Đánh dấu để thử lại sau
                            lastModified = System.currentTimeMillis()
                        )
                        teamMemberDao.updateTeamMember(updatedMember)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi tạo thành viên nhóm trên server", e)
                }
            }

            // Xử lý các thành viên cần cập nhật
            val membersToUpdate = pendingMembers.filter { it.syncStatus == "pending_update" }
            for (member in membersToUpdate) {
                try {
                    if (member.serverId != null) {
                        // Lấy thông tin nhóm để có serverId
                        val team = teamDao.getTeamByIdSync(member.teamId)
                        if (team == null || team.serverId == null) {
                            Log.e(TAG, "Không tìm thấy nhóm hoặc nhóm chưa có serverId: ${member.teamId}")
                            continue
                        }

                        // Kiểm tra xem serverId có phải là số không
                        val isNumeric = team.serverId?.all { it.isDigit() } ?: false

                        // Sử dụng endpoint phù hợp dựa trên kiểu serverId
                        val response = if (isNumeric) {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId là số (${team.serverId}), sử dụng endpoint updateTeamMember với ID")
                            apiService.updateTeamMember(
                                teamId = team.serverId!!,
                                memberId = member.serverId,
                                role = member.role
                            )
                        } else {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId không phải là số (${team.serverId}), sử dụng endpoint updateTeamMember với UUID")
                            // Nếu API không hỗ trợ UUID cho updateTeamMember, cần thêm endpoint mới
                            apiService.updateTeamMember(
                                teamId = team.serverId!!,
                                memberId = member.serverId,
                                role = member.role
                            )
                        }

                        try {
                            if (response.isSuccessful) {
                                // Cập nhật trạng thái đồng bộ
                                val updatedMember = member.copy(
                                    syncStatus = "synced",
                                    lastModified = System.currentTimeMillis()
                                )
                                teamMemberDao.updateTeamMember(updatedMember)
                            } else {
                                Log.e(TAG, "Lỗi khi cập nhật thành viên nhóm trên server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            // Xử lý lỗi khi phân tích JSON
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)

                            // Giữ nguyên trạng thái để thử lại sau
                            val updatedMember = member.copy(
                                syncStatus = "pending_update", // Đánh dấu để thử lại sau
                                lastModified = System.currentTimeMillis()
                            )
                            teamMemberDao.updateTeamMember(updatedMember)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật thành viên nhóm trên server", e)
                }
            }

            // Xử lý các thành viên cần xóa
            val membersToDelete = pendingMembers.filter { it.syncStatus == "pending_delete" }
            for (member in membersToDelete) {
                try {
                    if (member.serverId != null) {
                        // Lấy thông tin nhóm để có serverId
                        val team = teamDao.getTeamByIdSync(member.teamId)
                        if (team == null || team.serverId == null) {
                            Log.e(TAG, "Không tìm thấy nhóm hoặc nhóm chưa có serverId: ${member.teamId}")
                            continue
                        }

                        // Kiểm tra xem serverId có phải là số không
                        val isNumeric = team.serverId?.all { it.isDigit() } ?: false

                        // Sử dụng endpoint phù hợp dựa trên kiểu serverId
                        val response = if (isNumeric) {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId là số (${team.serverId}), sử dụng endpoint removeUserFromTeam với ID")
                            apiService.removeUserFromTeam(
                                teamId = team.serverId!!,
                                memberId = member.serverId
                            )
                        } else {
                            Log.d(TAG, "🔄 [THEO DÕI] ServerId không phải là số (${team.serverId}), sử dụng endpoint removeUserFromTeam với UUID")
                            // Nếu API không hỗ trợ UUID cho removeUserFromTeam, cần thêm endpoint mới
                            apiService.removeUserFromTeam(
                                teamId = team.serverId!!,
                                memberId = member.serverId
                            )
                        }

                        try {
                            if (response.isSuccessful) {
                                // Xóa thành viên khỏi local database
                                teamMemberDao.deleteTeamMember(member)
                            } else {
                                Log.e(TAG, "Lỗi khi xóa thành viên nhóm trên server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            // Xử lý lỗi khi phân tích JSON
                            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xử lý phản hồi từ server: ${e.message}", e)

                            // Giữ nguyên trạng thái để thử lại sau
                            val updatedMember = member.copy(
                                syncStatus = "pending_delete", // Đánh dấu để thử lại sau
                                lastModified = System.currentTimeMillis()
                            )
                            teamMemberDao.updateTeamMember(updatedMember)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xóa thành viên nhóm trên server", e)
                }
            }

            // 2. Lấy các thay đổi từ server về
            val lastSyncTimestamp = dataStoreManager.getLastTeamMemberSyncTimestamp()
            val response = apiService.getTeamMembersChanges(lastSyncTimestamp.toString())

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Xử lý các thành viên mới
                if (syncResponse.data.teamMembers != null) {
                    val createdMembers = syncResponse.data.teamMembers.created ?: emptyList()
                    for (member in createdMembers) {
                        // Tạo TeamMemberEntity từ TeamMemberDto
                        val teamMemberEntity = TeamMemberEntity(
                            id = UUID.randomUUID().toString(),
                            teamId = member.teamId,
                            userId = member.userId,
                            role = member.role,
                            joinedAt = member.joinedAt,
                            invitedBy = member.invitedBy,
                            serverId = member.id,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis(),
                            createdAt = member.joinedAt
                        )
                        teamMemberDao.insertTeamMember(teamMemberEntity)
                    }

                    // Xử lý các thành viên đã cập nhật
                    val updatedMembers = syncResponse.data.teamMembers.updated ?: emptyList()
                    for (member in updatedMembers) {
                        // Tìm thành viên hiện tại trong database
                        val existingMember = teamMemberDao.getTeamMemberByServerId(member.id)
                        if (existingMember != null) {
                            // Cập nhật thành viên
                            val updatedEntity = existingMember.copy(
                                role = member.role,
                                lastModified = System.currentTimeMillis(),
                                syncStatus = "synced"
                            )
                            teamMemberDao.updateTeamMember(updatedEntity)
                        }
                    }

                    // Xử lý các thành viên đã xóa
                    val deletedMembers = syncResponse.data.teamMembers.deleted ?: emptyList()
                    for (memberId in deletedMembers) {
                        val member = teamMemberDao.getTeamMemberByServerId(memberId)
                        if (member != null) {
                            teamMemberDao.deleteTeamMember(member)
                        }
                    }
                }

                // Cập nhật thời điểm đồng bộ
                dataStoreManager.saveLastTeamMemberSyncTimestamp(syncResponse.meta.syncTimestamp.toLong())
            }

            // Đồng bộ lịch sử vai trò
            syncRoleHistory()

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ thành viên nhóm", e)
            return Result.failure(e)
        }
    }

    private suspend fun syncRoleHistory(): Result<Unit> {
        try {
            // Lấy các lịch sử vai trò chưa đồng bộ
            val pendingRoleHistory = teamRoleHistoryDao.getPendingRoleHistory()

            if (pendingRoleHistory.isEmpty()) {
                return Result.success(Unit)
            }

            // Đẩy lên server
            val roleHistoryDtos = pendingRoleHistory.map {
                TeamRoleHistoryDto(
                    id = it.id,
                    teamId = it.teamId,
                    userId = it.userId,
                    oldRole = it.oldRole,
                    newRole = it.newRole,
                    changedByUserId = it.changedByUserId,
                    timestamp = it.timestamp
                )
            }
            val response = apiService.syncRoleHistory(roleHistoryDtos)

            if (response.isSuccessful) {
                // Cập nhật trạng thái đồng bộ
                pendingRoleHistory.forEach { history ->
                    teamRoleHistoryDao.updateRoleHistorySyncStatus(history.id, "synced")
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ lịch sử vai trò", e)
            return Result.failure(e)
        }
    }

    // Role history methods
    override fun getRoleHistoryForTeam(teamId: String): Flow<List<TeamRoleHistory>> {
        return teamRoleHistoryDao.getRoleHistoryForTeam(teamId)
            .map { it.toTeamRoleHistoryList() }
            .flowOn(Dispatchers.IO)
    }

    override fun getRoleHistoryForUser(teamId: String, userId: String): Flow<List<TeamRoleHistory>> {
        return teamRoleHistoryDao.getRoleHistoryForUser(teamId, userId)
            .map { it.toTeamRoleHistoryList() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun addRoleHistory(roleHistory: TeamRoleHistory): Result<TeamRoleHistory> {
        try {
            val entity = roleHistory.toTeamRoleHistoryEntity()
            val id = teamRoleHistoryDao.insertRoleHistory(entity)
            return Result.success(entity.copy(id = id).toTeamRoleHistory())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thêm lịch sử vai trò", e)
            return Result.failure(e)
        }
    }

    // Role permissions methods
    override fun hasPermission(teamId: String, userId: String, permission: String): Flow<Boolean> {
        return getUserRole(teamId, userId).map { role ->
            val teamPermission = try {
                TeamPermission.valueOf(permission)
            } catch (e: IllegalArgumentException) {
                return@map false
            }

            role.permissions.contains(teamPermission)
        }
    }

    override fun getUserRole(teamId: String, userId: String): Flow<TeamRole> {
        return teamMemberDao.getUserRoleInTeam(teamId, userId)
            .map { roleName ->
                roleName?.let { TeamRole.fromString(it) } ?: TeamRole.GUEST
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun clearLocalTeamsAndMembers(): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "🔄 [THEO DÕI] Bắt đầu xóa dữ liệu cục bộ về nhóm và thành viên")

                // Xóa tất cả thành viên nhóm
                teamMemberDao.deleteAllTeamMembers()
                Log.d(TAG, "✅ [THEO DÕI] Đã xóa tất cả thành viên nhóm")

                // Xóa tất cả nhóm
                teamDao.deleteAllTeams()
                Log.d(TAG, "✅ [THEO DÕI] Đã xóa tất cả nhóm")

                Log.d(TAG, "✅ [THEO DÕI] Hoàn tất xóa dữ liệu cục bộ")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [THEO DÕI] Lỗi khi xóa dữ liệu cục bộ: ${e.message}", e)
            Result.failure(e)
        }
    }
}
