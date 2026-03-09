package dev.outerstellar.starter.persistence

interface MessageCache {
    fun get(key: String): List<Any>?
    fun put(key: String, value: List<Any>)
    fun invalidateAll()
}

object NoOpMessageCache : MessageCache {
    override fun get(key: String): List<Any>? = null
    override fun put(key: String, value: List<Any>) {}
    override fun invalidateAll() {}
}
