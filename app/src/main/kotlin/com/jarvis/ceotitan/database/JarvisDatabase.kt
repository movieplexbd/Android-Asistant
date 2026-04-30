package com.jarvis.ceotitan.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jarvis.ceotitan.database.dao.*
import com.jarvis.ceotitan.database.entities.*

@Database(
    entities = [
        CacheEntity::class,
        MemoryEntity::class,
        InteractionEntity::class,
        BusinessNoteEntity::class,
        UserCustomEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun memoryDao(): MemoryDao
    abstract fun interactionDao(): InteractionDao
    abstract fun businessNoteDao(): BusinessNoteDao
    abstract fun userCustomDao(): UserCustomDao
}
