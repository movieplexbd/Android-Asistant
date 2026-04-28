package com.assistant.android.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.assistant.android.memory.dao.ContactDao
import com.assistant.android.memory.dao.HistoryDao
import com.assistant.android.memory.dao.PreferenceDao
import com.assistant.android.memory.dao.ContextDao
import com.assistant.android.memory.entity.Contact
import com.assistant.android.memory.entity.History
import com.assistant.android.memory.entity.Preference
import com.assistant.android.memory.entity.ContextEntity

@Database(entities = [Contact::class, History::class, Preference::class, ContextEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun historyDao(): HistoryDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun contextDao(): ContextDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistant_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
