package com.example.taskapplication.data.network

import android.util.Log
import com.example.taskapplication.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý trạng thái kết nối và đồng bộ hóa
 */
@Singleton
class ConnectionStateManager @Inject constructor(
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val syncRepository: SyncRepository,
    private val scope: CoroutineScope
) {
    private val TAG = "ConnectionStateManager"
    
    // Trạng thái kết nối mạng
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    // Trạng thái đồng bộ hóa
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Thời gian đồng bộ cuối cùng
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()
    
    // Số lượng thay đổi chưa đồng bộ
    private val _pendingChangesCount = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCount.asStateFlow()
    
    init {
        // Theo dõi trạng thái kết nối mạng
        scope.launch {
            networkConnectivityObserver.observe().collectLatest { isConnected ->
                val previousState = _isOnline.value
                _isOnline.value = isConnected
                
                // Nếu kết nối được khôi phục, thực hiện đồng bộ hóa
                if (!previousState && isConnected) {
                    Log.d(TAG, "Network connection restored, starting sync")
                    syncIfNeeded()
                }
            }
        }
        
        // Khởi tạo thời gian đồng bộ cuối cùng
        scope.launch {
            try {
                val lastSync = syncRepository.getLastSyncTimestamp()
                _lastSyncTime.value = lastSync
            } catch (e: Exception) {
                Log.e(TAG, "Error getting last sync time", e)
            }
        }
        
        // Theo dõi số lượng thay đổi chưa đồng bộ
        monitorPendingChanges()
    }
    
    /**
     * Theo dõi số lượng thay đổi chưa đồng bộ
     */
    private fun monitorPendingChanges() {
        scope.launch {
            try {
                val hasPendingChanges = syncRepository.hasPendingChanges()
                _pendingChangesCount.value = if (hasPendingChanges) 1 else 0
            } catch (e: Exception) {
                Log.e(TAG, "Error checking pending changes", e)
            }
        }
    }
    
    /**
     * Thực hiện đồng bộ hóa nếu cần thiết
     */
    suspend fun syncIfNeeded() {
        if (!_isOnline.value) {
            Log.d(TAG, "Cannot sync: offline")
            return
        }
        
        if (_syncState.value == SyncState.SYNCING) {
            Log.d(TAG, "Sync already in progress")
            return
        }
        
        _syncState.value = SyncState.SYNCING
        
        try {
            // Đẩy các thay đổi local lên server
            if (syncRepository.hasPendingChanges()) {
                Log.d(TAG, "Pushing local changes to server")
                syncRepository.pushChanges()
            }
            
            // Lấy các thay đổi từ server
            Log.d(TAG, "Pulling changes from server")
            syncRepository.quickSync()
            
            // Cập nhật thời gian đồng bộ cuối cùng
            _lastSyncTime.value = syncRepository.getLastSyncTimestamp()
            _syncState.value = SyncState.IDLE
            
            // Cập nhật số lượng thay đổi chưa đồng bộ
            monitorPendingChanges()
            
            Log.d(TAG, "Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _syncState.value = SyncState.ERROR
        }
    }
    
    /**
     * Thực hiện đồng bộ hóa ban đầu
     */
    suspend fun performInitialSync(): Boolean {
        if (!_isOnline.value) {
            Log.d(TAG, "Cannot perform initial sync: offline")
            return false
        }
        
        _syncState.value = SyncState.SYNCING
        
        return try {
            val result = syncRepository.initialSync()
            _lastSyncTime.value = syncRepository.getLastSyncTimestamp()
            _syncState.value = SyncState.IDLE
            
            Log.d(TAG, "Initial sync completed successfully")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed", e)
            _syncState.value = SyncState.ERROR
            false
        }
    }
    
    /**
     * Lên lịch đồng bộ hóa định kỳ
     */
    fun schedulePeriodicSync(intervalMinutes: Int = 15) {
        scope.launch {
            syncRepository.schedulePeriodicSync(intervalMinutes)
        }
    }
    
    /**
     * Hủy đồng bộ hóa định kỳ
     */
    fun cancelPeriodicSync() {
        scope.launch {
            syncRepository.cancelPeriodicSync()
        }
    }
    
    /**
     * Trạng thái đồng bộ hóa
     */
    enum class SyncState {
        IDLE,       // Không có đồng bộ hóa nào đang diễn ra
        SYNCING,    // Đang đồng bộ hóa
        ERROR       // Lỗi đồng bộ hóa
    }
}
