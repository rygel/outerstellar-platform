package io.github.rygel.outerstellar.platform.analytics

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class SegmentAnalyticsService(writeKey: String) : AnalyticsService {
    private val logger = LoggerFactory.getLogger(SegmentAnalyticsService::class.java)
    private val client = HttpClient.newHttpClient()
    private val json = Json { encodeDefaults = true }
    private val authHeader = "Basic " + Base64.getEncoder().encodeToString("$writeKey:".toByteArray())

    override fun identify(userId: String, traits: Map<String, Any>) {
        send(
            "identify",
            buildJsonObject {
                put("userId", userId)
                put("traits", traits.toJsonObject())
                put("timestamp", Instant.now().toString())
            },
        )
    }

    override fun track(userId: String, event: String, properties: Map<String, Any>) {
        send(
            "track",
            buildJsonObject {
                put("userId", userId)
                put("event", event)
                put("properties", properties.toJsonObject())
                put("timestamp", Instant.now().toString())
            },
        )
    }

    override fun page(userId: String, path: String) {
        send(
            "page",
            buildJsonObject {
                put("userId", userId)
                put("name", path)
                put("properties", buildJsonObject { put("path", path) })
                put("timestamp", Instant.now().toString())
            },
        )
    }

    private fun send(endpoint: String, body: JsonObject) {
        try {
            val payload = buildJsonObject {
                body.forEach { (key, value) -> put(key, value) }
                put("messageId", UUID.randomUUID().toString())
                put(
                    "context",
                    buildJsonObject {
                        put(
                            "library",
                            buildJsonObject {
                                put("name", "outerstellar-platform")
                                put("version", "1.0")
                            },
                        )
                    },
                )
            }
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://api.segment.io/v1/$endpoint"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonObject.serializer(), payload)))
                    .build()
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally { e ->
                logger.warn("Segment {} call failed: {}", endpoint, e.message)
                null
            }
        } catch (e: IOException) {
            logger.warn("Segment analytics IO error on {}: {}", endpoint, e.message)
        }
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
