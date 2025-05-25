package com.example.taskapplication.domain.repository

interface SyncRepository {
    suspend fun initialSync(): Result<Unit>
    suspend fun quickSync(): Result<Unit>
    suspend fun pushChanges(): Result<Unit>
    suspend fun getLastSyncTimestamp(): Long?
    suspend fun updateLastSyncTimestamp(timestamp: Long)
    suspend fun schedulePeriodicSync()
    suspend fun cancelPeriodicSync()
    suspend fun hasPendingChanges(): Boolean
    suspend fun syncSelectedItems(tasks: List<com.example.taskapplication.data.database.entities.PersonalTaskEntity>, subtasks: List<com.example.taskapplication.data.database.entities.SubtaskEntity>): Result<Unit>
}