package com.example.taskapplication.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionChecker @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ConnectionChecker"
        private const val API_HOST = "10.0.2.2"
        private const val API_PORT = "8000"
        private const val WS_PORT = "8080"
    }
    /**
     * Kiểm tra kết nối mạng (phiên bản đồng bộ)
     * Chỉ kiểm tra kết nối mạng cơ bản, không kiểm tra kết nối đến server
     * Sử dụng cho các trường hợp không cần kiểm tra kết nối đến server
     */
    fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork
            if (networkCapabilities == null) {
                Log.e("ConnectionChecker", "No active network")
                return false
            }

            val activeNetwork = connectivityManager.getNetworkCapabilities(networkCapabilities)
            if (activeNetwork == null) {
                Log.e("ConnectionChecker", "No network capabilities")
                return false
            }

            val isAvailable = when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d("ConnectionChecker", "WIFI connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d("ConnectionChecker", "CELLULAR connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    Log.d("ConnectionChecker", "ETHERNET connection available")
                    true
                }
                else -> {
                    Log.e("ConnectionChecker", "No suitable network transport available")
                    false
                }
            }

            return isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connection", e)
            return false
        }
    }

    /**
     * Kiểm tra kết nối mạng và kết nối đến server (phiên bản bất đồng bộ)
     * Sử dụng cho các trường hợp cần kiểm tra kết nối đến server
     */
    suspend fun isServerAvailable(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork
            if (networkCapabilities == null) {
                Log.e("ConnectionChecker", "No active network")
                return false
            }

            val activeNetwork = connectivityManager.getNetworkCapabilities(networkCapabilities)
            if (activeNetwork == null) {
                Log.e("ConnectionChecker", "No network capabilities")
                return false
            }

            val isAvailable = when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d("ConnectionChecker", "WIFI connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d("ConnectionChecker", "CELLULAR connection available")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    Log.d("ConnectionChecker", "ETHERNET connection available")
                    true
                }
                else -> {
                    Log.e("ConnectionChecker", "No suitable network transport available")
                    false
                }
            }

            // Kiểm tra kết nối đến server
            if (isAvailable) {
                try {
                    Log.d(TAG, "🌐 [SERVER_CHECK] ===== KIỂM TRA KẾT NỐI SERVER =====")
                    Log.d(TAG, "🌐 [SERVER_CHECK] API Host: $API_HOST")
                    Log.d(TAG, "🌐 [SERVER_CHECK] API Port: $API_PORT")
                    Log.d(TAG, "🌐 [SERVER_CHECK] WebSocket Port: $WS_PORT")
                    Log.d(TAG, "🌐 [SERVER_CHECK] Full API URL: http://$API_HOST:$API_PORT")
                    Log.d(TAG, "🌐 [SERVER_CHECK] Full WebSocket URL: ws://$API_HOST:$WS_PORT")

                    // Thử kết nối đến API server
                    Log.d(TAG, "🔍 [SERVER_CHECK] Checking API server connection...")
                    val apiAvailable = checkServerConnection(API_HOST, API_PORT)
                    Log.d(TAG, "📡 [SERVER_CHECK] API server result: $apiAvailable")

                    Log.d(TAG, "🔍 [SERVER_CHECK] Checking WebSocket server connection...")
                    val wsAvailable = checkServerConnection(API_HOST, WS_PORT)
                    Log.d(TAG, "📡 [SERVER_CHECK] WebSocket server result: $wsAvailable")

                    Log.d(TAG, "📊 [SERVER_CHECK] Final results:")
                    Log.d(TAG, "   - API server available: $apiAvailable")
                    Log.d(TAG, "   - WebSocket server available: $wsAvailable")

                    // Nếu cả hai server đều khả dụng, trả về true
                    if (apiAvailable && wsAvailable) {
                        Log.d(TAG, "✅ [SERVER_CHECK] Both servers available - returning TRUE")
                        return true
                    } else {
                        Log.e(TAG, "❌ [SERVER_CHECK] Cannot connect to servers - API: $apiAvailable, WebSocket: $wsAvailable")
                        Log.e(TAG, "❌ [SERVER_CHECK] Please check if Laravel server is running:")
                        Log.e(TAG, "   - php artisan serve --host=0.0.0.0 --port=8000")
                        Log.e(TAG, "   - php artisan reverb:start --host=0.0.0.0 --port=8080")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 [SERVER_CHECK] Exception when checking server connection:")
                    Log.e(TAG, "💥 [SERVER_CHECK] Exception type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "💥 [SERVER_CHECK] Exception message: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Mặc định trả về kết quả dựa trên kiểm tra network capabilities
            Log.d(TAG, "Network available: $isAvailable")
            return isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connection", e)
            return false
        }
    }

    /**
     * Kiểm tra kết nối đến server
     * @param host Địa chỉ host
     * @param port Cổng
     * @return true nếu kết nối thành công, false nếu không
     */
    private suspend fun checkServerConnection(host: String, port: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = java.net.Socket()
                val socketAddress = java.net.InetSocketAddress(host, port.toInt())
                socket.connect(socketAddress, 3000) // 3 seconds timeout
                socket.close()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to $host:$port", e)
                false
            }
        }
    }
}