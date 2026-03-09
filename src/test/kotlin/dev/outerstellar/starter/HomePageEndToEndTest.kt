package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.JooqMessageRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class HomePageEndToEndTest {
  @Test
  fun `home page is available on running server`() {
    val config =
      AppConfig(
        port = 0,
        jdbcUrl = "jdbc:h2:mem:e2e-home-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        jdbcUser = "sa",
        jdbcPassword = "",
      )
    val dataSource = createDataSource(config)

    migrate(dataSource)

    val repository = JooqMessageRepository(DSL.using(dataSource, SQLDialect.H2))
    repository.seedStarterMessages()

    val appHandler = app(repository, createRenderer())
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
