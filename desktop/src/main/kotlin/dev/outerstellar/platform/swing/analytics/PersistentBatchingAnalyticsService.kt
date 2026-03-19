package dev.outerstellar.platform.swing.analytics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.outerstellar.platform.analytics.AnalyticsService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory

class PersistentBatchingAnalyticsService(
    private val writeKey: String,
    dataDir: Path = Path.of("./data"),
    /** Drop all events when the file exceeds this size. Oldest events are removed first. */
    private val maxFileSizeBytes: Long = 2 * 1024 * 1024L,
    /** During a failed flush, discard events older than this many days. */
    private val maxEventAgeDays: Long = 30L,
    httpClient: HttpClient = HttpClient.newHttpClient(),
) : AnalyticsService {
    private val logger = LoggerFactory.getLogger(PersistentBatchingAnalyticsService::class.java)
    private val file = dataDir.resolve("analytics.ndjson")
    private val mapper = jacksonObjectMapper()
    private val client = httpClient
    private val authHeader =
        "Basic " + Base64.getEncoder().encodeToString("$writeKey:".toByteArray())

    init {
        Files.createDirectories(dataDir)
    }

    override fun identify(userId: String, traits: Map<String, Any>) {
        append(
            mapOf(
                "type" to "identify",
                "userId" to userId,
                "traits" to traits,
                "timestamp" to Instant.now().toString(),
            )
        )
    }

    override fun track(userId: String, event: String, properties: Map<String, Any>) {
        append(
            mapOf(
                "type" to "track",
                "userId" to userId,
                "event" to event,
                "properties" to properties,
                "timestamp" to Instant.now().toString(),
            )
        )
    }

    override fun page(userId: String, path: String) {
        append(
            mapOf(
                "type" to "page",
                "userId" to userId,
                "name" to path,
                "properties" to mapOf("path" to path),
                "timestamp" to Instant.now().toString(),
            )
        )
    }

    @Synchronized
    private fun append(event: Map<String, Any>) {
        try {
            val currentSize = if (Files.exists(file)) Files.size(file) else 0L
            if (currentSize >= maxFileSizeBytes) {
                // File is full — drop the oldest half to make room, then append
                trimToHalf()
            }
            val payload = event + mapOf("messageId" to UUID.randomUUID().toString())
            val line = mapper.writeValueAsString(payload) + "\n"
            Files.write(
                file,
                line.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } catch (e: Exception) {
            logger.warn("Failed to persist analytics event: {}", e.message)
        }
    }

    @Synchronized
    fun flush() {
        if (!Files.exists(file)) return
        val lines = Files.readAllLines(file).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            deleteFile()
            return
        }

        var malformedCount = 0
        val events =
            lines.mapNotNull { line ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    mapper.readValue(line, Map::class.java) as Map<String, Any>
                } catch (e: Exception) {
                    logger.warn("Skipping malformed analytics line: {}", e.message)
                    malformedCount++
                    null
                }
            }

        if (malformedCount > 0) {
            // Rewrite the file without the corrupt lines so they are not retried on future flushes
            if (events.isEmpty()) {
                deleteFile()
            } else {
                val clean = events.joinToString("\n") { mapper.writeValueAsString(it) } + "\n"
                Files.writeString(file, clean)
            }
            logger.info("Removed {} malformed analytics lines from disk", malformedCount)
        }

        if (events.isEmpty()) {
            return
        }

        try {
            val batch = mapOf("batch" to events, "messageId" to UUID.randomUUID().toString())
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://api.segment.io/v1/batch"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(batch)))
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..299) {
                deleteFile()
                logger.info("Analytics batch flushed: {} events", events.size)
            } else {
                logger.warn(
                    "Analytics batch flush rejected with HTTP {}: {} events kept",
                    response.statusCode(),
                    events.size,
                )
                pruneOldEvents(events)
            }
        } catch (e: Exception) {
            logger.warn(
                "Analytics flush failed (no network?), {} events retained: {}",
                events.size,
                e.message,
            )
            pruneOldEvents(events)
        }
    }

    /**
     * After a failed flush, discard events older than [maxEventAgeDays] and rewrite the file. This
     * bounds how much data accumulates during a long offline period.
     */
    private fun pruneOldEvents(events: List<Map<String, Any>>) {
        val cutoff = Instant.now().minus(maxEventAgeDays, ChronoUnit.DAYS)
        val kept =
            events.filter { event ->
                val ts =
                    (event["timestamp"] as? String)?.let {
                        runCatching { Instant.parse(it) }.getOrNull()
                    }
                ts == null || ts.isAfter(cutoff)
            }
        val dropped = events.size - kept.size
        if (dropped > 0) {
            logger.info("Pruned {} analytics events older than {} days", dropped, maxEventAgeDays)
        }
        if (kept.isEmpty()) {
            deleteFile()
        } else {
            val content = kept.joinToString("\n") { mapper.writeValueAsString(it) } + "\n"
            Files.writeString(file, content)
        }
    }

    /**
     * When the file exceeds [maxFileSizeBytes], drop the oldest half of the lines to make room. The
     * newest events are always preferred over the oldest.
     */
    private fun trimToHalf() {
        if (!Files.exists(file)) return
        val lines = Files.readAllLines(file).filter { it.isNotBlank() }
        val keep = lines.drop(lines.size / 2)
        logger.warn(
            "Analytics file exceeded {}KB — dropped {} oldest events",
            maxFileSizeBytes / 1024,
            lines.size - keep.size,
        )
        if (keep.isEmpty()) {
            deleteFile()
        } else {
            Files.writeString(file, keep.joinToString("\n") + "\n")
        }
    }

    private fun deleteFile() {
        Files.deleteIfExists(file)
    }
}
