package com.example.taskapplication.di

import androidx.work.WorkerFactory
import com.example.taskapplication.data.worker.TaskWorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @Singleton
    abstract fun bindWorkerFactory(factory: TaskWorkerFactory): WorkerFactory
}
