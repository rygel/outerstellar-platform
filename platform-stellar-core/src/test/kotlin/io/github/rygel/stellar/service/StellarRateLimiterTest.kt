package io.github.rygel.stellar.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StellarRateLimiterTest {

    @Test
    fun `allows requests up to max limit`() {
        val limiter = StellarRateLimiter(maxRequests = 5, windowSeconds = 60)

        for (i in 1..5) {
            assertTrue(limiter.checkLimit("user:1"), "Request $i should be allowed")
        }
    }

    @Test
    fun `blocks requests after max limit exceeded`() {
        val limiter = StellarRateLimiter(maxRequests = 3, windowSeconds = 60)

        assertTrue(limiter.checkLimit("user:1"))
        assertTrue(limiter.checkLimit("user:1"))
        assertTrue(limiter.checkLimit("user:1"))
        assertFalse(limiter.checkLimit("user:1"), "4th request should be blocked")
        assertFalse(limiter.checkLimit("user:1"), "5th request should also be blocked")
    }

    @Test
    fun `remaining decreases with each request`() {
        val limiter = StellarRateLimiter(maxRequests = 5, windowSeconds = 60)

        assertEquals(5, limiter.remaining("user:1"))
        limiter.checkLimit("user:1")
        assertEquals(4, limiter.remaining("user:1"))
        limiter.checkLimit("user:1")
        assertEquals(3, limiter.remaining("user:1"))
    }

    @Test
    fun `remaining returns zero when limit exhausted`() {
        val limiter = StellarRateLimiter(maxRequests = 2, windowSeconds = 60)

        limiter.checkLimit("user:1")
        limiter.checkLimit("user:1")
        assertEquals(0, limiter.remaining("user:1"))

        // Even after more attempts, remaining stays at zero
        limiter.checkLimit("user:1")
        assertEquals(0, limiter.remaining("user:1"))
    }

    @Test
    fun `different keys have independent limits`() {
        val limiter = StellarRateLimiter(maxRequests = 2, windowSeconds = 60)

        assertTrue(limiter.checkLimit("user:1"))
        assertTrue(limiter.checkLimit("user:1"))
        assertFalse(limiter.checkLimit("user:1"))

        // user:2 should still have full quota
        assertTrue(limiter.checkLimit("user:2"))
        assertTrue(limiter.checkLimit("user:2"))
        assertFalse(limiter.checkLimit("user:2"))
    }

    @Test
    fun `resets after window expires`() {
        // Use a 1-second window so the test completes quickly
        val limiter = StellarRateLimiter(maxRequests = 2, windowSeconds = 1)

        assertTrue(limiter.checkLimit("user:1"))
        assertTrue(limiter.checkLimit("user:1"))
        assertFalse(limiter.checkLimit("user:1"))

        // Wait for the window to expire
        Thread.sleep(1_500)

        assertTrue(limiter.checkLimit("user:1"), "Should be allowed again after window expires")
        assertEquals(1, limiter.remaining("user:1"))
    }
}
