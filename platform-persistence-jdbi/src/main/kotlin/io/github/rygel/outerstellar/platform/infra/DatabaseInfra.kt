package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.rygel.outerstellar.platform.RuntimeConfig
import javax.sql.DataSource
import org.flywaydb.core.Flyway

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
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}

fun migratePlugin(dataSource: DataSource, location: String, historyTable: String) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .table(historyTable)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate()
}
