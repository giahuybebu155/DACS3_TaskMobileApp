package com.example.taskapplication.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.database.dao.MessageDao
import com.example.taskapplication.data.database.dao.PersonalTaskDao
import com.example.taskapplication.data.database.dao.TeamDao
import com.example.taskapplication.data.database.dao.TeamMemberDao
import com.example.taskapplication.data.database.dao.TeamTaskDao
import com.example.taskapplication.data.database.dao.UserDao
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.repository.SyncRepository
import com.example.taskapplication.workers.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val personalTaskDao: PersonalTaskDao,
    private val teamTaskDao: TeamTaskDao,
    private val teamDao: TeamDao,
    private val teamMemberDao: TeamMemberDao,
    private val userDao: UserDao,
    private val messageDao: MessageDao,
    private val dataStoreManager: DataStoreManager,
    private val connectionChecker: ConnectionChecker,
    @ApplicationContext private val context: Context
) : SyncRepository {

    // Temporarily commented out to fix compilation issues

    private val TAG = "SyncRepository"

    override suspend fun initialSync(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            return Result.failure(IOException("No network connection"))
        }

        try {
            val deviceId = dataStoreManager.getDeviceId()
            if (deviceId.isEmpty()) {
                return Result.failure(IOException("Device ID not found"))
            }

            // Gọi API initial sync
            val response = apiService.initialSync(deviceId)

            if (!response.isSuccessful) {
                return Result.failure(IOException("Initial sync failed: ${response.code()}"))
            }

            val syncResponse = response.body() ?: return Result.failure(IOException("Empty response"))

            // Xóa dữ liệu cũ
            personalTaskDao.deleteAllTasks()
            teamTaskDao.deleteAllTasks()
            teamDao.deleteAllTeams()
            teamMemberDao.deleteAllTeamMembers()
            messageDao.deleteAllMessages()

            // Lưu dữ liệu mới

            // Lưu các task cá nhân
            syncResponse.personalTasks.forEach { task ->
                personalTaskDao.insertTask(
                    com.example.taskapplication.data.database.entities.PersonalTaskEntity(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        dueDate = task.dueDate,
                        priority = task.priority,
                        isCompleted = task.isCompleted,
                        userId = task.userId,
                        serverId = task.id,
                        syncStatus = "synced",
                        lastModified = task.lastModified,
                        createdAt = task.createdAt
                    )
                )
            }

            // Lưu các task nhóm
            syncResponse.teamTasks.forEach { task ->
                teamTaskDao.insertTask(
                    com.example.taskapplication.data.database.entities.TeamTaskEntity(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        dueDate = task.dueDate,
                        priority = task.priority,
                        isCompleted = task.isCompleted,
                        teamId = task.teamId,
                        assignedUserId = task.assignedUserId,
                        serverId = task.id,
                        syncStatus = "synced",
                        lastModified = task.lastModified,
                        createdAt = task.createdAt
                    )
                )
            }

            // Lưu các nhóm
            syncResponse.teams.forEach { team ->
                teamDao.insertTeam(team.toEntity())
            }

            // Lưu các thành viên nhóm
            syncResponse.teamMembers.forEach { member ->
                teamMemberDao.insertTeamMember(
                    com.example.taskapplication.data.database.entities.TeamMemberEntity(
                        id = member.id,
                        teamId = member.teamId,
                        userId = member.userId,
                        role = member.role,
                        joinedAt = System.currentTimeMillis(),
                        invitedBy = null,
                        serverId = member.id,
                        syncStatus = "synced",
                        lastModified = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            // Lưu các tin nhắn
            syncResponse.messages?.forEach { message ->
                messageDao.insertMessage(
                    com.example.taskapplication.data.database.entities.MessageEntity(
                        id = message.id,
                        content = message.content,
                        senderId = message.senderId,
                        teamId = message.teamId,
                        receiverId = message.receiverId,
                        timestamp = message.timestamp,
                        isRead = message.isRead,
                        isDeleted = message.isDeleted,
                        lastModified = message.lastModified,
                        syncStatus = "synced",
                        serverId = message.id,
                        clientTempId = null,
                        createdAt = message.timestamp
                    )
                )
            }

            // Lưu thông tin người dùng
            userDao.insertUser(
                com.example.taskapplication.data.database.entities.UserEntity(
                    id = syncResponse.user.id.toString(),
                    name = syncResponse.user.name,
                    email = syncResponse.user.email,
                    avatar = syncResponse.user.avatar,
                    serverId = syncResponse.user.id.toString(),
                    syncStatus = "synced",
                    lastModified = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            )

            // Cập nhật thời điểm đồng bộ
            val syncTimestamp = syncResponse.timestamp
            dataStoreManager.saveLastSyncTimestamp(syncTimestamp)

            Log.d(TAG, "Initial sync completed successfully, timestamp: $syncTimestamp")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed", e)
            return Result.failure(e)
        }
    }

    override suspend fun quickSync(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            return Result.failure(IOException("No network connection"))
        }

        try {
            val lastSyncTimestamp = dataStoreManager.lastSyncTimestamp.first()
            val deviceId = dataStoreManager.getDeviceId()

            if (deviceId.isEmpty()) {
                return Result.failure(IOException("Device ID not found"))
            }

            // Gọi API quick sync
            val response = apiService.quickSync(
                deviceId = deviceId,
                lastSyncedAt = lastSyncTimestamp.toString()
            )

            if (!response.isSuccessful) {
                return Result.failure(IOException("Quick sync failed: ${response.code()}"))
            }

            val syncResponse = response.body() ?: return Result.failure(IOException("Empty response"))

            // Xử lý các task cá nhân mới
            if (syncResponse.personalTasks != null) {
                val createdTasks = syncResponse.personalTasks["created"] ?: emptyList()
                for (task in createdTasks) {
                    personalTaskDao.insertTask(
                        com.example.taskapplication.data.database.entities.PersonalTaskEntity(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            isCompleted = task.isCompleted,
                            userId = task.userId,
                            serverId = task.id,
                            syncStatus = "synced",
                            lastModified = task.lastModified,
                            createdAt = task.createdAt
                        )
                    )
                }

                // Xử lý các task cá nhân đã cập nhật
                val updatedTasks = syncResponse.personalTasks["updated"] ?: emptyList()
                for (task in updatedTasks) {
                    personalTaskDao.updateTask(
                        com.example.taskapplication.data.database.entities.PersonalTaskEntity(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            isCompleted = task.isCompleted,
                            userId = task.userId,
                            serverId = task.id,
                            syncStatus = "synced",
                            lastModified = task.lastModified,
                            createdAt = task.createdAt
                        )
                    )
                }

                // Xử lý các task cá nhân đã xóa
                val deletedTasks = syncResponse.personalTasks["deleted"] ?: emptyList()
                for (taskId in deletedTasks) {
                    personalTaskDao.deleteTask(taskId.id)
                }
            }

            // Xử lý các task nhóm
            if (syncResponse.teamTasks != null) {
                val createdTasks = syncResponse.teamTasks["created"] ?: emptyList()
                for (task in createdTasks) {
                    teamTaskDao.insertTask(
                        com.example.taskapplication.data.database.entities.TeamTaskEntity(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            isCompleted = task.isCompleted,
                            teamId = task.teamId,
                            assignedUserId = task.assignedUserId,
                            serverId = task.id,
                            syncStatus = "synced",
                            lastModified = task.lastModified,
                            createdAt = task.createdAt
                        )
                    )
                }

                // Xử lý các task nhóm đã cập nhật
                val updatedTasks = syncResponse.teamTasks["updated"] ?: emptyList()
                for (task in updatedTasks) {
                    teamTaskDao.updateTask(
                        com.example.taskapplication.data.database.entities.TeamTaskEntity(
                            id = task.id,
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            isCompleted = task.isCompleted,
                            teamId = task.teamId,
                            assignedUserId = task.assignedUserId,
                            serverId = task.id,
                            syncStatus = "synced",
                            lastModified = task.lastModified,
                            createdAt = task.createdAt
                        )
                    )
                }

                // Xử lý các task nhóm đã xóa
                val deletedTasks = syncResponse.teamTasks["deleted"] ?: emptyList()
                for (taskId in deletedTasks) {
                    teamTaskDao.deleteTask(taskId.id)
                }
            }

            // Xử lý các nhóm
            if (syncResponse.teams != null) {
                val createdTeams = syncResponse.teams["created"] ?: emptyList()
                for (team in createdTeams) {
                    teamDao.insertTeam(team.toEntity())
                }

                // Xử lý các nhóm đã cập nhật
                val updatedTeams = syncResponse.teams["updated"] ?: emptyList()
                for (team in updatedTeams) {
                    teamDao.updateTeam(team.toEntity())
                }

                // Xử lý các nhóm đã xóa
                val deletedTeams = syncResponse.teams["deleted"] ?: emptyList()
                for (teamId in deletedTeams) {
                    teamDao.deleteTeam(teamId.id)
                }
            }

            // Xử lý các thành viên nhóm
            if (syncResponse.teamMembers != null) {
                val createdMembers = syncResponse.teamMembers["created"] ?: emptyList()
                for (member in createdMembers) {
                    teamMemberDao.insertTeamMember(
                        com.example.taskapplication.data.database.entities.TeamMemberEntity(
                            id = member.id,
                            teamId = member.teamId,
                            userId = member.userId,
                            role = member.role,
                            joinedAt = System.currentTimeMillis(),
                            invitedBy = null,
                            serverId = member.id,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                // Xử lý các thành viên nhóm đã cập nhật
                val updatedMembers = syncResponse.teamMembers["updated"] ?: emptyList()
                for (member in updatedMembers) {
                    teamMemberDao.updateTeamMember(
                        com.example.taskapplication.data.database.entities.TeamMemberEntity(
                            id = member.id,
                            teamId = member.teamId,
                            userId = member.userId,
                            role = member.role,
                            joinedAt = System.currentTimeMillis(),
                            invitedBy = null,
                            serverId = member.id,
                            syncStatus = "synced",
                            lastModified = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                // Xử lý các thành viên nhóm đã xóa
                val deletedMembers = syncResponse.teamMembers["deleted"] ?: emptyList()
                for (memberId in deletedMembers) {
                    val member = teamMemberDao.getTeamMemberByServerId(memberId.id)
                    if (member != null) {
                        teamMemberDao.deleteTeamMember(member)
                    }
                }
            }

            // Xử lý các tin nhắn
            if (syncResponse.messages != null) {
                val createdMessages = syncResponse.messages["created"] ?: emptyList()
                for (message in createdMessages) {
                    messageDao.insertMessage(
                        com.example.taskapplication.data.database.entities.MessageEntity(
                            id = message.id,
                            content = message.content,
                            senderId = message.senderId,
                            teamId = message.teamId,
                            receiverId = message.receiverId,
                            timestamp = message.timestamp,
                            isRead = message.isRead,
                            isDeleted = message.isDeleted,
                            lastModified = message.lastModified,
                            syncStatus = "synced",
                            serverId = message.id,
                            clientTempId = null,
                            createdAt = message.timestamp
                        )
                    )
                }

                // Xử lý các tin nhắn đã cập nhật
                val updatedMessages = syncResponse.messages["updated"] ?: emptyList()
                for (message in updatedMessages) {
                    messageDao.updateMessage(
                        com.example.taskapplication.data.database.entities.MessageEntity(
                            id = message.id,
                            content = message.content,
                            senderId = message.senderId,
                            teamId = message.teamId,
                            receiverId = message.receiverId,
                            timestamp = message.timestamp,
                            isRead = message.isRead,
                            isDeleted = message.isDeleted,
                            lastModified = message.lastModified,
                            syncStatus = "synced",
                            serverId = message.id,
                            clientTempId = null,
                            createdAt = message.timestamp
                        )
                    )
                }

                // Xử lý các tin nhắn đã xóa
                val deletedMessages = syncResponse.messages["deleted"] ?: emptyList()
                for (messageId in deletedMessages) {
                    messageDao.deleteMessage(messageId.id)
                }
            }

            // Cập nhật thời điểm đồng bộ
            val newSyncTimestamp = syncResponse.timestamp
            dataStoreManager.saveLastSyncTimestamp(newSyncTimestamp)

            Log.d(TAG, "Quick sync completed successfully, new timestamp: $newSyncTimestamp")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Quick sync failed", e)
            return Result.failure(e)
        }
    }

    override suspend fun pushChanges(): Result<Unit> {
        if (!connectionChecker.isNetworkAvailable()) {
            return Result.failure(IOException("No network connection"))
        }

        try {
            val deviceId = dataStoreManager.getDeviceId()
            if (deviceId.isEmpty()) {
                return Result.failure(IOException("Device ID not found"))
            }

            // Lấy các task cá nhân cần đồng bộ
            val pendingPersonalTasks = personalTaskDao.getPendingSyncTasks()

            // Lấy các task nhóm cần đồng bộ
            val pendingTeamTasks = teamTaskDao.getPendingSyncTasks()

            // Lấy các tin nhắn cần đồng bộ
            val pendingMessages = messageDao.getPendingSyncMessages()

            if (pendingPersonalTasks.isEmpty() && pendingTeamTasks.isEmpty() && pendingMessages.isEmpty()) {
                Log.d(TAG, "No pending changes to push")
                return Result.success(Unit)
            }

            // Chuẩn bị dữ liệu để đẩy lên server
            val changes = com.example.taskapplication.data.api.model.SyncChangesRequest(
                deviceId = deviceId,
                personalTasks = com.example.taskapplication.data.api.model.SyncChanges(
                    created = pendingPersonalTasks.filter { it.syncStatus == "pending_create" }.map {
                        com.example.taskapplication.data.api.model.PersonalTaskDto(
                            id = it.id,
                            title = it.title,
                            description = it.description ?: "",
                            status = if (it.isCompleted) "completed" else "pending",
                            priority = it.priority.toString(),
                            dueDate = it.dueDate ?: 0L,
                            createdAt = it.createdAt,
                            updatedAt = it.lastModified,
                            userId = it.userId
                        )
                    },
                    updated = pendingPersonalTasks.filter { it.syncStatus == "pending_update" }.map {
                        com.example.taskapplication.data.api.model.PersonalTaskDto(
                            id = it.id,
                            title = it.title,
                            description = it.description ?: "",
                            status = if (it.isCompleted) "completed" else "pending",
                            priority = it.priority.toString(),
                            dueDate = it.dueDate ?: 0L,
                            createdAt = it.createdAt,
                            updatedAt = it.lastModified,
                            userId = it.userId
                        )
                    },
                    deleted = pendingPersonalTasks.filter { it.syncStatus == "pending_delete" }.map { it.id }
                ),
                teamTasks = com.example.taskapplication.data.api.model.SyncChanges(
                    created = pendingTeamTasks.filter { it.syncStatus == "pending_create" }.map {
                        com.example.taskapplication.data.api.model.TeamTaskDto(
                            id = it.id,
                            title = it.title,
                            description = it.description ?: "",
                            status = if (it.isCompleted) "completed" else "pending",
                            priority = it.priority.toString(),
                            dueDate = it.dueDate,
                            createdAt = it.createdAt,
                            updatedAt = it.lastModified,
                            teamId = it.teamId,
                            assignedUserId = it.assignedUserId
                        )
                    },
                    updated = pendingTeamTasks.filter { it.syncStatus == "pending_update" }.map {
                        com.example.taskapplication.data.api.model.TeamTaskDto(
                            id = it.id,
                            title = it.title,
                            description = it.description ?: "",
                            status = if (it.isCompleted) "completed" else "pending",
                            priority = it.priority.toString(),
                            dueDate = it.dueDate,
                            createdAt = it.createdAt,
                            updatedAt = it.lastModified,
                            teamId = it.teamId,
                            assignedUserId = it.assignedUserId
                        )
                    },
                    deleted = pendingTeamTasks.filter { it.syncStatus == "pending_delete" }.map { it.id }
                ),
                messages = com.example.taskapplication.data.api.model.SyncChanges(
                    created = pendingMessages.filter { it.syncStatus == "pending_create" }.map {
                        com.example.taskapplication.data.api.model.MessageDto(
                            id = it.id,
                            content = it.content,
                            senderId = it.senderId,
                            teamId = it.teamId,
                            receiverId = it.receiverId,
                            timestamp = it.timestamp,
                            isRead = it.isRead,
                            isDeleted = it.isDeleted,
                            lastModified = it.lastModified
                        )
                    },
                    updated = pendingMessages.filter { it.syncStatus == "pending_update" }.map {
                        com.example.taskapplication.data.api.model.MessageDto(
                            id = it.id,
                            content = it.content,
                            senderId = it.senderId,
                            teamId = it.teamId,
                            receiverId = it.receiverId,
                            timestamp = it.timestamp,
                            isRead = it.isRead,
                            isDeleted = it.isDeleted,
                            lastModified = it.lastModified
                        )
                    },
                    deleted = pendingMessages.filter { it.syncStatus == "pending_delete" }.map { it.id }
                )
            )

            // Gửi thay đổi lên server
            val response = apiService.pushChanges(changes)

            if (!response.isSuccessful) {
                return Result.failure(IOException("Push changes failed: ${response.code()}"))
            }

            val pushResponse = response.body() ?: return Result.failure(IOException("Empty response"))

            // Cập nhật trạng thái đồng bộ của các entity đã đồng bộ thành công
            pushResponse.syncedEntities.personalTasks.forEach { taskId ->
                personalTaskDao.updateSyncStatus(taskId, "synced")
            }

            pushResponse.syncedEntities.teamTasks.forEach { taskId ->
                teamTaskDao.updateSyncStatus(taskId, "synced")
            }

            pushResponse.syncedEntities.messages.forEach { messageId ->
                messageDao.updateSyncStatus(messageId, "synced")
            }

            // Xử lý xung đột nếu có
            if (pushResponse.conflicts.isNotEmpty()) {
                resolveConflicts(pushResponse.conflicts)
            }

            Log.d(TAG, "Push changes completed successfully")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Push changes failed", e)
            return Result.failure(e)
        }
    }

    private suspend fun resolveConflicts(conflicts: List<com.example.taskapplication.data.api.model.ConflictDto>): Result<Unit> {
        try {
            val resolutions = mutableListOf<com.example.taskapplication.data.api.model.ConflictResolution>()

            for (conflict in conflicts) {
                when (conflict.type) {
                    "CONTENT_CONFLICT" -> {
                        // Ưu tiên phiên bản server cho xung đột nội dung
                        resolutions.add(com.example.taskapplication.data.api.model.ConflictResolution(
                            id = conflict.id,
                            type = conflict.type,
                            resolution = "server"
                        ))
                    }
                    "METADATA_CONFLICT" -> {
                        // Hợp nhất metadata
                        resolutions.add(com.example.taskapplication.data.api.model.ConflictResolution(
                            id = conflict.id,
                            type = conflict.type,
                            resolution = "merge"
                        ))
                    }
                    "PERMISSION_CONFLICT" -> {
                        // Ưu tiên phiên bản server cho xung đột quyền
                        resolutions.add(com.example.taskapplication.data.api.model.ConflictResolution(
                            id = conflict.id,
                            type = conflict.type,
                            resolution = "server"
                        ))
                    }
                    else -> {
                        // Mặc định ưu tiên phiên bản server
                        resolutions.add(com.example.taskapplication.data.api.model.ConflictResolution(
                            id = conflict.id,
                            type = conflict.type,
                            resolution = "server"
                        ))
                    }
                }
            }

            // Gửi các quyết định giải quyết xung đột lên server
            val request = com.example.taskapplication.data.api.model.ResolveConflictsRequest(resolutions)
            val response = apiService.resolveConflicts(request)

            if (!response.isSuccessful) {
                return Result.failure(IOException("Failed to resolve conflicts: ${response.code()}"))
            }

            val resolveResponse = response.body() ?: return Result.failure(IOException("Empty response"))

            // Cập nhật dữ liệu cục bộ với dữ liệu đã giải quyết xung đột
            updateResolvedEntities(resolveResponse.resolvedEntities)

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving conflicts", e)
            return Result.failure(e)
        }
    }

    private suspend fun updateResolvedEntities(resolvedEntities: com.example.taskapplication.data.api.model.ResolvedEntitiesDto) {
        // Cập nhật các task cá nhân
        resolvedEntities.personalTasks.forEach { task ->
            personalTaskDao.updateTask(
                com.example.taskapplication.data.database.entities.PersonalTaskEntity(
                    id = task.id,
                    title = task.title,
                    description = task.description,
                    dueDate = task.dueDate,
                    priority = task.priority,
                    isCompleted = task.isCompleted,
                    userId = task.userId,
                    serverId = task.id,
                    syncStatus = "synced",
                    lastModified = task.lastModified,
                    createdAt = task.createdAt
                )
            )
        }

        // Cập nhật các task nhóm
        resolvedEntities.teamTasks.forEach { task ->
            teamTaskDao.updateTask(
                com.example.taskapplication.data.database.entities.TeamTaskEntity(
                    id = task.id,
                    title = task.title,
                    description = task.description,
                    dueDate = task.dueDate,
                    priority = task.priority,
                    isCompleted = task.isCompleted,
                    teamId = task.teamId,
                    assignedUserId = task.assignedUserId,
                    serverId = task.id,
                    syncStatus = "synced",
                    lastModified = task.lastModified,
                    createdAt = task.createdAt
                )
            )
        }

        // Cập nhật các tin nhắn
        resolvedEntities.messages.forEach { message ->
            messageDao.updateMessage(
                com.example.taskapplication.data.database.entities.MessageEntity(
                    id = message.id,
                    content = message.content,
                    senderId = message.senderId,
                    teamId = message.teamId,
                    receiverId = message.receiverId,
                    timestamp = message.timestamp,
                    isRead = message.isRead,
                    isDeleted = message.isDeleted,
                    lastModified = message.lastModified,
                    syncStatus = "synced",
                    serverId = message.id,
                    clientTempId = null,
                    createdAt = message.timestamp
                )
            )
        }
    }

    override suspend fun getLastSyncTimestamp(): Long? {
        return dataStoreManager.lastSyncTimestamp.first()
    }

    override suspend fun updateLastSyncTimestamp(timestamp: Long) {
        dataStoreManager.saveLastSyncTimestamp(timestamp)
    }

    override suspend fun schedulePeriodicSync(intervalMinutes: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )

        Log.d(TAG, "Scheduled periodic sync every $intervalMinutes minutes")
    }

    override suspend fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork("periodic_sync")
        Log.d(TAG, "Cancelled periodic sync")
    }

    override suspend fun hasPendingChanges(): Boolean {
        val pendingPersonalTasks = personalTaskDao.getPendingSyncTasks()
        val pendingTeamTasks = teamTaskDao.getPendingSyncTasks()
        val pendingMessages = messageDao.getPendingSyncMessages()

        return pendingPersonalTasks.isNotEmpty() ||
               pendingTeamTasks.isNotEmpty() ||
               pendingMessages.isNotEmpty()
    }
}
