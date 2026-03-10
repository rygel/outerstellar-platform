package dev.outerstellar.starter.service

interface EventPublisher {
    /**
     * Triggers a refresh of a UI component by its ID.
     */
    fun publishRefresh(targetId: String)
}

object NoOpEventPublisher : EventPublisher {
    override fun publishRefresh(targetId: String) {}
}
