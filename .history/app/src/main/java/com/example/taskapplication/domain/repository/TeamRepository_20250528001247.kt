package com.example.taskapplication.domain.repository

import com.example.taskapplication.domain.model.Team
import com.example.taskapplication.domain.model.TeamMember
import com.example.taskapplication.domain.model.TeamRole
import com.example.taskapplication.domain.model.TeamRoleHistory
import kotlinx.coroutines.flow.Flow

interface TeamRepository {
    fun getAllTeams(): Flow<List<Team>>
    fun getTeamsForUser(userId: String): Flow<List<Team>>
    fun getTeamsByOwner(ownerId: String): Flow<List<Team>>
    fun getTeamById(teamId: String): Flow<Team?>
    suspend fun createTeam(team: Team): Result<Team>
    suspend fun updateTeam(team: Team): Result<Team>
    suspend fun deleteTeam(teamId: String): Result<Unit>
    fun getTeamMembers(teamId: String): Flow<List<TeamMember>>
    suspend fun inviteUserToTeam(teamId: String, userEmail: String): Result<TeamMember>
    suspend fun removeUserFromTeam(teamId: String, userId: String): Result<Unit>
    suspend fun changeUserRole(teamId: String, userId: String, newRole: String): Result<TeamMember>
    fun isUserAdminOfTeam(teamId: String, userId: String): Flow<Boolean>
    fun isUserMemberOfTeam(teamId: String, userId: String): Flow<Boolean>
    suspend fun syncTeams(): Result<Unit>
    suspend fun syncTeamMembers(): Result<Unit>
    suspend fun clearLocalTeamsAndMembers(): Result<Unit>

    // Role history methods
    fun getRoleHistoryForTeam(teamId: String): Flow<List<TeamRoleHistory>>
    fun getRoleHistoryForUser(teamId: String, userId: String): Flow<List<TeamRoleHistory>>
    suspend fun addRoleHistory(roleHistory: TeamRoleHistory): Result<TeamRoleHistory>

    // Role permissions methods
    fun hasPermission(teamId: String, userId: String, permission: String): Flow<Boolean>
    fun getUserRole(teamId: String, userId: String): Flow<TeamRole>
}