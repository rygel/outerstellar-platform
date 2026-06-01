package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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

private val MIGRATION_NAMES: List<String> by lazy {
    val stream =
        Thread.currentThread().contextClassLoader.getResourceAsStream("db/migration/migrations.index")
            ?: error("Migration manifest not found on classpath: db/migration/migrations.index")
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

fun migrate(dataSource: DataSource) {
    val config = Flyway.configure().dataSource(dataSource)
    if (IS_NATIVE_IMAGE) {
        logger.info("Running in native image mode, extracting migrations to filesystem")
        val tempDir = extractMigrationsToFilesystem("db/migration", MIGRATION_NAMES)
        logger.info("Extracted {} migrations to {}", MIGRATION_NAMES.size, tempDir.toAbsolutePath())
        config.locations("filesystem:${tempDir.toAbsolutePath()}")
    } else {
        logger.info("Running in JVM mode, using classpath migrations")
        config.locations("classpath:db/migration")
    }
    config.load().migrate()
}

fun migrateExtension(
    dataSource: DataSource,
    location: String,
    historyTable: String,
    migrationNames: List<String>? = null,
) {
    val config =
        Flyway.configure().dataSource(dataSource).table(historyTable).baselineOnMigrate(true).baselineVersion("0")
    if (IS_NATIVE_IMAGE && location.startsWith("classpath:") && migrationNames != null) {
        val classpathDir = location.removePrefix("classpath:")
        val tempDir = extractMigrationsToFilesystem(classpathDir, migrationNames)
        config.locations("filesystem:${tempDir.toAbsolutePath()}")
    } else {
        config.locations(location)
    }
    config.load().migrate()
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
