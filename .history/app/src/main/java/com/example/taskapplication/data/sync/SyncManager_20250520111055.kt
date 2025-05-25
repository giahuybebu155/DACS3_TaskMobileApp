package com.example.taskapplication.data.sync

import android.util.Log
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.database.dao.TeamDao
import com.example.taskapplication.data.database.dao.TeamInvitationDao
import com.example.taskapplication.data.database.dao.TeamMemberDao
import com.example.taskapplication.data.database.dao.TeamMessageDao
import com.example.taskapplication.data.database.dao.TeamTaskDao
import com.example.taskapplication.data.database.entities.TeamEntity
import com.example.taskapplication.data.database.entities.TeamInvitationEntity
import com.example.taskapplication.data.database.entities.TeamMemberEntity
import com.example.taskapplication.data.database.entities.TeamMessageEntity
import com.example.taskapplication.data.database.entities.TeamTaskEntity
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.SyncStatus
import com.example.taskapplication.domain.model.Team
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.model.TeamMember
import com.example.taskapplication.domain.model.TeamMessage
import com.example.taskapplication.domain.model.TeamTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý đồng bộ dữ liệu giữa cục bộ và server
 */
@Singleton
class SyncManager @Inject constructor(
    private val apiService: ApiService,
    private val teamDao: TeamDao,
    private val teamMemberDao: TeamMemberDao,
    private val teamInvitationDao: TeamInvitationDao,
    private val teamTaskDao: TeamTaskDao,
    private val teamMessageDao: TeamMessageDao,
    private val connectionChecker: ConnectionChecker,
    private val dataStoreManager: DataStoreManager,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "SyncManager"

    // Sự kiện đồng bộ
    private val _syncEvents = MutableSharedFlow<SyncEvent>()
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    /**
     * Đồng bộ tất cả dữ liệu
     */
    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!connectionChecker.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("Không có kết nối mạng"))
        }

        try {
            // Đồng bộ theo thứ tự: teams -> members -> tasks -> messages
            val syncTeamsResult = syncTeams()
            if (syncTeamsResult.isFailure) {
                Log.e(TAG, "Lỗi đồng bộ teams: ${syncTeamsResult.exceptionOrNull()?.message}")
            }

            val syncMembersResult = syncTeamMembers()
            if (syncMembersResult.isFailure) {
                Log.e(TAG, "Lỗi đồng bộ members: ${syncMembersResult.exceptionOrNull()?.message}")
            }

            val syncTasksResult = syncTeamTasks()
            if (syncTasksResult.isFailure) {
                Log.e(TAG, "Lỗi đồng bộ tasks: ${syncTasksResult.exceptionOrNull()?.message}")
            }

            val syncMessagesResult = syncTeamMessages()
            if (syncMessagesResult.isFailure) {
                Log.e(TAG, "Lỗi đồng bộ messages: ${syncMessagesResult.exceptionOrNull()?.message}")
            }

            // Cập nhật thời gian đồng bộ
            dataStoreManager.saveLastSyncTimestamp(System.currentTimeMillis())

            // Phát sự kiện đồng bộ hoàn tất
            _syncEvents.emit(SyncEvent.SyncCompleted)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đồng bộ tất cả dữ liệu: ${e.message}", e)
            _syncEvents.emit(SyncEvent.SyncFailed(e.message ?: "Lỗi không xác định"))
            return@withContext Result.failure(e)
        }
    }

    /**
     * Đồng bộ dữ liệu nhóm
     */
    suspend fun syncTeams(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!connectionChecker.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("Không có kết nối mạng"))
        }

        try {
            // 1. Đẩy các thay đổi cục bộ lên server
            val pendingTeams = teamDao.getPendingSyncTeams()

            // Xử lý các nhóm cần tạo mới
            val teamsToCreate = pendingTeams.filter { it.syncStatus == SyncStatus.PENDING_CREATE.name }
            for (team in teamsToCreate) {
                try {
                    val response = apiService.createTeam(team.toApiRequest())
                    if (response.isSuccessful && response.body() != null) {
                        val serverTeam = response.body()!!
                        val updatedTeam = team.copy(
                            serverId = serverTeam.id.toString(),
                            syncStatus = SyncStatus.SYNCED.name,
                            lastModified = System.currentTimeMillis()
                        )
                        teamDao.updateTeam(updatedTeam)
                        Log.d(TAG, "Đã tạo nhóm trên server: ${serverTeam.id}")
                    } else {
                        Log.e(TAG, "Lỗi khi tạo nhóm trên server: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi tạo nhóm trên server", e)
                }
            }

            // Xử lý các nhóm cần cập nhật
            val teamsToUpdate = pendingTeams.filter { it.syncStatus == SyncStatus.PENDING_UPDATE.name }
            for (team in teamsToUpdate) {
                try {
                    if (team.serverId != null) {
                        val response = apiService.updateTeam(team.serverId, team.toApiRequest())
                        if (response.isSuccessful) {
                            val updatedTeam = team.copy(
                                syncStatus = SyncStatus.SYNCED.name,
                                lastModified = System.currentTimeMillis()
                            )
                            teamDao.updateTeam(updatedTeam)
                            Log.d(TAG, "Đã cập nhật nhóm trên server: ${team.serverId}")
                        } else {
                            Log.e(TAG, "Lỗi khi cập nhật nhóm trên server: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật nhóm trên server", e)
                }
            }

            // Xử lý các nhóm cần xóa
            val teamsToDelete = pendingTeams.filter { it.syncStatus == SyncStatus.PENDING_DELETE.name }
            for (team in teamsToDelete) {
                try {
                    if (team.serverId != null) {
                        val response = apiService.deleteTeam(team.serverId)
                        if (response.isSuccessful) {
                            teamDao.deleteTeam(team)
                            Log.d(TAG, "Đã xóa nhóm trên server: ${team.serverId}")
                        } else {
                            Log.e(TAG, "Lỗi khi xóa nhóm trên server: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xóa nhóm trên server", e)
                }
            }

            // 2. Lấy các thay đổi từ server về
            val lastSyncTimestamp = dataStoreManager.getLastTeamSyncTimestamp()
            val response = apiService.getTeamsChanges(lastSyncTimestamp.toString())

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Xử lý các nhóm mới
                syncResponse.data.teams?.created?.forEach { serverTeam ->
                    val localTeam = TeamEntity(
                        id = UUID.randomUUID().toString(),
                        name = serverTeam.name,
                        description = serverTeam.description,
                        ownerId = serverTeam.ownerId.toString(),
                        createdBy = serverTeam.createdBy.toString(),
                        serverId = serverTeam.id.toString(),
                        syncStatus = SyncStatus.SYNCED.name,
                        lastModified = System.currentTimeMillis(),
                        createdAt = serverTeam.createdAt
                    )
                    teamDao.insertTeam(localTeam)
                    Log.d(TAG, "Đã lưu nhóm mới từ server: ${serverTeam.id}")
                }

                // Xử lý các nhóm đã cập nhật
                syncResponse.data.teams?.updated?.forEach { serverTeam ->
                    val existingTeam = teamDao.getTeamByServerIdSync(serverTeam.id.toString())
                    if (existingTeam != null) {
                        val updatedTeam = existingTeam.copy(
                            name = serverTeam.name,
                            description = serverTeam.description,
                            syncStatus = SyncStatus.SYNCED.name,
                            lastModified = System.currentTimeMillis()
                        )
                        teamDao.updateTeam(updatedTeam)
                        Log.d(TAG, "Đã cập nhật nhóm từ server: ${serverTeam.id}")
                    }
                }

                // Xử lý các nhóm đã xóa
                syncResponse.data.teams?.deleted?.forEach { teamId ->
                    val team = teamDao.getTeamByServerIdSync(teamId.toString())
                    if (team != null) {
                        teamDao.deleteTeam(team)
                        Log.d(TAG, "Đã xóa nhóm theo yêu cầu từ server: $teamId")
                    }
                }

                // Cập nhật thời điểm đồng bộ
                dataStoreManager.saveLastTeamSyncTimestamp(syncResponse.meta.syncTimestamp.toLong())
            }

            // Phát sự kiện đồng bộ nhóm hoàn tất
            _syncEvents.emit(SyncEvent.TeamsSynced)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ nhóm: ${e.message}", e)
            _syncEvents.emit(SyncEvent.SyncFailed("Lỗi đồng bộ nhóm: ${e.message}"))
            return@withContext Result.failure(e)
        }
    }

    // Các phương thức đồng bộ khác sẽ được triển khai tương tự

    /**
     * Lưu lời mời từ WebSocket
     */
    suspend fun saveInvitation(invitation: TeamInvitation) {
        withContext(Dispatchers.IO) {
            val entity = TeamInvitationEntity(
                id = UUID.randomUUID().toString(),
                teamId = invitation.teamId.toString(),
                teamName = invitation.teamName ?: "Nhóm không xác định",
                email = invitation.email,
                role = invitation.role,
                status = invitation.status,
                token = invitation.token,
                createdAt = invitation.createdAt.toLong(),
                expiresAt = invitation.expiresAt.toLong(),
                inviterId = invitation.createdBy.toString(),
                inviterName = null,
                serverId = invitation.id.toString(),
                syncStatus = SyncStatus.SYNCED.name,
                lastModified = System.currentTimeMillis()
            )
            teamInvitationDao.insertInvitation(entity)
            Log.d(TAG, "Đã lưu lời mời từ WebSocket: ${invitation.id}")

            // Phát sự kiện lời mời mới
            _syncEvents.emit(SyncEvent.NewInvitation(invitation))
        }
    }

    /**
     * Lấy danh sách nhóm cục bộ
     */
    suspend fun getLocalTeams(): List<Team> {
        return teamDao.getAllTeamsSync().map { it.toDomainModel() }
    }

    /**
     * Đồng bộ dữ liệu nhóm cụ thể
     */
    suspend fun syncTeamData(teamId: Long) {
        coroutineScope.launch {
            try {
                // Đồng bộ thành viên nhóm
                syncTeamMembers(teamId)

                // Đồng bộ công việc nhóm
                syncTeamTasks(teamId)

                // Đồng bộ tin nhắn nhóm
                syncTeamMessages(teamId)

                Log.d(TAG, "Đã đồng bộ dữ liệu cho nhóm: $teamId")

                // Phát sự kiện đồng bộ hoàn tất
                _syncEvents.emit(SyncEvent.TeamDataSynced(teamId.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi đồng bộ dữ liệu nhóm $teamId: ${e.message}", e)
                _syncEvents.emit(SyncEvent.SyncFailed("Lỗi đồng bộ dữ liệu nhóm $teamId: ${e.message}"))
            }
        }
    }

    /**
     * Đồng bộ thành viên nhóm
     */
    suspend fun syncTeamMembers(teamId: Long? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!connectionChecker.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("Không có kết nối mạng"))
        }

        try {
            // 1. Đẩy các thay đổi cục bộ lên server
            val pendingMembers = if (teamId != null) {
                teamMemberDao.getPendingSyncTeamMembersByTeam(teamId.toString())
            } else {
                teamMemberDao.getPendingSyncTeamMembers()
            }

            // Xử lý các thành viên cần tạo mới
            val membersToCreate = pendingMembers.filter { it.syncStatus == SyncStatus.PENDING_CREATE.name }
            for (member in membersToCreate) {
                try {
                    val team = teamDao.getTeamByIdSync(member.teamId)
                    if (team?.serverId != null) {
                        val response = apiService.addTeamMember(
                            teamId = team.serverId,
                            userId = member.userId,
                            role = member.role
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val serverMember = response.body()!!
                            val updatedMember = member.copy(
                                serverId = serverMember.id.toString(),
                                syncStatus = SyncStatus.SYNCED.name,
                                lastModified = System.currentTimeMillis()
                            )
                            teamMemberDao.updateTeamMember(updatedMember)
                            Log.d(TAG, "Đã tạo thành viên nhóm trên server: ${serverMember.id}")
                        } else {
                            Log.e(TAG, "Lỗi khi tạo thành viên nhóm trên server: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi tạo thành viên nhóm trên server", e)
                }
            }

            // Xử lý các thành viên cần cập nhật
            val membersToUpdate = pendingMembers.filter { it.syncStatus == SyncStatus.PENDING_UPDATE.name }
            for (member in membersToUpdate) {
                try {
                    if (member.serverId != null) {
                        val team = teamDao.getTeamByIdSync(member.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.updateTeamMember(
                                teamId = team.serverId,
                                memberId = member.serverId,
                                role = member.role
                            )

                            if (response.isSuccessful) {
                                val updatedMember = member.copy(
                                    syncStatus = SyncStatus.SYNCED.name,
                                    lastModified = System.currentTimeMillis()
                                )
                                teamMemberDao.updateTeamMember(updatedMember)
                                Log.d(TAG, "Đã cập nhật thành viên nhóm trên server: ${member.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi cập nhật thành viên nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật thành viên nhóm trên server", e)
                }
            }

            // Xử lý các thành viên cần xóa
            val membersToDelete = pendingMembers.filter { it.syncStatus == SyncStatus.PENDING_DELETE.name }
            for (member in membersToDelete) {
                try {
                    if (member.serverId != null) {
                        val team = teamDao.getTeamByIdSync(member.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.removeTeamMember(
                                teamId = team.serverId,
                                memberId = member.serverId
                            )

                            if (response.isSuccessful) {
                                teamMemberDao.deleteTeamMember(member)
                                Log.d(TAG, "Đã xóa thành viên nhóm trên server: ${member.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi xóa thành viên nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xóa thành viên nhóm trên server", e)
                }
            }

            // 2. Lấy các thay đổi từ server về
            val lastSyncTimestamp = dataStoreManager.getLastTeamMemberSyncTimestamp()
            val response = if (teamId != null) {
                apiService.getTeamMembersChangesByTeam(teamId.toString(), lastSyncTimestamp.toString())
            } else {
                apiService.getTeamMembersChanges(lastSyncTimestamp.toString())
            }

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Xử lý các thành viên mới
                syncResponse.data.teamMembers?.created?.forEach { serverMember ->
                    val localMember = TeamMemberEntity(
                        id = UUID.randomUUID().toString(),
                        teamId = serverMember.teamId.toString(),
                        userId = serverMember.userId.toString(),
                        role = serverMember.role,
                        joinedAt = serverMember.joinedAt,
                        invitedBy = serverMember.invitedBy?.toString(),
                        serverId = serverMember.id.toString(),
                        syncStatus = SyncStatus.SYNCED.name,
                        lastModified = System.currentTimeMillis(),
                        createdAt = serverMember.joinedAt
                    )
                    teamMemberDao.insertTeamMember(localMember)
                    Log.d(TAG, "Đã lưu thành viên nhóm mới từ server: ${serverMember.id}")
                }

                // Xử lý các thành viên đã cập nhật
                syncResponse.data.teamMembers?.updated?.forEach { serverMember ->
                    val existingMember = teamMemberDao.getTeamMemberByServerId(serverMember.id.toString())
                    if (existingMember != null) {
                        val updatedMember = existingMember.copy(
                            role = serverMember.role,
                            syncStatus = SyncStatus.SYNCED.name,
                            lastModified = System.currentTimeMillis()
                        )
                        teamMemberDao.updateTeamMember(updatedMember)
                        Log.d(TAG, "Đã cập nhật thành viên nhóm từ server: ${serverMember.id}")
                    }
                }

                // Xử lý các thành viên đã xóa
                syncResponse.data.teamMembers?.deleted?.forEach { memberId ->
                    val member = teamMemberDao.getTeamMemberByServerId(memberId.toString())
                    if (member != null) {
                        teamMemberDao.deleteTeamMember(member)
                        Log.d(TAG, "Đã xóa thành viên nhóm theo yêu cầu từ server: $memberId")
                    }
                }

                // Cập nhật thời điểm đồng bộ
                dataStoreManager.saveLastTeamMemberSyncTimestamp(syncResponse.meta.syncTimestamp.toLong())
            }

            // Phát sự kiện đồng bộ thành viên nhóm hoàn tất
            _syncEvents.emit(SyncEvent.TeamMembersSynced)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ thành viên nhóm: ${e.message}", e)
            _syncEvents.emit(SyncEvent.SyncFailed("Lỗi đồng bộ thành viên nhóm: ${e.message}"))
            return@withContext Result.failure(e)
        }
    }

    /**
     * Đồng bộ công việc nhóm
     */
    suspend fun syncTeamTasks(teamId: Long? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!connectionChecker.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("Không có kết nối mạng"))
        }

        try {
            // 1. Đẩy các thay đổi cục bộ lên server
            val pendingTasks = if (teamId != null) {
                teamTaskDao.getPendingSyncTasksByTeam(teamId.toString())
            } else {
                teamTaskDao.getPendingSyncTasks()
            }

            // Xử lý các công việc cần tạo mới
            val tasksToCreate = pendingTasks.filter { it.syncStatus == SyncStatus.PENDING_CREATE.name }
            for (task in tasksToCreate) {
                try {
                    val team = teamDao.getTeamByIdSync(task.teamId)
                    if (team?.serverId != null) {
                        val response = apiService.createTeamTask(
                            teamId = team.serverId,
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate?.toString(),
                            priority = task.priority.toString(),
                            assignedUserId = task.assignedUserId
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val serverTask = response.body()!!
                            val updatedTask = task.copy(
                                serverId = serverTask.id.toString(),
                                syncStatus = SyncStatus.SYNCED.name,
                                lastModified = System.currentTimeMillis()
                            )
                            teamTaskDao.updateTask(updatedTask)
                            Log.d(TAG, "Đã tạo công việc nhóm trên server: ${serverTask.id}")
                        } else {
                            Log.e(TAG, "Lỗi khi tạo công việc nhóm trên server: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi tạo công việc nhóm trên server", e)
                }
            }

            // Xử lý các công việc cần cập nhật
            val tasksToUpdate = pendingTasks.filter { it.syncStatus == SyncStatus.PENDING_UPDATE.name }
            for (task in tasksToUpdate) {
                try {
                    if (task.serverId != null) {
                        val team = teamDao.getTeamByIdSync(task.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.updateTeamTask(
                                teamId = team.serverId,
                                taskId = task.serverId,
                                title = task.title,
                                description = task.description,
                                dueDate = task.dueDate?.toString(),
                                priority = task.priority.toString(),
                                assignedUserId = task.assignedUserId,
                                isCompleted = task.isCompleted
                            )

                            if (response.isSuccessful) {
                                val updatedTask = task.copy(
                                    syncStatus = SyncStatus.SYNCED.name,
                                    lastModified = System.currentTimeMillis()
                                )
                                teamTaskDao.updateTask(updatedTask)
                                Log.d(TAG, "Đã cập nhật công việc nhóm trên server: ${task.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi cập nhật công việc nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật công việc nhóm trên server", e)
                }
            }

            // Xử lý các công việc cần xóa
            val tasksToDelete = pendingTasks.filter { it.syncStatus == SyncStatus.PENDING_DELETE.name }
            for (task in tasksToDelete) {
                try {
                    if (task.serverId != null) {
                        val team = teamDao.getTeamByIdSync(task.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.deleteTeamTask(
                                teamId = team.serverId,
                                taskId = task.serverId
                            )

                            if (response.isSuccessful) {
                                teamTaskDao.deleteTask(task)
                                Log.d(TAG, "Đã xóa công việc nhóm trên server: ${task.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi xóa công việc nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xóa công việc nhóm trên server", e)
                }
            }

            // 2. Lấy các thay đổi từ server về
            val lastSyncTimestamp = dataStoreManager.getLastTeamTaskSyncTimestamp()
            val response = if (teamId != null) {
                apiService.getTeamTasksChangesByTeam(teamId.toString(), lastSyncTimestamp.toString())
            } else {
                apiService.getTeamTasksChanges(lastSyncTimestamp.toString())
            }

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Xử lý các công việc mới
                syncResponse.data.teamTasks?.created?.forEach { serverTask ->
                    val localTask = TeamTaskEntity(
                        id = UUID.randomUUID().toString(),
                        teamId = serverTask.teamId.toString(),
                        title = serverTask.title,
                        description = serverTask.description,
                        assignedUserId = serverTask.assignedUserId?.toString(),
                        dueDate = serverTask.dueDate?.toLong(),
                        priority = serverTask.priority.toInt(),
                        isCompleted = serverTask.isCompleted,
                        serverId = serverTask.id.toString(),
                        syncStatus = SyncStatus.SYNCED.name,
                        lastModified = System.currentTimeMillis(),
                        createdAt = serverTask.createdAt.toLong()
                    )
                    teamTaskDao.insertTask(localTask)
                    Log.d(TAG, "Đã lưu công việc nhóm mới từ server: ${serverTask.id}")
                }

                // Xử lý các công việc đã cập nhật
                syncResponse.data.teamTasks?.updated?.forEach { serverTask ->
                    val existingTask = teamTaskDao.getTaskByServerId(serverTask.id.toString())
                    if (existingTask != null) {
                        val updatedTask = existingTask.copy(
                            title = serverTask.title,
                            description = serverTask.description,
                            assignedUserId = serverTask.assignedUserId?.toString(),
                            dueDate = serverTask.dueDate?.toLong(),
                            priority = serverTask.priority.toInt(),
                            isCompleted = serverTask.isCompleted,
                            syncStatus = SyncStatus.SYNCED.name,
                            lastModified = System.currentTimeMillis()
                        )
                        teamTaskDao.updateTask(updatedTask)
                        Log.d(TAG, "Đã cập nhật công việc nhóm từ server: ${serverTask.id}")
                    }
                }

                // Xử lý các công việc đã xóa
                syncResponse.data.teamTasks?.deleted?.forEach { taskId ->
                    val task = teamTaskDao.getTaskByServerId(taskId.toString())
                    if (task != null) {
                        teamTaskDao.deleteTask(task)
                        Log.d(TAG, "Đã xóa công việc nhóm theo yêu cầu từ server: $taskId")
                    }
                }

                // Cập nhật thời điểm đồng bộ
                dataStoreManager.saveLastTeamTaskSyncTimestamp(syncResponse.meta.syncTimestamp.toLong())
            }

            // Phát sự kiện đồng bộ công việc nhóm hoàn tất
            _syncEvents.emit(SyncEvent.TeamTasksSynced)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ công việc nhóm: ${e.message}", e)
            _syncEvents.emit(SyncEvent.SyncFailed("Lỗi đồng bộ công việc nhóm: ${e.message}"))
            return@withContext Result.failure(e)
        }
    }

    /**
     * Đồng bộ tin nhắn nhóm
     */
    suspend fun syncTeamMessages(teamId: Long? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!connectionChecker.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("Không có kết nối mạng"))
        }

        try {
            // 1. Đẩy các thay đổi cục bộ lên server
            val pendingMessages = if (teamId != null) {
                teamMessageDao.getPendingSyncMessagesByTeam(teamId.toString())
            } else {
                teamMessageDao.getPendingSyncMessages()
            }

            // Xử lý các tin nhắn cần tạo mới
            val messagesToCreate = pendingMessages.filter { it.syncStatus == SyncStatus.PENDING_CREATE.name }
            for (message in messagesToCreate) {
                try {
                    val team = teamDao.getTeamByIdSync(message.teamId)
                    if (team?.serverId != null) {
                        val response = apiService.sendTeamMessage(
                            teamId = team.serverId,
                            content = message.content,
                            senderId = message.senderId
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val serverMessage = response.body()!!
                            val updatedMessage = message.copy(
                                serverId = serverMessage.id.toString(),
                                syncStatus = SyncStatus.SYNCED.name,
                                lastModified = System.currentTimeMillis()
                            )
                            teamMessageDao.updateMessage(updatedMessage)
                            Log.d(TAG, "Đã tạo tin nhắn nhóm trên server: ${serverMessage.id}")
                        } else {
                            Log.e(TAG, "Lỗi khi tạo tin nhắn nhóm trên server: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi tạo tin nhắn nhóm trên server", e)
                }
            }

            // Xử lý các tin nhắn cần cập nhật
            val messagesToUpdate = pendingMessages.filter { it.syncStatus == SyncStatus.PENDING_UPDATE.name }
            for (message in messagesToUpdate) {
                try {
                    if (message.serverId != null) {
                        val team = teamDao.getTeamByIdSync(message.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.updateTeamMessage(
                                teamId = team.serverId,
                                messageId = message.serverId,
                                content = message.content
                            )

                            if (response.isSuccessful) {
                                val updatedMessage = message.copy(
                                    syncStatus = SyncStatus.SYNCED.name,
                                    lastModified = System.currentTimeMillis()
                                )
                                teamMessageDao.updateMessage(updatedMessage)
                                Log.d(TAG, "Đã cập nhật tin nhắn nhóm trên server: ${message.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi cập nhật tin nhắn nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật tin nhắn nhóm trên server", e)
                }
            }

            // Xử lý các tin nhắn cần xóa
            val messagesToDelete = pendingMessages.filter { it.syncStatus == SyncStatus.PENDING_DELETE.name }
            for (message in messagesToDelete) {
                try {
                    if (message.serverId != null) {
                        val team = teamDao.getTeamByIdSync(message.teamId)
                        if (team?.serverId != null) {
                            val response = apiService.deleteTeamMessage(
                                teamId = team.serverId,
                                messageId = message.serverId
                            )

                            if (response.isSuccessful) {
                                teamMessageDao.deleteMessage(message)
                                Log.d(TAG, "Đã xóa tin nhắn nhóm trên server: ${message.serverId}")
                            } else {
                                Log.e(TAG, "Lỗi khi xóa tin nhắn nhóm trên server: ${response.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xóa tin nhắn nhóm trên server", e)
                }
            }

            // 2. Lấy các thay đổi từ server về
            val lastSyncTimestamp = dataStoreManager.getLastTeamMessageSyncTimestamp()
            val response = if (teamId != null) {
                apiService.getTeamMessagesChangesByTeam(teamId.toString(), lastSyncTimestamp.toString())
            } else {
                apiService.getTeamMessagesChanges(lastSyncTimestamp.toString())
            }

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Xử lý các tin nhắn mới
                syncResponse.data.teamMessages?.created?.forEach { serverMessage ->
                    val localMessage = TeamMessageEntity(
                        id = UUID.randomUUID().toString(),
                        teamId = serverMessage.teamId.toString(),
                        senderId = serverMessage.senderId.toString(),
                        content = serverMessage.content,
                        timestamp = serverMessage.timestamp.toLong(),
                        serverId = serverMessage.id.toString(),
                        syncStatus = SyncStatus.SYNCED.name,
                        lastModified = System.currentTimeMillis(),
                        createdAt = serverMessage.timestamp.toLong()
                    )
                    teamMessageDao.insertMessage(localMessage)
                    Log.d(TAG, "Đã lưu tin nhắn nhóm mới từ server: ${serverMessage.id}")
                }

                // Xử lý các tin nhắn đã cập nhật
                syncResponse.data.teamMessages?.updated?.forEach { serverMessage ->
                    val existingMessage = teamMessageDao.getMessageByServerId(serverMessage.id.toString())
                    if (existingMessage != null) {
                        val updatedMessage = existingMessage.copy(
                            content = serverMessage.content,
                            syncStatus = SyncStatus.SYNCED.name,
                            lastModified = System.currentTimeMillis()
                        )
                        teamMessageDao.updateMessage(updatedMessage)
                        Log.d(TAG, "Đã cập nhật tin nhắn nhóm từ server: ${serverMessage.id}")
                    }
                }

                // Xử lý các tin nhắn đã xóa
                syncResponse.data.teamMessages?.deleted?.forEach { messageId ->
                    val message = teamMessageDao.getMessageByServerId(messageId.toString())
                    if (message != null) {
                        teamMessageDao.deleteMessage(message)
                        Log.d(TAG, "Đã xóa tin nhắn nhóm theo yêu cầu từ server: $messageId")
                    }
                }

                // Cập nhật thời điểm đồng bộ
                dataStoreManager.saveLastTeamMessageSyncTimestamp(syncResponse.meta.syncTimestamp.toLong())
            }

            // Phát sự kiện đồng bộ tin nhắn nhóm hoàn tất
            _syncEvents.emit(SyncEvent.TeamMessagesSynced)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ tin nhắn nhóm: ${e.message}", e)
            _syncEvents.emit(SyncEvent.SyncFailed("Lỗi đồng bộ tin nhắn nhóm: ${e.message}"))
            return@withContext Result.failure(e)
        }
    }
}

/**
 * Các sự kiện đồng bộ
 */
sealed class SyncEvent {
    object SyncStarted : SyncEvent()
    object SyncCompleted : SyncEvent()
    object TeamsSynced : SyncEvent()
    object TeamMembersSynced : SyncEvent()
    object TeamTasksSynced : SyncEvent()
    object TeamMessagesSynced : SyncEvent()
    data class TeamDataSynced(val teamId: String) : SyncEvent()
    data class SyncFailed(val error: String) : SyncEvent()
    data class NewInvitation(val invitation: TeamInvitation) : SyncEvent()
    data class NewTask(val task: TeamTask) : SyncEvent()
    data class NewMessage(val message: TeamMessage) : SyncEvent()

    // Sự kiện cho các tài nguyên khác
    data class TaskAssigned(val task: TeamTask, val userId: String) : SyncEvent()
    data class TaskCompleted(val task: TeamTask) : SyncEvent()
    data class KanbanBoardUpdated(val boardId: String, val teamId: String) : SyncEvent()
    data class DocumentUpdated(val documentId: String, val teamId: String) : SyncEvent()
}
