package com.example.taskapplication.di

import android.content.Context
import androidx.room.Room
import com.example.taskapplication.data.database.AppDatabase
import com.example.taskapplication.data.database.dao.*
import com.example.taskapplication.data.local.dao.TeamRoleHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.util.Log

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Danh sách các tên cơ sở dữ liệu cũ cần xóa
    private val OLD_DATABASE_NAMES = listOf(
        "task_app_database",
        "task_app_database_new",
        "task_app_database_v11",
        "task_app_database_v10",
        "task_app_database_v9",
        "task_app_database_v8"
        // Không thêm "task_app_database_v12" và "task_app_database_v13" vào đây
        // để tránh xóa cơ sở dữ liệu hiện tại
    )

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // Xóa các cơ sở dữ liệu cũ
        deleteOldDatabases(context)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "task_app_database_v13" // Sử dụng cùng tên với AppDatabase.kt
        )
        // Migrations are defined in AppDatabase.kt
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun providePersonalTaskDao(database: AppDatabase): PersonalTaskDao {
        return database.personalTaskDao()
    }

    @Provides
    @Singleton
    fun provideSubtaskDao(database: AppDatabase): SubtaskDao {
        return database.subtaskDao()
    }

    @Provides
    @Singleton
    fun provideTeamTaskDao(database: AppDatabase): TeamTaskDao {
        return database.teamTaskDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideMessageReadStatusDao(database: AppDatabase): MessageReadStatusDao {
        return database.messageReadStatusDao()
    }

    @Provides
    @Singleton
    fun provideMessageReactionDao(database: AppDatabase): MessageReactionDao {
        return database.messageReactionDao()
    }

    @Provides
    @Singleton
    fun provideTeamDao(database: AppDatabase): TeamDao {
        return database.teamDao()
    }

    @Provides
    @Singleton
    fun provideTeamMemberDao(database: AppDatabase): TeamMemberDao {
        return database.teamMemberDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideTeamInvitationDao(database: AppDatabase): TeamInvitationDao {
        return database.teamInvitationDao()
    }

    @Provides
    @Singleton
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    @Singleton
    fun provideNotificationSettingsDao(database: AppDatabase): NotificationSettingsDao {
        return database.notificationSettingsDao()
    }

    @Provides
    @Singleton
    fun provideCalendarEventDao(database: AppDatabase): CalendarEventDao {
        return database.calendarEventDao()
    }

    @Provides
    @Singleton
    fun provideTeamRoleHistoryDao(database: AppDatabase): TeamRoleHistoryDao {
        return database.teamRoleHistoryDao()
    }

    @Provides
    @Singleton
    fun provideKanbanBoardDao(database: AppDatabase): KanbanBoardDao {
        return database.kanbanBoardDao()
    }

    @Provides
    @Singleton
    fun provideKanbanColumnDao(database: AppDatabase): KanbanColumnDao {
        return database.kanbanColumnDao()
    }

    @Provides
    @Singleton
    fun provideKanbanTaskDao(database: AppDatabase): KanbanTaskDao {
        return database.kanbanTaskDao()
    }

    @Provides
    @Singleton
    fun provideTeamDocumentDao(database: AppDatabase): TeamDocumentDao {
        return database.teamDocumentDao()
    }

    @Provides
    @Singleton
    fun provideDocumentFolderDao(database: AppDatabase): DocumentFolderDao {
        return database.documentFolderDao()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(database: AppDatabase): DocumentDao {
        return database.documentDao()
    }

    @Provides
    @Singleton
    fun provideDocumentVersionDao(database: AppDatabase): DocumentVersionDao {
        return database.documentVersionDao()
    }

    @Provides
    @Singleton
    fun provideDocumentPermissionDao(database: AppDatabase): DocumentPermissionDao {
        return database.documentPermissionDao()
    }

    @Provides
    @Singleton
    fun provideAttachmentDao(database: AppDatabase): AttachmentDao {
        return database.attachmentDao()
    }

    /**
     * Xóa các cơ sở dữ liệu cũ để tránh xung đột
     */
    private fun deleteOldDatabases(context: Context) {
        for (dbName in OLD_DATABASE_NAMES) {
            try {
                val mainDbFile = context.getDatabasePath("$dbName.db")
                val shmFile = context.getDatabasePath("$dbName-shm")
                val walFile = context.getDatabasePath("$dbName-wal")

                val mainDeleted = if (mainDbFile.exists()) mainDbFile.delete() else false
                val shmDeleted = if (shmFile.exists()) shmFile.delete() else false
                val walDeleted = if (walFile.exists()) walFile.delete() else false

                Log.d("DatabaseModule", "Deleting old database: $dbName - " +
                        "main: ${if (mainDeleted) "deleted" else "not found"}, " +
                        "shm: ${if (shmDeleted) "deleted" else "not found"}, " +
                        "wal: ${if (walDeleted) "deleted" else "not found"}")
            } catch (e: Exception) {
                Log.e("DatabaseModule", "Error deleting database $dbName", e)
            }
        }
    }
}