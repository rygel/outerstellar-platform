package dev.outerstellar.starter.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): DataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = jdbcUser
            password = jdbcPassword
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 300_000
            connectionTimeout = 10_000
            poolName = "outerstellar-pool"
        }
    )

fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}
