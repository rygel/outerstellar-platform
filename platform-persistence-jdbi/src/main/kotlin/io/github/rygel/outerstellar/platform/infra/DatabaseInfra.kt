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

fun migrate(dataSource: DataSource, extensionLocation: String? = null, extensionMigrationNames: List<String>? = null) {
    repairLegacyExtensionHistoryTable(dataSource)
    val config = Flyway.configure().dataSource(dataSource)
    val locations = mutableListOf<String>()
    if (IS_NATIVE_IMAGE) {
        logger.info("Running in native image mode, extracting migrations to filesystem")
        val tempDir = extractMigrationsToFilesystem("db/migration", MIGRATION_NAMES)
        logger.info("Extracted {} migrations to {}", MIGRATION_NAMES.size, tempDir.toAbsolutePath())
        locations.add("filesystem:${tempDir.toAbsolutePath()}")
        if (extensionLocation != null) {
            locations.addAll(resolveExtensionLocationNative(extensionLocation, extensionMigrationNames))
        }
    } else {
        logger.info("Running in JVM mode, using classpath migrations")
        locations.add("classpath:db/migration")
        if (extensionLocation != null) {
            locations.add(extensionLocation)
        }
    }
    @Suppress("SpreadOperator") config.locations(*locations.toTypedArray())
    config.load().migrate()
}

@Deprecated("Extensions now share the platform history table. Use migrate() with extensionLocation instead.")
@Suppress("UNUSED_PARAMETER")
fun migrateExtension(
    dataSource: DataSource,
    location: String,
    historyTable: String,
    migrationNames: List<String>? = null,
) {
    repairLegacyExtensionHistoryTable(dataSource)
    migrate(dataSource = dataSource, extensionLocation = location, extensionMigrationNames = migrationNames)
}

private fun resolveExtensionLocationNative(location: String, migrationNames: List<String>?): List<String> {
    if (!location.startsWith("classpath:") || migrationNames == null) {
        return listOf(location)
    }
    val classpathDir = location.removePrefix("classpath:")
    val tempDir = extractMigrationsToFilesystem(classpathDir, migrationNames)
    return listOf("filesystem:${tempDir.toAbsolutePath()}")
}

private fun repairLegacyExtensionHistoryTable(dataSource: DataSource) {
    try {
        dataSource.connection.use { conn ->
            if (!legacyTableExists(conn)) return@use
            logger.info("Detected legacy flyway_extension_history table, consolidating into flyway_schema_history")
            val maxRank = maxInstalledRank(conn)
            copyExtensionRows(conn, maxRank)
            conn.prepareStatement("DROP TABLE flyway_extension_history").use { it.executeUpdate() }
            logger.info("Consolidated extension migration history and dropped flyway_extension_history")
        }
    } catch (e: Exception) {
        logger.warn("Could not check/repair legacy extension history table: {}", e.message)
    }
}

private fun legacyTableExists(conn: java.sql.Connection): Boolean {
    val rs = conn.metaData.getTables(null, "public", "flyway_extension_history", null)
    return rs.use { it.next() }
}

private fun maxInstalledRank(conn: java.sql.Connection): Int =
    conn.prepareStatement("SELECT COALESCE(MAX(installed_rank), 0) FROM flyway_schema_history").use { stmt ->
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

private fun copyExtensionRows(conn: java.sql.Connection, startRank: Int) {
    val rows = readExtensionRows(conn)
    if (rows.isEmpty()) return
    conn
        .prepareStatement(
            "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .use { insert ->
            rows.fold(startRank) { rank, row ->
                insert.setInt(1, rank + 1)
                insert.setString(2, row.version)
                insert.setString(3, row.description)
                insert.setString(4, row.type)
                insert.setString(5, row.script)
                insert.setInt(6, row.checksum)
                insert.setString(7, row.installedBy)
                insert.setTimestamp(8, row.installedOn)
                insert.setInt(9, row.executionTime)
                insert.setBoolean(10, row.success)
                insert.executeUpdate()
                rank + 1
            }
        }
}

private data class ExtensionRow(
    val version: String,
    val description: String,
    val type: String,
    val script: String,
    val checksum: Int,
    val installedBy: String,
    val installedOn: java.sql.Timestamp,
    val executionTime: Int,
    val success: Boolean,
)

private fun readExtensionRows(conn: java.sql.Connection): List<ExtensionRow> =
    conn
        .prepareStatement(
            "SELECT version, description, type, script, checksum, installed_by, installed_on, execution_time, success FROM flyway_extension_history ORDER BY installed_rank"
        )
        .use { select ->
            select.executeQuery().use { rs ->
                val rows = mutableListOf<ExtensionRow>()
                while (rs.next()) {
                    rows.add(
                        ExtensionRow(
                            version = rs.getString("version"),
                            description = rs.getString("description"),
                            type = rs.getString("type"),
                            script = rs.getString("script"),
                            checksum = rs.getInt("checksum"),
                            installedBy = rs.getString("installed_by"),
                            installedOn = rs.getTimestamp("installed_on"),
                            executionTime = rs.getInt("execution_time"),
                            success = rs.getBoolean("success"),
                        )
                    )
                }
                rows
            }
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
