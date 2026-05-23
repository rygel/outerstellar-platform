package io.github.rygel.outerstellar.platform.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi

class TestDatabase(val dbName: String, val jdbcUrl: String, val jdbcUser: String, val jdbcPassword: String) {
    private var _dataSource: HikariDataSource? = null

    val dataSource: DataSource
        get() =
            _dataSource
                ?: HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = this@TestDatabase.jdbcUrl
                            username = jdbcUser
                            password = jdbcPassword
                            maximumPoolSize = 10
                            minimumIdle = 2
                        }
                    )
                    .also { ds ->
                        _dataSource = ds
                        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
                    }

    val jdbi: Jdbi by lazy { Jdbi.create(dataSource) }

    fun drop() {
        _dataSource?.close()
        SharedPostgres.dropDatabase(dbName)
    }
}
