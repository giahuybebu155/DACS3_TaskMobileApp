package com.example.taskapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.data.network.ConnectionStateManager
import com.example.taskapplication.domain.usecase.auth.IsLoggedInUseCase
import com.example.taskapplication.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _openInvitationsScreen = MutableStateFlow(false)
    val openInvitationsScreen: StateFlow<Boolean> = _openInvitationsScreen.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            _isLoggedIn.value = isLoggedInUseCase()
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _isLoggedIn.value = false
        }
    }

    /**
     * Đặt trạng thái mở màn hình lời mời
     */
    fun setOpenInvitationsScreen(open: Boolean) {
        _openInvitationsScreen.value = open
    }

    /**
     * Đánh dấu đã xử lý lời mời
     */
    fun markInvitationsHandled() {
        _openInvitationsScreen.value = false
    }
}
