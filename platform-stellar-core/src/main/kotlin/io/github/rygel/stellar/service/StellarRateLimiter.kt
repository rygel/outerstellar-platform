package io.github.rygel.stellar.service

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple in-memory rate limiter backed by a Caffeine cache.
 *
 * Each key gets an [AtomicInteger] counter that expires after [windowSeconds]. Once the counter reaches [maxRequests]
 * the key is considered rate-limited until the cache entry expires and a fresh window begins.
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
