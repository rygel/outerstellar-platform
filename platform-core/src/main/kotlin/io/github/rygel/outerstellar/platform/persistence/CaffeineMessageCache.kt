package io.github.rygel.outerstellar.platform.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.StoredMessage
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_MAX_SIZE = 1000L
private const val DEFAULT_TTL_MINUTES = 10L

class CaffeineMessageCache(
    maxSize: Long = DEFAULT_MAX_SIZE,
    ttlMinutes: Long = DEFAULT_TTL_MINUTES,
    meterRegistry: MeterRegistry? = null,
) : MessageCache {
    private val cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build<String, Any>()

    private val generations = ConcurrentHashMap<String, AtomicLong>()

    init {
        if (meterRegistry != null) {
            CaffeineCacheMetrics.monitor(meterRegistry, cache, "messageCache")
        }
    }

    override fun getMessage(syncId: String): StoredMessage? = cache.getIfPresent("entity:$syncId") as? StoredMessage

    override fun putMessage(syncId: String, message: StoredMessage) {
        cache.put("entity:$syncId", message)
    }

    override fun getMessageList(key: String): PagedResult<MessageSummary>? =
        cache.getIfPresent(key) as? PagedResult<MessageSummary>

    override fun putMessageList(key: String, result: PagedResult<MessageSummary>) {
        cache.put(key, result)
    }

    override fun getMessageListOrPut(
        key: String,
        loader: () -> PagedResult<MessageSummary>,
    ): PagedResult<MessageSummary> = cache.get(key) { loader() } as PagedResult<MessageSummary>

    override fun invalidate(key: String) {
        cache.invalidate(key)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun invalidateNamespace(namespace: String) {
        generations.computeIfAbsent(namespace) { AtomicLong(0) }.incrementAndGet()
        val prefix = "$namespace:"
        cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(prefix) })
    }

    fun generationKey(namespace: String, key: String): String {
        val gen = generations.computeIfAbsent(namespace) { AtomicLong(0) }.get()
        return "$namespace:$gen:$key"
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
