package com.example.taskapplication.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory để tạo InvitationNotificationWorker với các dependency
 */
@Singleton
class InvitationWorkerFactory @Inject constructor(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val connectionChecker: ConnectionChecker,
    private val dataStoreManager: DataStoreManager,
    private val webSocketManager: WebSocketManager
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            InvitationNotificationWorker::class.java.name -> {
                InvitationNotificationWorker(
                    appContext,
                    workerParameters,
                    teamInvitationRepository,
                    connectionChecker,
                    dataStoreManager,
                    webSocketManager
                )
            }
            else -> null
        }
    }
}
