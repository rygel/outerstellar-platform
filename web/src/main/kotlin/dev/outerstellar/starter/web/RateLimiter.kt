package dev.outerstellar.starter.web

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.web.RateLimiter")

private const val DEFAULT_MAX_REQUESTS = 10
private const val DEFAULT_WINDOW_MS = 60_000L
private const val CLEANUP_THRESHOLD = 1000

class TokenBucket(private val maxRequests: Int, private val windowMs: Long) {
    private val count = AtomicInteger(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())

    fun tryConsume(): Boolean {
        val now = System.currentTimeMillis()
        if (now - windowStart.get() > windowMs) {
            windowStart.set(now)
            count.set(1)
            return true
        }
        return count.incrementAndGet() <= maxRequests
    }
}

fun rateLimitFilter(
    maxRequests: Int = DEFAULT_MAX_REQUESTS,
    windowMs: Long = DEFAULT_WINDOW_MS,
    pathPrefixes: List<String> = listOf("/api/v1/auth/login", "/api/v1/auth/register"),
): Filter {
    val buckets = ConcurrentHashMap<String, TokenBucket>()

    return Filter { next: HttpHandler ->
        { request ->
            val path = request.uri.path
            if (pathPrefixes.any { path.startsWith(it) }) {
                    val clientIp =
                        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                            ?: request.header("X-Real-IP")
                            ?: "unknown"

                    val key = "$clientIp:$path"
                    val bucket = buckets.computeIfAbsent(key) { TokenBucket(maxRequests, windowMs) }

                    if (bucket.tryConsume()) {
                        next(request)
                    } else {
                        logger.warn("Rate limit exceeded for {} on {}", clientIp, path)
                        Response(Status.TOO_MANY_REQUESTS)
                            .body("Too many requests. Please try again later.")
                    }
                } else {
                    next(request)
                }
                .also {
                    // Periodic cleanup of old entries
                    if (buckets.size > CLEANUP_THRESHOLD) {
                        buckets.keys.removeIf { key ->
                            val b = buckets[key]
                            b == null
                        }
                    }
                }
        }
    }
}
