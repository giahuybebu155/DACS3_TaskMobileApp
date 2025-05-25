package com.example.taskapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskapplication.data.local.entity.TeamRoleHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for team role history operations
 */
@Dao
interface TeamRoleHistoryDao {
    
    /**
     * Insert a new role history entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoleHistory(roleHistory: TeamRoleHistoryEntity): Long
    
    /**
     * Get role history for a team
     */
    @Query("SELECT * FROM team_role_history WHERE teamId = :teamId ORDER BY timestamp DESC")
    fun getRoleHistoryForTeam(teamId: String): Flow<List<TeamRoleHistoryEntity>>
    
    /**
     * Get role history for a user in a team
     */
    @Query("SELECT * FROM team_role_history WHERE teamId = :teamId AND userId = :userId ORDER BY timestamp DESC")
    fun getRoleHistoryForUser(teamId: String, userId: String): Flow<List<TeamRoleHistoryEntity>>
    
    /**
     * Get role history entries that need to be synced
     */
    @Query("SELECT * FROM team_role_history WHERE syncStatus = 'pending'")
    fun getPendingSyncRoleHistory(): Flow<List<TeamRoleHistoryEntity>>
    
    /**
     * Update sync status for a role history entry
     */
    @Query("UPDATE team_role_history SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, syncStatus: String)
    
    /**
     * Delete role history entries older than a certain time
     */
    @Query("DELETE FROM team_role_history WHERE timestamp < :timestamp")
    suspend fun deleteOldRoleHistory(timestamp: Long)
}
