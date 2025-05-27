package com.example.taskapplication.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.taskapplication.data.repository.PersonalTaskRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PersonalTaskSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val personalTaskRepository: PersonalTaskRepositoryImpl
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            personalTaskRepository.syncTasks()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
} 