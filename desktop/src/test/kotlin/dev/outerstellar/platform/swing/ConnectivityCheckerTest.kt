package dev.outerstellar.platform.swing

import io.mockk.every
import io.mockk.mockk
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for ConnectivityChecker.
 *
 * Covers:
 * - isOnline true when server returns 2xx
 * - isOnline false when server returns 5xx
 * - isOnline false on network exception
 * - observer notified when state changes from online to offline
 * - observer notified when state changes from offline to online
 * - observer NOT notified when state does not change
 * - start/stop lifecycle does not throw
 */
class ConnectivityCheckerTest {

    private fun mockHttp(statusCode: Int): HttpClient {
        val client = mockk<HttpClient>()
        val response = mockk<HttpResponse<Void>>()
        every { response.statusCode() } returns statusCode
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<Void>>()) } returns
            response
        return client
    }

    private fun mockHttpThrows(): HttpClient {
        val client = mockk<HttpClient>()
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<Void>>()) } throws
            java.net.ConnectException("refused")
        return client
    }

    @Test
    fun `isOnline true when server returns 200`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        checker.check()
        assertTrue(checker.isOnline)
    }

    @Test
    fun `isOnline true when server returns 204`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(204))
        checker.check()
        assertTrue(checker.isOnline)
    }

    @Test
    fun `isOnline false when server returns 500`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(500))
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `isOnline false when server returns 503`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(503))
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `isOnline false on network exception`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows())
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `observer notified when state changes to offline`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        checker.check() // prime as online

        val latch = CountDownLatch(1)
        val states = mutableListOf<Boolean>()
        checker.addObserver { online ->
            states.add(online)
            latch.countDown()
        }

        // Now go offline
        val offlineChecker =
            ConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows()).also { c
                ->
                // Share the same internal state by simulating: set online first, then check offline
            }
        // Use the same checker instance but swap state via check()
        val offlineClient = mockHttpThrows()
        val checkerWithOffline =
            ConnectivityChecker("http://localhost/health", httpClient = offlineClient)
        checkerWithOffline.check() // starts as offline (default true → becomes false → notifies)

        // For the original checker: it starts online; now simulate going offline
        // by making a new checker that goes online→offline
        val transitioning =
            ConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        val latch2 = CountDownLatch(1)
        val captured = mutableListOf<Boolean>()
        transitioning.addObserver { online ->
            captured.add(online)
            latch2.countDown()
        }
        transitioning.check() // goes online (default was true → stays true, no notify)

        // Now simulate going offline (state was true after check, now false)
        val offlineTransitioning =
            ConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows())
        val latch3 = CountDownLatch(1)
        val results = mutableListOf<Boolean>()
        offlineTransitioning.addObserver { state ->
            results.add(state)
            latch3.countDown()
        }
        offlineTransitioning.check() // default isOnline=true, check returns false → notify(false)

        assertTrue(latch3.await(1, TimeUnit.SECONDS), "Observer should have been notified")
        assertEquals(listOf(false), results)
    }

    @Test
    fun `observer notified when state changes from offline to online`() {
        // Start offline
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows())
        checker.check() // online=true → false, notified

        val latch = CountDownLatch(1)
        val results = mutableListOf<Boolean>()
        checker.addObserver { online ->
            results.add(online)
            latch.countDown()
        }

        // Now recover (need a fresh check with 200)
        // We can't change httpClient on existing checker, so test the behavior directly:
        // Create checker that starts offline then goes online
        val offlineThenOnline =
            object {
                var callCount = 0
                val client = mockk<HttpClient>()
                val response200 =
                    mockk<HttpResponse<Void>>().also { every { it.statusCode() } returns 200 }

                init {
                    every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<Void>>()) }
                        .answers {
                            callCount++
                            if (callCount == 1) throw java.net.ConnectException("refused")
                            response200
                        }
                }
            }

        val c2 =
            ConnectivityChecker("http://localhost/health", httpClient = offlineThenOnline.client)
        val latch2 = CountDownLatch(2)
        val states2 = mutableListOf<Boolean>()
        c2.addObserver { online ->
            states2.add(online)
            latch2.countDown()
        }
        c2.check() // first: online=true → false, notify(false)
        c2.check() // second: false → true, notify(true)

        assertTrue(latch2.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(false, true), states2)
    }

    @Test
    fun `observer NOT notified when state does not change`() {
        val checker = ConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        var callCount = 0
        checker.addObserver { callCount++ }

        checker.check() // true→true (initial is true, stays true)
        checker.check() // true→true again

        assertEquals(0, callCount, "Observer should not be called when state does not change")
    }

    @Test
    fun `start and stop do not throw`() {
        val checker =
            ConnectivityChecker(
                "http://localhost/health",
                intervalSeconds = 60,
                httpClient = mockHttp(200),
            )
        checker.start()
        checker.start() // idempotent
        checker.stop()
        checker.stop() // idempotent
    }
}
