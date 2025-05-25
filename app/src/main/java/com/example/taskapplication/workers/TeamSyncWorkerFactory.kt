package com.example.taskapplication.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.repository.TeamRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory để tạo TeamSyncWorker với các dependency
 */
@Singleton
class TeamSyncWorkerFactory @Inject constructor(
    private val teamRepository: TeamRepository,
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
            TeamSyncWorker::class.java.name -> {
                TeamSyncWorker(
                    appContext,
                    workerParameters,
                    teamRepository,
                    connectionChecker,
                    dataStoreManager,
                    webSocketManager
                )
            }
            else -> null
        }
    }
}
