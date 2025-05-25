package com.example.taskapplication.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.taskapplication.data.worker.TaskWorkerFactory
import com.example.taskapplication.workers.InvitationWorkerFactory
import com.example.taskapplication.workers.TeamSyncWorkerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory kết hợp để tạo các worker khác nhau
 */
@Singleton
class CompositeWorkerFactory @Inject constructor(
    private val taskWorkerFactory: TaskWorkerFactory,
    private val invitationWorkerFactory: InvitationWorkerFactory,
    private val teamSyncWorkerFactory: TeamSyncWorkerFactory
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        // Thử tạo worker từ TaskWorkerFactory
        val taskWorker = taskWorkerFactory.createWorker(appContext, workerClassName, workerParameters)
        if (taskWorker != null) {
            return taskWorker
        }

        // Thử tạo worker từ InvitationWorkerFactory
        val invitationWorker = invitationWorkerFactory.createWorker(appContext, workerClassName, workerParameters)
        if (invitationWorker != null) {
            return invitationWorker
        }

        // Không có factory nào có thể tạo worker
        return null
    }
}
