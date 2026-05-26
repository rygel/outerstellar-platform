package io.github.rygel.outerstellar.platform.web

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenBucketTest {

    @Test
    fun `allows up to max requests then denies`() {
        val bucket = TokenBucket(3, 60_000L)

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())
    }

    @Test
    fun `resets after window expires`() {
        val bucket = TokenBucket(2, 50L)

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())

        Thread.sleep(60L)

        assertTrue(bucket.tryConsume())
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())
    }

    @Test
    fun `is thread-safe under concurrent access`() {
        val bucket = TokenBucket(10, 60_000L)
        val threadCount = 4
        val attemptsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val accepted = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(attemptsPerThread) {
                        if (bucket.tryConsume()) {
                            accepted.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(10, accepted.get(), "Exactly maxRequests should succeed despite concurrency")
    }
}
