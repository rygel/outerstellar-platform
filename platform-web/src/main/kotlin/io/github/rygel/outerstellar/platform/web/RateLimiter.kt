package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.web.RateLimiter")

private const val DEFAULT_MAX_REQUESTS = 10
private const val DEFAULT_WINDOW_MS = 60_000L
private const val RESET_MAX_REQUESTS = 5
private const val RESET_WINDOW_MS = 900_000L // 15 minutes
private const val MAX_BUCKETS = 10_000L
private const val ACCOUNT_MAX_REQUESTS = 20
private const val ACCOUNT_WINDOW_MS = 900_000L // 15 minutes

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
    trustedProxies: List<String> = emptyList(),
): Filter {
    val ipBuckets =
        Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterWrite(RESET_WINDOW_MS * 2, TimeUnit.MILLISECONDS)
            .build<String, TokenBucket>()

    val accountBuckets =
        Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterWrite(ACCOUNT_WINDOW_MS * 2, TimeUnit.MILLISECONDS)
            .build<String, TokenBucket>()

    return Filter { next: HttpHandler ->
        { request ->
            val path = request.uri.path
            if (pathPrefixes.any { path.startsWith(it) }) {
                val sourceAddress = request.source?.address
                val clientIp =
                    if (trustedProxies.isNotEmpty() && sourceAddress != null && sourceAddress in trustedProxies) {
                        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                            ?: request.header("X-Real-IP")
                            ?: sourceAddress
                    } else if (sourceAddress != null) {
                        sourceAddress
                    } else {
                        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                            ?: request.header("X-Real-IP")
                            ?: "unknown"
                    }

                val override = SENSITIVE_PATHS.entries.find { path.startsWith(it.key) }?.value
                val effectiveMax = override?.maxRequests ?: maxRequests
                val effectiveWindow = override?.windowMs ?: windowMs

                val ipKey = "$clientIp:$path"
                val ipBucket = ipBuckets.get(ipKey) { TokenBucket(effectiveMax, effectiveWindow) }

                if (!ipBucket.tryConsume()) {
                    logger.warn("Rate limit exceeded for {} on {}", clientIp, path)
                    return@Filter Response(Status.TOO_MANY_REQUESTS).body("Too many requests. Please try again later.")
                }

                val account = extractAccountIdentifier(request)
                if (account != null) {
                    val accountKey = "account:$account"
                    val accountBucket =
                        accountBuckets.get(accountKey) { TokenBucket(ACCOUNT_MAX_REQUESTS, ACCOUNT_WINDOW_MS) }

                    if (!accountBucket.tryConsume()) {
                        logger.warn("Per-account rate limit exceeded for account {} on {}", account, path)
                        return@Filter Response(Status.TOO_MANY_REQUESTS)
                            .body("Too many login attempts for this account. Please try again later.")
                    }
                }

                next(request)
            } else {
                next(request)
            }
        }
    }
}

internal fun extractAccountIdentifier(request: org.http4k.core.Request): String? {
    val contentType = request.header("content-type").orEmpty()
    if (!contentType.contains("json") && !contentType.contains("form")) return null

    val body = request.bodyString()
    if (body.isBlank()) return null

    return if (contentType.contains("json")) {
        extractJsonValue(body, "username") ?: extractJsonValue(body, "email")
    } else {
        val params =
            body.split("&").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null to null
            }
        params["email"]?.trim()?.lowercase() ?: params["username"]?.trim()?.lowercase()
    }
}

private fun extractJsonValue(json: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\""
    val match = Regex(pattern).find(json)
    return match?.groupValues?.get(1)?.trim()?.lowercase()
}
