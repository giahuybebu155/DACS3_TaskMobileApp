package com.example.taskapplication.di

import android.content.Context
import com.example.taskapplication.data.api.ApiService
import com.example.taskapplication.data.api.AuthInterceptor
import com.example.taskapplication.data.api.GsonProvider
import com.example.taskapplication.data.api.PersonalTaskListResponseAdapter
import com.example.taskapplication.data.api.response.PersonalTaskListResponse
import com.example.taskapplication.data.network.NetworkConnectivityObserver
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            // BALANCED TIMEOUTS - Fast enough to prevent ANR, long enough for normal operations
            .connectTimeout(8, TimeUnit.SECONDS)   // Tăng từ 5s lên 8s - đủ cho network chậm
            .readTimeout(15, TimeUnit.SECONDS)     // Tăng từ 10s lên 15s - đủ cho API response
            .writeTimeout(10, TimeUnit.SECONDS)    // Tăng từ 5s lên 10s - đủ cho upload
            .build()
    }

    @Provides
    @Singleton
    @Named("heavy_operations")
    fun provideHeavyOperationsOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            // EXTENDED TIMEOUTS - For heavy operations like file upload, large data sync
            .connectTimeout(15, TimeUnit.SECONDS)  // Longer connect for slow networks
            .readTimeout(30, TimeUnit.SECONDS)     // Longer read for large responses
            .writeTimeout(30, TimeUnit.SECONDS)    // Longer write for file uploads
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        // Sử dụng GsonProvider để tạo Gson với các TypeAdapter tùy chỉnh
        return GsonProvider.createGson().newBuilder()
            .registerTypeAdapter(PersonalTaskListResponse::class.java, PersonalTaskListResponseAdapter())
            .create()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/api/") // URL for accessing localhost from Android emulator
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNetworkConnectivityObserver(@ApplicationContext context: Context): NetworkConnectivityObserver {
        return NetworkConnectivityObserver(context)
    }
}