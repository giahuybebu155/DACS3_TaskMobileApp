package com.example.taskapplication.data.database.dao

import androidx.room.*
import com.example.taskapplication.data.database.entities.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamMemberDao {
    @Query("SELECT * FROM team_members WHERE teamId = :teamId")
    fun getTeamMembers(teamId: String): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members WHERE userId = :userId")
    fun getUserTeamMemberships(userId: String): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members WHERE teamId = :teamId AND userId = :userId")
    suspend fun getTeamMemberSync(teamId: String, userId: String): TeamMemberEntity?

    @Query("SELECT * FROM team_members WHERE serverId = :serverId")
    suspend fun getTeamMemberByServerIdSync(serverId: String): TeamMemberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE teamId = :teamId AND userId = :userId)")
    suspend fun isUserMemberOfTeam(teamId: String, userId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE teamId = :teamId AND userId = :userId)")
    fun isUserMemberOfTeamFlow(teamId: String, userId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE teamId = :teamId AND userId = :userId AND role = 'manager')")
    suspend fun isUserAdminOfTeam(teamId: String, userId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE teamId = :teamId AND userId = :userId AND role = 'manager')")
    fun isUserAdminOfTeamFlow(teamId: String, userId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMember(member: TeamMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMembers(members: List<TeamMemberEntity>)

    @Update
    suspend fun updateTeamMember(member: TeamMemberEntity)

    @Delete
    suspend fun deleteTeamMember(member: TeamMemberEntity)

    @Query("UPDATE team_members SET syncStatus = 'pending_delete', lastModified = :timestamp WHERE id = :memberId")
    suspend fun markTeamMemberForDeletion(memberId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE team_members SET syncStatus = 'pending_delete', lastModified = :timestamp WHERE teamId = :teamId AND userId = :userId")
    suspend fun markTeamMemberForDeletion(teamId: String, userId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM team_members WHERE teamId = :teamId AND role = 'admin'")
    suspend fun getAdminCountForTeam(teamId: String): Int

    @Query("SELECT COUNT(*) FROM team_members WHERE teamId = :teamId AND role = 'admin'")
    suspend fun countAdminsInTeam(teamId: String): Int

    @Query("SELECT role FROM team_members WHERE teamId = :teamId AND userId = :userId")
    fun getUserRoleInTeam(teamId: String, userId: String): Flow<String?>

    @Query("DELETE FROM team_members WHERE teamId = :teamId AND userId = :userId")
    suspend fun deleteTeamMember(teamId: String, userId: String)

    @Query("DELETE FROM team_members WHERE teamId = :teamId")
    suspend fun deleteTeamMembers(teamId: String)

    @Query("SELECT * FROM team_members WHERE syncStatus != 'synced'")
    suspend fun getPendingSyncTeamMembers(): List<TeamMemberEntity>

    @Query("DELETE FROM team_members WHERE id = :memberId AND syncStatus = 'pending_create'")
    suspend fun deleteLocalOnlyTeamMember(memberId: String)

    @Query("DELETE FROM team_members WHERE syncStatus = 'pending_delete' AND serverId = :serverId")
    suspend fun deleteServerConfirmedTeamMember(serverId: String)

    @Query("UPDATE team_members SET serverId = :serverId, syncStatus = 'synced' WHERE id = :memberId")
    suspend fun updateMemberServerId(memberId: String, serverId: String)

    @Query("UPDATE team_members SET syncStatus = 'synced' WHERE id = :memberId")
    suspend fun markMemberAsSynced(memberId: String)

    @Query("SELECT * FROM team_members WHERE syncStatus = 'pending_create' OR syncStatus = 'pending_update'")
    suspend fun getPendingMembers(): List<TeamMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: TeamMemberEntity)

    @Update
    suspend fun updateMember(member: TeamMemberEntity)

    @Query("DELETE FROM team_members WHERE id = :memberId")
    suspend fun deleteMember(memberId: String)

    @Query("DELETE FROM team_members")
    suspend fun deleteAllTeamMembers()

    @Query("SELECT * FROM team_members WHERE serverId = :serverId")
    suspend fun getTeamMemberByServerId(serverId: String): TeamMemberEntity?


}
