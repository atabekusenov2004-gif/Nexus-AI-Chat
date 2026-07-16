package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val searchQueries: List<String>? = null,
    val sources: List<WebSourceEntity>? = null,
    val isPending: Boolean = false,
    val error: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class WebSourceEntity(
    val uri: String?,
    val title: String?
)

class Converters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).fromJson(value)
    }

    @TypeConverter
    fun fromSourceList(value: List<WebSourceEntity>?): String? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, WebSourceEntity::class.java)
        return moshi.adapter<List<WebSourceEntity>>(type).toJson(value)
    }

    @TypeConverter
    fun toSourceList(value: String?): List<WebSourceEntity>? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, WebSourceEntity::class.java)
        return moshi.adapter<List<WebSourceEntity>>(type).fromJson(value)
    }
}
