package com.example.taskapplication.data.database

import android.content.Context
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lớp tiện ích để xóa database cũ khi có lỗi migration
 */
@Singleton
class DatabaseCleaner @Inject constructor(
    private val context: Context
) {
    /**
     * Xóa database cũ và tạo lại database mới
     * Chỉ sử dụng khi có lỗi migration không thể khắc phục
     */
    fun cleanDatabase() {
        try {
            // Lấy đường dẫn đến thư mục databases
            val databasesDir = context.getDatabasePath("task_app_database_v12").parentFile
            
            // Xóa tất cả các file database cũ
            databasesDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("task_app_database_v")) {
                    val deleted = file.delete()
                    Log.d("DatabaseCleaner", "Deleted database file ${file.name}: $deleted")
                }
            }
            
            // Xóa các file shm và wal
            databasesDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith("-shm") || file.name.endsWith("-wal")) {
                    val deleted = file.delete()
                    Log.d("DatabaseCleaner", "Deleted database file ${file.name}: $deleted")
                }
            }
            
            Log.d("DatabaseCleaner", "Database cleaned successfully")
        } catch (e: Exception) {
            Log.e("DatabaseCleaner", "Error cleaning database", e)
        }
    }
    
    /**
     * Kiểm tra xem có cần xóa database không
     */
    fun needsCleaning(): Boolean {
        val oldDbFile = context.getDatabasePath("task_app_database_v12")
        return oldDbFile.exists()
    }
}
