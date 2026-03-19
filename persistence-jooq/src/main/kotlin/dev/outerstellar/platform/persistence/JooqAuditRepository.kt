package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.AuditEntry
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class JooqAuditRepository(private val dsl: DSLContext) : AuditRepository {

    private val table = DSL.table("audit_log")
    private val idField = DSL.field("id", Long::class.java)
    private val actorIdField = DSL.field("actor_id", UUID::class.java)
    private val actorUsernameField = DSL.field("actor_username", String::class.java)
    private val targetIdField = DSL.field("target_id", UUID::class.java)
    private val targetUsernameField = DSL.field("target_username", String::class.java)
    private val actionField = DSL.field("action", String::class.java)
    private val detailField = DSL.field("detail", String::class.java)
    private val createdAtField = DSL.field("created_at", LocalDateTime::class.java)

    override fun log(entry: AuditEntry) {
        dsl.insertInto(table)
            .set(actorIdField, entry.actorId?.let { UUID.fromString(it) })
            .set(actorUsernameField, entry.actorUsername)
            .set(targetIdField, entry.targetId?.let { UUID.fromString(it) })
            .set(targetUsernameField, entry.targetUsername)
            .set(actionField, entry.action)
            .set(detailField, entry.detail)
            .execute()
    }

    override fun findRecent(limit: Int): List<AuditEntry> {
        return dsl.select(
                idField,
                actorIdField,
                actorUsernameField,
                targetIdField,
                targetUsernameField,
                actionField,
                detailField,
                createdAtField,
            )
            .from(table)
            .orderBy(createdAtField.desc())
            .limit(limit)
            .fetch()
            .map { record ->
                AuditEntry(
                    id = record.get(idField) ?: 0,
                    actorId = record.get(actorIdField)?.toString(),
                    actorUsername = record.get(actorUsernameField),
                    targetId = record.get(targetIdField)?.toString(),
                    targetUsername = record.get(targetUsernameField),
                    action = record.get(actionField) ?: "",
                    detail = record.get(detailField),
                    createdAt =
                        record.get(createdAtField)?.toInstant(ZoneOffset.UTC)
                            ?: java.time.Instant.now(),
                )
            }
    }
}
