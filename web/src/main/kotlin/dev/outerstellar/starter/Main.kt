package dev.outerstellar.starter

import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.di.webModule
import dev.outerstellar.starter.security.securityModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.PasswordEncoder
import dev.outerstellar.starter.security.UserRepository
import javax.sql.DataSource
import org.http4k.core.HttpHandler
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
import org.http4k.server.asServer
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.Main")

object MainComponent : KoinComponent {
  val config: AppConfig by inject()
  val dataSource: DataSource by inject()
  val repository: MessageRepository by inject()
  val userRepository: UserRepository by inject()
  val passwordEncoder: PasswordEncoder by inject()
  val app: PolyHandler by inject(named("webServer"))
}

fun main() {
  startKoin {
    modules(persistenceModule, coreModule, webModule, securityModule)
  }

  val main = MainComponent
  migrate(main.dataSource)
  main.repository.seedStarterMessages()
  
  (main.userRepository as JooqUserRepository).seedAdminUser(main.passwordEncoder.encode("admin123"))

  val server = main.app.asServer(Jetty(main.config.port)).start()
  logger.info("Outerstellar starter running on http://localhost:{}", server.port())
}
