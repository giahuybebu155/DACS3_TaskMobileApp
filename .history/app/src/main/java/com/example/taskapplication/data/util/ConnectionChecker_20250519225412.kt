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

            // Kiểm tra kết nối đến server
            if (isAvailable) {
                try {
                    // Thử kết nối đến API server
                    val apiAvailable = checkServerConnection(API_HOST, API_PORT)
                    val wsAvailable = checkServerConnection(API_HOST, WS_PORT)

                    Log.d(TAG, "API server available: $apiAvailable")
                    Log.d(TAG, "WebSocket server available: $wsAvailable")

                    // Nếu cả hai server đều khả dụng, trả về true
                    if (apiAvailable && wsAvailable) {
                        return true
                    } else {
                        Log.e(TAG, "Cannot connect to servers - API: $apiAvailable, WebSocket: $wsAvailable")
                    }
                } catch (e: Exception) {
                    Log.e("ConnectionChecker", "Error checking server connection", e)
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
    private fun checkServerConnection(host: String, port: String): Boolean {
        return try {
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