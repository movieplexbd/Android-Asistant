package com.assistant.android.memory.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assistant.android.memory.entity.Contact

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact_table ORDER BY name ASC")
    fun getAllContacts(): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Query("DELETE FROM contact_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM contact_table WHERE name LIKE :name LIMIT 1")
    suspend fun getContactByName(name: String): Contact?
}
