package dev.outerstellar.starter

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.PasswordEncoder
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.PostgresWebTest
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.StubOutboxRepository
import dev.outerstellar.starter.web.StubTransactionManager
import dev.outerstellar.starter.web.WebPageFactory
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer

class HomePageEndToEndTest {
  @Test
  fun `home page is available on running server`() {
    PostgresWebTest.setup()
    val repository = JooqMessageRepository(PostgresWebTest.testDsl, PostgresWebTest.testDsl)
    repository.seedStarterMessages()

    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    val messageService = MessageService(repository, outbox, transactionManager, cache)
    val pageFactory = WebPageFactory(repository)
    
    val securityService = mockk<SecurityService>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    
    val appHandler = app(
        messageService, 
        repository, 
        outbox, 
        cache, 
        createRenderer(), 
        pageFactory, 
        PostgresWebTest.testConfig, 
        securityService, 
        userRepository, 
        passwordEncoder
    )
    val server = appHandler.asServer(Jetty(0)).start()

    try {
      val actualPort = server.port()
      val response =
        org.http4k.client.JavaHttpClient()(Request(GET, "http://localhost:$actualPort/"))

      assertEquals(Status.OK, response.status)
      assertTrue(response.bodyString().contains("Outerstellar Starter"))
      assertTrue(response.header("content-type")?.contains("text/html") == true)
    } finally {
      server.stop()
    }
  }
}
