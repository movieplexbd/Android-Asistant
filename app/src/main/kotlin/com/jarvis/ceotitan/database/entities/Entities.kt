package com.jarvis.ceotitan.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hitCount: Int = 0
)

@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val response: String,
    val brainLayer: String = "LOCAL",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_notes")
data class BusinessNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_customs")
data class UserCustomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val meaning: String,
    val type: String = "slang"
)
