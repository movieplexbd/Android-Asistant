package com.assistant.android.memory.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assistant.android.memory.entity.Preference

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preference_table")
    fun getAllPreferences(): List<Preference>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: Preference)

    @Query("DELETE FROM preference_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM preference_table WHERE key = :key LIMIT 1")
    suspend fun getPreference(key: String): Preference?
}
