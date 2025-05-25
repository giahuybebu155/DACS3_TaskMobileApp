package com.example.taskapplication.data.database.dao

import androidx.room.*
import com.example.taskapplication.data.database.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE teamId = :teamId ORDER BY timestamp DESC")
    fun getTeamMessages(teamId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (senderId = :userId1 AND receiverId = :userId2) OR (senderId = :userId2 AND receiverId = :userId1) ORDER BY timestamp DESC")
    fun getDirectMessages(userId1: String, userId2: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    fun getAllUserMessages(userId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId")
    fun getMessageSync(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE serverId = :serverId")
    suspend fun getMessageByServerId(serverId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE serverId = :serverId")
    fun getMessageByServerIdSync(serverId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE clientTempId = :clientTempId")
    suspend fun getMessageByClientTempId(clientTempId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("UPDATE messages SET syncStatus = 'pending', lastModified = :timestamp WHERE id = :messageId")
    suspend fun markMessageForDeletion(messageId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE id = :messageId AND syncStatus = 'pending'")
    suspend fun deleteLocalOnlyMessage(messageId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteSyncedMessage(messageId: String)

    @Query("UPDATE messages SET isDeleted = 1, lastModified = :timestamp WHERE serverId = :messageId")
    suspend fun markMessageAsDeleted(messageId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM messages WHERE syncStatus = 'pending'")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE teamId = :teamId AND syncStatus = 'pending'")
    suspend fun getPendingMessagesByTeam(teamId: String): List<MessageEntity>

    @Query("UPDATE messages SET serverId = :serverId WHERE id = :messageId")
    suspend fun updateMessageServerId(messageId: String, serverId: String)

    @Query("UPDATE messages SET syncStatus = 'synced' WHERE id = :messageId")
    suspend fun markMessageAsSynced(messageId: String)

    // For pagination
    @Query("SELECT * FROM messages WHERE teamId = :teamId AND timestamp < :olderThan ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getOlderTeamMessages(teamId: String, olderThan: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE teamId = :teamId AND receiverId = :userId AND isRead = 0")
    fun getUnreadMessagesCount(teamId: String, userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE teamId = :teamId AND senderId != :userId AND isRead = 0")
    suspend fun getUnreadMessageCount(teamId: String, userId: String): Int

    @Query("SELECT DISTINCT teamId FROM messages WHERE teamId IS NOT NULL")
    suspend fun getTeamsWithMessages(): List<String>

    @Query("SELECT * FROM messages WHERE syncStatus != 'synced'")
    suspend fun getPendingSyncMessages(): List<MessageEntity>

    @Query("UPDATE messages SET syncStatus = :status WHERE id = :messageId")
    suspend fun updateSyncStatus(messageId: String, status: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
}