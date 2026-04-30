package com.jarvis.ceotitan.memory

import com.jarvis.ceotitan.database.dao.InteractionDao
import com.jarvis.ceotitan.database.dao.MemoryDao
import com.jarvis.ceotitan.database.dao.UserCustomDao
import com.jarvis.ceotitan.database.entities.InteractionEntity
import com.jarvis.ceotitan.database.entities.MemoryEntity
import com.jarvis.ceotitan.database.entities.UserCustomEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao,
    private val interactionDao: InteractionDao,
    private val userCustomDao: UserCustomDao
) {
    private val contextMap = mutableMapOf<String, String>()

    suspend fun saveInteraction(command: String, response: String) {
        interactionDao.insert(
            InteractionEntity(command = command, response = response)
        )
        extractAndSaveContext(command)
    }

    suspend fun saveMemory(key: String, value: String, category: String = "general") {
        val existing = memoryDao.getByKey(key)
        if (existing != null) {
            memoryDao.update(existing.copy(value = value, usageCount = existing.usageCount + 1))
        } else {
            memoryDao.insert(MemoryEntity(key = key, value = value, category = category))
        }
    }

    suspend fun getMemory(key: String): String? {
        return contextMap[key] ?: memoryDao.getByKey(key)?.value
    }

    fun getRecentInteractions(limit: Int = 50): Flow<List<InteractionEntity>> {
        return interactionDao.getRecent(limit)
    }

    suspend fun learnCustomWord(word: String, meaning: String) {
        userCustomDao.insert(UserCustomEntity(word = word, meaning = meaning))
    }

    suspend fun getAllCustomWords(): List<UserCustomEntity> {
        return userCustomDao.getAll()
    }

    fun setContext(key: String, value: String) {
        contextMap[key] = value
    }

    fun getContext(key: String): String? = contextMap[key]

    fun clearContext() = contextMap.clear()

    private suspend fun extractAndSaveContext(command: String) {
        val contactPatterns = Regex("(?:call|message|msg|কল|মেসেজ)\\s+(\\w+)", RegexOption.IGNORE_CASE)
        contactPatterns.find(command)?.let { match ->
            val contact = match.groupValues[1]
            setContext("last_contact", contact)
            setContext("last_person", contact)
        }

        val appPatterns = Regex("(?:open|kholo|খোলো|launch)\\s+(\\w+)", RegexOption.IGNORE_CASE)
        appPatterns.find(command)?.let { match ->
            val app = match.groupValues[1]
            setContext("current_app", app)
            setContext("last_opened_app", app)
        }
    }

    suspend fun deleteAllMemory() {
        memoryDao.deleteAll()
        contextMap.clear()
    }
}
