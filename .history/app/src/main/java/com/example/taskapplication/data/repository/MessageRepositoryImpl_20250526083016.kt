package com.example.taskapplication.data.repository

import android.util.Log
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.database.dao.AttachmentDao
import com.example.taskapplication.data.database.dao.MessageDao
import com.example.taskapplication.data.database.dao.MessageReactionDao
import com.example.taskapplication.data.database.dao.MessageReadStatusDao
import com.example.taskapplication.data.database.entities.MessageEntity
import com.example.taskapplication.data.database.entities.MessageReadStatusEntity
import com.example.taskapplication.data.mapper.toDomainModel
import com.example.taskapplication.data.mapper.toEntity
import com.example.taskapplication.domain.model.Attachment
import com.example.taskapplication.data.util.ConnectionChecker
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.Message
import com.example.taskapplication.domain.model.MessageReaction
import com.example.taskapplication.domain.model.MessageReadStatus
import com.example.taskapplication.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val messageReadStatusDao: MessageReadStatusDao,
    private val messageReactionDao: MessageReactionDao,
    private val attachmentDao: AttachmentDao,
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager,
    private val connectionChecker: ConnectionChecker
) : MessageRepository {

    private val TAG = "MessageRepository"

    override fun getTeamMessages(teamId: String): Flow<List<Message>> {
        return messageDao.getTeamMessages(teamId)
            .map { entities ->
                entities.map { entity ->
                    entity.toDomainModel(emptyList(), emptyList(), emptyList())
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getTeamMessages(teamId: String, limit: Int, beforeId: String?, afterId: String?): Flow<List<Message>> {
        // Triển khai lấy tin nhắn với phân trang
        return messageDao.getTeamMessages(teamId)
            .map { entities ->
                var filteredEntities = entities

                // Lọc theo beforeId nếu có
                if (beforeId != null) {
                    val beforeMessage = messageDao.getMessageSync(beforeId)
                    if (beforeMessage != null) {
                        filteredEntities = filteredEntities.filter { it.timestamp < beforeMessage.timestamp }
                    }
                }

                // Lọc theo afterId nếu có
                if (afterId != null) {
                    val afterMessage = messageDao.getMessageSync(afterId)
                    if (afterMessage != null) {
                        filteredEntities = filteredEntities.filter { it.timestamp > afterMessage.timestamp }
                    }
                }

                // Giới hạn số lượng
                if (filteredEntities.size > limit) {
                    filteredEntities = filteredEntities.take(limit)
                }

                filteredEntities.map { it.toDomainModel(emptyList(), emptyList(), emptyList()) }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getDirectMessages(userId1: String, userId2: String): Flow<List<Message>> {
        return messageDao.getDirectMessages(userId1, userId2)
            .map { entities ->
                entities.map { entity ->
                    entity.toDomainModel(emptyList(), emptyList(), emptyList())
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getMessageById(id: String): Message? {
        val message = messageDao.getMessage(id) ?: return null
        // Trong thực tế, bạn sẽ cần lấy thêm thông tin về readBy và reactions
        return message.toDomainModel(emptyList(), emptyList(), emptyList())
    }

    override suspend fun sendTeamMessage(teamId: String, content: String): Result<Message> {
        return sendTeamMessage(teamId, content, UUID.randomUUID().toString(), null)
    }

    override suspend fun sendTeamMessage(
        teamId: String,
        content: String,
        clientTempId: String,
        attachments: List<com.example.taskapplication.domain.model.Attachment>?
    ): Result<Message> {
        try {
            Log.d(TAG, "💬 [SEND_MSG] ===== BẮT ĐẦU GỬI TIN NHẮN TEAM =====")
            Log.d(TAG, "💬 [SEND_MSG] Team ID: $teamId")
            Log.d(TAG, "💬 [SEND_MSG] Content: ${content.take(50)}${if (content.length > 50) "..." else ""}")
            Log.d(TAG, "💬 [SEND_MSG] Client temp ID: $clientTempId")
            Log.d(TAG, "💬 [SEND_MSG] Attachments count: ${attachments?.size ?: 0}")
            Log.d(TAG, "💬 [SEND_MSG] Timestamp: ${System.currentTimeMillis()}")

            val currentUserId = dataStoreManager.getCurrentUserId()
            if (currentUserId == null) {
                Log.e(TAG, "❌ [SEND_MSG] User not logged in")
                return Result.failure(IOException("User not logged in"))
            }
            Log.d(TAG, "👤 [SEND_MSG] Current user ID: $currentUserId")

            // Tạo message mới
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            Log.d(TAG, "📋 [SEND_MSG] Generated message ID: $messageId")

            val messageEntity = MessageEntity(
                id = messageId,
                content = content,
                senderId = currentUserId,
                teamId = teamId,
                receiverId = null,
                timestamp = timestamp,
                serverId = null,
                syncStatus = "pending_create",
                lastModified = timestamp,
                createdAt = timestamp,
                isDeleted = false,
                isRead = false,
                clientTempId = clientTempId
            )

            Log.d(TAG, "💾 [SEND_MSG] Saving message to local database...")
            messageDao.insertMessage(messageEntity)
            Log.d(TAG, "✅ [SEND_MSG] Message saved to local database")

            // Lưu các tệp đính kèm nếu có
            if (attachments != null && attachments.isNotEmpty()) {
                Log.d(TAG, "📎 [SEND_MSG] Saving ${attachments.size} attachments...")
                for (attachment in attachments) {
                    val attachmentEntity = attachment.toEntity().copy(
                        messageId = messageId,
                        syncStatus = "pending_create"
                    )
                    attachmentDao.insertAttachment(attachmentEntity)
                    Log.d(TAG, "📎 [SEND_MSG] Saved attachment: ${attachment.fileName}")
                }
                Log.d(TAG, "✅ [SEND_MSG] All attachments saved")
            } else {
                Log.d(TAG, "📎 [SEND_MSG] No attachments to save")
            }

            // Nếu có kết nối mạng, gửi lên server
            if (connectionChecker.isNetworkAvailable()) {
                Log.d(TAG, "🌐 [SEND_MSG] Network available - attempting to send to server...")
                try {
                    // TODO: Triển khai gửi lên server ở đây
                    Log.d(TAG, "📡 [SEND_MSG] Server sync not implemented yet - message will sync later")
                    Log.d(TAG, "📡 [SEND_MSG] Expecting WebSocket event 'new-chat-message' soon...")
                } catch (e: Exception) {
                    Log.e(TAG, "💥 [SEND_MSG] Error sending message to server:")
                    Log.e(TAG, "💥 [SEND_MSG] Exception type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "💥 [SEND_MSG] Exception message: ${e.message}")
                    e.printStackTrace()
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            } else {
                Log.d(TAG, "📶 [SEND_MSG] No network connection - message will sync when online")
            }

            // Lấy danh sách tệp đính kèm để trả về
            val savedAttachments = if (attachments != null && attachments.isNotEmpty()) {
                Log.d(TAG, "📎 [SEND_MSG] Returning ${attachments.size} attachments")
                attachments
            } else {
                Log.d(TAG, "📎 [SEND_MSG] No attachments to return")
                emptyList()
            }

            val resultMessage = messageEntity.toDomainModel(emptyList(), emptyList(), savedAttachments)
            Log.d(TAG, "🎉 [SEND_MSG] SUCCESS - Message created and saved")
            Log.d(TAG, "🎉 [SEND_MSG] Message ID: ${resultMessage.id}")
            Log.d(TAG, "🎉 [SEND_MSG] Client temp ID: ${resultMessage.clientTempId}")
            Log.d(TAG, "🎉 [SEND_MSG] Sync status: ${resultMessage.syncStatus}")
            Log.d(TAG, "📡 [SEND_MSG] Other devices should receive this message via WebSocket")

            return Result.success(resultMessage)
        } catch (e: Exception) {
            Log.e(TAG, "💥 [SEND_MSG] EXCEPTION when sending team message:")
            Log.e(TAG, "💥 [SEND_MSG] Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "💥 [SEND_MSG] Exception message: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun sendDirectMessage(receiverId: String, content: String): Result<Message> {
        try {
            val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))

            // Tạo message mới
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val messageEntity = MessageEntity(
                id = messageId,
                content = content,
                senderId = currentUserId,
                teamId = null,
                receiverId = receiverId,
                timestamp = timestamp,
                serverId = null,
                syncStatus = "pending_create",
                lastModified = timestamp,
                createdAt = timestamp,
                isDeleted = false,
                isRead = false
            )

            messageDao.insertMessage(messageEntity)

            // Nếu có kết nối mạng, gửi lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai gửi lên server ở đây
                    // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi gửi tin nhắn trực tiếp lên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            return Result.success(messageEntity.toDomainModel(emptyList(), emptyList(), emptyList()))
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gửi tin nhắn trực tiếp", e)
            return Result.failure(e)
        }
    }

    override suspend fun retrySendMessage(clientTempId: String): Result<Message> {
        try {
            // Tìm tin nhắn theo clientTempId
            val message = messageDao.getMessageByClientTempId(clientTempId)
                ?: return Result.failure(IOException("Message not found"))

            // Cập nhật trạng thái để gửi lại
            val updatedMessage = message.copy(
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis()
            )

            messageDao.updateMessage(updatedMessage)

            // Nếu có kết nối mạng, gửi lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai gửi lên server ở đây
                    // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi gửi lại tin nhắn lên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            // Lấy danh sách tệp đính kèm
            val attachments = attachmentDao.getAttachmentsByMessageIdSync(updatedMessage.id)
                .map { it.toDomainModel() }

            return Result.success(updatedMessage.toDomainModel(emptyList(), emptyList(), attachments))
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thử gửi lại tin nhắn", e)
            return Result.failure(e)
        }
    }

    override suspend fun updateMessage(message: Message): Result<Message> {
        try {
            val messageEntity = message.toEntity().copy(
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )

            messageDao.updateMessage(messageEntity)

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai đồng bộ với server ở đây
                    // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi cập nhật tin nhắn trên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            // Lấy danh sách tệp đính kèm
            val attachments = attachmentDao.getAttachmentsByMessageIdSync(messageEntity.id)
                .map { it.toDomainModel() }

            return Result.success(messageEntity.toDomainModel(emptyList(), emptyList(), attachments))
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi cập nhật tin nhắn", e)
            return Result.failure(e)
        }
    }

    override suspend fun editMessage(messageId: String, newContent: String): Result<Message> {
        try {
            val message = messageDao.getMessage(messageId)
                ?: return Result.failure(IOException("Message not found"))

            // Kiểm tra quyền chỉnh sửa (chỉ người gửi mới có quyền chỉnh sửa)
            val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))
            if (message.senderId != currentUserId) {
                return Result.failure(IOException("Bạn không có quyền chỉnh sửa tin nhắn này"))
            }

            // Cập nhật nội dung tin nhắn
            val updatedMessage = message.copy(
                content = newContent,
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )

            messageDao.updateMessage(updatedMessage)

            // Nếu có kết nối mạng, đồng bộ lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai đồng bộ với server ở đây
                    // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi chỉnh sửa tin nhắn trên server", e)
                    // Không trả về lỗi vì đã lưu thành công vào local database
                }
            }

            // Lấy danh sách tệp đính kèm
            val attachments = attachmentDao.getAttachmentsByMessageIdSync(updatedMessage.id)
                .map { it.toDomainModel() }

            return Result.success(updatedMessage.toDomainModel(emptyList(), emptyList(), attachments))
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi chỉnh sửa tin nhắn", e)
            return Result.failure(e)
        }
    }

    override suspend fun deleteMessage(messageId: String): Result<Unit> {
        try {
            val message = messageDao.getMessage(messageId)
            if (message != null) {
                // Nếu message đã được đồng bộ với server, đánh dấu để xóa sau
                if (message.serverId != null) {
                    val updatedMessage = message.copy(
                        syncStatus = "pending_delete",
                        lastModified = System.currentTimeMillis(),
                        isDeleted = true
                    )
                    messageDao.updateMessage(updatedMessage)
                } else {
                    // Nếu message chưa được đồng bộ với server, xóa luôn
                    messageDao.deleteMessage(message)
                }

                // Nếu có kết nối mạng, đồng bộ lên server
                if (connectionChecker.isNetworkAvailable() && message.serverId != null) {
                    try {
                        // Triển khai xóa trên server ở đây
                        // Hiện tại chỉ trả về thành công vì chúng ta đã xử lý trong local database
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi xóa tin nhắn trên server", e)
                        // Không trả về lỗi vì đã xử lý thành công trong local database
                    }
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xóa tin nhắn", e)
            return Result.failure(e)
        }
    }

    override suspend fun markMessageAsRead(messageId: String): Result<Unit> {
        try {
            val message = messageDao.getMessage(messageId)
            if (message != null && !message.isRead) {
                val updatedMessage = message.copy(
                    isRead = true,
                    syncStatus = if (message.syncStatus == "synced") "pending_update" else message.syncStatus,
                    lastModified = System.currentTimeMillis()
                )
                messageDao.updateMessage(updatedMessage)

                // Lưu trạng thái đọc
                val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))
                val readStatus = MessageReadStatusEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    userId = currentUserId,
                    readAt = System.currentTimeMillis(),
                    serverId = null,
                    syncStatus = "pending_create",
                    lastModified = System.currentTimeMillis()
                )
                messageReadStatusDao.insertReadStatus(readStatus)

                // Nếu có kết nối mạng, đồng bộ lên server
                if (connectionChecker.isNetworkAvailable() && message.serverId != null) {
                    try {
                        // Triển khai đồng bộ với server ở đây
                        // Hiện tại chỉ trả về thành công vì chúng ta đã lưu vào local database
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi đánh dấu tin nhắn đã đọc trên server", e)
                        // Không trả về lỗi vì đã lưu thành công vào local database
                    }
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đánh dấu tin nhắn đã đọc", e)
            return Result.failure(e)
        }
    }

    override suspend fun addReaction(messageId: String, reaction: String): Result<MessageReaction> {
        // Triển khai thêm reaction
        return Result.success(MessageReaction("1", messageId, "1", reaction, null, System.currentTimeMillis()))
    }

    override suspend fun removeReaction(messageId: String, reactionId: String): Result<Unit> {
        // Triển khai xóa reaction
        return Result.success(Unit)
    }

    override suspend fun getUnreadMessageCount(): Result<Map<String, Int>> {
        try {
            val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))

            // Lấy danh sách team mà người dùng tham gia
            val teams = messageDao.getTeamsWithMessages()
            val result = mutableMapOf<String, Int>()

            // Đếm số lượng tin nhắn chưa đọc cho mỗi team
            for (teamId in teams) {
                val count = messageDao.getUnreadMessageCount(teamId, currentUserId)
                result[teamId] = count
            }

            return Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy số lượng tin nhắn chưa đọc", e)
            return Result.failure(e)
        }
    }

    override suspend fun getTeamUnreadMessageCount(teamId: String): Result<Int> {
        try {
            val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))
            val count = messageDao.getUnreadMessageCount(teamId, currentUserId)
            return Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy số lượng tin nhắn chưa đọc của nhóm", e)
            return Result.failure(e)
        }
    }

    override suspend fun getOlderTeamMessages(teamId: String, olderThan: Long, limit: Int): Result<List<Message>> {
        try {
            val messages = messageDao.getOlderTeamMessages(teamId, olderThan, limit)
            val result = messages.map { entity ->
                val attachments = attachmentDao.getAttachmentsByMessageIdSync(entity.id)
                    .map { it.toDomainModel() }
                entity.toDomainModel(emptyList(), emptyList(), attachments)
            }
            return Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy tin nhắn cũ hơn của nhóm", e)
            return Result.failure(e)
        }
    }

    override suspend fun sendTypingStatus(teamId: String, isTyping: Boolean): Result<Unit> {
        try {
            val currentUserId = dataStoreManager.getCurrentUserId() ?: return Result.failure(IOException("User not logged in"))

            // Nếu có kết nối mạng, gửi trạng thái đang nhập lên server
            if (connectionChecker.isNetworkAvailable()) {
                try {
                    // Triển khai gửi trạng thái đang nhập lên server
                    // Hiện tại chỉ trả về thành công
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi gửi trạng thái đang nhập lên server", e)
                    return Result.failure(e)
                }
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gửi trạng thái đang nhập", e)
            return Result.failure(e)
        }
    }

    override suspend fun syncMessages(): Result<Unit> {
        // Triển khai đồng bộ tin nhắn
        return Result.success(Unit)
    }

    override suspend fun syncTeamMessages(teamId: String): Result<Unit> {
        // Triển khai đồng bộ tin nhắn của team
        return Result.success(Unit)
    }

    override suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun saveReadStatus(readStatus: MessageReadStatus) {
        // Triển khai lưu trạng thái đọc
    }

    override suspend fun markMessageAsDeleted(messageId: String) {
        // Triển khai đánh dấu tin nhắn đã xóa
        messageDao.markMessageAsDeleted(messageId, System.currentTimeMillis())
    }
}
