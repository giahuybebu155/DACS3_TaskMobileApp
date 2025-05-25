package com.example.taskapplication.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.repository.TeamInvitationRepository
import com.example.taskapplication.ui.team.detail.InvitationsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel cho màn hình lời mời của người dùng
 */
@HiltViewModel
class UserInvitationsViewModel @Inject constructor(
    private val teamInvitationRepository: TeamInvitationRepository
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
    
    init {
        loadInvitations()
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
}
