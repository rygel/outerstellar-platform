package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.PostgresWebTest
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.StubOutboxRepository
import dev.outerstellar.starter.web.StubTransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status

class StarterAppTest : PostgresWebTest() {
  @Test
  fun `home page renders seeded starter content`() {
    val repository = JooqMessageRepository(testDsl, testDsl)
    repository.seedStarterMessages()

    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    val messageService = MessageService(repository, outbox, transactionManager, cache)

    val response = app(messageService, repository, outbox, cache, createRenderer(), testConfig)(Request(GET, "/"))

    assertEquals(Status.OK, response.status)
    assertTrue(response.bodyString().contains("Outerstellar Starter"))
    assertTrue(response.bodyString().contains("Auth Examples"))
    assertTrue(response.bodyString().contains("/api/v1/sync"))
  }

  @Test
  fun `auth and error example pages render themed htmx shells`() {
    val repository = JooqMessageRepository(testDsl, testDsl)
    repository.seedStarterMessages()
    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    val messageService = MessageService(repository, outbox, transactionManager, cache)
    val app = app(messageService, repository, outbox, cache, createRenderer(), testConfig)

    val authResponse = app(Request(GET, "/auth?lang=fr&theme=bootstrap"))
    val formResponse = app(Request(GET, "/auth/components/forms/register?lang=fr&theme=bootstrap"))
    val resultResponse =
      app(
        Request(POST, "/auth/components/result?lang=fr&theme=bootstrap")
          .header("content-type", "application/x-www-form-urlencoded")
          .body(
            "mode=register&email=jeanne%40example.com&password=password123&confirmPassword=password123"
          )
      )
    val errorResponse = app(Request(GET, "/errors/not-found?lang=fr&theme=bootstrap"))

    assertEquals(Status.OK, authResponse.status)
    assertTrue(authResponse.bodyString().contains("Exemples d'authentification"))
    assertTrue(authResponse.bodyString().contains("hx-get"))
    assertEquals(Status.OK, formResponse.status)
    assertTrue(formResponse.bodyString().contains("Créer un compte"))
    assertEquals(Status.OK, resultResponse.status)
    assertTrue(resultResponse.bodyString().contains("Formulaire accepté"))
    assertEquals(Status.OK, errorResponse.status)
    assertTrue(errorResponse.bodyString().contains("La page est introuvable"))
  }

  @Test
  fun `metrics endpoint is available and collects requests`() {
    val repository = JooqMessageRepository(testDsl, testDsl)
    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    val messageService = MessageService(repository, outbox, transactionManager, cache)
    val app = app(messageService, repository, outbox, cache, createRenderer(), testConfig)

    // Initial call
    app(Request(GET, "/"))
    
    // Check metrics
    val response = app(Request(GET, "/metrics"))
    assertEquals(Status.OK, response.status)
    val body = response.bodyString()
    assertTrue(body.isNotEmpty())
  }
}
