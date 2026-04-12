package io.github.rygel.outerstellar.platform.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_SIZE = 1000L
private const val DEFAULT_TTL_MINUTES = 10L

class CaffeineMessageCache(meterRegistry: MeterRegistry? = null) : MessageCache {
    private val cache =
        Caffeine.newBuilder()
            .maximumSize(DEFAULT_MAX_SIZE)
            .expireAfterWrite(DEFAULT_TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build<String, Any>()

    init {
        if (meterRegistry != null) {
            CaffeineCacheMetrics.monitor(meterRegistry, cache, "messageCache")
        }
    }

    override fun get(key: String): Any? = cache.getIfPresent(key)

    override fun put(key: String, value: Any) {
        cache.put(key, value)
    }

    override fun getOrPut(key: String, loader: () -> Any): Any = cache.get(key) { loader() }

    override fun invalidate(key: String) {
        cache.invalidate(key)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun invalidateByPrefix(prefix: String) {
        cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(prefix) })
    }

    override fun getStats(): Map<String, Any> {
        val stats = cache.stats()
        return mapOf(
            "hitCount" to stats.hitCount(),
            "missCount" to stats.missCount(),
            "evictionCount" to stats.evictionCount(),
            "evictionWeight" to stats.evictionWeight(),
            "hitRate" to stats.hitRate(),
            "missRate" to stats.missRate(),
            "averageLoadPenalty" to stats.averageLoadPenalty(),
        )
    }
}
