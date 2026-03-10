package dev.outerstellar.starter

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.*
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer

class HomePageEndToEndTest : PostgresWebTest() {
  @Test
  fun `home page is available on running server`() {
    val repository = JooqMessageRepository(testDsl, testDsl)
    repository.seedStarterMessages()

    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    val messageService = MessageService(repository, outbox, transactionManager, cache)
    val pageFactory = WebPageFactory(repository, true)
    val i18n = I18nService.fromResourceBundle("messages")
    
    val securityService = mockk<SecurityService>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    
    val appHandler = app(messageService, repository, outbox, cache, createRenderer(), pageFactory, testConfig, i18n, securityService, userRepository)
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
