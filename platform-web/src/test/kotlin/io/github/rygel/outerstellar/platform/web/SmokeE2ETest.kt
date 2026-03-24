package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.di.webModule
import io.github.rygel.outerstellar.platform.security.securityModule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.server.PolyHandler
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.inject

class SmokeE2ETest : KoinTest {

    private val app: PolyHandler by inject(named("webServer"))

    @BeforeTest
    fun setup() {
        stopKoin() // Ensure clean state
        startKoin { modules(persistenceModule, coreModule, securityModule, webModule) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `web application starts up and responds to health check`() {
        val response = app.http!!(Request(GET, "/health"))
        assertEquals(200, response.status.code)
        val body = response.bodyString()
        assertTrue(body.contains("\"UP\""), "Health check did not return UP status: $body")
    }

    @Test
    fun `metrics endpoint is available`() {
        val response = app.http!!(Request(GET, "/metrics"))
        assertEquals(200, response.status.code)
    }
}
