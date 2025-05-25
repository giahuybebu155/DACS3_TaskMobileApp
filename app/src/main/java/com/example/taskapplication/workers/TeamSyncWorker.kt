package com.example.taskapplication.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.taskapplication.R
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.repository.TeamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker để đồng bộ dữ liệu team và team member
 */
class TeamSyncWorker constructor(
    context: Context,
    workerParams: WorkerParameters,
    private val teamRepository: TeamRepository,
    private val connectionChecker: ConnectionChecker,
    private val dataStoreManager: DataStoreManager,
    private val webSocketManager: WebSocketManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TeamSyncWorker"
        private const val TEAM_SYNC_WORK_NAME = "team_sync_polling"
        private const val CHANNEL_ID = "team_updates"
        private const val NOTIFICATION_GROUP = "team_updates_group"

        /**
         * Lên lịch đồng bộ team định kỳ
         */
        fun schedulePeriodicTeamSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TeamSyncWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TEAM_SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )

            Log.d(TAG, "Đã lên lịch đồng bộ team định kỳ")
        }

        /**
         * Tạo kênh thông báo cho cập nhật team
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.team_update_channel_name)
                val descriptionText = context.getString(R.string.team_update_channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Bắt đầu đồng bộ dữ liệu team")

        // Tạo kênh thông báo nếu chưa có
        createNotificationChannel(applicationContext)

        // Kiểm tra kết nối mạng
        if (!connectionChecker.isNetworkAvailable()) {
            Log.d(TAG, "Không có kết nối mạng, thử lại sau")
            return@withContext Result.retry()
        }

        try {
            // Lấy userId hiện tại
            val userId = dataStoreManager.getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                Log.d(TAG, "Không tìm thấy userId, không thể đồng bộ team")
                return@withContext Result.failure()
            }

            // Đảm bảo WebSocket được kết nối
            ensureWebSocketConnected()

            // Đồng bộ teams từ server
            Log.d(TAG, "Bắt đầu đồng bộ teams từ server")
            val syncResult = teamRepository.syncTeams()

            if (syncResult.isSuccess) {
                Log.d(TAG, "Đồng bộ teams thành công")
            } else {
                val error = syncResult.exceptionOrNull()
                Log.e(TAG, "Lỗi đồng bộ teams: ${error?.message}", error)
                // Tiếp tục xử lý ngay cả khi đồng bộ thất bại
            }

            // Đồng bộ team members từ server
            Log.d(TAG, "Bắt đầu đồng bộ team members từ server")
            val syncMembersResult = teamRepository.syncTeamMembers()

            if (syncMembersResult.isSuccess) {
                Log.d(TAG, "Đồng bộ team members thành công")
            } else {
                val error = syncMembersResult.exceptionOrNull()
                Log.e(TAG, "Lỗi đồng bộ team members: ${error?.message}", error)
            }

            // Lên lịch kiểm tra lại sau 15 phút
            val workManager = WorkManager.getInstance(applicationContext)
            val checkAgainWork = OneTimeWorkRequestBuilder<TeamSyncWorker>()
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            workManager.enqueue(checkAgainWork)
            Log.d(TAG, "Đã lên lịch đồng bộ lại team sau 15 phút")

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đồng bộ team", e)
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Đảm bảo WebSocket được kết nối
     */
    private suspend fun ensureWebSocketConnected() {
        try {
            // Kiểm tra trạng thái kết nối hiện tại
            val currentState = webSocketManager.connectionState.first()
            Log.d(TAG, "Trạng thái kết nối WebSocket hiện tại: $currentState")

            if (currentState != com.example.taskapplication.data.websocket.ConnectionState.CONNECTED) {
                // Lấy token và teamId từ DataStore
                val authToken = dataStoreManager.getAuthToken()
                val currentTeamId = dataStoreManager.getCurrentTeamId()

                if (authToken != null && currentTeamId != null) {
                    Log.d(TAG, "Kết nối lại WebSocket với token=${authToken.take(5)}... và teamId=$currentTeamId")
                    webSocketManager.connect(authToken, currentTeamId)

                    // Đợi kết nối thành công
                    var attempts = 0
                    while (attempts < 5) {
                        delay(1000) // Đợi 1 giây
                        val newState = webSocketManager.connectionState.first()
                        Log.d(TAG, "Trạng thái kết nối sau khi thử kết nối: $newState (lần thử $attempts)")

                        if (newState == com.example.taskapplication.data.websocket.ConnectionState.CONNECTED) {
                            Log.d(TAG, "WebSocket đã kết nối thành công sau ${attempts + 1} lần thử")
                            break
                        }
                        attempts++
                    }
                } else {
                    Log.e(TAG, "Không thể kết nối WebSocket: thiếu token hoặc teamId")
                }
            } else {
                Log.d(TAG, "WebSocket đã được kết nối")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kết nối WebSocket", e)
        }
    }
}
