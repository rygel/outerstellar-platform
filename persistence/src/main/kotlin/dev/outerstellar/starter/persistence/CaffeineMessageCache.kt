package dev.outerstellar.starter.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.util.concurrent.TimeUnit

class CaffeineMessageCache(registry: MeterRegistry? = null) : MessageCache {
    private val cache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .recordStats()
        .build<String, List<Any>>()

    init {
        if (registry != null) {
            CaffeineCacheMetrics.monitor(registry, cache, "message_cache")
        }
    }

    override fun get(key: String): List<Any>? = cache.getIfPresent(key)

    override fun put(key: String, value: List<Any>) {
        cache.put(key, value)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun getStats(): Map<String, Any> {
        val stats = cache.stats()
        return mapOf(
            "estimatedSize" to cache.estimatedSize(),
            "hitCount" to stats.hitCount(),
            "missCount" to stats.missCount(),
            "loadSuccessCount" to stats.loadSuccessCount(),
            "loadFailureCount" to stats.loadFailureCount(),
            "totalLoadTime" to stats.totalLoadTime(),
            "evictionCount" to stats.evictionCount(),
            "evictionWeight" to stats.evictionWeight(),
            "hitRate" to stats.hitRate(),
            "missRate" to stats.missRate(),
            "averageLoadPenalty" to stats.averageLoadPenalty()
        )
    }
}
