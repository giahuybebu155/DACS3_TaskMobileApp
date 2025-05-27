package com.example.taskapplication.data.api

import com.example.taskapplication.BuildConfig
import com.example.taskapplication.data.api.response.PersonalTaskListResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class is a helper for direct API access without going through the repository layer.
 * Normally you should use Repository classes, but this can be useful for quick prototyping
 * or for components that don't need the full repository functionality.
 */
@Singleton
class ApiClient @Inject constructor(
    private val authInterceptor: AuthInterceptor
) {
    private val baseUrl = "http://10.0.2.2:8000/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        // Giảm timeout để tránh ANR khi server down
        .connectTimeout(5, TimeUnit.SECONDS)  // Giảm từ 30s xuống 5s
        .readTimeout(10, TimeUnit.SECONDS)    // Giảm từ 30s xuống 10s
        .writeTimeout(5, TimeUnit.SECONDS)    // Giảm từ 30s xuống 5s
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(PersonalTaskListResponse::class.java, PersonalTaskListResponseAdapter())
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}