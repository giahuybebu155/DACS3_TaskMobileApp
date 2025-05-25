package com.example.taskapplication.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val connectionChecker: ConnectionChecker,
    private val dataStoreManager: DataStoreManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val SYNC_WORK_NAME = "periodic_sync"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )

            Log.d(TAG, "Periodic sync scheduled every $intervalMinutes minutes")
        }

        fun scheduleOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "one_time_sync",
                    ExistingWorkPolicy.REPLACE,
                    request
                )

            Log.d(TAG, "One-time sync scheduled")
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
            Log.d(TAG, "Periodic sync cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync work")

        // Check if we have internet connection
        if (!connectionChecker.isNetworkAvailable()) {
            Log.d(TAG, "No network connection, retrying later")
            return@withContext Result.retry()
        }

        try {
            // First push local changes
            Log.d(TAG, "Pushing local changes")
            val pushResult = syncRepository.pushChanges()

            if (pushResult.isFailure) {
                val exception = pushResult.exceptionOrNull()
                Log.e(TAG, "Error pushing changes", exception)
                return@withContext if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }

            // Then fetch new changes
            Log.d(TAG, "Fetching remote changes")
            val syncResult = syncRepository.quickSync()

            if (syncResult.isFailure) {
                val exception = syncResult.exceptionOrNull()
                Log.e(TAG, "Error syncing", exception)
                return@withContext if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }

            // Update last sync time
            dataStoreManager.saveLastSyncTimestamp(System.currentTimeMillis())
            Log.d(TAG, "Sync completed successfully")

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}