package dev.outerstellar.platform.analytics

interface AnalyticsService {
    fun identify(userId: String, traits: Map<String, Any> = emptyMap())

    fun track(userId: String, event: String, properties: Map<String, Any> = emptyMap())

    fun page(userId: String, path: String)
}

class NoOpAnalyticsService : AnalyticsService {
    override fun identify(userId: String, traits: Map<String, Any>) = Unit

    override fun track(userId: String, event: String, properties: Map<String, Any>) = Unit

    override fun page(userId: String, path: String) = Unit
}
