package com.example.taskapplication.data.repository

import android.util.Log
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.api.request.ChangePasswordRequest
import com.example.taskapplication.data.api.request.GoogleAuthRequest
import com.example.taskapplication.data.api.request.LoginRequest
import com.example.taskapplication.data.api.request.LogoutRequest
import com.example.taskapplication.data.api.request.RegisterRequest
import com.example.taskapplication.data.api.request.UserSettingsRequest
import com.example.taskapplication.data.api.response.PaginationMeta
import com.example.taskapplication.data.api.response.UserSettings
import com.example.taskapplication.data.mapper.toDomainModel
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.User
import com.example.taskapplication.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager,
    private val webSocketManager: com.example.taskapplication.data.websocket.WebSocketManager
) : AuthRepository {

    private val TAG = "AuthRepository"

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            Log.d(TAG, "Đang thử đăng nhập với email: $email")
            val deviceId = getOrCreateDeviceId()
            val deviceName = getDeviceName()
            val loginRequest = LoginRequest(email, password, deviceId, deviceName)
            Log.d(TAG, "Yêu cầu đăng nhập: $loginRequest")

            val response = apiService.login(loginRequest)
            Log.d(TAG, "Mã phản hồi đăng nhập: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                Log.d(TAG, "Đăng nhập thành công, token: ${authResponse.token.take(10)}...")

                // Lưu token authentication
                dataStoreManager.saveAuthToken(authResponse.token)
                val user = authResponse.user.toDomainModel()
                dataStoreManager.saveUserInfo(user)

                // 🚀 AUTO-CONNECT WEBSOCKET AFTER LOGIN
                Log.d(TAG, "🔌 [LOGIN] Auto-connecting WebSocket after successful login")
                try {
                    webSocketManager.connect(authResponse.token)
                    Log.d(TAG, "✅ [LOGIN] WebSocket connection initiated")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [LOGIN] Failed to connect WebSocket: ${e.message}", e)
                    // Don't fail login if WebSocket fails
                }

                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Đăng nhập thất bại: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng nhập", e)
            Result.failure(e)
        }
    }

    override suspend fun register(name: String, email: String, password: String): Result<User> {
        return try {
            Log.d(TAG, "Đang thử đăng ký với email: $email, tên: $name")
            val deviceId = getOrCreateDeviceId()
            val deviceName = getDeviceName()
            val registerRequest = RegisterRequest(name, email, password, password, deviceId, deviceName)
            Log.d(TAG, "Yêu cầu đăng ký: $registerRequest")

            val response = apiService.register(registerRequest)
            Log.d(TAG, "Mã phản hồi đăng ký: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                Log.d(TAG, "Đăng ký thành công, token: ${authResponse.token.take(10)}...")

                // Lưu token authentication
                dataStoreManager.saveAuthToken(authResponse.token)
                val user = authResponse.user.toDomainModel()
                dataStoreManager.saveUserInfo(user)

                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Đăng ký thất bại: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký", e)
            Result.failure(e)
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        var deviceId = dataStoreManager.deviceId.first()
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            dataStoreManager.saveDeviceId(deviceId)
        }
        return deviceId
    }

    private fun getDeviceName(): String {
        return android.os.Build.MODEL
    }

    override suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            Log.d(TAG, "Đang thử đăng nhập Google với token: ${idToken.take(10)}...")
            val deviceId = getOrCreateDeviceId()
            val deviceName = getDeviceName()
            val googleAuthRequest = GoogleAuthRequest(idToken, deviceId, deviceName)
            Log.d(TAG, "Yêu cầu đăng nhập Google: $googleAuthRequest")

            val response = apiService.loginWithGoogle(googleAuthRequest)
            Log.d(TAG, "Mã phản hồi đăng nhập Google: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                Log.d(TAG, "Đăng nhập Google thành công, token: ${authResponse.token.take(10)}...")

                // Lưu token authentication
                dataStoreManager.saveAuthToken(authResponse.token)
                val user = authResponse.user.toDomainModel()
                dataStoreManager.saveUserInfo(user)

                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Đăng nhập Google thất bại: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng nhập Google", e)
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val deviceId = getOrCreateDeviceId()
            val logoutRequest = LogoutRequest(deviceId)
            val response = apiService.logout(logoutRequest)

            // Clear local data
            dataStoreManager.clearAuthToken()
            dataStoreManager.clearUserInfo()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng xuất", e)
            // Đăng xuất thành công ngay cả khi API fails
            dataStoreManager.clearAuthToken()
            dataStoreManager.clearUserInfo()
            Result.success(Unit)
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        val token = dataStoreManager.authToken.first()
        return token != null && token.isNotEmpty()
    }

    override suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = apiService.getCurrentUser()

            if (response.isSuccessful && response.body() != null) {
                val userResponse = response.body()!!
                if (userResponse.data != null) {
                    try {
                        val user = userResponse.data.toDomainModel()

                        // Cập nhật thông tin người dùng trong DataStore
                        dataStoreManager.saveUserInfo(user)

                        Result.success(user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi chuyển đổi dữ liệu người dùng", e)
                        // Nếu có lỗi khi chuyển đổi dữ liệu, thử lấy từ local
                        val localUser = dataStoreManager.userInfo.first()
                        if (localUser != null) {
                            Result.success(localUser)
                        } else {
                            Result.failure(e)
                        }
                    }
                } else {
                    Log.e(TAG, "Dữ liệu người dùng từ server là null")
                    // Nếu dữ liệu từ server là null, thử lấy từ local
                    val localUser = dataStoreManager.userInfo.first()
                    if (localUser != null) {
                        Result.success(localUser)
                    } else {
                        Result.failure(Exception("Dữ liệu người dùng từ server là null"))
                    }
                }
            } else {
                // Nếu API thất bại, thử lấy từ local
                val localUser = dataStoreManager.userInfo.first()
                if (localUser != null) {
                    Result.success(localUser)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                    Log.e(TAG, "Lỗi lấy thông tin người dùng: $errorBody")
                    Result.failure(Exception(errorBody))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy thông tin người dùng", e)
            // Thử lấy từ local nếu có lỗi
            val localUser = dataStoreManager.userInfo.first()
            if (localUser != null) {
                Result.success(localUser)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            Log.d(TAG, "Đang thử đổi mật khẩu")
            val request = ChangePasswordRequest(currentPassword, newPassword, newPassword)

            val response = apiService.changePassword(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Đổi mật khẩu thành công")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Đổi mật khẩu thất bại: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đổi mật khẩu", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserSettings(): Result<UserSettings> {
        return try {
            val response = apiService.getUserSettings()
            if (response.isSuccessful && response.body() != null) {
                val settingsResponse = response.body()!!

                // Lưu cài đặt người dùng
                dataStoreManager.saveUserSettings(settingsResponse.data)

                Result.success(settingsResponse.data)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Lỗi lấy cài đặt người dùng: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy cài đặt người dùng", e)
            Result.failure(e)
        }
    }

    override suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings> {
        return try {
            val request = UserSettingsRequest(
                theme = settings.theme,
                language = settings.language,
                notificationsEnabled = settings.notificationsEnabled,
                taskReminders = settings.taskReminders,
                teamInvitations = settings.teamInvitations,
                teamUpdates = settings.teamUpdates,
                chatMessages = settings.chatMessages
            )

            val response = apiService.updateUserSettings(request)
            if (response.isSuccessful && response.body() != null) {
                val settingsResponse = response.body()!!

                // Lưu cài đặt người dùng
                dataStoreManager.saveUserSettings(settingsResponse.data)

                Result.success(settingsResponse.data)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Lỗi cập nhật cài đặt người dùng: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi cập nhật cài đặt người dùng", e)
            Result.failure(e)
        }
    }

    override suspend fun searchUsers(
        query: String?,
        name: String?,
        email: String?,
        excludeTeam: Int?,
        perPage: Int?
    ): Result<Pair<List<User>, PaginationMeta>> {
        return try {
            val response = apiService.searchUsers(query, name, email, excludeTeam, perPage)
            if (response.isSuccessful && response.body() != null) {
                val searchResponse = response.body()!!
                val users = searchResponse.data.map { it.toDomainModel() }
                Result.success(Pair(users, searchResponse.meta))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Lỗi không xác định"
                Log.e(TAG, "Lỗi tìm kiếm người dùng: $errorBody")
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tìm kiếm người dùng", e)
            Result.failure(e)
        }
    }

    /**
     * Lấy token xác thực hiện tại
     * @return Token xác thực hoặc null nếu chưa đăng nhập
     */
    override fun getAuthToken(): String? {
        return try {
            // Sử dụng runBlocking để chuyển đổi từ Flow sang giá trị đồng bộ
            kotlinx.coroutines.runBlocking {
                val token = dataStoreManager.authToken.first()
                if (token.isNullOrEmpty()) {
                    Log.d(TAG, "Không tìm thấy token xác thực")
                    null
                } else {
                    Log.d(TAG, "Đã lấy token xác thực: ${token.take(10)}...")
                    token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy token xác thực", e)
            null
        }
    }
}
