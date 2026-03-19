package dev.outerstellar.platform.web

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.platform.web.RateLimiter")

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

    fun isExpired(): Boolean = System.currentTimeMillis() - windowStart.get() > windowMs
}

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
                    // Periodic cleanup: evict buckets whose window has already expired
                    if (buckets.size > CLEANUP_THRESHOLD) {
                        buckets.entries.removeIf { (_, bucket) -> bucket.isExpired() }
                    }
                }
        }
    }
}
