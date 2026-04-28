package com.assistant.android.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preference_table")
data class Preference(
    @PrimaryKey val key: String,
    val value: String
)
