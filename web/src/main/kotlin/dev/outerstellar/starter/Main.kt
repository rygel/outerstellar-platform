package dev.outerstellar.starter

import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.di.webModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.security.PasswordEncoder
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.securityModule
import dev.outerstellar.starter.service.OutboxProcessor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
import org.http4k.server.asServer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.Main")

object MainComponent : KoinComponent {
    val config: AppConfig by inject()
    val dataSource: DataSource by inject()
    val repository: MessageRepository by inject()
    val contactRepository: dev.outerstellar.starter.persistence.ContactRepository by inject()
    val userRepository: UserRepository by inject()
    val passwordEncoder: PasswordEncoder by inject()
    val outboxProcessor: OutboxProcessor by inject()
    val app: PolyHandler by inject(named("webServer"))
}

fun main() {
    startKoin { modules(persistenceModule, coreModule, webModule, securityModule) }

    val main = MainComponent
    migrate(main.dataSource)

    val adminPassword = System.getenv("ADMIN_PASSWORD")
        ?: java.util.UUID.randomUUID().toString().also { generated ->
            logger.warn("ADMIN_PASSWORD env var not set. Using generated password for first-boot admin: {}", generated)
            logger.warn("Set ADMIN_PASSWORD to a secure value before deploying to production.")
        }
    main.userRepository.seedAdminUser(main.passwordEncoder.encode(adminPassword))

    val outboxScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "outbox-processor").also { it.isDaemon = true }
    }
    outboxScheduler.scheduleWithFixedDelay(
        { main.outboxProcessor.processPending() },
        30L, 30L, TimeUnit.SECONDS,
    )
    Runtime.getRuntime().addShutdownHook(Thread({
        logger.info("Shutting down outbox scheduler")
        outboxScheduler.shutdownNow()
    }, "outbox-shutdown"))

    val server = main.app.asServer(Jetty(main.config.port)).start()
    logger.info("Outerstellar starter running on http://localhost:{}", server.port())
}
