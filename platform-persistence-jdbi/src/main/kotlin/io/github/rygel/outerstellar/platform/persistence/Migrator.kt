package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import org.slf4j.LoggerFactory

/**
 * Standalone Flyway migration runner. Runs platform database migrations only as a separate command, so the main
 * application can start with `FLYWAY_ENABLED=false`. Extension migrations are not run here — they require the host's
 * `ExtensionMigrations` wiring, which is only available through `createServerComponents` / `migrate(ds, extension)`.
 *
 * Environment variables: JDBC_URL, JDBC_USER, JDBC_PASSWORD, APP_PROFILE, HIKARI_* (optional).
 *
 * Usage: java -cp platform-persistence-jdbi-1.6.1.jar io.github.rygel.outerstellar.platform.persistence.MigratorKt
 *
 * Or via Maven: mvn -pl platform-persistence-jdbi exec:java
 * -Dexec.mainClass=io.github.rygel.outerstellar.platform.persistence.MigratorKt
 */
fun main() {
    val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.persistence.Migrator")
    val config = io.github.rygel.outerstellar.platform.AppConfig.fromEnvironment()

    logger.info(
        "Running Flyway migrations against {} as user {} (profile: {})",
        config.jdbcUrl.replace(Regex("://[^:]+:[^@]+@"), "://***:***@"),
        config.jdbcUser,
        config.profile,
    )

    val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)

    try {
        migrate(ds)
        logger.info("Migrations completed successfully")
    } catch (e: Exception) {
        logger.error("Migration failed: {}", e.message, e)
        System.exit(1)
    } finally {
        ds.close()
    }

    logger.info("All migrations complete")
    System.exit(0)
}
