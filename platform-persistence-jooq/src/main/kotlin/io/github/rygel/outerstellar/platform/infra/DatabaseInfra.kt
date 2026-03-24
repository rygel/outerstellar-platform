package io.github.rygel.outerstellar.platform.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = jdbcUser
            password = jdbcPassword
            maximumPoolSize = 20
            minimumIdle = 2
            idleTimeout = 300_000
            maxLifetime = 1_800_000 // 30 minutes — prevents stale connections
            connectionTimeout = 10_000
            leakDetectionThreshold = 60_000 // warn if connection held > 60s
            poolName = "outerstellar-pool"
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
