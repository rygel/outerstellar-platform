package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Caffeine
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.web.RateLimiter")

private const val DEFAULT_MAX_REQUESTS = 10
private const val DEFAULT_WINDOW_MS = 60_000L
private const val RESET_MAX_REQUESTS = 5
private const val RESET_WINDOW_MS = 900_000L // 15 minutes
private const val MAX_BUCKETS = 10_000L

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

/** Per-path rate limit configuration. */
data class RateLimit(val maxRequests: Int, val windowMs: Long)

private val SENSITIVE_PATHS =
    mapOf(
        "/api/v1/auth/reset-request" to RateLimit(RESET_MAX_REQUESTS, RESET_WINDOW_MS),
        "/api/v1/auth/reset-confirm" to RateLimit(RESET_MAX_REQUESTS, RESET_WINDOW_MS),
        "/auth/components/reset-confirm" to RateLimit(RESET_MAX_REQUESTS, RESET_WINDOW_MS),
    )

fun rateLimitFilter(
    maxRequests: Int = DEFAULT_MAX_REQUESTS,
    windowMs: Long = DEFAULT_WINDOW_MS,
    pathPrefixes: List<String> =
        listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/reset-request",
            "/api/v1/auth/reset-confirm",
            // HTML form auth actions (sign-in, register, recover) all POST to the same path
            "/auth/components/result",
            "/auth/components/reset-confirm",
        ),
): Filter {
    val buckets =
        Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterWrite(RESET_WINDOW_MS * 2, TimeUnit.MILLISECONDS)
            .build<String, TokenBucket>()

    return Filter { next: HttpHandler ->
        {
                request ->
            val path = request.uri.path
            if (pathPrefixes.any { path.startsWith(it) }) {
                val clientIp =
                    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                        ?: request.header("X-Real-IP")
                        ?: "unknown"

                val override = SENSITIVE_PATHS.entries.find { path.startsWith(it.key) }?.value
                val effectiveMax = override?.maxRequests ?: maxRequests
                val effectiveWindow = override?.windowMs ?: windowMs

                val key = "$clientIp:$path"
                val bucket = buckets.get(key) { TokenBucket(effectiveMax, effectiveWindow) }

                if (bucket.tryConsume()) {
                    next(request)
                } else {
                    logger.warn("Rate limit exceeded for {} on {}", clientIp, path)
                    Response(Status.TOO_MANY_REQUESTS).body("Too many requests. Please try again later.")
                }
            } else {
                next(request)
            }
        }
    }
}
