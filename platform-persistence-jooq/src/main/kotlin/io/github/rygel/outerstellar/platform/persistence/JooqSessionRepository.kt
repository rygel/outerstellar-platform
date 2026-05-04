package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.Session
import io.github.rygel.outerstellar.platform.security.SessionRepository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqSessionRepository(private val dsl: DSLContext) : SessionRepository {

    private val table = DSL.table("plt_sessions")
    private val idField = DSL.field(DSL.name("id"), SQLDataType.BIGINT)
    private val tokenHashField = DSL.field(DSL.name("token_hash"), SQLDataType.VARCHAR)
    private val userIdField = DSL.field(DSL.name("user_id"), SQLDataType.UUID)
    private val createdAtField = DSL.field(DSL.name("created_at"), SQLDataType.TIMESTAMP)
    private val expiresAtField = DSL.field(DSL.name("expires_at"), SQLDataType.TIMESTAMP)

    private fun mapRecord(record: Record): Session {
        val createdAt = record.get(createdAtField)
        val expiresAt = record.get(expiresAtField)
        return Session(
            id = record.get(idField)!!,
            tokenHash = record.get(tokenHashField)!!,
            userId = record.get(userIdField)!!,
            createdAt = createdAt?.toInstant() ?: Instant.now(),
            expiresAt = expiresAt?.toInstant() ?: Instant.now(),
        )
    }

    override fun save(session: Session) {
        dsl.insertInto(table)
            .set(tokenHashField, session.tokenHash)
            .set(userIdField, session.userId)
            .set(createdAtField, Timestamp.from(session.createdAt))
            .set(expiresAtField, Timestamp.from(session.expiresAt))
            .execute()
    }

    override fun findByTokenHash(tokenHash: String): Session? {
        return dsl.select()
            .from(table)
            .where(tokenHashField.eq(tokenHash).and(expiresAtField.gt(Timestamp.from(Instant.now()))))
            .fetchOne()
            ?.let { mapRecord(it) }
    }

    override fun findByTokenHashIncludingExpired(tokenHash: String): Session? {
        return dsl.select().from(table).where(tokenHashField.eq(tokenHash)).fetchOne()?.let { mapRecord(it) }
    }

    override fun updateExpiresAt(tokenHash: String, expiresAt: Instant) {
        dsl.update(table).set(expiresAtField, Timestamp.from(expiresAt)).where(tokenHashField.eq(tokenHash)).execute()
    }

    override fun deleteByTokenHash(tokenHash: String) {
        dsl.deleteFrom(table).where(tokenHashField.eq(tokenHash)).execute()
    }

    override fun deleteByUserId(userId: UUID) {
        dsl.deleteFrom(table).where(userIdField.eq(userId)).execute()
    }

    override fun deleteExpired() {
        dsl.deleteFrom(table).where(expiresAtField.le(Timestamp.from(Instant.now()))).execute()
    }
}
