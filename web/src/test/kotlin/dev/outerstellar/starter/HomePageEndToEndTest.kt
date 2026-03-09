package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.PostgresWebTest
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.StubOutboxRepository
import dev.outerstellar.starter.web.StubTransactionManager
import dev.outerstellar.starter.web.WebPageFactory
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
    val appHandler = app(messageService, repository, outbox, cache, createRenderer(), pageFactory, testConfig)
    val server = appHandler.asServer(Jetty(0)).start()

    try {
      val actualPort = server.port()
      val response =
        org.http4k.client.JavaHttpClient()(Request(GET, "http://localhost:$actualPort/"))

      assertEquals(Status.OK, response.status)
      assertTrue(response.bodyString().contains("Outerstellar Starter"))
      assertTrue(response.bodyString().contains("Auth Examples"))
      assertTrue(response.bodyString().contains("/api/v1/sync"))
      assertTrue(response.header("content-type")?.contains("text/html") == true)
    } finally {
      server.stop()
    }
  }
}
