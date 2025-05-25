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
            println("🔍 [DEBUG UI] Bắt đầu tải danh sách nhóm")

            try {
                val userId = dataStoreManager.getCurrentUserId()
                println("🔍 [DEBUG UI] UserId hiện tại: $userId")

                if (userId != null) {
                    println("🔍 [DEBUG UI] Đang lấy danh sách nhóm cho userId: $userId")
                    teamRepository.getTeamsForUser(userId)
                        .catch { e ->
                            println("❌ [DEBUG UI] Lỗi khi lấy danh sách nhóm: ${e.message}")
                            _teamsState.value = TeamsState.Error(e.message ?: "Unknown error")
                        }
                        .collect { teamsList ->
                            println("✅ [DEBUG UI] Nhận được ${teamsList.size} nhóm từ repository")
                            println("✅ [DEBUG UI] Danh sách nhóm: ${teamsList.map { it.name }}")

                            _teamsState.value = if (teamsList.isEmpty()) {
                                println("ℹ️ [DEBUG UI] Danh sách nhóm trống, hiển thị trạng thái Empty")
                                TeamsState.Empty
                            } else {
                                println("✅ [DEBUG UI] Hiển thị ${teamsList.size} nhóm")
                                TeamsState.Success(teamsList)
                            }
                        }
                } else {
                    println("❌ [DEBUG UI] Người dùng chưa đăng nhập")
                    _teamsState.value = TeamsState.Error("User not logged in")
                }
            } catch (e: Exception) {
                println("❌ [DEBUG UI] Lỗi không xác định: ${e.message}")
                e.printStackTrace()
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

    /**
     * Refresh teams from server
     */
    fun refreshTeams() {
        viewModelScope.launch {
            _teamsState.value = TeamsState.Loading

            try {
                // Đồng bộ nhóm từ server
                teamRepository.syncTeams()

                // Đồng bộ thành viên nhóm
                try {
                    teamRepository.syncTeamMembers()
                } catch (e: Exception) {
                    println("Lỗi khi đồng bộ thành viên nhóm từ server: ${e.message}")
                }

                // Tải lại danh sách nhóm
                loadTeams()
                loadPendingInvitationsCount()
            } catch (e: Exception) {
                _teamsState.value = TeamsState.Error(e.message ?: "Không thể làm mới danh sách nhóm")
            }
        }
    }

    /**
     * Force reload teams from server by clearing local data
     */
    fun forceReloadTeams() {
        viewModelScope.launch {
            _teamsState.value = TeamsState.Loading

            try {
                // Xóa dữ liệu cục bộ và tải lại từ server
                teamRepository.clearLocalTeamsAndMembers()

                // Đồng bộ nhóm từ server
                teamRepository.syncTeams()

                // Đồng bộ thành viên nhóm
                try {
                    teamRepository.syncTeamMembers()
                } catch (e: Exception) {
                    println("Lỗi khi đồng bộ thành viên nhóm từ server: ${e.message}")
                }

                // Tải lại danh sách nhóm
                loadTeams()
                loadPendingInvitationsCount()
            } catch (e: Exception) {
                _teamsState.value = TeamsState.Error(e.message ?: "Không thể tải lại danh sách nhóm từ server")
            }
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
