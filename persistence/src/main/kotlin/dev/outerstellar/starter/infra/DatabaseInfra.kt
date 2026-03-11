package dev.outerstellar.starter.infra

import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import javax.sql.DataSource

fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): DataSource =
    JdbcDataSource().apply {
        setURL(jdbcUrl)
        user = jdbcUser
        password = jdbcPassword
    }

fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}
