package com.example.taskapplication.data.websocket

import android.util.Log
import com.example.taskapplication.data.api.model.ApiMessage
import com.example.taskapplication.data.api.model.ApiMessageDelete
import com.example.taskapplication.data.api.model.ApiMessageUpdate
import com.example.taskapplication.data.api.model.ApiReadStatus
import com.example.taskapplication.data.api.model.ApiReaction
import com.example.taskapplication.data.api.model.ApiTypingStatus
import com.example.taskapplication.data.api.model.SubscribeRequest
import com.example.taskapplication.data.api.model.WebSocketEvent
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "ChatWebSocketClient"
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var messageHandler: MessageHandler? = null
    private var isConnected = false
    private val subscribedChannels = mutableSetOf<String>()

    fun setMessageHandler(handler: MessageHandler) {
        this.messageHandler = handler
    }

    fun connect(token: String) {
        if (isConnected) {
            Log.d(TAG, "WebSocket already connected")
            return
        }

        val request = Request.Builder()
            .url("ws://10.0.2.2:6001/ws?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                isConnected = true

                // Resubscribe to previously subscribed channels
                subscribedChannels.forEach { channel ->
                    subscribeToChannel(channel)
                }

                messageHandler?.onConnectionOpened()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                try {
                    val jsonObject = JsonParser.parseString(text).asJsonObject
                    val event = jsonObject.get("event").asString

                    when (event) {
                        "team.chat.message.created" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val message = gson.fromJson(data, ApiMessage::class.java)
                            messageHandler?.onNewMessage(message)
                        }
                        "team.chat.message.updated" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val messageUpdate = gson.fromJson(data, ApiMessageUpdate::class.java)
                            messageHandler?.onMessageUpdated(messageUpdate)
                        }
                        "team.chat.message.deleted" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val messageDelete = gson.fromJson(data, ApiMessageDelete::class.java)
                            messageHandler?.onMessageDeleted(messageDelete)
                        }
                        "team.chat.message.read" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val readStatus = gson.fromJson(data, ApiReadStatus::class.java)
                            messageHandler?.onMessageRead(readStatus)
                        }
                        "team.chat.typing" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val typingStatus = gson.fromJson(data, ApiTypingStatus::class.java)
                            messageHandler?.onTypingStatusChanged(typingStatus)
                        }
                        "team.chat.message.reaction" -> {
                            val data = jsonObject.get("data").asJsonObject
                            val reaction = gson.fromJson(data, ApiReaction::class.java)
                            messageHandler?.onReactionAdded(reaction)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failure", t)
                isConnected = false
                messageHandler?.onConnectionError(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closed: $reason")
                isConnected = false
                messageHandler?.onConnectionClosed()
            }
        })
    }

    fun subscribeToTeamChat(teamId: String) {
        val channel = "team.$teamId.chat"
        subscribedChannels.add(channel)

        if (isConnected) {
            subscribeToChannel(channel)
        }
    }

    private fun subscribeToChannel(channel: String) {
        val subscribeRequest = SubscribeRequest("subscribe", channel)
        val json = gson.toJson(subscribeRequest)
        webSocket?.send(json)
        Log.d(TAG, "Subscribed to channel: $channel")
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        Log.d(TAG, "WebSocket disconnected")
    }

    interface MessageHandler {
        fun onConnectionOpened()
        fun onNewMessage(message: ApiMessage)
        fun onMessageUpdated(message: ApiMessageUpdate)
        fun onMessageDeleted(message: ApiMessageDelete)
        fun onMessageRead(readStatus: ApiReadStatus)
        fun onTypingStatusChanged(typingStatus: ApiTypingStatus)
        fun onReactionAdded(reaction: ApiReaction)
        fun onConnectionError(throwable: Throwable)
        fun onConnectionClosed()
    }
}
