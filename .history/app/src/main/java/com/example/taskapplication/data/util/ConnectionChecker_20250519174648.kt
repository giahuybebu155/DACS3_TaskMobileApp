package com.example.taskapplication.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionChecker @Inject constructor(
    private val context: Context
) {
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
                    val runtime = Runtime.getRuntime()
                    val process = runtime.exec("ping -c 1 10.0.2.2")
                    val exitValue = process.waitFor()
                    val pingSuccess = (exitValue == 0)

                    Log.d("ConnectionChecker", "Ping to 10.0.2.2 success: $pingSuccess")

                    // Nếu ping thành công, trả về true
                    if (pingSuccess) {
                        return true
                    } else {
                        Log.e("ConnectionChecker", "Cannot ping to 10.0.2.2")
                    }
                } catch (e: Exception) {
                    Log.e("ConnectionChecker", "Error checking server connection", e)
                }
            }

            // Mặc định trả về kết quả dựa trên kiểm tra network capabilities
            Log.d("ConnectionChecker", "Network available: $isAvailable")
            return isAvailable
        } catch (e: Exception) {
            Log.e("ConnectionChecker", "Error checking network connection", e)
            return false
        }
    }
}