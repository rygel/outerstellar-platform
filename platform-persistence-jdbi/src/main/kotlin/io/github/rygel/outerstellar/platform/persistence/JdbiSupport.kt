package io.github.rygel.outerstellar.platform.persistence

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.ArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.Query
import org.jdbi.v3.core.statement.StatementContext

data class FilterClause(val sql: String, val binder: (Query) -> Unit)

fun String.escapeLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")

fun ResultSet.getNullableInstant(column: String): Instant? = getTimestamp(column)?.toInstant()

fun ResultSet.getRequiredInstant(column: String): Instant =
    getTimestamp(column)?.toInstant() ?: error("$column is unexpectedly null")

fun ResultSet.getInstantOrDefault(column: String, default: Instant = Instant.now()): Instant =
    getTimestamp(column)?.toInstant() ?: default

class InstantArgumentFactory : ArgumentFactory {
    override fun build(type: Type, value: Any, config: ConfigRegistry): Optional<Argument> {
        val rawType = if (type is ParameterizedType) type.rawType else type
        if (rawType !is Class<*> || !Instant::class.java.isAssignableFrom(rawType)) return Optional.empty()
        val instant = value as Instant
        return Optional.of(Argument { position, stmt, _ -> stmt.setTimestamp(position, Timestamp.from(instant)) })
    }
}

class InstantColumnMapper : ColumnMapper<Instant> {
    override fun map(r: ResultSet, columnNumber: Int, ctx: StatementContext): Instant? =
        r.getTimestamp(columnNumber)?.toInstant()

    override fun map(r: ResultSet, columnLabel: String, ctx: StatementContext): Instant? =
        r.getTimestamp(columnLabel)?.toInstant()
}
