package dev.outerstellar.starter.swing.analytics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PersistentBatchingAnalyticsServiceTest {
    @TempDir lateinit var tempDir: Path

    private val mapper = jacksonObjectMapper()
    private lateinit var httpClient: HttpClient
    private lateinit var httpResponse: HttpResponse<Void>

    @BeforeEach
    fun setUp() {
        httpClient = mockk()
        httpResponse = mockk()
    }

    private fun service(maxFileSizeBytes: Long = 2 * 1024 * 1024L, maxEventAgeDays: Long = 30L) =
        PersistentBatchingAnalyticsService(
            writeKey = "test-key",
            dataDir = tempDir,
            maxFileSizeBytes = maxFileSizeBytes,
            maxEventAgeDays = maxEventAgeDays,
            httpClient = httpClient,
        )

    private val analyticsFile
        get() = tempDir.resolve("analytics.ndjson")

    private fun readEvents(): List<Map<String, Any>> =
        Files.readAllLines(analyticsFile).filter { it.isNotBlank() }.map { mapper.readValue(it) }

    private fun respondWith(statusCode: Int) {
        every { httpResponse.statusCode() } returns statusCode
        every { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) } returns httpResponse
    }

    private fun respondWithException() {
        every { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) } throws
            IOException("Connection refused")
    }

    // ── append ───────────────────────────────────────────────────────────────

    @Test
    fun `track appends event to file`() {
        service().track("alice", "Button Clicked", mapOf("button" to "sync"))

        assertTrue(Files.exists(analyticsFile))
        val events = readEvents()
        assertEquals(1, events.size)
        assertEquals("track", events[0]["type"])
        assertEquals("alice", events[0]["userId"])
        assertEquals("Button Clicked", events[0]["event"])
    }

    @Test
    fun `identify appends event to file`() {
        service().identify("alice", mapOf("role" to "admin"))

        val events = readEvents()
        assertEquals(1, events.size)
        assertEquals("identify", events[0]["type"])
        assertEquals("alice", events[0]["userId"])
    }

    @Test
    fun `page appends event to file`() {
        service().page("alice", "/messages")

        val events = readEvents()
        assertEquals(1, events.size)
        assertEquals("page", events[0]["type"])
        assertEquals("/messages", events[0]["name"])
    }

    @Test
    fun `multiple events accumulate as separate lines`() {
        val svc = service()
        svc.track("alice", "Event One")
        svc.track("alice", "Event Two")
        svc.track("alice", "Event Three")

        assertEquals(3, readEvents().size)
    }

    @Test
    fun `each event gets a unique messageId`() {
        val svc = service()
        svc.track("alice", "Event One")
        svc.track("alice", "Event Two")

        val ids = readEvents().map { it["messageId"] }
        assertEquals(2, ids.distinct().size)
    }

    // ── flush — success ───────────────────────────────────────────────────────

    @Test
    fun `flush sends all events as a single batch and deletes file on success`() {
        val svc = service()
        svc.track("alice", "Event One")
        svc.track("alice", "Event Two")
        respondWith(200)

        svc.flush()

        assertFalse(Files.exists(analyticsFile))
        verify(exactly = 1) { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) }
    }

    @Test
    fun `flush accepts any 2xx status as success`() {
        service().track("alice", "Event")
        respondWith(204)

        service().flush()

        assertFalse(Files.exists(analyticsFile))
    }

    @Test
    fun `flush does nothing when file does not exist`() {
        service().flush()

        verify(exactly = 0) { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) }
    }

    @Test
    fun `flush deletes file when it is empty`() {
        Files.writeString(analyticsFile, "\n\n")

        service().flush()

        assertFalse(Files.exists(analyticsFile))
        verify(exactly = 0) { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) }
    }

    // ── flush — failure: retain events ───────────────────────────────────────

    @Test
    fun `flush retains events when Segment returns non-2xx`() {
        val svc = service()
        svc.track("alice", "Event")
        respondWith(500)

        svc.flush()

        assertTrue(Files.exists(analyticsFile))
        assertEquals(1, readEvents().size)
    }

    @Test
    fun `flush retains events when network throws`() {
        val svc = service()
        svc.track("alice", "Event")
        respondWithException()

        svc.flush()

        assertTrue(Files.exists(analyticsFile))
        assertEquals(1, readEvents().size)
    }

    // ── flush — failure: prune old events ────────────────────────────────────

    @Test
    fun `flush prunes events older than maxEventAgeDays after failed flush`() {
        val svc = service(maxEventAgeDays = 7)
        respondWithException()

        // Write one fresh event and one stale event directly
        val fresh =
            """{"type":"track","userId":"alice","event":"Recent","timestamp":"${Instant.now()}","messageId":"1"}"""
        val stale =
            """{"type":"track","userId":"alice","event":"Old","timestamp":"${Instant.now().minus(10, ChronoUnit.DAYS)}","messageId":"2"}"""
        Files.writeString(analyticsFile, "$fresh\n$stale\n")

        svc.flush()

        assertTrue(Files.exists(analyticsFile))
        val kept = readEvents()
        assertEquals(1, kept.size)
        assertEquals("Recent", kept[0]["event"])
    }

    @Test
    fun `flush deletes file when all events are pruned after failed flush`() {
        service(maxEventAgeDays = 7).also { respondWithException() }

        val stale =
            """{"type":"track","userId":"alice","event":"Old","timestamp":"${Instant.now().minus(10, ChronoUnit.DAYS)}","messageId":"1"}"""
        Files.writeString(analyticsFile, "$stale\n")

        service(maxEventAgeDays = 7).flush()

        assertFalse(Files.exists(analyticsFile))
    }

    @Test
    fun `flush skips malformed lines and sends remaining valid events`() {
        respondWith(200)
        val valid =
            """{"type":"track","userId":"alice","event":"Good","timestamp":"${Instant.now()}","messageId":"1"}"""
        Files.writeString(analyticsFile, "not-json\n$valid\n{broken\n")

        service().flush()

        // File deleted after successful flush — malformed lines were silently dropped
        assertFalse(Files.exists(analyticsFile))
        verify(exactly = 1) { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) }
    }

    @Test
    fun `flush does not send when all lines are malformed`() {
        Files.writeString(analyticsFile, "not-json\n{broken\n")

        service().flush()

        assertFalse(Files.exists(analyticsFile))
        verify(exactly = 0) { httpClient.send(any(), any<HttpResponse.BodyHandler<Void>>()) }
    }

    // ── file size guardrail ───────────────────────────────────────────────────

    @Test
    fun `append trims oldest half of events when file exceeds maxFileSizeBytes`() {
        // Use a tiny limit so we can trigger it with a few events
        val svc = service(maxFileSizeBytes = 200)

        // Fill the file past the limit
        repeat(10) { i -> svc.track("alice", "Event $i") }

        val events = readEvents()
        // Fewer than 10 events should remain — oldest were dropped
        assertTrue(events.size < 10, "Expected some events to be trimmed, got ${events.size}")
        // The last event written must still be present
        assertTrue(events.any { it["event"] == "Event 9" }, "Newest event should be retained")
    }

    @Test
    fun `append keeps newest events when trimming`() {
        val svc = service(maxFileSizeBytes = 200)

        repeat(10) { i -> svc.track("alice", "Event $i") }

        val events = readEvents()
        val eventNames = events.map { it["event"] as String }
        // The very first events should have been dropped
        assertFalse(eventNames.contains("Event 0"), "Oldest event should have been trimmed")
    }
}
