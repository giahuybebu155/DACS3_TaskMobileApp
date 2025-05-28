package com.example.taskapplication.ui.team

import android.util.Log
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

    companion object {
        private const val TAG = "UserInvitationsViewModel"
    }

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

    // Rate limiting cho sync
    private var lastSyncTime = 0L
    private val SYNC_COOLDOWN_MS = 5000L // 5 giây cooldown

    init {
        loadInvitations()

        // Đồng bộ lời mời khi khởi tạo - CHỈ MỘT LẦN
        syncInvitations()

        // Lắng nghe sự kiện WebSocket
        viewModelScope.launch {
            webSocketManager.connectionState.collect { state ->
                _connectionState.value = state

                // KHÔNG tự động sync khi WebSocket kết nối để tránh spam
                // Chỉ sync khi có sự kiện cụ thể
            }
        }

        // Lắng nghe sự kiện lời mời
        viewModelScope.launch {
            webSocketManager.events.collectLatest { event ->
                when (event) {
                    is ChatEvent.TeamInvitation -> {
                        // Chỉ load local data, KHÔNG sync để tránh spam API
                        Log.d(TAG, "📨 [WS_EVENT] New invitation received - loading local data")
                        loadInvitations()
                    }
                    is ChatEvent.TeamInvitationAccepted,
                    is ChatEvent.TeamInvitationRejected,
                    is ChatEvent.TeamInvitationCancelled -> {
                        // Chỉ load local data, KHÔNG sync để tránh spam API
                        Log.d(TAG, "📝 [WS_EVENT] Invitation status changed - loading local data")
                        loadInvitations()
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
     * Kết nối lại WebSocket (User-based connection - RECOMMENDED)
     */
    fun reconnectWebSocket() {
        viewModelScope.launch {
            try {
                // Chỉ cần token, không cần teamId
                val authToken = dataStoreManager.getAuthToken()

                if (authToken != null) {
                    Log.d(TAG, "🔄 [RECONNECT] Connecting WebSocket with user-based connection")
                    webSocketManager.connect(authToken) // Use user-based connection
                } else {
                    Log.e(TAG, "❌ [RECONNECT] No auth token available")
                    _actionResult.value = "Lỗi kết nối: Không có token xác thực"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [RECONNECT] Error connecting WebSocket", e)
                _actionResult.value = "Lỗi kết nối: ${e.message}"
            }
        }
    }

    /**
     * Đồng bộ lời mời từ server với rate limiting
     */
    private fun syncInvitations() {
        val currentTime = System.currentTimeMillis()

        // Kiểm tra rate limiting
        if (currentTime - lastSyncTime < SYNC_COOLDOWN_MS) {
            Log.d(TAG, "🚫 [SYNC] Rate limited - skipping sync (last sync: ${currentTime - lastSyncTime}ms ago)")
            return
        }

        lastSyncTime = currentTime
        Log.d(TAG, "🔄 [SYNC] Starting invitation sync")

        viewModelScope.launch {
            try {
                val result = teamInvitationRepository.syncInvitations()
                if (result.isSuccess) {
                    Log.d(TAG, "✅ [SYNC] Sync successful")
                    // Đồng bộ thành công, làm mới danh sách lời mời
                    refreshInvitations()
                } else {
                    // Đồng bộ thất bại, ghi log lỗi
                    Log.e(TAG, "❌ [SYNC] Sync failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 [SYNC] Sync exception", e)
            }
        }
    }
}
