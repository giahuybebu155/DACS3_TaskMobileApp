package com.example.taskapplication.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.data.websocket.ChatEvent
import com.example.taskapplication.data.websocket.ConnectionState
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import com.example.taskapplication.ui.team.detail.InvitationsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel cho màn hình lời mời của người dùng
 */
@HiltViewModel
class UserInvitationsViewModel @Inject constructor(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val webSocketManager: WebSocketManager,
    private val dataStoreManager: com.example.taskapplication.data.util.DataStoreManager
) : ViewModel() {

    // Danh sách lời mời
    private val _invitations = MutableStateFlow<List<TeamInvitation>>(emptyList())
    val invitations: StateFlow<List<TeamInvitation>> = _invitations

    // Trạng thái lời mời
    private val _invitationsState = MutableStateFlow<InvitationsState>(InvitationsState.Loading)
    val invitationsState: StateFlow<InvitationsState> = _invitationsState

    // Kết quả hành động
    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult

    // Trạng thái kết nối WebSocket
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    init {
        loadInvitations()

        // Lắng nghe sự kiện WebSocket
        viewModelScope.launch {
            webSocketManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        // Lắng nghe sự kiện lời mời
        viewModelScope.launch {
            webSocketManager.events.collectLatest { event ->
                when (event) {
                    is ChatEvent.TeamInvitation -> {
                        // Refresh danh sách lời mời khi có lời mời mới
                        refreshInvitations()
                    }
                    is ChatEvent.TeamInvitationAccepted,
                    is ChatEvent.TeamInvitationRejected,
                    is ChatEvent.TeamInvitationCancelled -> {
                        // Refresh danh sách lời mời khi có thay đổi
                        refreshInvitations()
                    }
                    else -> {
                        // Không xử lý các sự kiện khác
                    }
                }
            }
        }
    }

    /**
     * Tải danh sách lời mời
     */
    private fun loadInvitations() {
        viewModelScope.launch {
            _invitationsState.value = InvitationsState.Loading

            try {
                teamInvitationRepository.getUserInvitations()
                    .catch { e ->
                        _invitationsState.value = InvitationsState.Error(e.message ?: "Failed to load invitations")
                        _invitations.value = emptyList()
                    }
                    .collect { allInvitations ->
                        // Lọc lấy các lời mời đang chờ
                        val pendingInvitations = allInvitations.filter { it.status == "pending" }
                        _invitations.value = pendingInvitations

                        _invitationsState.value = if (pendingInvitations.isEmpty()) {
                            InvitationsState.Empty
                        } else {
                            InvitationsState.Success
                        }
                    }
            } catch (e: Exception) {
                _invitationsState.value = InvitationsState.Error(e.message ?: "Failed to load invitations")
                _invitations.value = emptyList()
            }
        }
    }

    /**
     * Làm mới danh sách lời mời
     */
    fun refreshInvitations() {
        loadInvitations()
    }

    /**
     * Chấp nhận lời mời
     */
    fun acceptInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                teamInvitationRepository.updateInvitationStatus(invitationId, "accepted")
                    .onSuccess {
                        _actionResult.value = "Đã chấp nhận lời mời"
                        refreshInvitations()
                    }
                    .onFailure { e ->
                        _actionResult.value = "Lỗi: ${e.message}"
                    }
            } catch (e: Exception) {
                _actionResult.value = "Lỗi: ${e.message}"
            }
        }
    }

    /**
     * Từ chối lời mời
     */
    fun rejectInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                teamInvitationRepository.updateInvitationStatus(invitationId, "rejected")
                    .onSuccess {
                        _actionResult.value = "Đã từ chối lời mời"
                        refreshInvitations()
                    }
                    .onFailure { e ->
                        _actionResult.value = "Lỗi: ${e.message}"
                    }
            } catch (e: Exception) {
                _actionResult.value = "Lỗi: ${e.message}"
            }
        }
    }

    /**
     * Xóa kết quả hành động
     */
    fun clearActionResult() {
        _actionResult.value = null
    }

    /**
     * Kết nối lại WebSocket
     */
    fun reconnectWebSocket() {
        viewModelScope.launch {
            try {
                // Lấy token và teamId từ DataStore
                val authToken = dataStoreManager.getAuthToken()
                val currentTeamId = dataStoreManager.getCurrentTeamId()

                if (authToken != null && currentTeamId != null) {
                    webSocketManager.connect(authToken, currentTeamId)
                }
            } catch (e: Exception) {
                _actionResult.value = "Lỗi kết nối: ${e.message}"
            }
        }
    }
}
