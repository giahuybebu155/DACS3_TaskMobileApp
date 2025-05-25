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
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi đồng bộ dữ liệu nhóm $teamId: ${e.message}", e)
            }
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
    data class SyncFailed(val error: String) : SyncEvent()
    data class NewInvitation(val invitation: TeamInvitation) : SyncEvent()
    data class NewTask(val task: TeamTask) : SyncEvent()
    data class NewMessage(val message: TeamMessage) : SyncEvent()
}
