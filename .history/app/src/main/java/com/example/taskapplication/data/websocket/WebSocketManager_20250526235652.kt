package com.example.taskapplication.data.websocket

import android.util.Log
// import com.example.taskapplication.data.repository.MessageRepository
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.Message
import com.example.taskapplication.domain.model.MessageReadStatus
import com.example.taskapplication.domain.model.MessageReaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val networkConnectivityObserver: com.example.taskapplication.data.network.NetworkConnectivityObserver,
    private val authManager: com.example.taskapplication.domain.repository.AuthRepository,
    private val dataStoreManager: DataStoreManager,
    private val scope: CoroutineScope,
    private val applicationContext: android.content.Context
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

    init {
        // Theo dõi trạng thái kết nối mạng
        scope.launch {
            networkConnectivityObserver.observe().collect { isConnected ->
                if (isConnected) {
                    // Kết nối lại WebSocket khi có mạng
                    if (_connectionState.value == ConnectionState.DISCONNECTED ||
                        _connectionState.value == ConnectionState.ERROR) {
                        reconnect()
                    }
                } else {
                    // Đánh dấu là đã ngắt kết nối khi mất mạng
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.d(TAG, "Network disconnected")
                }
            }
        }
    }

    fun connect(authToken: String, teamId: String) {
        Log.d(TAG, "🔌 [CONNECT] ===== BẮT ĐẦU KẾT NỐI WEBSOCKET =====")
        Log.d(TAG, "🔌 [CONNECT] Auth Token: ${authToken.take(10)}...${authToken.takeLast(5)}")
        Log.d(TAG, "🔌 [CONNECT] Team ID: $teamId")
        Log.d(TAG, "🔌 [CONNECT] Current connection state: ${_connectionState.value}")

        val teamIdLong = teamId.toLongOrNull()
        if (teamIdLong == null) {
            Log.e(TAG, "❌ [CONNECT] Invalid team ID: $teamId")
            return
        }
        Log.d(TAG, "✅ [CONNECT] Team ID parsed successfully: $teamIdLong")

        // Nếu đã kết nối với cùng một token và teamId, không cần kết nối lại
        if (_connectionState.value == ConnectionState.CONNECTED &&
            lastAuthToken == authToken && lastTeamId == teamId) {
            Log.d(TAG, "⚡ [CONNECT] WebSocket already connected with same token and teamId - SKIPPING")
            return
        }

        // Nếu đang kết nối, hủy kết nối hiện tại trước
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "🔄 [CONNECT] WebSocket is connecting, disconnecting first")
            disconnect()
        }

        // Save connection info for reconnection
        Log.d(TAG, "💾 [CONNECT] Saving connection info for reconnection")
        lastAuthToken = authToken
        lastTeamId = teamId

        // Reset reconnect attempt counter
        reconnectAttempt = 0
        Log.d(TAG, "🔄 [CONNECT] Reset reconnect attempt counter to 0")

        // Cancel any pending reconnect job
        reconnectJob?.cancel()
        Log.d(TAG, "🚫 [CONNECT] Cancelled any pending reconnect jobs")

        val wsUrl = "ws://10.0.2.2:8080?token=$authToken"
        Log.d(TAG, "🌐 [CONNECT] WebSocket URL: $wsUrl")
        Log.d(TAG, "🎯 [CONNECT] Target team: $teamId (parsed: $teamIdLong)")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        try {
            Log.d(TAG, "🔧 [CONNECT] Creating OkHttpClient with timeouts:")
            Log.d(TAG, "   - Connect timeout: 10s")
            Log.d(TAG, "   - Read timeout: 30s")
            Log.d(TAG, "   - Write timeout: 10s")
            Log.d(TAG, "   - Ping interval: 30s")

            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Tăng thời gian đọc
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS) // Thêm ping để giữ kết nối
                .build()

            Log.d(TAG, "🎧 [CONNECT] Creating WebSocket listener for team: $teamIdLong")
            webSocket = client.newWebSocket(request, createWebSocketListener(teamIdLong))
            _connectionState.value = ConnectionState.CONNECTING
            Log.d(TAG, "📡 [CONNECT] WebSocket connection request sent - State: CONNECTING")
            Log.d(TAG, "⏳ [CONNECT] Waiting for server response...")
        } catch (e: Exception) {
            Log.e(TAG, "💥 [CONNECT] Error creating WebSocket connection", e)
            Log.e(TAG, "💥 [CONNECT] Exception details: ${e.message}")
            e.printStackTrace()
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Kết nối đến WebSocket server với URL tùy chỉnh
     * @param serverUrl URL của WebSocket server
     * @param authToken Token xác thực
     * @param teamId ID của nhóm
     */
    fun connect(serverUrl: String, authToken: String, teamId: String) {
        this.lastAuthToken = authToken
        this.lastTeamId = teamId

        val teamIdLong = teamId.toLongOrNull() ?: return

        // Hủy kết nối hiện tại nếu có
        disconnect()

        // Hủy job reconnect nếu có
        reconnectJob?.cancel()

        val request = Request.Builder()
            .url("$serverUrl?token=$authToken")
            .build()

        webSocket = OkHttpClient().newWebSocket(request, createWebSocketListener(teamIdLong))
        _connectionState.value = ConnectionState.CONNECTING
    }

    private fun createWebSocketListener(teamId: Long): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "🎉 [WS_OPEN] ===== WEBSOCKET CONNECTED =====")
                Log.d(TAG, "🎉 [WS_OPEN] Response code: ${response.code}")
                Log.d(TAG, "🎉 [WS_OPEN] Response message: ${response.message}")
                Log.d(TAG, "🎉 [WS_OPEN] Team ID: $teamId")

                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "✅ [WS_OPEN] Connection state updated to: CONNECTED")

                // Subscribe to team channel
                val subscribeMessage = JSONObject().apply {
                    put("event", "subscribe")
                    put("channel", "private-teams.$teamId")
                }.toString()

                Log.d(TAG, "📡 [WS_OPEN] Subscribing to team channel: private-teams.$teamId")
                Log.d(TAG, "📡 [WS_OPEN] Subscribe message: $subscribeMessage")
                webSocket.send(subscribeMessage)

                // Đăng ký kênh user để nhận thông báo lời mời
                val userId = runBlocking { dataStoreManager.getCurrentUserId() }
                Log.d(TAG, "👤 [WS_OPEN] Current user ID: $userId")

                if (userId != null) {
                    val userSubscribeMessage = JSONObject().apply {
                        put("event", "subscribe")
                        put("channel", "private-users.$userId")
                    }.toString()

                    Log.d(TAG, "📡 [WS_OPEN] Subscribing to user channel: private-users.$userId")
                    Log.d(TAG, "📡 [WS_OPEN] User subscribe message: $userSubscribeMessage")
                    webSocket.send(userSubscribeMessage)
                } else {
                    Log.w(TAG, "⚠️ [WS_OPEN] User ID is null - cannot subscribe to user channel")
                }

                Log.d(TAG, "🎯 [WS_OPEN] WebSocket connected and subscribed to team $teamId")

                // Đồng bộ lời mời sau khi kết nối
                scope.launch {
                    try {
                        val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                        val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.InvitationNotificationWorker>()
                            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()
                        workManager.enqueue(syncWork)
                        Log.d(TAG, "Enqueued invitation sync work after WebSocket connection")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enqueueing invitation sync work", e)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📨 [WS_MESSAGE] ===== RECEIVED MESSAGE =====")
                Log.d(TAG, "📨 [WS_MESSAGE] Raw message: $text")
                Log.d(TAG, "📨 [WS_MESSAGE] Message length: ${text.length}")
                Log.d(TAG, "📨 [WS_MESSAGE] Processing in coroutine...")

                scope.launch {
                    try {
                        processMessage(text)
                        Log.d(TAG, "✅ [WS_MESSAGE] Message processed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 [WS_MESSAGE] Error processing WebSocket message", e)
                        Log.e(TAG, "💥 [WS_MESSAGE] Exception details: ${e.message}")
                        Log.e(TAG, "💥 [WS_MESSAGE] Failed message: $text")
                        e.printStackTrace()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "🔌 [WS_CLOSED] ===== WEBSOCKET CLOSED =====")
                Log.d(TAG, "🔌 [WS_CLOSED] Close code: $code")
                Log.d(TAG, "🔌 [WS_CLOSED] Close reason: $reason")
                Log.d(TAG, "🔌 [WS_CLOSED] Is normal closure: ${code == 1000}")

                _connectionState.value = ConnectionState.DISCONNECTED
                Log.d(TAG, "❌ [WS_CLOSED] Connection state updated to: DISCONNECTED")

                // Thử kết nối lại nếu đóng không phải do người dùng
                if (code != 1000) {
                    Log.d(TAG, "🔄 [WS_CLOSED] Abnormal closure detected - scheduling reconnect")
                    scheduleReconnect()
                } else {
                    Log.d(TAG, "✅ [WS_CLOSED] Normal closure - no reconnect needed")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "💥 [WS_FAILURE] ===== WEBSOCKET FAILURE =====")
                Log.e(TAG, "💥 [WS_FAILURE] Error: ${t.message}")
                Log.e(TAG, "💥 [WS_FAILURE] Error type: ${t.javaClass.simpleName}")
                Log.e(TAG, "💥 [WS_FAILURE] Response code: ${response?.code}")
                Log.e(TAG, "💥 [WS_FAILURE] Response message: ${response?.message}")
                Log.e(TAG, "💥 [WS_FAILURE] Response body: ${response?.body}")

                _connectionState.value = ConnectionState.ERROR
                Log.e(TAG, "❌ [WS_FAILURE] Connection state updated to: ERROR")

                t.printStackTrace()

                // Schedule reconnection with exponential backoff
                Log.d(TAG, "🔄 [WS_FAILURE] Scheduling reconnect due to failure")
                scheduleReconnect()
            }
        }
    }

    private suspend fun processMessage(text: String) {
        try {
            val json = JSONObject(text)
            val eventName = json.optString("event")
            val data = json.optJSONObject("data")

            Log.d(TAG, "Processing event: $eventName with data: $data")

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
                "team.invitation.created", "team-invitation", "team_invitation" -> {
                    Log.d(TAG, "📨 [WS_INVITATION] Nhận được lời mời team mới:")
                    Log.d(TAG, "   - Raw data: $data")

                    val teamId = data?.optLong("team_id")?.toString()
                        ?: data?.optString("team_id")
                        ?: return

                    val inviterId = data.optLong("inviter_id")?.toString()
                        ?: data.optString("inviter_id")
                        ?: return

                    val invitationId = data.optString("invitation_id")
                        ?: data.optString("id")
                        ?: return

                    Log.d(TAG, "📋 [WS_INVITATION] Parsed invitation data:")
                    Log.d(TAG, "   - Team ID: $teamId")
                    Log.d(TAG, "   - Inviter ID: $inviterId")
                    Log.d(TAG, "   - Invitation ID: $invitationId")

                    // Emit event để các thành phần khác trong ứng dụng có thể xử lý
                    _events.emit(ChatEvent.TeamInvitation(teamId, inviterId, invitationId))
                    Log.d(TAG, "✅ [WS_INVITATION] Đã emit TeamInvitation event")

                    // Trigger worker để đồng bộ lời mời
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.InvitationNotificationWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)

                    Log.d(TAG, "🔄 [WS_INVITATION] Đã trigger InvitationNotificationWorker")
                }
                "team.invitation.accepted" -> {
                    Log.d(TAG, "✅ [WS_ACCEPTED] Nhận được sự kiện chấp nhận lời mời:")
                    Log.d(TAG, "   - Raw data: $data")

                    val teamId = data?.optLong("team_id")?.toString() ?: return
                    val invitationId = data.optString("invitation_id") ?: return
                    val userId = data.optLong("user_id")?.toString() ?: return

                    Log.d(TAG, "📋 [WS_ACCEPTED] Parsed accepted invitation data:")
                    Log.d(TAG, "   - Team ID: $teamId")
                    Log.d(TAG, "   - Invitation ID: $invitationId")
                    Log.d(TAG, "   - User ID: $userId")

                    _events.emit(ChatEvent.TeamInvitationAccepted(teamId, invitationId, userId))
                    Log.d(TAG, "✅ [WS_ACCEPTED] Đã emit TeamInvitationAccepted event")

                    // Trigger worker để đồng bộ lời mời
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.InvitationNotificationWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)
                    Log.d(TAG, "🔄 [WS_ACCEPTED] Đã trigger InvitationNotificationWorker")

                    // Trigger đồng bộ dữ liệu team cho user mới join
                    Log.d(TAG, "🔄 [WS_ACCEPTED] Trigger đồng bộ dữ liệu team cho user mới join...")
                    triggerTeamDataSyncForNewMember(teamId, userId)
                }
                "team.invitation.rejected" -> {
                    Log.d(TAG, "Received team invitation rejected event: $data")
                    val teamId = data?.optLong("team_id")?.toString() ?: return
                    val invitationId = data.optString("invitation_id") ?: return

                    _events.emit(ChatEvent.TeamInvitationRejected(teamId, invitationId))
                }
                "team.invitation.cancelled" -> {
                    Log.d(TAG, "Received team invitation cancelled event: $data")
                    val teamId = data?.optLong("team_id")?.toString() ?: return
                    val invitationId = data.optString("invitation_id") ?: return

                    _events.emit(ChatEvent.TeamInvitationCancelled(teamId, invitationId))
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
                // Xử lý sự kiện cập nhật thông tin nhóm
                "team.updated" -> {
                    Log.d(TAG, "Received team updated event: $data")
                    val teamId = data?.optLong("id")?.toString() ?: data?.optString("id") ?: return
                    val name = data.optString("name") ?: return
                    val description = data.optString("description", "")

                    _events.emit(ChatEvent.TeamUpdated(teamId, name, description))

                    // Trigger worker để đồng bộ thông tin nhóm
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)

                    Log.d(TAG, "Enqueued team sync work after receiving team updated event")
                }
                // Xử lý sự kiện thêm thành viên vào nhóm
                "team.member.added" -> {
                    Log.d(TAG, "Received team member added event: $data")
                    val teamId = data?.optLong("team_id")?.toString() ?: data?.optString("team_id") ?: return
                    val userId = data.optLong("user_id")?.toString() ?: data.optString("user_id") ?: return
                    val role = data.optString("role", "member")

                    _events.emit(ChatEvent.TeamMemberAdded(teamId, userId, role))

                    // Trigger worker để đồng bộ thành viên nhóm
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)

                    Log.d(TAG, "Enqueued team sync work after receiving team member added event")
                }
                // Xử lý sự kiện xóa thành viên khỏi nhóm
                "team.member.removed" -> {
                    Log.d(TAG, "Received team member removed event: $data")
                    val teamId = data?.optLong("team_id")?.toString() ?: data?.optString("team_id") ?: return
                    val userId = data.optLong("user_id")?.toString() ?: data.optString("user_id") ?: return

                    _events.emit(ChatEvent.TeamMemberRemoved(teamId, userId))

                    // Trigger worker để đồng bộ thành viên nhóm
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)

                    Log.d(TAG, "Enqueued team sync work after receiving team member removed event")
                }
                // Xử lý sự kiện thay đổi vai trò thành viên
                "team.member.updated", "team.member.role.changed" -> {
                    Log.d(TAG, "Received team member role changed event: $data")
                    val teamId = data?.optLong("team_id")?.toString() ?: data?.optString("team_id") ?: return
                    val userId = data.optLong("user_id")?.toString() ?: data.optString("user_id") ?: return
                    val newRole = data.optString("role", "member")

                    _events.emit(ChatEvent.TeamMemberRoleChanged(teamId, userId, newRole))

                    // Trigger worker để đồng bộ thành viên nhóm
                    val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                    val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(syncWork)

                    Log.d(TAG, "Enqueued team sync work after receiving team member role changed event")
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
            Log.d(TAG, "Attempting to reconnect with token=${lastAuthToken.take(5)}... and teamId=$lastTeamId")
            connect(lastAuthToken, lastTeamId)
        } else {
            // Try to get connection info from AuthManager and DataStoreManager
            val newToken = authManager.getAuthToken()
            val currentTeamId = runBlocking { dataStoreManager.getCurrentTeamId() }

            if (newToken != null && !currentTeamId.isNullOrEmpty()) {
                Log.d(TAG, "Reconnecting with new token and teamId from storage")
                lastAuthToken = newToken
                lastTeamId = currentTeamId
                connect(newToken, currentTeamId)
            } else {
                Log.e(TAG, "Cannot reconnect: missing connection information")
                // Đánh dấu là đã ngắt kết nối để UI có thể hiển thị thông báo
                _connectionState.value = ConnectionState.ERROR
            }
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

    /**
     * Inscrever-se em um canal de equipe
     */
    fun subscribeToTeam(teamId: Long) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.d(TAG, "Não é possível inscrever-se no canal da equipe $teamId: WebSocket não está conectado")
            return
        }

        val subscribeMessage = JSONObject().apply {
            put("event", "subscribe")
            put("channel", "private-teams.$teamId")
        }.toString()

        Log.d(TAG, "Inscrevendo-se no canal: private-teams.$teamId")
        webSocket?.send(subscribeMessage)
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

    /**
     * Trigger đồng bộ dữ liệu team cho member mới join
     */
    private fun triggerTeamDataSyncForNewMember(teamId: String, userId: String) {
        try {
            Log.d(TAG, "🔄 [NEW_MEMBER_SYNC] Bắt đầu trigger đồng bộ dữ liệu cho member mới:")
            Log.d(TAG, "   - Team ID: $teamId")
            Log.d(TAG, "   - User ID: $userId")

            val workManager = androidx.work.WorkManager.getInstance(applicationContext)

            // 1. Đồng bộ thông tin team và members
            val teamSyncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.TeamSyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(androidx.work.workDataOf("team_id" to teamId, "new_member_id" to userId))
                .build()
            workManager.enqueue(teamSyncWork)
            Log.d(TAG, "✅ [NEW_MEMBER_SYNC] Đã enqueue TeamSyncWorker")

            // 2. Đồng bộ tất cả dữ liệu team cho member mới
            val generalSyncWork = androidx.work.OneTimeWorkRequestBuilder<com.example.taskapplication.workers.SyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(androidx.work.workDataOf("team_id" to teamId, "sync_for_new_member" to true, "sync_type" to "new_member_data"))
                .build()
            workManager.enqueue(generalSyncWork)
            Log.d(TAG, "✅ [NEW_MEMBER_SYNC] Đã enqueue SyncWorker cho member mới")

            Log.d(TAG, "🎉 [NEW_MEMBER_SYNC] Hoàn thành trigger đồng bộ dữ liệu cho member mới")

        } catch (e: Exception) {
            Log.e(TAG, "💥 [NEW_MEMBER_SYNC] Lỗi khi trigger đồng bộ dữ liệu cho member mới: ${e.message}", e)
        }
    }
}

sealed class ChatEvent {
    data class NewMessage(val message: Message) : ChatEvent()
    data class MessageRead(val readStatus: MessageReadStatus) : ChatEvent()
    data class UserTyping(val userId: String, val isTyping: Boolean, val teamId: String) : ChatEvent()
    data class MessageReaction(val messageId: String, val userId: String, val reaction: String, val timestamp: Long) : ChatEvent()
    data class MessageUpdated(val message: Message) : ChatEvent()
    data class MessageDeleted(val messageId: String, val deletedAt: Long = System.currentTimeMillis()) : ChatEvent()
    data class TeamInvitation(val teamId: String, val inviterId: String, val invitationId: String) : ChatEvent()
    data class TeamInvitationAccepted(val teamId: String, val invitationId: String, val userId: String) : ChatEvent()
    data class TeamInvitationRejected(val teamId: String, val invitationId: String) : ChatEvent()
    data class TeamInvitationCancelled(val teamId: String, val invitationId: String) : ChatEvent()

    // Thêm các sự kiện mới cho team và team member
    data class TeamUpdated(val teamId: String, val name: String, val description: String) : ChatEvent()
    data class TeamMemberAdded(val teamId: String, val userId: String, val role: String) : ChatEvent()
    data class TeamMemberRemoved(val teamId: String, val userId: String) : ChatEvent()
    data class TeamMemberRoleChanged(val teamId: String, val userId: String, val newRole: String) : ChatEvent()
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}