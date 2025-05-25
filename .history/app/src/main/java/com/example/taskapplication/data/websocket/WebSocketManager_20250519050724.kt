package com.example.taskapplication.data.websocket

import android.util.Log
// import com.example.taskapplication.data.repository.MessageRepository
import com.example.taskapplication.domain.model.Message
import com.example.taskapplication.domain.model.MessageReadStatus
import com.example.taskapplication.domain.model.MessageReaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val messageRepository: com.example.taskapplication.domain.repository.MessageRepository,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events = _events.asSharedFlow()

    private val TAG = "WebSocketManager"

    // Variables for reconnection
    private var lastAuthToken: String = ""
    private var lastTeamId: String = ""
    private var reconnectAttempt = 0
    private val maxReconnectDelay = 16000L // 16 seconds
    private val baseReconnectDelay = 1000L // 1 second
    private var reconnectJob: kotlinx.coroutines.Job? = null

    fun connect(authToken: String, teamId: String) {
        val teamIdLong = teamId.toLongOrNull() ?: return
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }

        // Save connection info for reconnection
        lastAuthToken = authToken
        lastTeamId = teamId

        // Reset reconnect attempt counter
        reconnectAttempt = 0

        // Cancel any pending reconnect job
        reconnectJob?.cancel()

        val request = Request.Builder()
            .url("ws://10.0.2.2:6001/ws?token=$authToken")
            .build()

        webSocket = OkHttpClient().newWebSocket(request, createWebSocketListener(teamIdLong))
        _connectionState.value = ConnectionState.CONNECTING
    }

    private fun createWebSocketListener(teamId: Long): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED

                // Subscribe to channel
                webSocket.send(
                    JSONObject().apply {
                        put("event", "subscribe")
                        put("channel", "private-teams.$teamId")
                    }.toString()
                )

                Log.d(TAG, "WebSocket connected and subscribed to team $teamId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                scope.launch {
                    processMessage(text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.d(TAG, "WebSocket closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                Log.e(TAG, "WebSocket failure", t)
                // Schedule reconnection with exponential backoff
                scheduleReconnect()
            }
        }
    }

    private suspend fun processMessage(text: String) {
        try {
            val json = JSONObject(text)
            val eventName = json.optString("event")
            val data = json.optJSONObject("data")

            when (eventName) {
                "new-chat-message" -> {
                    val message = parseMessage(data)
                    messageRepository.saveMessage(message)
                    _events.emit(ChatEvent.NewMessage(message))
                }
                "message-read" -> {
                    val readStatus = parseReadStatus(data)
                    messageRepository.saveReadStatus(readStatus)
                    _events.emit(ChatEvent.MessageRead(readStatus))
                }
                "user-typing" -> {
                    val userId = data?.optLong("user_id") ?: return
                    val isTyping = data.optBoolean("is_typing")
                    val teamId = data.optLong("team_id").toString()
                    _events.emit(ChatEvent.UserTyping(userId.toString(), isTyping, teamId))
                }
                "message-reaction-updated" -> {
                    val messageId = data?.optLong("message_id") ?: return
                    val userId = data.optLong("user_id")
                    val reaction = data.optString("reaction")
                    val timestamp = data.optLong("created_at", System.currentTimeMillis())
                    _events.emit(ChatEvent.MessageReaction(messageId.toString(), userId.toString(), reaction, timestamp))
                }
                "message-updated" -> {
                    val messageId = data?.optLong("id") ?: return
                    val content = data.optString("message")
                    val updatedAt = data.optLong("updated_at", System.currentTimeMillis())

                    // Lấy tin nhắn hiện tại từ cơ sở dữ liệu
                    val existingMessage = messageRepository.getMessageById(messageId.toString())
                    if (existingMessage != null) {
                        // Cập nhật nội dung và thời gian cập nhật
                        val updatedMessage = existingMessage.copy(
                            content = content,
                            lastModified = updatedAt
                        )
                        messageRepository.updateMessage(updatedMessage)
                        _events.emit(ChatEvent.MessageUpdated(updatedMessage))
                    }
                }
                "message-deleted" -> {
                    val messageId = data?.optLong("id") ?: return
                    val deletedAt = data.optLong("deleted_at", System.currentTimeMillis())
                    messageRepository.markMessageAsDeleted(messageId.toString())
                    _events.emit(ChatEvent.MessageDeleted(messageId.toString(), deletedAt))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "WebSocket disconnected")

        // Cancel any pending reconnect job
        reconnectJob?.cancel()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = kotlin.math.min(baseReconnectDelay * (1 shl reconnectAttempt), maxReconnectDelay)
            Log.d(TAG, "Scheduling reconnect in $delay ms (attempt $reconnectAttempt)")
            kotlinx.coroutines.delay(delay)
            reconnectAttempt++
            reconnect()
        }
    }

    private fun reconnect() {
        if (lastAuthToken.isNotEmpty() && lastTeamId.isNotEmpty()) {
            Log.d(TAG, "Attempting to reconnect...")
            connect(lastAuthToken, lastTeamId)
        } else {
            Log.e(TAG, "Cannot reconnect: missing connection information")
        }
    }

    fun sendTypingStatus(teamId: String, isTyping: Boolean) {
        val teamIdLong = teamId.toLongOrNull() ?: return
        val json = JSONObject().apply {
            put("event", "client-typing")
            put("channel", "private-teams.$teamIdLong")
            put("data", JSONObject().apply {
                put("team_id", teamIdLong)
                put("is_typing", isTyping)
                put("timestamp", System.currentTimeMillis())
            })
        }
        webSocket?.send(json.toString())
    }

    private fun parseMessage(data: JSONObject?): Message {
        if (data == null) {
            throw IllegalArgumentException("Message data is null")
        }

        // Parse attachments if any
        val attachments = mutableListOf<com.example.taskapplication.domain.model.Attachment>()
        val attachmentsArray = data.optJSONArray("attachments")
        if (attachmentsArray != null) {
            for (i in 0 until attachmentsArray.length()) {
                val attachmentJson = attachmentsArray.optJSONObject(i)
                if (attachmentJson != null) {
                    attachments.add(
                        com.example.taskapplication.domain.model.Attachment(
                            id = attachmentJson.optLong("id").toString(),
                            messageId = data.optLong("id").toString(),
                            fileName = attachmentJson.optString("file_name"),
                            fileSize = attachmentJson.optLong("file_size"),
                            fileType = attachmentJson.optString("file_type"),
                            url = attachmentJson.optString("url"),
                            serverId = attachmentJson.optLong("id").toString(),
                            syncStatus = "synced",
                            createdAt = attachmentJson.optLong("created_at", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // Parse user info if available
        val userJson = data.optJSONObject("user")
        val senderName = userJson?.optString("name") ?: data.optString("sender_name")

        return Message(
            id = data.optLong("id").toString(),
            teamId = data.optLong("team_id").toString(),
            senderId = data.optLong("user_id").toString(),
            senderName = senderName,
            content = data.optString("message"),
            timestamp = data.optLong("created_at"),
            fileUrl = data.optString("file_url").takeIf { it.isNotEmpty() },
            status = "synced",
            serverId = data.optLong("id").toString(),
            syncStatus = "synced",
            lastModified = data.optLong("updated_at", System.currentTimeMillis()),
            createdAt = data.optLong("created_at"),
            clientTempId = data.optString("client_temp_id").takeIf { it.isNotEmpty() },
            attachments = attachments,
            isDeleted = data.optLong("deleted_at", 0) > 0
        )
    }

    private fun parseReadStatus(data: JSONObject?): MessageReadStatus {
        if (data == null) {
            throw IllegalArgumentException("Read status data is null")
        }

        return MessageReadStatus(
            id = System.currentTimeMillis().toString(),
            messageId = data.optLong("message_id").toString(),
            userId = data.optLong("user_id").toString(),
            readAt = data.optLong("read_at"),
            serverId = data.optString("id").takeIf { it.isNotEmpty() },
            syncStatus = "synced",
            lastModified = System.currentTimeMillis()
        )
    }
}

sealed class ChatEvent {
    data class NewMessage(val message: Message) : ChatEvent()
    data class MessageRead(val readStatus: MessageReadStatus) : ChatEvent()
    data class UserTyping(val userId: String, val isTyping: Boolean, val teamId: String) : ChatEvent()
    data class MessageReaction(val messageId: String, val userId: String, val reaction: String, val timestamp: Long) : ChatEvent()
    data class MessageUpdated(val message: Message) : ChatEvent()
    data class MessageDeleted(val messageId: String, val deletedAt: Long = System.currentTimeMillis()) : ChatEvent()
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}