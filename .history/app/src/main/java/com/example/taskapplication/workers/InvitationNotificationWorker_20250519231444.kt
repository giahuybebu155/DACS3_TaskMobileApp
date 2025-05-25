package com.example.taskapplication.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.taskapplication.R
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import com.example.taskapplication.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker để kiểm tra và hiển thị thông báo về lời mời mới
 */
class InvitationNotificationWorker constructor(
    context: Context,
    workerParams: WorkerParameters,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val connectionChecker: ConnectionChecker,
    private val dataStoreManager: DataStoreManager,
    private val webSocketManager: WebSocketManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "InvitationNotificationWorker"
        private const val INVITATION_WORK_NAME = "invitation_notification_polling"
        private const val CHANNEL_ID = "team_invitations"
        private const val NOTIFICATION_GROUP = "team_invitations_group"

        /**
         * Lên lịch kiểm tra lời mời định kỳ
         */
        fun schedulePeriodicInvitationCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<InvitationNotificationWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    INVITATION_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )

            Log.d(TAG, "Đã lên lịch kiểm tra lời mời định kỳ")
        }

        /**
         * Tạo kênh thông báo cho lời mời
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.invitation_channel_name)
                val descriptionText = context.getString(R.string.invitation_channel_description)
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
        Log.d(TAG, "Đang kiểm tra lời mời mới")

        // Tạo kênh thông báo nếu chưa có
        createNotificationChannel(applicationContext)

        // Kiểm tra kết nối mạng
        if (!connectionChecker.isNetworkAvailable()) {
            Log.d(TAG, "Không có kết nối mạng, thử lại sau")
            return@withContext Result.retry()
        }

        try {
            // Lấy email người dùng hiện tại
            val email = dataStoreManager.userEmail.first()
            if (email.isNullOrEmpty()) {
                Log.d(TAG, "Không tìm thấy email người dùng, không thể kiểm tra lời mời")
                return@withContext Result.failure()
            }

            // Đảm bảo WebSocket được kết nối
            ensureWebSocketConnected()

            // Đồng bộ lời mời từ server
            Log.d(TAG, "Bắt đầu đồng bộ lời mời từ server")
            val syncResult = teamInvitationRepository.syncInvitations()

            if (syncResult.isSuccess) {
                Log.d(TAG, "Đồng bộ lời mời thành công")
            } else {
                val error = (syncResult as? Result.Failure)?.exception
                Log.e(TAG, "Lỗi đồng bộ lời mời: ${error?.message}", error)
                // Tiếp tục xử lý ngay cả khi đồng bộ thất bại
            }

            // Lấy danh sách lời mời đang chờ
            val invitations = teamInvitationRepository.getUserInvitations().first()
                .filter { it.status == "pending" }

            Log.d(TAG, "Danh sách lời mời: ${invitations.size} lời mời - ${invitations.map { "${it.teamName} (${it.id})" }}")

            if (invitations.isNotEmpty()) {
                Log.d(TAG, "Tìm thấy ${invitations.size} lời mời đang chờ")
                showInvitationNotifications(invitations)
            } else {
                Log.d(TAG, "Không tìm thấy lời mời đang chờ nào")
            }

            // Lên lịch kiểm tra lại sau 15 phút nếu có lời mời đang chờ
            if (invitations.isNotEmpty()) {
                val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                val checkAgainWork = androidx.work.OneTimeWorkRequestBuilder<InvitationNotificationWorker>()
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .build()
                workManager.enqueue(checkAgainWork)
                Log.d(TAG, "Đã lên lịch kiểm tra lại lời mời sau 15 phút")
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kiểm tra lời mời", e)
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Hiển thị thông báo cho các lời mời
     */
    private fun showInvitationNotifications(invitations: List<TeamInvitation>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Đảm bảo kênh thông báo đã được tạo
        createNotificationChannel(applicationContext)

        // Tạo intent để mở ứng dụng
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_INVITATIONS", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Hiển thị thông báo cho mỗi lời mời
        invitations.forEachIndexed { index, invitation ->
            val notificationId = invitation.id.hashCode()

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(applicationContext.getString(R.string.invitation_title))
                .setContentText(applicationContext.getString(R.string.invitation_content, invitation.teamName))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .build()

            notificationManager.notify(notificationId, notification)
        }

        // Nếu có nhiều hơn 1 lời mời, hiển thị thông báo tóm tắt
        if (invitations.size > 1) {
            val summaryNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(applicationContext.getString(R.string.invitation_summary_title, invitations.size))
                .setContentText(applicationContext.getString(R.string.invitation_summary_content, invitations.size))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .build()

            notificationManager.notify(0, summaryNotification)
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
