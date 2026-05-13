package io.github.rygel.outerstellar.platform.persistence

object QueryCount {
    private val count = ThreadLocal.withInitial { 0 }

    fun drain(): Int {
        val v = count.get()
        count.set(0)
        return v
    }

    fun increment() {
        count.set(count.get() + 1)
    }
}
