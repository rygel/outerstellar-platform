package io.github.rygel.stellar.service

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory rate limiter backed by Caffeine cache.
 *
 * Each key gets an [AtomicInteger] counter that expires after [windowSeconds]. Once the counter reaches [maxRequests]
 * the key is considered rate-limited until the cache entry expires and a fresh window begins.
 *
 * **Limitation:** This implementation is per-JVM-instance only. In a distributed deployment with multiple server
 * instances behind a load balancer, each instance maintains independent counters. A user can effectively multiply their
 * allowed rate by the number of instances (N * maxRequests per window).
 *
 * For production multi-instance deployments, replace with a Redis-backed implementation using INCR + EXPIRE for atomic
 * distributed counting.
 */
class StellarRateLimiter(private val maxRequests: Int = 10, private val windowSeconds: Long = 60) {
    private val counters =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(windowSeconds)).build<String, AtomicInteger>()

    /** Returns `true` if the request is allowed, `false` if the caller has exceeded the rate limit for this window. */
    fun checkLimit(key: String): Boolean {
        val counter = counters.get(key) { AtomicInteger(0) }!!
        return counter.incrementAndGet() <= maxRequests
    }

    /** Returns how many requests remain in the current window for [key]. */
    fun remaining(key: String): Int {
        val current = counters.getIfPresent(key)?.get() ?: 0
        return (maxRequests - current).coerceAtLeast(0)
    }
}
