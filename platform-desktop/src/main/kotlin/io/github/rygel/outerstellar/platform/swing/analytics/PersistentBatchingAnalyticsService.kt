package io.github.rygel.outerstellar.platform.swing.analytics

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class PersistentBatchingAnalyticsService(
    private val writeKey: String,
    dataDir: Path = Path.of("./data"),
    private val maxFileSizeBytes: Long = 2 * 1024 * 1024L,
    private val maxEventAgeDays: Long = 30L,
    httpClient: HttpClient = HttpClient.newHttpClient(),
) : AnalyticsService {
    private val logger = LoggerFactory.getLogger(PersistentBatchingAnalyticsService::class.java)
    private val file = dataDir.resolve("analytics.ndjson")
    private val json = Json { encodeDefaults = true }
    private val client = httpClient
    private val authHeader = "Basic " + Base64.getEncoder().encodeToString("$writeKey:".toByteArray())

    init {
        Files.createDirectories(dataDir)
    }

    override fun identify(userId: String, traits: Map<String, Any>) {
        append(
            buildJsonObject {
                put("type", "identify")
                put("userId", userId)
                put("traits", traits.toJsonObject())
                put("timestamp", Instant.now().toString())
            }
        )
    }

    override fun track(userId: String, event: String, properties: Map<String, Any>) {
        append(
            buildJsonObject {
                put("type", "track")
                put("userId", userId)
                put("event", event)
                put("properties", properties.toJsonObject())
                put("timestamp", Instant.now().toString())
            }
        )
    }

    override fun page(userId: String, path: String) {
        append(
            buildJsonObject {
                put("type", "page")
                put("userId", userId)
                put("name", path)
                put("properties", buildJsonObject { put("path", path) })
                put("timestamp", Instant.now().toString())
            }
        )
    }

    @Synchronized
    private fun append(event: JsonObject) {
        try {
            val currentSize = if (Files.exists(file)) Files.size(file) else 0L
            if (currentSize >= maxFileSizeBytes) {
                trimToHalf()
            }
            val payload = buildJsonObject {
                event.forEach { (key, value) -> put(key, value) }
                put("messageId", UUID.randomUUID().toString())
            }
            val line = json.encodeToString(JsonObject.serializer(), payload) + "\n"
            Files.write(file, line.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
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
                    json.decodeFromString(JsonElement.serializer(), line) as? JsonObject
                } catch (e: Exception) {
                    logger.warn("Skipping malformed analytics line: {}", e.message)
                    malformedCount++
                    null
                }
            }

        if (malformedCount > 0) {
            if (events.isEmpty()) {
                deleteFile()
            } else {
                val clean = events.joinToString("\n") { json.encodeToString(JsonElement.serializer(), it) } + "\n"
                Files.writeString(file, clean)
            }
            logger.info("Removed {} malformed analytics lines from disk", malformedCount)
        }

        if (events.isEmpty()) {
            return
        }

        try {
            val batch = buildJsonObject {
                put("batch", buildJsonArray { events.forEach { add(it) } })
                put("messageId", UUID.randomUUID().toString())
            }
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://api.segment.io/v1/batch"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonElement.serializer(), batch)))
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
            logger.warn("Analytics flush failed (no network?), {} events retained: {}", events.size, e.message)
            pruneOldEvents(events)
        }
    }

    private fun pruneOldEvents(events: List<JsonObject>) {
        val cutoff = Instant.now().minus(maxEventAgeDays, ChronoUnit.DAYS)
        val kept =
            events.filter { event ->
                val ts =
                    (event["timestamp"] as? JsonPrimitive)?.content?.let {
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
            val content = kept.joinToString("\n") { json.encodeToString(JsonElement.serializer(), it) } + "\n"
            Files.writeString(file, content)
        }
    }

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

    private fun Map<String, Any>.toJsonObject(): JsonObject = buildJsonObject {
        for ((key, value) in this@toJsonObject) {
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") put(key, (value as Map<String, Any>).toJsonObject())
                is List<*> ->
                    put(
                        key,
                        buildJsonArray {
                            for (item in value) {
                                when (item) {
                                    is String -> add(JsonPrimitive(item))
                                    is Number -> add(JsonPrimitive(item.toString().toDouble()))
                                    is Boolean -> add(JsonPrimitive(item))
                                    is Map<*, *> ->
                                        @Suppress("UNCHECKED_CAST") add((item as Map<String, Any>).toJsonObject())
                                    else -> add(JsonPrimitive(item.toString()))
                                }
                            }
                        },
                    )
                else -> put(key, value.toString())
            }
        }
    }
}
