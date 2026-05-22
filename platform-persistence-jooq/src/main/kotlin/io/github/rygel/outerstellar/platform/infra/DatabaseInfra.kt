package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.rygel.outerstellar.platform.RuntimeConfig
import java.nio.file.Files
import javax.sql.DataSource
import org.flywaydb.core.Flyway

private val IS_NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagekind") != null

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
            poolName = "outerstellar-pool"
        }
    )

fun migrate(dataSource: DataSource) {
    val config = Flyway.configure().dataSource(dataSource)
    if (IS_NATIVE_IMAGE) {
        val tempDir = extractMigrationsToFilesystem("db/migration", MIGRATION_NAMES)
        config.locations("filesystem:${tempDir.toAbsolutePath()}")
    } else {
        config.locations("classpath:db/migration")
    }
    config.load().migrate()
}

fun migratePlugin(
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
