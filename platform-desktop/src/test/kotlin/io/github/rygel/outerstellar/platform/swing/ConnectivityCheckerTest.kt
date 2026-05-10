package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.sync.engine.HttpConnectivityChecker
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

class ConnectivityCheckerTest {

    private fun mockHttp(statusCode: Int): HttpClient {
        val client = mockk<HttpClient>()
        val response = mockk<HttpResponse<Void>>()
        every { response.statusCode() } returns statusCode
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<Void>>()) } returns response
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
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        checker.check()
        assertTrue(checker.isOnline)
    }

    @Test
    fun `isOnline true when server returns 204`() {
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(204))
        checker.check()
        assertTrue(checker.isOnline)
    }

    @Test
    fun `isOnline false when server returns 500`() {
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(500))
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `isOnline false when server returns 503`() {
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(503))
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `isOnline false on network exception`() {
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows())
        checker.check()
        assertFalse(checker.isOnline)
    }

    @Test
    fun `observer notified when state changes to offline`() {
        val transitioning = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        transitioning.check() // stays online (default true → true, no notify)

        val latch3 = CountDownLatch(1)
        val results = mutableListOf<Boolean>()
        val offlineTransitioning = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttpThrows())
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
        val offlineThenOnline =
            object {
                var callCount = 0
                val client = mockk<HttpClient>()
                val response200 = mockk<HttpResponse<Void>>().also { every { it.statusCode() } returns 200 }

                init {
                    every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<Void>>()) }
                        .answers {
                            callCount++
                            if (callCount == 1) throw java.net.ConnectException("refused")
                            response200
                        }
                }
            }

        val c2 = HttpConnectivityChecker("http://localhost/health", httpClient = offlineThenOnline.client)
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
        val checker = HttpConnectivityChecker("http://localhost/health", httpClient = mockHttp(200))
        var callCount = 0
        checker.addObserver { callCount++ }

        checker.check() // true→true (initial is true, stays true)
        checker.check() // true→true again

        assertEquals(0, callCount, "Observer should not be called when state does not change")
    }

    @Test
    fun `start and stop do not throw`() {
        val checker =
            HttpConnectivityChecker("http://localhost/health", intervalSeconds = 60, httpClient = mockHttp(200))
        checker.start()
        checker.start() // idempotent
        checker.stop()
        checker.stop() // idempotent
    }
}
