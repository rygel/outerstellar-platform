package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.RuntimeConfig
import java.nio.file.Files
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.infra.DatabaseInfra")

private val IS_NATIVE_IMAGE = run {
    val imagekind = System.getProperty("org.graalvm.nativeimage.imagekind")
    val substrate = System.getProperty("java.vm.name")?.contains("Substrate", ignoreCase = true) == true
    val result = imagekind != null || substrate
    logger.info("Native image detection: imagekind={}, substrate={}, result={}", imagekind, substrate, result)
    result
}

/**
 * Classpath location of the platform's own Flyway migrations. Namespaced under `db/migration/platform/` (ADR-0004) so
 * that Flyway, which scans a `classpath:` location recursively, never picks up a host's or sibling module's migrations
 * that live under their own `db/migration/<owner>/` subtrees. Before namespacing, registering the shared `db/migration`
 * parent caused Flyway to recurse into foreign subtrees and collide on `V1` ("Found more than one migration with
 * version 1") — see #601.
 */
private const val PLATFORM_MIGRATION_LOCATION = "db/migration/platform"

private val MIGRATION_NAMES: List<String> by lazy {
    val stream =
        Thread.currentThread().contextClassLoader.getResourceAsStream("$PLATFORM_MIGRATION_LOCATION/migrations.index")
            ?: error("Migration manifest not found on classpath: $PLATFORM_MIGRATION_LOCATION/migrations.index")
    stream.bufferedReader().use { it.readLines().filter { line -> line.isNotBlank() } }
}

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): HikariDataSource =
    createDataSource(jdbcUrl, jdbcUser, jdbcPassword, RuntimeConfig())

fun createDataSource(
    jdbcUrl: String,
    jdbcUser: String,
    jdbcPassword: String,
    runtime: RuntimeConfig,
): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = jdbcUser
            password = jdbcPassword
            maximumPoolSize = runtime.hikariMaximumPoolSize
            minimumIdle = runtime.hikariMinimumIdle
            idleTimeout = runtime.hikariIdleTimeoutMs
            maxLifetime = runtime.hikariMaxLifetimeMs
            connectionTimeout = runtime.hikariConnectionTimeoutMs
            leakDetectionThreshold = runtime.hikariLeakDetectionThresholdMs
            poolName = "outerstellar-jdbi-pool"
        }
    )

/**
 * Run database migrations as two isolated Flyway passes.
 *
 * 1. **Platform pass** — the platform's own migrations under [PLATFORM_MIGRATION_LOCATION] against the default
 *    `flyway_schema_history`. Scoped to the platform-owned subdirectory (ADR-0004) so Flyway never recurses into
 *    foreign migration trees.
 * 2. **Extension pass** (only if [extension] is provided) — a **separate** `Flyway.configure()` against the extension's
 *    declared [ExtensionMigrations.location] and [ExtensionMigrations.historyTable], baselined at 0. Running it as a
 *    second pass with its own history table means the extension's `V1` and the platform's `V1` never share a
 *    `CompositeMigrationResolver`, so they cannot collide (#611).
 *
 * Honors the standard Flyway `flyway.outOfOrder` system property / `FLYWAY_OUT_OF_ORDER` env var on both passes (#561).
 *
 * @param extension optional extension migration metadata; `null` runs the platform pass only.
 */
fun migrate(dataSource: DataSource, extension: ExtensionMigrations? = null) {
    val outOfOrder = (System.getProperty("flyway.outOfOrder") ?: System.getenv("FLYWAY_OUT_OF_ORDER")) == "true"
    if (outOfOrder) {
        logger.info("Flyway outOfOrder=true (from flyway.outOfOrder) — pending migrations will be applied")
    }

    // --- Platform pass: platform-owned migrations against the default history table. ---
    val platformLocation =
        if (IS_NATIVE_IMAGE) {
            logger.info("Running platform migrations in native image mode, extracting to filesystem")
            val tempDir = extractMigrationsToFilesystem(PLATFORM_MIGRATION_LOCATION, MIGRATION_NAMES)
            logger.info("Extracted {} platform migrations to {}", MIGRATION_NAMES.size, tempDir.toAbsolutePath())
            "filesystem:${tempDir.toAbsolutePath()}"
        } else {
            logger.info("Running platform migrations in JVM mode, using classpath migrations")
            "classpath:$PLATFORM_MIGRATION_LOCATION"
        }
    val platformConfig = Flyway.configure().dataSource(dataSource).locations(platformLocation)
    if (outOfOrder) {
        platformConfig.outOfOrder(true)
    }
    platformConfig.load().migrate()

    // --- Extension pass: the extension's migrations against its OWN history table, isolated from the platform. ---
    if (extension != null) {
        logger.info(
            "Running extension migrations from {} against history table {}",
            extension.location,
            extension.historyTable,
        )
        val extensionLocation =
            if (IS_NATIVE_IMAGE) {
                resolveExtensionLocationNative(extension.location, extension.migrationNames)
            } else {
                extension.location
            }
        val extensionConfig =
            Flyway.configure()
                .dataSource(dataSource)
                .locations(extensionLocation)
                .table(extension.historyTable)
                .baselineOnMigrate(true)
                .baselineVersion("0")
        if (outOfOrder) {
            extensionConfig.outOfOrder(true)
        }
        extensionConfig.load().migrate()
    }
}

private fun resolveExtensionLocationNative(location: String, migrationNames: List<String>): String {
    if (!location.startsWith("classpath:")) {
        return location
    }
    val classpathDir = location.removePrefix("classpath:")
    val tempDir = extractMigrationsToFilesystem(classpathDir, migrationNames)
    return "filesystem:${tempDir.toAbsolutePath()}"
}

private fun extractMigrationsToFilesystem(classpathDir: String, migrationNames: List<String>): java.nio.file.Path {
    val tempDir = Files.createTempDirectory("flyway-migrations")
    tempDir.toFile().deleteOnExit()
    val cl = Thread.currentThread().contextClassLoader
    for (name in migrationNames) {
        val resourcePath = "$classpathDir/$name.sql"
        val sql =
            cl.getResourceAsStream(resourcePath)?.readBytes()
                ?: error("Migration not found on classpath: $resourcePath")
        Files.write(tempDir.resolve("$name.sql"), sql)
    }
    return tempDir
}
