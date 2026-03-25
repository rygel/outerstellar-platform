package io.github.rygel.stellar.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_SIZE = 5000L
private const val DEFAULT_TTL_SECONDS = 300L

interface StellarCache {
    fun <T> get(key: String): T?

    fun put(key: String, value: Any, ttlSeconds: Long = DEFAULT_TTL_SECONDS)

    fun invalidate(key: String)

    fun invalidateByPrefix(prefix: String)
}

class CaffeineStellarCache : StellarCache {
    private data class CacheEntry(val value: Any, val ttlSeconds: Long)

    private val cache =
        Caffeine.newBuilder()
            .maximumSize(DEFAULT_MAX_SIZE)
            .expireAfter(
                object : Expiry<String, CacheEntry> {
                    override fun expireAfterCreate(key: String, value: CacheEntry, currentTime: Long): Long =
                        TimeUnit.SECONDS.toNanos(value.ttlSeconds)

                    override fun expireAfterUpdate(
                        key: String,
                        value: CacheEntry,
                        currentTime: Long,
                        currentDuration: Long,
                    ): Long = TimeUnit.SECONDS.toNanos(value.ttlSeconds)

                    override fun expireAfterRead(
                        key: String,
                        value: CacheEntry,
                        currentTime: Long,
                        currentDuration: Long,
                    ): Long = currentDuration
                }
            )
            .recordStats()
            .build<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST") override fun <T> get(key: String): T? = cache.getIfPresent(key)?.value as? T

    override fun put(key: String, value: Any, ttlSeconds: Long) {
        cache.put(key, CacheEntry(value, ttlSeconds))
    }

    override fun invalidate(key: String) {
        cache.invalidate(key)
    }

    override fun invalidateByPrefix(prefix: String) {
        val keysToRemove = cache.asMap().keys.filter { it.startsWith(prefix) }
        cache.invalidateAll(keysToRemove)
    }
}

object NoOpStellarCache : StellarCache {
    override fun <T> get(key: String): T? = null

    override fun put(key: String, value: Any, ttlSeconds: Long) {
        // No-op
    }

    override fun invalidate(key: String) {
        // No-op
    }

    override fun invalidateByPrefix(prefix: String) {
        // No-op
    }
}
