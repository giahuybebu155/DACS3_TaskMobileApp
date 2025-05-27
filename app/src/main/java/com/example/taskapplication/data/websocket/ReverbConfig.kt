package com.example.taskapplication.data.websocket

/**
 * Configuration for Laravel Reverb WebSocket connection
 * Based on official API documentation
 */
object ReverbConfig {
    // ✅ OFFICIAL Laravel Reverb Configuration (from API docs)
    const val REVERB_APP_KEY = "8tbaaum6noyzpvygcb1q"
    const val REVERB_APP_ID = "401709"
    const val WEBSOCKET_HOST = "10.0.2.2"
    const val WEBSOCKET_PORT = 8080
    const val API_BASE_URL = "http://10.0.2.2:8000/api/"

    // ✅ PRIMARY WebSocket URL (Pusher protocol format)
    fun getWebSocketUrl(): String {
        return "ws://$WEBSOCKET_HOST:$WEBSOCKET_PORT/app/$REVERB_APP_KEY"
    }

    // ✅ Channel naming conventions
    fun getTeamChannel(teamId: String): String {
        return "private-teams.$teamId"
    }

    fun getUserChannel(userId: String): String {
        return "private-users.$userId"
    }

    // ✅ Authentication format for channel subscription
    fun formatAuthToken(authToken: String): String {
        return "Bearer $authToken"
    }

    // Alternative endpoints (fallback only)
    fun getAlternativeUrls(): List<String> {
        return listOf(
            "ws://$WEBSOCKET_HOST:$WEBSOCKET_PORT/app/$REVERB_APP_KEY",
            "ws://$WEBSOCKET_HOST:$WEBSOCKET_PORT/",
            "ws://$WEBSOCKET_HOST:$WEBSOCKET_PORT/websocket",
            "ws://$WEBSOCKET_HOST:$WEBSOCKET_PORT/ws"
        )
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
