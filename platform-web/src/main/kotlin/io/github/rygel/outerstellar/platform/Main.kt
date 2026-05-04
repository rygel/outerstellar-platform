package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.di.webModule
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater
import io.github.rygel.outerstellar.platform.security.PasswordEncoder
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.security.securityModule
import io.github.rygel.outerstellar.platform.service.OutboxProcessor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.http4k.core.PolyHandler
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

private const val OUTBOX_INTERVAL_SECONDS = 30L
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.Main")

object MainComponent : KoinComponent {
    val config: AppConfig by inject()
    val repository: MessageRepository by inject()
    val contactRepository: io.github.rygel.outerstellar.platform.persistence.ContactRepository by inject()
    val userRepository: UserRepository by inject()
    val passwordEncoder: PasswordEncoder by inject()
    val outboxProcessor: OutboxProcessor by inject()
    val activityUpdater: AsyncActivityUpdater by inject()
    val app: PolyHandler by inject(named("webServer"))
}

fun main() {
    startKoin { modules(persistenceModule, coreModule, webModule, securityModule) }

    val main = MainComponent

    val adminPassword =
        System.getenv("ADMIN_PASSWORD")
            ?: java.util.UUID.randomUUID().toString().also { generated ->
                logger.warn(
                    "ADMIN_PASSWORD env var not set. Using generated password for first-boot admin: {}",
                    generated,
                )
                logger.warn("Set ADMIN_PASSWORD to a secure value before deploying to production.")
            }
    if (main.userRepository.findByUsername("admin") == null) {
        main.userRepository.seedAdminUser(main.passwordEncoder.encode(adminPassword))
    }

    val outboxScheduler =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "outbox-processor").also { it.isDaemon = true } }
    outboxScheduler.scheduleWithFixedDelay(
        {
            main.outboxProcessor.processPending()
            main.activityUpdater.flush()
        },
        OUTBOX_INTERVAL_SECONDS,
        OUTBOX_INTERVAL_SECONDS,
        TimeUnit.SECONDS,
    )
    val server = main.app.asServer(Jetty(main.config.port)).start()
    logger.info("Outerstellar platform running on http://localhost:{}", server.port())

    registerShutdownHook(main, outboxScheduler, server)
}

private fun registerShutdownHook(main: MainComponent, outboxScheduler: ScheduledExecutorService, server: Http4kServer) {
    Runtime.getRuntime()
        .addShutdownHook(
            Thread(
                {
                    logger.info("Graceful shutdown initiated")
                    logger.info("Flushing pending activity updates...")
                    main.activityUpdater.flush()
                    logger.info("Stopping outbox scheduler...")
                    outboxScheduler.shutdown()
                    try {
                        if (!outboxScheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            outboxScheduler.shutdownNow()
                        }
                    } catch (e: InterruptedException) {
                        outboxScheduler.shutdownNow()
                    }
                    logger.info("Stopping HTTP server...")
                    server.stop()
                    logger.info("Shutdown complete")
                },
                "graceful-shutdown",
            )
        )
}
