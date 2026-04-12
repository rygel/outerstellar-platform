package io.github.rygel.outerstellar.platform.persistence

interface MessageCache {
    fun get(key: String): Any?

    fun put(key: String, value: Any)

    fun getOrPut(key: String, loader: () -> Any): Any = get(key) ?: loader().also { put(key, it) }

    fun invalidate(key: String)

    fun invalidateAll()

    fun invalidateByPrefix(prefix: String) = invalidateAll()

    fun getStats(): Map<String, Any>
}

object NoOpMessageCache : MessageCache {
    override fun get(key: String): Any? = null

    override fun put(key: String, value: Any) {
        // No-op
    }

    override fun getOrPut(key: String, loader: () -> Any): Any = loader()

    override fun invalidate(key: String) {
        // No-op
    }

    override fun invalidateAll() {
        // No-op
    }

    override fun getStats(): Map<String, Any> = emptyMap()
}
