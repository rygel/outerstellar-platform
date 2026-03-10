package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class SyncIntegrationTest : PostgresWebTest() {
  @Test
  fun `sync pushes local changes and pulls server changes`() {
    val serverRepository = JooqMessageRepository(testDsl, testDsl)
    
    // For client repository, we use a separate schema or just another table set in H2 for now,
    // but the task says "Replace H2 with Testcontainers". 
    // Usually, client and server would be different DBs.
    // Let's use H2 for the client side (as it's a desktop app) and Postgres for the server.
    // OR we can use two different Postgres databases/containers, but that's heavy.
    // Let's stick to Postgres for server-side and keep H2 for the client-side stubbing if needed, 
    // but actually, the client repo in this test is also JooqMessageRepository.
    
    val clientDataSource = createDataSource("jdbc:h2:mem:client-sync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
    migrate(clientDataSource)
    val clientRepository = JooqMessageRepository(DSL.using(clientDataSource, SQLDialect.H2))

    val outbox = StubOutboxRepository()
    val cache = StubMessageCache()
    val transactionManager = StubTransactionManager()
    
    serverRepository.createServerMessage("Server", "Created on the server")
    clientRepository.createLocalMessage("Swing Client", "Created on the desktop")

    val pageFactory = WebPageFactory(serverRepository, true)
    val i18n = I18nService.fromResourceBundle("web-messages")
    val syncService =
      SyncService(
        repository = clientRepository,
        serverBaseUrl = "http://localhost:8080",
        httpClient = app(
            messageService = MessageService(serverRepository, outbox, transactionManager, cache), 
            repository = serverRepository, 
            outboxRepository = outbox, 
            cache = cache, 
            renderer = createRenderer(), 
            pageFactory = pageFactory, 
            config = testConfig, 
            i18nService = i18n
        ),
      )

    val stats = syncService.sync()

    assertTrue(serverRepository.listMessages().any { it.content == "Created on the desktop" })
    assertTrue(clientRepository.listMessages().any { it.content == "Created on the server" })
    assertEquals(0, clientRepository.listDirtyMessages().size)
    assertTrue(stats.pushedCount >= 1)
    assertTrue(stats.pulledCount >= 1)
  }
}
