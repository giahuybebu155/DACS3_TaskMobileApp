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

            // Triển khai đồng bộ hóa ban đầu ở đây
            // Lấy tất cả dữ liệu từ server và lưu vào local database

            // Cập nhật thời điểm đồng bộ
            dataStoreManager.saveLastSyncTimestamp(System.currentTimeMillis())

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

            // Lấy các task cần đồng bộ
            val pendingPersonalTasks = personalTaskDao.getPendingSyncTasks()

            if (pendingPersonalTasks.isEmpty()) {
                Log.d(TAG, "No pending changes to push")
                return Result.success(Unit)
            }

            // Triển khai đẩy các thay đổi lên server ở đây
            // Xử lý các task đang chờ tạo, cập nhật, xóa

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Push changes failed", e)
            return Result.failure(e)
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
