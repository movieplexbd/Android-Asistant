package com.assistant.android.memory.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assistant.android.memory.entity.ContextEntity

@Dao
interface ContextDao {
    @Query("SELECT * FROM context_table ORDER BY timestamp DESC")
    fun getAllContexts(): List<ContextEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contextEntity: ContextEntity)

    @Query("DELETE FROM context_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM context_table WHERE key = :key LIMIT 1")
    suspend fun getContext(key: String): ContextEntity?
}
