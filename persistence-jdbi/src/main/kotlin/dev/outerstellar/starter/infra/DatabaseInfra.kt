package dev.outerstellar.starter.infra

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
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 300_000
            connectionTimeout = 10_000
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
        .load()
        .migrate()
}
