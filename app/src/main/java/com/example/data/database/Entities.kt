package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userQuery: String,
    val assistantReply: String,
    val toolUsed: String? = null,
    val speakerConfidence: Float = 1.0f,
    val isOwnerVerified: Boolean = true,
    val languageCode: String = "en",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)
