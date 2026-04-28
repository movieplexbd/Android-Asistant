package com.assistant.android.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class History(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val command: String,
    val aiResponse: String,
    val timestamp: Long
)
