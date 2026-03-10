package dev.outerstellar.starter.persistence

interface MessageCache {
    fun get(key: String): Any?
    fun put(key: String, value: Any)
    fun invalidate(key: String)
    fun invalidateAll()
    fun getStats(): Map<String, Any>
}

object NoOpMessageCache : MessageCache {
    override fun get(key: String): Any? = null
    override fun put(key: String, value: Any) {
        // No-op
    }
    override fun invalidate(key: String) {
        // No-op
    }
    override fun invalidateAll() {
        // No-op
    }
    override fun getStats(): Map<String, Any> = emptyMap()
}
