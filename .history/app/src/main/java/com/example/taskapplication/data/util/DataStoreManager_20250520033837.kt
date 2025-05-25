package com.example.taskapplication.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "task_app_preferences")

@Singleton
class DataStoreManager @Inject constructor(
    private val context: Context
) {
    // Keys
    private object PreferencesKeys {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val LAST_NOTIFICATION_CHECK_TIMESTAMP = longPreferencesKey("last_notification_check_timestamp")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_MESSAGE_SYNC_TIMESTAMP = longPreferencesKey("last_message_sync_timestamp")
        val LAST_TEAM_SYNC_TIMESTAMP = longPreferencesKey("last_team_sync_timestamp")
        val LAST_TEAM_MEMBER_SYNC_TIMESTAMP = longPreferencesKey("last_team_member_sync_timestamp")
        val LAST_TEAM_TASK_SYNC_TIMESTAMP = longPreferencesKey("last_team_task_sync_timestamp")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BIOMETRIC_TYPE = stringPreferencesKey("biometric_type")
        val CURRENT_TEAM_ID = stringPreferencesKey("current_team_id")

        // User settings
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        val TEAM_INVITATIONS = booleanPreferencesKey("team_invitations")
        val TEAM_UPDATES = booleanPreferencesKey("team_updates")
        val CHAT_MESSAGES = booleanPreferencesKey("chat_messages")
    }

    // Authentication preferences
    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN] = token
        }
    }

    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTH_TOKEN]
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REFRESH_TOKEN] = token
        }
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REFRESH_TOKEN]
    }

    suspend fun saveTokenExpiresAt(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOKEN_EXPIRES_AT] = timestamp
        }
    }

    val tokenExpiresAt: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TOKEN_EXPIRES_AT]
    }

    // User information
    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = userId
        }
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ID]
    }

    suspend fun saveUserInfo(name: String, email: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
            preferences[PreferencesKeys.USER_EMAIL] = email
        }
    }

    suspend fun saveUserInfo(user: com.example.taskapplication.domain.model.User) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = user.id
            preferences[PreferencesKeys.USER_NAME] = user.name
            preferences[PreferencesKeys.USER_EMAIL] = user.email
        }
    }

    val userName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_NAME]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_EMAIL]
    }

    val userInfo: Flow<com.example.taskapplication.domain.model.User?> = context.dataStore.data.map { preferences ->
        val id = preferences[PreferencesKeys.USER_ID]
        val name = preferences[PreferencesKeys.USER_NAME]
        val email = preferences[PreferencesKeys.USER_EMAIL]

        if (id != null && name != null && email != null) {
            com.example.taskapplication.domain.model.User(
                id = id,
                name = name,
                email = email
            )
        } else {
            null
        }
    }

    // Sync preferences
    suspend fun saveLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    val lastSyncTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP]
    }

    // Notification preferences
    suspend fun saveLastNotificationCheckTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_NOTIFICATION_CHECK_TIMESTAMP] = timestamp
        }
    }

    val lastNotificationCheckTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_NOTIFICATION_CHECK_TIMESTAMP]
    }

    suspend fun getLastNotificationCheckTimestamp(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_NOTIFICATION_CHECK_TIMESTAMP] ?: 0L
        }.first()
    }

    // Device ID
    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_ID] = deviceId
        }
    }

    val deviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEVICE_ID]
    }

    suspend fun getDeviceId(): String {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEVICE_ID] ?: ""
        }.first()
    }

    suspend fun getCurrentUserId(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.USER_ID]
        }.first()
    }

    suspend fun getUserEmail(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.USER_EMAIL]
        }.first()
    }

    // Message sync
    suspend fun saveLastMessageSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_MESSAGE_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastMessageSyncTimestamp(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_MESSAGE_SYNC_TIMESTAMP] ?: 0L
        }.first()
    }

    // Team sync
    suspend fun saveLastTeamSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastTeamSyncTimestamp(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_SYNC_TIMESTAMP] ?: 0L
        }.first()
    }

    // Team member sync
    suspend fun saveLastTeamMemberSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_MEMBER_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastTeamMemberSyncTimestamp(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_MEMBER_SYNC_TIMESTAMP] ?: 0L
        }.first()
    }

    // Team task sync
    suspend fun saveLastTeamTaskSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_TASK_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastTeamTaskSyncTimestamp(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_TEAM_TASK_SYNC_TIMESTAMP] ?: 0L
        }.first()
    }

    // Clear specific preferences
    suspend fun clearAuthToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.AUTH_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
            preferences.remove(PreferencesKeys.TOKEN_EXPIRES_AT)
        }
    }

    suspend fun clearUserInfo() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.USER_NAME)
            preferences.remove(PreferencesKeys.USER_EMAIL)
        }
    }

    // Onboarding preferences
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
    }

    // Clear all preferences
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // Biometric authentication preferences
    suspend fun saveBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] = enabled
        }
    }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BIOMETRIC_ENABLED] ?: false
    }

    suspend fun saveBiometricType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_TYPE] = type
        }
    }

    val biometricType: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BIOMETRIC_TYPE]
    }

    // User settings preferences
    suspend fun saveUserSettings(settings: com.example.taskapplication.data.api.response.UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = settings.theme
            preferences[PreferencesKeys.LANGUAGE] = settings.language
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            preferences[PreferencesKeys.TASK_REMINDERS] = settings.taskReminders
            preferences[PreferencesKeys.TEAM_INVITATIONS] = settings.teamInvitations
            preferences[PreferencesKeys.TEAM_UPDATES] = settings.teamUpdates
            preferences[PreferencesKeys.CHAT_MESSAGES] = settings.chatMessages
        }
    }

    val userSettings: Flow<com.example.taskapplication.data.api.response.UserSettings?> = context.dataStore.data.map { preferences ->
        val theme = preferences[PreferencesKeys.THEME]
        val language = preferences[PreferencesKeys.LANGUAGE]
        val notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED]
        val taskReminders = preferences[PreferencesKeys.TASK_REMINDERS]
        val teamInvitations = preferences[PreferencesKeys.TEAM_INVITATIONS]
        val teamUpdates = preferences[PreferencesKeys.TEAM_UPDATES]
        val chatMessages = preferences[PreferencesKeys.CHAT_MESSAGES]

        if (theme != null && language != null && notificationsEnabled != null &&
            taskReminders != null && teamInvitations != null && teamUpdates != null && chatMessages != null) {
            com.example.taskapplication.data.api.response.UserSettings(
                theme = theme,
                language = language,
                notificationsEnabled = notificationsEnabled,
                taskReminders = taskReminders,
                teamInvitations = teamInvitations,
                teamUpdates = teamUpdates,
                chatMessages = chatMessages
            )
        } else {
            null
        }
    }

    suspend fun getUserSettings(): com.example.taskapplication.data.api.response.UserSettings? {
        return userSettings.first()
    }

    // Current team ID
    suspend fun saveCurrentTeamId(teamId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_TEAM_ID] = teamId
        }
    }

    val currentTeamId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CURRENT_TEAM_ID]
    }

    suspend fun getCurrentTeamId(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CURRENT_TEAM_ID]
        }.first()
    }

    suspend fun getAuthToken(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN]
        }.first()
    }

    // Notification settings
    suspend fun saveNotificationSettings(settings: com.example.taskapplication.data.api.response.NotificationSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = settings.pushEnabled
            preferences[PreferencesKeys.TASK_REMINDERS] = settings.taskReminders
            preferences[PreferencesKeys.TEAM_INVITATIONS] = settings.teamInvitations
            preferences[PreferencesKeys.CHAT_MESSAGES] = settings.messageNotifications
        }
    }

    val notificationSettings: Flow<com.example.taskapplication.data.api.response.NotificationSettings> = context.dataStore.data.map { preferences ->
        com.example.taskapplication.data.api.response.NotificationSettings(
            pushEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
            emailEnabled = true,
            taskReminders = preferences[PreferencesKeys.TASK_REMINDERS] ?: true,
            teamInvitations = preferences[PreferencesKeys.TEAM_INVITATIONS] ?: true,
            messageNotifications = preferences[PreferencesKeys.CHAT_MESSAGES] ?: true,
            taskAssignments = true,
            taskComments = true,
            dailyDigest = false
        )
    }
}