package com.example.taskapplication

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.example.taskapplication.R
import com.example.taskapplication.data.database.DatabaseCleaner
import com.example.taskapplication.workers.InvitationNotificationWorker
import com.example.taskapplication.workers.PersonalTaskSyncWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TaskApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.work.WorkerFactory

    @Inject
    lateinit var databaseCleaner: DatabaseCleaner

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Set up global exception handler to prevent crashes
        setupGlobalExceptionHandler()

        // Kiểm tra và xóa database cũ nếu cần
        try {
            if (databaseCleaner.needsCleaning()) {
                Log.d("TaskApplication", "Cleaning old database...")
                databaseCleaner.cleanDatabase()
                Log.d("TaskApplication", "Database cleaned successfully")
            }
        } catch (e: Exception) {
            Log.e("TaskApplication", "Error cleaning database", e)
        }

        createNotificationChannels()
        scheduleWorkers()
    }

    /**
     * Set up global exception handler to prevent app crashes
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("TaskApplication", "💥 [GLOBAL_CRASH] ===== UNCAUGHT EXCEPTION =====")
            Log.e("TaskApplication", "💥 [GLOBAL_CRASH] Thread: ${thread.name}")
            Log.e("TaskApplication", "💥 [GLOBAL_CRASH] Exception type: ${exception.javaClass.simpleName}")
            Log.e("TaskApplication", "💥 [GLOBAL_CRASH] Exception message: ${exception.message}")

            // Log detailed exception info for network-related crashes
            when (exception) {
                is java.net.SocketTimeoutException -> {
                    Log.e("TaskApplication", "⏰ [GLOBAL_CRASH] Network timeout - server not responding")
                }
                is java.net.ConnectException -> {
                    Log.e("TaskApplication", "🔌 [GLOBAL_CRASH] Connection refused - server not running")
                }
                is java.net.UnknownHostException -> {
                    Log.e("TaskApplication", "🌐 [GLOBAL_CRASH] Unknown host - DNS resolution failed")
                }
                is retrofit2.HttpException -> {
                    Log.e("TaskApplication", "📡 [GLOBAL_CRASH] HTTP error: ${exception.code()}")
                }
                else -> {
                    Log.e("TaskApplication", "❓ [GLOBAL_CRASH] Other exception type")
                }
            }

            Log.e("TaskApplication", "⚠️ [GLOBAL_CRASH] This crash was likely caused by server connectivity issues")
            Log.e("TaskApplication", "⚠️ [GLOBAL_CRASH] App should work offline - please check server status")

            exception.printStackTrace()

            // Call the default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }

        Log.d("TaskApplication", "✅ Global exception handler set up successfully")
    }

    /**
     * Lên lịch cho các worker
     */
    private fun scheduleWorkers() {
        // Lên lịch kiểm tra lời mời
        InvitationNotificationWorker.schedulePeriodicInvitationCheck(this)

        // Lên lịch đồng bộ personal tasks
        schedulePersonalTaskSync()
    }

    private fun schedulePersonalTaskSync() {
        val syncWorkRequest = PeriodicWorkRequestBuilder<PersonalTaskSyncWorker>(
            15, TimeUnit.MINUTES // Đồng bộ mỗi 15 phút
        ).build()

        WorkManager.getInstance(this).enqueue(syncWorkRequest)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Kênh nhắc nhở công việc
            val taskReminderChannel = NotificationChannel(
                CHANNEL_TASK_REMINDER,
                "Nhắc nhở công việc",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nhắc nhở công việc"
                enableVibration(true)
            }

            // Kênh tin nhắn nhóm
            val teamMessageChannel = NotificationChannel(
                CHANNEL_TEAM_MESSAGE,
                "Tin nhắn nhóm",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo cho tin nhắn nhóm"
                enableVibration(true)
            }

            // Kênh phân công công việc
            val taskAssignmentChannel = NotificationChannel(
                CHANNEL_TASK_ASSIGNMENT,
                "Phân công công việc",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo cho phân công công việc"
                enableVibration(true)
            }

            // Kênh lời mời tham gia nhóm
            val teamInvitationChannel = NotificationChannel(
                CHANNEL_TEAM_INVITATION,
                getString(R.string.invitation_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.invitation_channel_description)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(
                    taskReminderChannel,
                    teamMessageChannel,
                    taskAssignmentChannel,
                    teamInvitationChannel
                )
            )
        }
    }



    companion object {
        const val CHANNEL_TASK_REMINDER = "task_reminder"
        const val CHANNEL_TEAM_MESSAGE = "team_message"
        const val CHANNEL_TASK_ASSIGNMENT = "task_assignment"
        const val CHANNEL_TEAM_INVITATION = "team_invitations"
    }
}
