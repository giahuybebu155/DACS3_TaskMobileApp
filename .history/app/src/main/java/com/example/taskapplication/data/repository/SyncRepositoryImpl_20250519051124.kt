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

            // Lấy tất cả dữ liệu từ server
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
            syncResponse.data.personalTasks.forEach { task ->
                personalTaskDao.insertTask(task.toEntity())
            }

            // Lưu các task nhóm
            syncResponse.data.teamTasks.forEach { task ->
                teamTaskDao.insertTask(task.toEntity())
            }

            // Lưu các nhóm
            syncResponse.data.teams.forEach { team ->
                teamDao.insertTeam(team.toEntity())
            }

            // Lưu các thành viên nhóm
            syncResponse.data.teamMembers.forEach { member ->
                teamMemberDao.insertTeamMember(member.toEntity())
            }

            // Lưu các tin nhắn
            syncResponse.data.messages.forEach { message ->
                messageDao.insertMessage(message.toEntity())
            }

            // Lưu thông tin người dùng
            syncResponse.data.users.forEach { user ->
                userDao.insertUser(user.toEntity())
            }

            // Cập nhật thời điểm đồng bộ
            val syncTimestamp = syncResponse.meta.syncTimestamp.toLong()
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

            // Lấy các thay đổi từ server kể từ lần đồng bộ cuối cùng
            val response = apiService.quickSync(
                deviceId = deviceId,
                lastSyncedAt = lastSyncTimestamp.toString()
            )

            if (!response.isSuccessful) {
                return Result.failure(IOException("Quick sync failed: ${response.code()}"))
            }

            val syncResponse = response.body() ?: return Result.failure(IOException("Empty response"))

            // Xử lý các task cá nhân mới
            syncResponse.data.personalTasks.created.forEach { task ->
                personalTaskDao.insertTask(task.toEntity())
            }

            // Xử lý các task cá nhân đã cập nhật
            syncResponse.data.personalTasks.updated.forEach { task ->
                personalTaskDao.updateTask(task.toEntity())
            }

            // Xử lý các task cá nhân đã xóa
            syncResponse.data.personalTasks.deleted.forEach { taskId ->
                personalTaskDao.deleteTask(taskId)
            }

            // Xử lý các task nhóm mới
            syncResponse.data.teamTasks.created.forEach { task ->
                teamTaskDao.insertTask(task.toEntity())
            }

            // Xử lý các task nhóm đã cập nhật
            syncResponse.data.teamTasks.updated.forEach { task ->
                teamTaskDao.updateTask(task.toEntity())
            }

            // Xử lý các task nhóm đã xóa
            syncResponse.data.teamTasks.deleted.forEach { taskId ->
                teamTaskDao.deleteTask(taskId)
            }

            // Xử lý các nhóm mới
            syncResponse.data.teams.created.forEach { team ->
                teamDao.insertTeam(team.toEntity())
            }

            // Xử lý các nhóm đã cập nhật
            syncResponse.data.teams.updated.forEach { team ->
                teamDao.updateTeam(team.toEntity())
            }

            // Xử lý các nhóm đã xóa
            syncResponse.data.teams.deleted.forEach { teamId ->
                teamDao.deleteTeam(teamId)
            }

            // Xử lý các thành viên nhóm mới
            syncResponse.data.teamMembers.created.forEach { member ->
                teamMemberDao.insertTeamMember(member.toEntity())
            }

            // Xử lý các thành viên nhóm đã cập nhật
            syncResponse.data.teamMembers.updated.forEach { member ->
                teamMemberDao.updateTeamMember(member.toEntity())
            }

            // Xử lý các thành viên nhóm đã xóa
            syncResponse.data.teamMembers.deleted.forEach { memberId ->
                teamMemberDao.deleteTeamMember(memberId)
            }

            // Xử lý các tin nhắn mới
            syncResponse.data.messages.created.forEach { message ->
                messageDao.insertMessage(message.toEntity())
            }

            // Xử lý các tin nhắn đã cập nhật
            syncResponse.data.messages.updated.forEach { message ->
                messageDao.updateMessage(message.toEntity())
            }

            // Xử lý các tin nhắn đã xóa
            syncResponse.data.messages.deleted.forEach { messageId ->
                messageDao.deleteMessage(messageId)
            }

            // Cập nhật thời điểm đồng bộ
            val newSyncTimestamp = syncResponse.meta.syncTimestamp.toLong()
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
                    created = pendingPersonalTasks.filter { it.syncStatus == "pending_create" }.map { it.toApiModel() },
                    updated = pendingPersonalTasks.filter { it.syncStatus == "pending_update" }.map { it.toApiModel() },
                    deleted = pendingPersonalTasks.filter { it.syncStatus == "pending_delete" }.map { it.id }
                ),
                teamTasks = com.example.taskapplication.data.api.model.SyncChanges(
                    created = pendingTeamTasks.filter { it.syncStatus == "pending_create" }.map { it.toApiModel() },
                    updated = pendingTeamTasks.filter { it.syncStatus == "pending_update" }.map { it.toApiModel() },
                    deleted = pendingTeamTasks.filter { it.syncStatus == "pending_delete" }.map { it.id }
                ),
                messages = com.example.taskapplication.data.api.model.SyncChanges(
                    created = pendingMessages.filter { it.syncStatus == "pending_create" }.map { it.toApiModel() },
                    updated = pendingMessages.filter { it.syncStatus == "pending_update" }.map { it.toApiModel() },
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
            personalTaskDao.updateTask(task.toEntity())
        }

        // Cập nhật các task nhóm
        resolvedEntities.teamTasks.forEach { task ->
            teamTaskDao.updateTask(task.toEntity())
        }

        // Cập nhật các tin nhắn
        resolvedEntities.messages.forEach { message ->
            messageDao.updateMessage(message.toEntity())
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
        return pendingPersonalTasks.isNotEmpty()
    }
}
