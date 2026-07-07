package io.github.rygel.outerstellar.platform.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.ArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.StatementContext

private class TestInstantArgumentFactory : ArgumentFactory {
    override fun build(type: Type, value: Any, config: ConfigRegistry): Optional<Argument> {
        val rawType = if (type is ParameterizedType) type.rawType else type
        if (rawType !is Class<*> || !Instant::class.java.isAssignableFrom(rawType)) return Optional.empty()
        val instant = value as Instant
        return Optional.of(Argument { position, stmt, _ -> stmt.setTimestamp(position, Timestamp.from(instant)) })
    }
}

private class TestInstantColumnMapper : ColumnMapper<Instant> {
    override fun map(r: ResultSet, columnNumber: Int, ctx: StatementContext): Instant? =
        r.getTimestamp(columnNumber)?.toInstant()

    override fun map(r: ResultSet, columnLabel: String, ctx: StatementContext): Instant? =
        r.getTimestamp(columnLabel)?.toInstant()
}

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
                        // Namespaced location (ADR-0004): scan only the platform-owned subtree, never the shared
                        // db/migration parent, so foreign migration trees can't collide on V1 (#601).
                        Flyway.configure().dataSource(ds).locations("classpath:db/migration/platform").load().migrate()
                    }

    val jdbi: Jdbi by lazy {
        Jdbi.create(dataSource).also {
            it.registerArgument(TestInstantArgumentFactory())
            it.registerColumnMapper(TestInstantColumnMapper())
        }
    }

    fun drop() {
        _dataSource?.close()
        SharedPostgres.dropDatabase(dbName)
    }
}
