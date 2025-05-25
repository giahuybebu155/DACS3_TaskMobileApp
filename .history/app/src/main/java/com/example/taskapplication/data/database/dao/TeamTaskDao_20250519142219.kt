package com.example.taskapplication.data.database.dao

import androidx.room.*
import com.example.taskapplication.data.database.entities.TeamTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamTaskDao {
    @Query("SELECT * FROM team_tasks ORDER BY dueDate ASC")
    fun getAllTeamTasks(): Flow<List<TeamTaskEntity>>

    @Query("SELECT * FROM team_tasks WHERE teamId = :teamId ORDER BY dueDate ASC")
    fun getTasksByTeam(teamId: String): Flow<List<TeamTaskEntity>>

    @Query("SELECT * FROM team_tasks WHERE assignedUserId = :userId ORDER BY dueDate ASC")
    fun getTasksAssignedToUser(userId: String): Flow<List<TeamTaskEntity>>

    @Query("SELECT * FROM team_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TeamTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TeamTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TeamTaskEntity>)

    @Update
    suspend fun updateTask(task: TeamTaskEntity)

    @Delete
    suspend fun deleteTask(task: TeamTaskEntity)

    @Query("UPDATE team_tasks SET syncStatus = 'pending_delete', lastModified = :timestamp WHERE id = :taskId")
    suspend fun markTaskForDeletion(taskId: String, timestamp: Long)

    @Query("DELETE FROM team_tasks WHERE id = :taskId AND syncStatus = 'pending_create'")
    suspend fun deleteLocalOnlyTask(taskId: String)

    @Query("DELETE FROM team_tasks WHERE id = :taskId")
    suspend fun deleteSyncedTask(taskId: String)

    @Query("SELECT * FROM team_tasks WHERE syncStatus != 'synced'")
    suspend fun getPendingSyncTasks(): List<TeamTaskEntity>

    @Query("SELECT * FROM team_tasks WHERE teamId = :teamId AND syncStatus != 'synced'")
    suspend fun getPendingSyncTasksByTeam(teamId: String): List<TeamTaskEntity>

    @Query("DELETE FROM team_tasks")
    suspend fun deleteAllTasks()

    @Query("UPDATE team_tasks SET syncStatus = :status WHERE id = :taskId")
    suspend fun updateSyncStatus(taskId: String, status: String)

    @Query("SELECT * FROM team_tasks WHERE serverId = :serverId")
    suspend fun getTaskByServerId(serverId: String): TeamTaskEntity?
}