package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.sync.SyncService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class SyncIntegrationTest {
  @Test
  fun `sync pushes local changes and pulls server changes`() {
    val serverRepository =
      createRepository("jdbc:h2:mem:server-sync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    val clientRepository =
      createRepository("jdbc:h2:mem:client-sync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")

    serverRepository.createServerMessage("Server", "Created on the server")
    clientRepository.createLocalMessage("Swing Client", "Created on the desktop")

    val syncService =
      SyncService(
        repository = clientRepository,
        serverBaseUrl = "http://localhost:8080",
        httpClient = app(serverRepository, createRenderer()),
      )

    val stats = syncService.sync()

    assertTrue(serverRepository.listMessages().any { it.content == "Created on the desktop" })
    assertTrue(clientRepository.listMessages().any { it.content == "Created on the server" })
    assertEquals(0, clientRepository.listDirtyMessages().size)
    assertTrue(stats.pushedCount >= 1)
    assertTrue(stats.pulledCount >= 1)
  }

  private fun createRepository(jdbcUrl: String): JooqMessageRepository {
    val dataSource = createDataSource(jdbcUrl, "sa", "")
    migrate(dataSource)
    return JooqMessageRepository(DSL.using(dataSource, SQLDialect.H2))
  }
}
