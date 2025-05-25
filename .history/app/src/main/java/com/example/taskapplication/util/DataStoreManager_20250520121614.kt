package com.example.taskapplication.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_ROLE = stringPreferencesKey("user_role")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val LAST_TEAM_SYNC_TIME = longPreferencesKey("last_team_sync_time")
        private val LAST_TEAM_MEMBER_SYNC_TIME = longPreferencesKey("last_team_member_sync_time")
        private val LAST_TEAM_TASK_SYNC_TIME = longPreferencesKey("last_team_task_sync_time")
        private val LAST_TEAM_MESSAGE_SYNC_TIME = longPreferencesKey("last_team_message_sync_time")
        private val CURRENT_TEAM_ID = stringPreferencesKey("current_team_id")
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        private val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
    }

    // User data
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val userName: Flow<String?> = dataStore.data.map { it[USER_NAME] }
    val userEmail: Flow<String?> = dataStore.data.map { it[USER_EMAIL] }
    val userRole: Flow<String?> = dataStore.data.map { it[USER_ROLE] }

    // Auth data
    val authToken: Flow<String?> = dataStore.data.map { it[AUTH_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN] }
    val tokenExpiry: Flow<Long?> = dataStore.data.map { it[TOKEN_EXPIRY] }

    // App settings
    val lastSyncTime: Flow<Long?> = dataStore.data.map { it[LAST_SYNC_TIME] }
    val lastTeamSyncTime: Flow<Long?> = dataStore.data.map { it[LAST_TEAM_SYNC_TIME] }
    val lastTeamMemberSyncTime: Flow<Long?> = dataStore.data.map { it[LAST_TEAM_MEMBER_SYNC_TIME] }
    val lastTeamTaskSyncTime: Flow<Long?> = dataStore.data.map { it[LAST_TEAM_TASK_SYNC_TIME] }
    val lastTeamMessageSyncTime: Flow<Long?> = dataStore.data.map { it[LAST_TEAM_MESSAGE_SYNC_TIME] }
    val currentTeamId: Flow<String?> = dataStore.data.map { it[CURRENT_TEAM_ID] }
    val themeMode: Flow<Int?> = dataStore.data.map { it[THEME_MODE] }
    val notificationEnabled: Flow<Boolean?> = dataStore.data.map { it[NOTIFICATION_ENABLED] }
    val darkModeEnabled: Flow<Boolean?> = dataStore.data.map { it[DARK_MODE_ENABLED] }

    suspend fun setUserId(userId: String) {
        dataStore.edit { it[USER_ID] = userId }
    }

    suspend fun setUserName(userName: String) {
        dataStore.edit { it[USER_NAME] = userName }
    }

    suspend fun setUserEmail(userEmail: String) {
        dataStore.edit { it[USER_EMAIL] = userEmail }
    }

    suspend fun setUserRole(userRole: String) {
        dataStore.edit { it[USER_ROLE] = userRole }
    }

    suspend fun setAuthToken(token: String) {
        dataStore.edit { it[AUTH_TOKEN] = token }
    }

    suspend fun setRefreshToken(token: String) {
        dataStore.edit { it[REFRESH_TOKEN] = token }
    }

    suspend fun setTokenExpiry(expiry: Long) {
        dataStore.edit { it[TOKEN_EXPIRY] = expiry }
    }

    suspend fun setLastSyncTime(time: Long) {
        dataStore.edit { it[LAST_SYNC_TIME] = time }
    }

    suspend fun setLastTeamSyncTimestamp(time: Long) {
        dataStore.edit { it[LAST_TEAM_SYNC_TIME] = time }
    }

    suspend fun setLastTeamMemberSyncTimestamp(time: Long) {
        dataStore.edit { it[LAST_TEAM_MEMBER_SYNC_TIME] = time }
    }

    suspend fun setLastTeamTaskSyncTimestamp(time: Long) {
        dataStore.edit { it[LAST_TEAM_TASK_SYNC_TIME] = time }
    }

    suspend fun setLastTeamMessageSyncTimestamp(time: Long) {
        dataStore.edit { it[LAST_TEAM_MESSAGE_SYNC_TIME] = time }
    }

    suspend fun setCurrentTeamId(teamId: String) {
        dataStore.edit { it[CURRENT_TEAM_ID] = teamId }
    }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE_ENABLED] = enabled }
    }

    suspend fun clearUserData() {
        dataStore.edit {
            it.remove(USER_ID)
            it.remove(USER_NAME)
            it.remove(USER_EMAIL)
            it.remove(USER_ROLE)
            it.remove(AUTH_TOKEN)
            it.remove(REFRESH_TOKEN)
            it.remove(TOKEN_EXPIRY)
        }
    }

    suspend fun clearAllData() {
        dataStore.edit { it.clear() }
    }

    suspend fun getCurrentUserId(): String? {
        var currentUserId: String? = null
        userId.collect {
            currentUserId = it
        }
        return currentUserId
    }

    suspend fun getAuthToken(): String? {
        var token: String? = null
        authToken.collect {
            token = it
        }
        return token
    }

    suspend fun getCurrentTeamId(): String? {
        var teamId: String? = null
        currentTeamId.collect {
            teamId = it
        }
        return teamId
    }

    suspend fun getLastSyncTimestamp(): Long {
        var timestamp: Long = 0
        lastSyncTime.collect {
            timestamp = it ?: 0
        }
        return timestamp
    }

    suspend fun getLastTeamSyncTimestamp(): Long {
        var timestamp: Long = 0
        lastTeamSyncTime.collect {
            timestamp = it ?: 0
        }
        return timestamp
    }

    suspend fun getLastTeamMemberSyncTimestamp(): Long {
        var timestamp: Long = 0
        lastTeamMemberSyncTime.collect {
            timestamp = it ?: 0
        }
        return timestamp
    }

    suspend fun getLastTeamTaskSyncTimestamp(): Long {
        var timestamp: Long = 0
        lastTeamTaskSyncTime.collect {
            timestamp = it ?: 0
        }
        return timestamp
    }

    suspend fun getLastTeamMessageSyncTimestamp(): Long {
        var timestamp: Long = 0
        lastTeamMessageSyncTime.collect {
            timestamp = it ?: 0
        }
        return timestamp
    }
}