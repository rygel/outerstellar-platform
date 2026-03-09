package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.service.MessageService
import javax.sql.DataSource
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.Main")

fun main() {
  val config = AppConfig.fromEnvironment()
  val dataSource = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)

  migrate(dataSource)

  val repository = JooqMessageRepository(DSL.using(dataSource, SQLDialect.H2))
  repository.seedStarterMessages()

  val messageService = MessageService(repository)

  val server = app(messageService, repository, createRenderer()).asServer(Jetty(config.port)).start()
  logger.info("Outerstellar starter running on http://localhost:{}", server.port())
}
