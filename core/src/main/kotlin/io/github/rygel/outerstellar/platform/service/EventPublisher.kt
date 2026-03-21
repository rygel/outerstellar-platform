package io.github.rygel.outerstellar.platform.service

interface EventPublisher {
    fun publishRefresh(targetId: String)
}

object NoOpEventPublisher : EventPublisher {
    override fun publishRefresh(targetId: String) {
        // No-op
    }
}
