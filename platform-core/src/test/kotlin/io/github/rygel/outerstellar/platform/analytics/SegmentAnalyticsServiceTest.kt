package io.github.rygel.outerstellar.platform.analytics

import io.mockk.spyk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject

class SegmentAnalyticsServiceTest {

    @Test
    fun `identify calls send with identify endpoint`() {
        val service = spyk(SegmentAnalyticsService("test-key"), recordPrivateCalls = true)
        service.identify("user1", mapOf("name" to "Alice"))
        verify { service["send"]("identify", ofType<JsonObject>()) }
    }

    @Test
    fun `track calls send with track endpoint`() {
        val service = spyk(SegmentAnalyticsService("test-key"), recordPrivateCalls = true)
        service.track("user1", "event", mapOf("key" to "value"))
        verify { service["send"]("track", ofType<JsonObject>()) }
    }

    @Test
    fun `page calls send with page endpoint`() {
        val service = spyk(SegmentAnalyticsService("test-key"), recordPrivateCalls = true)
        service.page("user1", "/home")
        verify { service["send"]("page", ofType<JsonObject>()) }
    }

    @Test
    fun `NoOpAnalyticsService implements interface and does not throw`() {
        val service: AnalyticsService = NoOpAnalyticsService()
        service.identify("user1", mapOf("name" to "Alice"))
        service.track("user1", "event", mapOf("key" to "value"))
        service.page("user1", "/home")
        assertTrue(service is AnalyticsService)
    }
}
