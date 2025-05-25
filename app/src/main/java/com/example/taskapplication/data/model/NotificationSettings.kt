package com.example.taskapplication.data.model

/**
 * Model class for notification settings
 */
data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val taskReminders: Boolean = true,
    val teamInvitations: Boolean = true,
    val teamUpdates: Boolean = true,
    val chatMessages: Boolean = true
)
