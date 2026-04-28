package com.assistant.android.memory

import com.assistant.android.memory.dao.ContactDao
import com.assistant.android.memory.dao.HistoryDao
import com.assistant.android.memory.dao.PreferenceDao
import com.assistant.android.memory.dao.ContextDao
import com.assistant.android.memory.entity.Contact
import com.assistant.android.memory.entity.History
import com.assistant.android.memory.entity.Preference
import com.assistant.android.memory.entity.ContextEntity

class MemoryRepository(private val database: AppDatabase) {

    private val contactDao = database.contactDao()
    private val historyDao = database.historyDao()
    private val preferenceDao = database.preferenceDao()
    private val contextDao = database.contextDao()

    // Contact Memory
    suspend fun insertContact(contact: Contact) = contactDao.insert(contact)
    fun getAllContacts(): List<Contact> = contactDao.getAllContacts()
    suspend fun getContactByName(name: String): Contact? = contactDao.getContactByName(name)

    // User Preferences
    suspend fun insertPreference(preference: Preference) = preferenceDao.insert(preference)
    fun getAllPreferences(): List<Preference> = preferenceDao.getAllPreferences()
    suspend fun getPreference(key: String): Preference? = preferenceDao.getPreference(key)

    // History Memory
    suspend fun insertHistory(history: History) = historyDao.insert(history)
    fun getAllHistory(): List<History> = historyDao.getAllHistory()

    // Smart Context Memory
    suspend fun insertContext(contextEntity: ContextEntity) = contextDao.insert(contextEntity)
    fun getAllContexts(): List<ContextEntity> = contextDao.getAllContexts()
    suspend fun getContext(key: String): ContextEntity? = contextDao.getContext(key)
}
