package io.github.rygel.outerstellar.platform.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SegmentAnalyticsServiceTest {

    @Test
    fun `identify sends identify payload`() {
        val sent = mutableListOf<SegmentEvent>()
        val service = SegmentAnalyticsService(capturingSender(sent))

        service.identify("user1", mapOf("name" to "Alice"))

        val event = sent.single()
        assertEquals("identify", event.endpoint)
        assertEquals("user1", event.payload["userId"]?.jsonPrimitive?.content)
        assertEquals("Alice", event.payload["traits"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertNotNull(event.payload["messageId"])
        assertNotNull(event.payload["context"])
    }

    @Test
    fun `track sends track payload`() {
        val sent = mutableListOf<SegmentEvent>()
        val service = SegmentAnalyticsService(capturingSender(sent))

        service.track("user1", "event", mapOf("key" to "value"))

        val event = sent.single()
        assertEquals("track", event.endpoint)
        assertEquals("user1", event.payload["userId"]?.jsonPrimitive?.content)
        assertEquals("event", event.payload["event"]?.jsonPrimitive?.content)
        assertEquals("value", event.payload["properties"]?.jsonObject?.get("key")?.jsonPrimitive?.content)
    }

    @Test
    fun `page sends page payload`() {
        val sent = mutableListOf<SegmentEvent>()
        val service = SegmentAnalyticsService(capturingSender(sent))

        service.page("user1", "/home")

        val event = sent.single()
        assertEquals("page", event.endpoint)
        assertEquals("user1", event.payload["userId"]?.jsonPrimitive?.content)
        assertEquals("/home", event.payload["name"]?.jsonPrimitive?.content)
        assertEquals("/home", event.payload["properties"]?.jsonObject?.get("path")?.jsonPrimitive?.content)
    }

    @Test
    fun `NoOpAnalyticsService implements interface and does not throw`() {
        val service: AnalyticsService = NoOpAnalyticsService()
        service.identify("user1", mapOf("name" to "Alice"))
        service.track("user1", "event", mapOf("key" to "value"))
        service.page("user1", "/home")
    }

    private fun capturingSender(sent: MutableList<SegmentEvent>): SegmentEventSender =
        SegmentEventSender { endpoint, payload ->
            sent += SegmentEvent(endpoint, payload)
        }

    private data class SegmentEvent(val endpoint: String, val payload: JsonObject)
}
