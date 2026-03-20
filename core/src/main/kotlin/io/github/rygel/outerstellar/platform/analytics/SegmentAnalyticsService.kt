package io.github.rygel.outerstellar.platform.analytics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory

class SegmentAnalyticsService(writeKey: String) : AnalyticsService {
    private val logger = LoggerFactory.getLogger(SegmentAnalyticsService::class.java)
    private val client = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper()
    private val authHeader =
        "Basic " + Base64.getEncoder().encodeToString("$writeKey:".toByteArray())

    override fun identify(userId: String, traits: Map<String, Any>) {
        send(
            "identify",
            mapOf("userId" to userId, "traits" to traits, "timestamp" to Instant.now().toString()),
        )
    }

    override fun track(userId: String, event: String, properties: Map<String, Any>) {
        send(
            "track",
            mapOf(
                "userId" to userId,
                "event" to event,
                "properties" to properties,
                "timestamp" to Instant.now().toString(),
            ),
        )
    }

    override fun page(userId: String, path: String) {
        send(
            "page",
            mapOf(
                "userId" to userId,
                "name" to path,
                "properties" to mapOf("path" to path),
                "timestamp" to Instant.now().toString(),
            ),
        )
    }

    private fun send(endpoint: String, body: Map<String, Any>) {
        try {
            val payload =
                body +
                    mapOf(
                        "messageId" to UUID.randomUUID().toString(),
                        "context" to
                            mapOf(
                                "library" to
                                    mapOf("name" to "outerstellar-platform", "version" to "1.0")
                            ),
                    )
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://api.segment.io/v1/$endpoint"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build()
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally { e ->
                logger.warn("Segment {} call failed: {}", endpoint, e.message)
                null
            }
        } catch (e: Exception) {
            logger.warn("Segment analytics error on {}: {}", endpoint, e.message)
        }
    }
}
