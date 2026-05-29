package io.github.rygel.outerstellar.platform.service

sealed interface PlatformEvent {
    data class Refresh(val target: RefreshTarget) : PlatformEvent
}

enum class RefreshTarget(val panelId: String) {
    MESSAGE_LIST_PANEL("message-list-panel"),
    CONTACT_LIST_PANEL("contact-list-panel"),
}

interface EventPublisher {
    fun publish(event: PlatformEvent)
}

object NoOpEventPublisher : EventPublisher {
    override fun publish(event: PlatformEvent) {
        // No-op
    }
}
