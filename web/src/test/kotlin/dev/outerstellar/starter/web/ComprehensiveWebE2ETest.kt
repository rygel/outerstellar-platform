package dev.outerstellar.starter.web

import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.di.webModule
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.security.securityModule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.PolyHandler
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class ComprehensiveWebE2ETest : KoinTest {

    private val app: PolyHandler by inject(named("webServer"))
    private val messageRepo: MessageRepository by inject()
    private val contactRepo: ContactRepository by inject()

    @BeforeTest
    fun setup() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single {
                        AppConfig(
                            jdbcUrl =
                                "jdbc:h2:mem:comprehensive_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                            devMode = true,
                        )
                    }
                },
                persistenceModule,
                coreModule,
                securityModule,
                webModule,
            )
        }

        // Manual migration and seeding for the test DB
        val ds = getKoin().get<javax.sql.DataSource>()
        dev.outerstellar.starter.infra.migrate(ds)

        messageRepo.seedStarterMessages()
        contactRepo.seedStarterContacts()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `main pages should render correctly without errors`() {
        val routes = listOf("/", "/contacts", "/auth", "/errors/not-found")

        routes.forEach { path ->
            val response = app.http!!(Request(GET, path))
            assertEquals(Status.OK, response.status, "Path $path failed")
            val body = response.bodyString()
            assertTrue(body.contains("<!DOCTYPE html>"), "Path $path did not return HTML")
            assertTrue(body.contains("site.css?v="), "Path $path is missing CSS cache buster")
        }
    }

    @Test
    fun `contacts page should display seeded data`() {
        val response = app.http!!(Request(GET, "/contacts"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("Alice Smith"), "Contacts page missing Alice")
        assertTrue(body.contains("Bob Johnson"), "Contacts page missing Bob")
        assertTrue(body.contains("Charlie Brown"), "Contacts page missing Charlie")
    }

    @Test
    fun `theme switching should refresh the page correctly`() {
        val response =
            app.http!!(Request(GET, "/components/navigation/page?theme=monokai&pagePath=/"))
        assertEquals(Status.OK, response.status)
        assertTrue(
            response.bodyString().contains("site.css?v="),
            "Refreshed page missing CSS cache buster",
        )
    }

    @Test
    fun `language switching should refresh the page correctly`() {
        val response =
            app.http!!(Request(GET, "/components/navigation/page?lang=fr&pagePath=/contacts"))
        assertEquals(Status.OK, response.status)
        assertTrue(
            response.bodyString().contains("site.css?v="),
            "Refreshed page missing CSS cache buster",
        )
    }
}
