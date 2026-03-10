package dev.outerstellar.starter.web

import dev.outerstellar.starter.di.apiClientModule
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.di.webModule
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class SmokeE2ETest : KoinTest {

    @BeforeEach
    fun setup() {
        startKoin {
            modules(
                coreModule,
                persistenceModule,
                apiClientModule,
                webModule,
                module {
                    single(named("jdbcUrl")) { "jdbc:h2:mem:test;MODE=PostgreSQL" }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `web application starts up and responds to health check`() {
        val app: HttpHandler by inject(named("webServer"))
        val response = app(Request(GET, "/health"))
        
        assertEquals(Status.OK, response.status)
        assertEquals("ok", response.bodyString())
    }

    @Test
    fun `metrics endpoint is available`() {
        val app: HttpHandler by inject(named("webServer"))
        val response = app(Request(GET, "/metrics"))
        
        assertEquals(Status.OK, response.status)
    }
}
