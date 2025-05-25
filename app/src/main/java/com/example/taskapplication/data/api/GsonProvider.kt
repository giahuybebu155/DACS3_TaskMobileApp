package com.example.taskapplication.data.api

import com.example.taskapplication.data.api.adapter.DateTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Cung cấp một phiên bản Gson đã được cấu hình với các TypeAdapter tùy chỉnh
 */
object GsonProvider {
    /**
     * Tạo một phiên bản Gson đã được cấu hình với các TypeAdapter tùy chỉnh
     */
    fun createGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Long::class.java, DateTypeAdapter())
            .registerTypeAdapter(Long::class.javaPrimitiveType, DateTypeAdapter())
            .create()
    }
}
