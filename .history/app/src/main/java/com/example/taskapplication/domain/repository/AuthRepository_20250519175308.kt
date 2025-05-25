package com.example.taskapplication.domain.repository

import com.example.taskapplication.data.api.response.PaginationMeta
import com.example.taskapplication.data.api.response.UserSettings
import com.example.taskapplication.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, email: String, password: String): Result<User>
    suspend fun loginWithGoogle(idToken: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun isLoggedIn(): Boolean
    suspend fun getCurrentUser(): Result<User>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun getUserSettings(): Result<UserSettings>
    suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings>
    suspend fun searchUsers(
        query: String? = null,
        name: String? = null,
        email: String? = null,
        excludeTeam: Int? = null,
        perPage: Int? = null
    ): Result<Pair<List<User>, PaginationMeta>>

    /**
     * Lấy token xác thực hiện tại
     * @return Token xác thực hoặc null nếu chưa đăng nhập
     */
    fun getAuthToken(): String?
}
