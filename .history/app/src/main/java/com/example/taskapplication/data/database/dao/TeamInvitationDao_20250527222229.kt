package com.example.taskapplication.data.database.dao

import androidx.room.*
import com.example.taskapplication.data.database.entities.TeamInvitationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for team invitations
 */
@Dao
interface TeamInvitationDao {
    /**
     * Get all invitations for a team
     */
    @Query("SELECT * FROM team_invitations WHERE teamId = :teamId AND syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    fun getTeamInvitations(teamId: String): Flow<List<TeamInvitationEntity>>

    /**
     * Get all invitations for a team with specific status
     */
    @Query("SELECT * FROM team_invitations WHERE teamId = :teamId AND status = :status AND syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    fun getTeamInvitationsByStatus(teamId: String, status: String): Flow<List<TeamInvitationEntity>>

    /**
     * Get all invitations for a user by email
     */
    @Query("SELECT * FROM team_invitations WHERE email = :email AND syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    fun getUserInvitationsByEmail(email: String): Flow<List<TeamInvitationEntity>>

    /**
     * Get all pending invitations for a user by email
     */
    @Query("SELECT * FROM team_invitations WHERE email = :email AND status = 'pending' AND syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    fun getPendingUserInvitationsByEmail(email: String): Flow<List<TeamInvitationEntity>>

    /**
     * Get invitation by ID
     */
    @Query("SELECT * FROM team_invitations WHERE id = :id")
    suspend fun getInvitationById(id: String): TeamInvitationEntity?

    /**
     * Get invitation by token
     */
    @Query("SELECT * FROM team_invitations WHERE token = :token")
    suspend fun getInvitationByToken(token: String): TeamInvitationEntity?

    /**
     * Insert a new invitation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: TeamInvitationEntity)

    /**
     * Update an invitation
     */
    @Update
    suspend fun updateInvitation(invitation: TeamInvitationEntity)

    /**
     * Delete an invitation
     */
    @Delete
    suspend fun deleteInvitation(invitation: TeamInvitationEntity)

    /**
     * Get all pending sync invitations
     */
    @Query("SELECT * FROM team_invitations WHERE syncStatus IN ('pending_create', 'pending_update', 'pending_delete')")
    suspend fun getPendingSyncInvitations(): List<TeamInvitationEntity>

    /**
     * Get invitation by server ID
     */
    @Query("SELECT * FROM team_invitations WHERE serverId = :serverId")
    suspend fun getInvitationByServerId(serverId: String): TeamInvitationEntity?

    /**
     * Update invitation status
     */
    @Query("UPDATE team_invitations SET status = :status, lastModified = :timestamp, syncStatus = 'pending_update' WHERE id = :invitationId")
    suspend fun updateInvitationStatus(invitationId: String, status: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Count invitations by status for a team
     */
    @Query("SELECT COUNT(*) FROM team_invitations WHERE teamId = :teamId AND status = :status AND syncStatus != 'pending_delete'")
    suspend fun countInvitationsByStatus(teamId: String, status: String): Int

    /**
     * Get expired invitations
     */
    @Query("SELECT * FROM team_invitations WHERE status = 'pending' AND expiresAt < :currentTime AND syncStatus != 'pending_delete'")
    suspend fun getExpiredInvitations(currentTime: Long = System.currentTimeMillis()): List<TeamInvitationEntity>

    /**
     * Get all invitations for a user by email (non-Flow version)
     */
    @Query("SELECT * FROM team_invitations WHERE email = :email AND syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    suspend fun getAllInvitationsForEmail(email: String): List<TeamInvitationEntity>
}
