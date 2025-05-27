package com.example.taskapplication.data.websocket

/**
 * Configuration for Laravel Reverb WebSocket connection
 */
object ReverbConfig {
    const val WEBSOCKET_URL = "ws://10.0.2.2:8080/ws"
    
    fun getWebSocketUrl(authToken: String): String {
        return "$WEBSOCKET_URL?token=$authToken"
    }
}

/**
 * Channel configuration for Reverb subscriptions
 */
object ChannelConfig {
    fun teamChannel(teamId: Int): String = "private-teams.$teamId"
    fun userChannel(userId: Int): String = "private-users.$userId"
    
    fun teamChannel(teamId: String): String {
        val teamIdInt = teamId.toIntOrNull() ?: return "private-teams.$teamId"
        return "private-teams.$teamIdInt"
    }
    
    fun userChannel(userId: String): String {
        val userIdInt = userId.toIntOrNull() ?: return "private-users.$userId"
        return "private-users.$userIdInt"
    }
}

/**
 * Reverb event data structure
 */
data class ReverbEvent(
    val event: String,
    val channel: String,
    val data: org.json.JSONObject?
)

/**
 * Reverb logger for debugging
 */
object ReverbLogger {
    private const val TAG = "Reverb"
    
    fun logConnection(url: String) {
        android.util.Log.d(TAG, "🔗 Connecting to: $url")
    }
    
    fun logEvent(event: String, channel: String, data: String) {
        android.util.Log.d(TAG, "📡 Event: $event")
        android.util.Log.d(TAG, "📢 Channel: $channel")
        android.util.Log.d(TAG, "📄 Data: $data")
    }
    
    fun logSubscription(channel: String, success: Boolean) {
        if (success) {
            android.util.Log.d(TAG, "✅ Subscribed to: $channel")
        } else {
            android.util.Log.e(TAG, "❌ Failed to subscribe: $channel")
        }
    }
    
    fun logError(error: String, exception: Exception? = null) {
        android.util.Log.e(TAG, "❌ Error: $error")
        exception?.let { android.util.Log.e(TAG, "Stack trace:", it) }
    }
}
