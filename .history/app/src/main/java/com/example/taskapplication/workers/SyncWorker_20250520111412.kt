package com.example.taskapplication.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.repository.SyncRepository
import com.example.taskapplication.data.sync.SyncManager
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
    private val syncManager: SyncManager,
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
                    ExistingPeriodicWorkPolicy.UPDATE,
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
        Log.d(TAG, "Bắt đầu công việc đồng bộ")

        // Kiểm tra kết nối mạng
        if (!connectionChecker.isNetworkAvailable()) {
            Log.d(TAG, "Không có kết nối mạng, thử lại sau")
            return@withContext Result.retry()
        }

        try {
            // Sử dụng cả SyncRepository và SyncManager để đảm bảo đồng bộ đầy đủ

            // 1. Đồng bộ dữ liệu cơ bản thông qua SyncRepository
            Log.d(TAG, "Đẩy các thay đổi cục bộ lên server")
            val pushResult = syncRepository.pushChanges()

            if (pushResult.isFailure) {
                val exception = pushResult.exceptionOrNull()
                Log.e(TAG, "Lỗi khi đẩy thay đổi lên server", exception)
                // Tiếp tục thực hiện đồng bộ qua SyncManager ngay cả khi SyncRepository thất bại
            }

            // 2. Đồng bộ dữ liệu nhóm thông qua SyncManager
            Log.d(TAG, "Đồng bộ dữ liệu nhóm thông qua SyncManager")
            val syncResult = syncManager.syncAll()

            if (syncResult.isFailure) {
                val exception = syncResult.exceptionOrNull()
                Log.e(TAG, "Lỗi khi đồng bộ dữ liệu nhóm", exception)
                return@withContext if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }

            // 3. Lấy các thay đổi từ server thông qua SyncRepository
            Log.d(TAG, "Lấy các thay đổi từ server")
            val quickSyncResult = syncRepository.quickSync()

            if (quickSyncResult.isFailure) {
                val exception = quickSyncResult.exceptionOrNull()
                Log.e(TAG, "Lỗi khi lấy thay đổi từ server", exception)
                // Không thất bại nếu chỉ quickSync thất bại, vì đã đồng bộ qua SyncManager
            }

            // Cập nhật thời gian đồng bộ cuối cùng
            dataStoreManager.saveLastSyncTimestamp(System.currentTimeMillis())
            Log.d(TAG, "Đồng bộ hoàn tất thành công")

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi không xác định khi đồng bộ", e)
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}