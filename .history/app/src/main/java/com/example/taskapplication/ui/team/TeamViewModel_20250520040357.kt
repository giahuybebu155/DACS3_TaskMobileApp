package com.example.taskapplication.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.Team
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import com.example.taskapplication.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Teams screen
 * Manages the state and data for displaying teams
 */
@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    // State for teams list
    private val _teamsState = MutableStateFlow<TeamsState>(TeamsState.Loading)
    val teamsState: StateFlow<TeamsState> = _teamsState

    // State for showing create team dialog
    private val _showCreateTeamDialog = MutableStateFlow(false)
    val showCreateTeamDialog: StateFlow<Boolean> = _showCreateTeamDialog

    // State for create team operation
    private val _createTeamState = MutableStateFlow<CreateTeamState>(CreateTeamState.Idle)
    val createTeamState: StateFlow<CreateTeamState> = _createTeamState

    // Count of pending invitations
    private val _pendingInvitationsCount = MutableStateFlow(0)
    val pendingInvitationsCount: StateFlow<Int> = _pendingInvitationsCount

    init {
        // Đồng bộ nhóm từ server trước khi tải danh sách nhóm
        viewModelScope.launch {
            try {
                // Đồng bộ nhóm từ server
                teamRepository.syncTeams()
                println("Đã đồng bộ nhóm từ server khi khởi tạo ViewModel")

                // Đồng bộ thành viên nhóm
                try {
                    teamRepository.syncTeamMembers()
                    println("Đã đồng bộ thành viên nhóm từ server khi khởi tạo ViewModel")
                } catch (e: Exception) {
                    println("Lỗi khi đồng bộ thành viên nhóm từ server: ${e.message}")
                }
            } catch (e: Exception) {
                println("Lỗi khi đồng bộ nhóm từ server: ${e.message}")
            }

            // Sau khi đồng bộ, tải danh sách nhóm và lời mời
            loadTeams()
            loadPendingInvitationsCount()
        }
    }

    /**
     * Load teams for the current user
     */
    fun loadTeams() {
        viewModelScope.launch {
            _teamsState.value = TeamsState.Loading

            try {
                val userId = dataStoreManager.getCurrentUserId()
                if (userId != null) {
                    teamRepository.getTeamsForUser(userId)
                        .catch { e ->
                            _teamsState.value = TeamsState.Error(e.message ?: "Unknown error")
                        }
                        .collect { teamsList ->
                            _teamsState.value = if (teamsList.isEmpty()) {
                                TeamsState.Empty
                            } else {
                                TeamsState.Success(teamsList)
                            }
                        }
                } else {
                    _teamsState.value = TeamsState.Error("User not logged in")
                }
            } catch (e: Exception) {
                _teamsState.value = TeamsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Show the create team dialog
     */
    fun showCreateTeamDialog() {
        _showCreateTeamDialog.value = true
    }

    /**
     * Hide the create team dialog
     */
    fun hideCreateTeamDialog() {
        _showCreateTeamDialog.value = false
        // Reset create team state
        _createTeamState.value = CreateTeamState.Idle
    }

    /**
     * Create a new team
     */
    fun createTeam(name: String, description: String?) {
        viewModelScope.launch {
            _createTeamState.value = CreateTeamState.Loading

            try {
                val userId = dataStoreManager.getCurrentUserId()
                if (userId != null) {
                    val newTeam = Team(
                        id = "",  // Repository will generate ID
                        name = name,
                        description = description,
                        ownerId = userId,
                        createdBy = userId
                    )

                    teamRepository.createTeam(newTeam)
                        .onSuccess { createdTeam ->
                            // Đồng bộ nhóm lên server ngay sau khi tạo
                            viewModelScope.launch {
                                try {
                                    teamRepository.syncTeams()
                                    println("Đã đồng bộ nhóm lên server sau khi tạo")
                                } catch (e: Exception) {
                                    println("Lỗi khi đồng bộ nhóm lên server: ${e.message}")
                                }
                            }

                            _createTeamState.value = CreateTeamState.Success
                            hideCreateTeamDialog()
                            loadTeams()
                        }
                        .onFailure { e ->
                            _createTeamState.value = CreateTeamState.Error(e.message ?: "Failed to create team")
                        }
                } else {
                    _createTeamState.value = CreateTeamState.Error("User not logged in")
                }
            } catch (e: Exception) {
                _createTeamState.value = CreateTeamState.Error(e.message ?: "Failed to create team")
            }
        }
    }

    /**
     * Load pending invitations count
     */
    private fun loadPendingInvitationsCount() {
        viewModelScope.launch {
            try {
                teamInvitationRepository.getUserInvitations()
                    .catch { e ->
                        // Just log error, don't update UI state
                        println("Error loading invitations: ${e.message}")
                    }
                    .collect { invitations ->
                        val pendingCount = invitations.count { it.status == "pending" }
                        _pendingInvitationsCount.value = pendingCount
                    }
            } catch (e: Exception) {
                // Just log error, don't update UI state
                println("Error loading invitations: ${e.message}")
            }
        }
    }

    /**
     * Reset error state
     */
    fun resetError() {
        if (_teamsState.value is TeamsState.Error) {
            _teamsState.value = TeamsState.Loading
            loadTeams()
        }

        if (_createTeamState.value is CreateTeamState.Error) {
            _createTeamState.value = CreateTeamState.Idle
        }
    }
}

/**
 * State for teams list
 */
sealed class TeamsState {
    object Loading : TeamsState()
    object Empty : TeamsState()
    data class Success(val teams: List<Team>) : TeamsState()
    data class Error(val message: String) : TeamsState()
}

/**
 * State for create team operation
 */
sealed class CreateTeamState {
    object Idle : CreateTeamState()
    object Loading : CreateTeamState()
    object Success : CreateTeamState()
    data class Error(val message: String) : CreateTeamState()
}
