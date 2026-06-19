package io.github.rygel.outerstellar.platform.persistence

object QueryCount {
    private val count = ThreadLocal.withInitial { 0 }

    fun drain(): Int {
        val v = count.get()
        // remove() rather than set(0) so the entry is dropped from the thread's map entirely — avoids
        // leaving a stale Integer on pooled threads (HikariCP/Netty) between requests.
        count.remove()
        return v
    }

    fun increment() {
        count.set(count.get() + 1)
    }
}
