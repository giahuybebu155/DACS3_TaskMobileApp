package com.example.taskapplication.data.database.dao

import androidx.room.*
import com.example.taskapplication.data.database.entities.PersonalTaskEntity
import com.example.taskapplication.domain.model.PersonalTask
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalTaskDao {
    @Query("SELECT * FROM personal_tasks ORDER BY `order` ASC")
    fun getAllTasks(): Flow<List<PersonalTaskEntity>>

    @Query("SELECT * FROM personal_tasks ORDER BY `order` ASC")
    suspend fun getAllTasksSync(): List<PersonalTaskEntity>

    @Query("SELECT * FROM personal_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): PersonalTaskEntity?

    @Query("SELECT * FROM personal_tasks WHERE syncStatus = :status")
    suspend fun getTasksByStatus(status: String): List<PersonalTaskEntity>

    @Query("SELECT * FROM personal_tasks WHERE syncStatus != :syncedStatus")
    suspend fun getPendingSyncTasks(syncedStatus: String = PersonalTask.SyncStatus.SYNCED): List<PersonalTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: PersonalTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<PersonalTaskEntity>) // For batch insert/update from sync

    @Update
    suspend fun updateTask(task: PersonalTaskEntity)

    @Delete
    suspend fun deleteTask(task: PersonalTaskEntity)

    // Delete task by ID
    @Query("DELETE FROM personal_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    // Mark for deletion when user deletes a synced task
    @Query("UPDATE personal_tasks SET syncStatus = :deletedStatus, lastModified = :timestamp WHERE id = :taskId AND serverId IS NOT NULL")
    suspend fun markTaskForDeletion(taskId: String, timestamp: Long, deletedStatus: String = PersonalTask.SyncStatus.DELETED)

    // Delete a task that was created locally and never synced
    @Query("DELETE FROM personal_tasks WHERE id = :taskId AND syncStatus = :createdStatus")
    suspend fun deleteLocalOnlyTask(taskId: String, createdStatus: String = PersonalTask.SyncStatus.CREATED)

    // Delete task from local DB after server confirms deletion during sync
    @Query("DELETE FROM personal_tasks WHERE id = :taskId")
    suspend fun deleteSyncedTask(taskId: String)

    // Clear tasks marked for deletion (e.g., after successful sync push)
    @Query("DELETE FROM personal_tasks WHERE syncStatus = :deletedStatus")
    suspend fun clearDeletedTasks(deletedStatus: String = PersonalTask.SyncStatus.DELETED)

    // Get tasks by priority
    @Query("SELECT * FROM personal_tasks WHERE priority = :priority ORDER BY `order` ASC")
    fun getTasksByPriority(priority: String): Flow<List<PersonalTaskEntity>>

    // Get tasks by status
    @Query("SELECT * FROM personal_tasks WHERE status = :status ORDER BY `order` ASC")
    fun getTasksByTaskStatus(status: String): Flow<List<PersonalTaskEntity>>

    // Get tasks by due date range
    @Query("SELECT * FROM personal_tasks WHERE dueDate BETWEEN :startDate AND :endDate ORDER BY dueDate ASC, `order` ASC")
    fun getTasksByDueDateRange(startDate: Long, endDate: Long): Flow<List<PersonalTaskEntity>>

    // Get overdue tasks
    @Query("SELECT * FROM personal_tasks WHERE dueDate < :currentDate AND status != 'completed' ORDER BY dueDate ASC, `order` ASC")
    fun getOverdueTasks(currentDate: Long): Flow<List<PersonalTaskEntity>>

    // Get tasks due today
    @Query("SELECT * FROM personal_tasks WHERE dueDate BETWEEN :startOfDay AND :endOfDay ORDER BY `order` ASC")
    fun getTasksDueToday(startOfDay: Long, endOfDay: Long): Flow<List<PersonalTaskEntity>>

    // Get tasks due this week
    @Query("SELECT * FROM personal_tasks WHERE dueDate BETWEEN :startOfWeek AND :endOfWeek ORDER BY dueDate ASC, `order` ASC")
    fun getTasksDueThisWeek(startOfWeek: Long, endOfWeek: Long): Flow<List<PersonalTaskEntity>>

    // Search tasks by title or description
    @Query("SELECT * FROM personal_tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY `order` ASC")
    fun searchTasks(query: String): Flow<List<PersonalTaskEntity>>

    // Get max order
    @Query("SELECT MAX(`order`) FROM personal_tasks")
    suspend fun getMaxOrder(): Int?

    // Update task order
    @Query("UPDATE personal_tasks SET `order` = :newOrder, syncStatus = :updatedStatus, lastModified = :timestamp WHERE id = :taskId")
    suspend fun updateTaskOrder(taskId: String, newOrder: Int, updatedStatus: String, timestamp: Long)

    // Đếm số lượng công việc đang chờ đồng bộ
    @Query("SELECT COUNT(*) FROM personal_tasks WHERE syncStatus != :syncedStatus")
    fun countPendingSyncTasks(syncedStatus: String = PersonalTask.SyncStatus.SYNCED): Flow<Int>

    @Query("DELETE FROM personal_tasks")
    suspend fun deleteAllTasks()

    @Query("UPDATE personal_tasks SET syncStatus = :status WHERE id = :taskId")
    suspend fun updateSyncStatus(taskId: String, status: String)
}