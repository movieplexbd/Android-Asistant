package com.jarvis.ceotitan.brain.cache

import com.jarvis.ceotitan.database.dao.CacheDao
import com.jarvis.ceotitan.database.entities.CacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheBrain @Inject constructor(
    private val cacheDao: CacheDao
) {
    private val memoryCache = LinkedHashMap<String, String>(50, 0.75f, true)
    private val MAX_MEMORY_SIZE = 50

    suspend fun get(query: String): String? {
        val normalizedQuery = normalize(query)

        memoryCache[normalizedQuery]?.let { return it }

        val cached = cacheDao.getByQuery(normalizedQuery)
        if (cached != null && !isExpired(cached.timestamp)) {
            memoryCache[normalizedQuery] = cached.response
            cacheDao.incrementHitCount(cached.id)
            return cached.response
        }
        return null
    }

    suspend fun put(query: String, response: String) {
        val normalizedQuery = normalize(query)
        memoryCache[normalizedQuery] = response
        if (memoryCache.size > MAX_MEMORY_SIZE) {
            memoryCache.entries.first().let { memoryCache.remove(it.key) }
        }

        val existing = cacheDao.getByQuery(normalizedQuery)
        if (existing != null) {
            cacheDao.update(existing.copy(response = response, timestamp = System.currentTimeMillis()))
        } else {
            cacheDao.insert(CacheEntity(query = normalizedQuery, response = response))
        }
    }

    suspend fun clearExpired() {
        val expireTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        cacheDao.deleteExpired(expireTime)
    }

    suspend fun clearAll() {
        memoryCache.clear()
        cacheDao.deleteAll()
    }

    private fun normalize(query: String) = query.lowercase().trim()

    private fun isExpired(timestamp: Long): Boolean {
        val maxAge = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - timestamp > maxAge
    }
}
