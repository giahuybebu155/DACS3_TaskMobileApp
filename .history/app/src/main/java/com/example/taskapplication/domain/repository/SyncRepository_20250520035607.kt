package com.example.taskapplication.domain.repository

interface SyncRepository {
    suspend fun initialSync(): Result<Unit>
    suspend fun quickSync(): Result<Unit>
    suspend fun pushChanges(): Result<Unit>
    suspend fun getLastSyncTimestamp(): Long?
    suspend fun updateLastSyncTimestamp(timestamp: Long)
    suspend fun schedulePeriodicSync(intervalMinutes: Int)
    suspend fun cancelPeriodicSync()
    suspend fun hasPendingChanges(): Boolean
}