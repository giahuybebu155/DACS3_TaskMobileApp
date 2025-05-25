package com.example.taskapplication.data.api.adapter

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * TypeAdapter tùy chỉnh cho Gson để xử lý việc chuyển đổi giữa chuỗi ngày tháng và số
 * Hỗ trợ cả trường hợp API trả về ngày tháng dưới dạng chuỗi hoặc số
 */
class DateTypeAdapter : TypeAdapter<Long>() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): Long? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                try {
                    reader.nextLong()
                } catch (e: NumberFormatException) {
                    // Nếu không thể đọc là Long, thử đọc là Double và chuyển đổi
                    reader.nextDouble().toLong()
                }
            }
            JsonToken.STRING -> {
                val dateString = reader.nextString()
                try {
                    // Thử phân tích chuỗi ngày tháng thành timestamp
                    dateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
                } catch (e: ParseException) {
                    // Nếu không thể phân tích, trả về thời gian hiện tại
                    System.currentTimeMillis()
                }
            }
            else -> {
                // Bỏ qua giá trị không hợp lệ và trả về thời gian hiện tại
                reader.skipValue()
                System.currentTimeMillis()
            }
        }
    }
}
