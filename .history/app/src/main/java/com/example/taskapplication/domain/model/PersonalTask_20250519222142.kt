package com.example.taskapplication.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PersonalTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val dueDate: Long? = null, // deadline
    val priority: String = "medium", // "low", "medium", "high"
    val status: String = "pending", // "pending", "in_progress", "completed", "overdue"
    val order: Int = 0,
    val userId: String? = null,
    val serverId: String? = null,
    val syncStatus: String = SyncStatus.SYNCED,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val subtasks: List<Subtask>? = null
) {
    fun isCompleted(): Boolean {
        return status == "completed"
    }

    fun isOverdue(): Boolean {
        return dueDate != null && dueDate < System.currentTimeMillis() && status != "completed"
    }

    fun getEffectiveStatus(): String {
        return if (isOverdue()) "overdue" else status
    }

    fun toApiRequest(): com.example.taskapplication.data.api.request.PersonalTaskRequest {
        return com.example.taskapplication.data.api.request.PersonalTaskRequest(
            title = title,
            description = description,
            dueDate = dueDate?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(it)) },
            priority = priority,
            status = status,
            order = order
        )
    }

    companion object {
    }

    object SyncStatus {
        const val SYNCED = "synced"
        const val CREATED = "pending_create"
        const val UPDATED = "pending_update"
        const val DELETED = "pending_delete"
    }
}