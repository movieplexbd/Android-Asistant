package com.jarvis.ceotitan.database.dao

import androidx.room.*
import com.jarvis.ceotitan.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE query = :query LIMIT 1")
    suspend fun getByQuery(query: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CacheEntity)

    @Update
    suspend fun update(cache: CacheEntity)

    @Query("UPDATE cache SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun incrementHitCount(id: Long)

    @Query("DELETE FROM cache WHERE timestamp < :expireTime")
    suspend fun deleteExpired(expireTime: Long)

    @Query("DELETE FROM cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun getCount(): Int
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memory WHERE category = :category ORDER BY usageCount DESC")
    fun getByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory ORDER BY timestamp DESC")
    fun getAll(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memory WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM memory")
    suspend fun deleteAll()
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<InteractionEntity>>

    @Insert
    suspend fun insert(interaction: InteractionEntity)

    @Query("DELETE FROM interactions WHERE timestamp < :before")
    suspend fun deleteOld(before: Long)

    @Query("SELECT COUNT(*) FROM interactions")
    suspend fun getCount(): Int
}

@Dao
interface BusinessNoteDao {
    @Query("SELECT * FROM business_notes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BusinessNoteEntity>>

    @Query("SELECT * FROM business_notes WHERE category = :category")
    fun getByCategory(category: String): Flow<List<BusinessNoteEntity>>

    @Insert
    suspend fun insert(note: BusinessNoteEntity)

    @Update
    suspend fun update(note: BusinessNoteEntity)

    @Delete
    suspend fun delete(note: BusinessNoteEntity)
}

@Dao
interface UserCustomDao {
    @Query("SELECT * FROM user_customs")
    suspend fun getAll(): List<UserCustomEntity>

    @Query("SELECT * FROM user_customs WHERE word = :word LIMIT 1")
    suspend fun getByWord(word: String): UserCustomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(custom: UserCustomEntity)

    @Delete
    suspend fun delete(custom: UserCustomEntity)
}
