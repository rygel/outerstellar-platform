package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.infra.NativeStartupCheck
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

private const val MILLIS_PER_SECOND = 1000L
private const val OUTBOX_INTERVAL_SECONDS = 30L
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.Main")

private fun elapsed(from: Long, phase: String) = "Startup — $phase: ${System.currentTimeMillis() - from}ms"

fun main() {
    NativeStartupCheck.run()
    val t0 = System.currentTimeMillis()

    val components = createServerComponents()
    logger.info(elapsed(t0, "Components created"))

    if (
        components.config.jdbcPassword == AppConfig.DEFAULT_JDBC_PASSWORD &&
            components.config.profile != "default" &&
            components.config.profile != "test"
    ) {
        logger.error(
            "FATAL: JDBC_PASSWORD is still the default '{}' with profile '{}'. " +
                "Set JDBC_PASSWORD to a secure value before deploying.",
            AppConfig.DEFAULT_JDBC_PASSWORD,
            components.config.profile,
        )
    }

    val server = components.app.asServer(Netty(components.config.port)).start()
    logger.info(elapsed(t0, "Server ready on :${server.port()}"))

    val adminPassword =
        System.getenv("ADMIN_PASSWORD")
            ?: java.util.UUID.randomUUID().toString().also {
                logger.warn("ADMIN_PASSWORD env var not set. A random password was generated for first-boot admin.")
                logger.warn("Set ADMIN_PASSWORD to a secure value before deploying to production.")
            }
    if (components.persistence.userRepository.findByUsername("admin") == null) {
        components.persistence.userRepository.seedAdminUser(BCryptPasswordEncoder().encode(adminPassword))
    }

    val outboxScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "outbox-processor").also { it.isDaemon = true }
    }
    val outboxTask =
        object : Runnable {
            override fun run() {
                try {
                    components.core.outboxProcessor.processPending()
                    components.security.asyncActivityUpdater.flush()
                } finally {
                    val delay =
                        maxOf(components.core.outboxProcessor.backoffMs, OUTBOX_INTERVAL_SECONDS * MILLIS_PER_SECOND)
                    outboxScheduler.schedule(this, delay, TimeUnit.MILLISECONDS)
                }
            }
        }
    outboxScheduler.schedule(outboxTask, 0, TimeUnit.MILLISECONDS)
    logger.info(elapsed(t0, "Background jobs started"))

    registerShutdownHook(components, outboxScheduler, server)
}

private fun registerShutdownHook(
    components: ServerComponents,
    outboxScheduler: ScheduledExecutorService,
    server: Http4kServer,
) {
    Runtime.getRuntime()
        .addShutdownHook(
            Thread(
                {
                    logger.info("Graceful shutdown initiated")
                    try {
                        logger.info("Flushing pending activity updates...")
                        components.security.asyncActivityUpdater.flush()
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
                    } finally {
                        // Always close the persistence layer (HikariDataSource) last so DB connections
                        // are released to the pool and Postgres even if an earlier shutdown step throws.
                        logger.info("Closing persistence (DB pool)...")
                        runCatching { components.persistence.close() }
                            .onFailure { logger.warn("Failed to close persistence cleanly", it) }
                        logger.info("Shutdown complete")
                    }
                },
                "graceful-shutdown",
            )
        )
}
