package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway

private const val DEFAULT_POOL_IDLE_TIMEOUT_MS = 300_000L
private const val DEFAULT_CONNECTION_MAX_LIFETIME_MS = 1_800_000L
private const val DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 60_000L
private const val DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = jdbcUser
            password = jdbcPassword
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = DEFAULT_POOL_IDLE_TIMEOUT_MS
            maxLifetime = DEFAULT_CONNECTION_MAX_LIFETIME_MS
            connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_MS
            leakDetectionThreshold = DEFAULT_LEAK_DETECTION_THRESHOLD_MS
            poolName = "outerstellar-jdbi-pool"
        }
    )

fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}

fun migratePlugin(dataSource: DataSource, location: String, historyTable: String) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .table(historyTable)
        .baselineOnMigrate(true)
        .load()
        .migrate()
}
