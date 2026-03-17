package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.security.OAuthConnection
import dev.outerstellar.starter.security.OAuthRepository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqOAuthRepository(private val dsl: DSLContext) : OAuthRepository {

    private val table = DSL.table("OAUTH_CONNECTIONS")
    private val idField = DSL.field(DSL.name("ID"), SQLDataType.BIGINT)
    private val userIdField = DSL.field(DSL.name("USER_ID"), SQLDataType.UUID)
    private val providerField = DSL.field(DSL.name("PROVIDER"), SQLDataType.VARCHAR)
    private val subjectField = DSL.field(DSL.name("SUBJECT"), SQLDataType.VARCHAR)
    private val emailField = DSL.field(DSL.name("EMAIL"), SQLDataType.VARCHAR)
    private val createdAtField = DSL.field(DSL.name("CREATED_AT"), SQLDataType.TIMESTAMP)

    private fun mapRecord(record: Record): OAuthConnection =
        OAuthConnection(
            id = record.get(idField)!!,
            userId = record.get(userIdField)!!,
            provider = record.get(providerField)!!,
            subject = record.get(subjectField)!!,
            email = record.get(emailField),
        )

    override fun save(connection: OAuthConnection) {
        dsl.insertInto(table)
            .set(userIdField, connection.userId)
            .set(providerField, connection.provider)
            .set(subjectField, connection.subject)
            .set(emailField, connection.email)
            .set(createdAtField, Timestamp.from(Instant.now()))
            .execute()
    }

    override fun findByProviderSubject(provider: String, subject: String): OAuthConnection? =
        dsl.select()
            .from(table)
            .where(providerField.eq(provider).and(subjectField.eq(subject)))
            .fetchOne()
            ?.let { mapRecord(it) }

    override fun findByUserId(userId: UUID): List<OAuthConnection> =
        dsl.select()
            .from(table)
            .where(userIdField.eq(userId))
            .orderBy(createdAtField.desc())
            .fetch()
            .map { mapRecord(it) }

    override fun delete(id: Long, userId: UUID) {
        dsl.deleteFrom(table).where(idField.eq(id).and(userIdField.eq(userId))).execute()
    }
}
