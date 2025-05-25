package com.example.taskapplication.di

import android.content.Context
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.util.ConnectionChecker
import com.example.taskapplication.util.DataStoreManager
import com.example.taskapplication.util.NetworkUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNetworkUtils(
        @ApplicationContext context: Context
    ): NetworkUtils {
        return NetworkUtils(context)
    }

    @Provides
    @Singleton
    fun provideConnectionChecker(
        @ApplicationContext context: Context
    ): ConnectionChecker {
        return ConnectionChecker(context)
    }

    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: Context
    ): DataStoreManager {
        return DataStoreManager(context)
    }
}