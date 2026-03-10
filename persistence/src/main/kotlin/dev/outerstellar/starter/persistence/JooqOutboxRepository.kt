package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.OUTBOX
import org.jooq.DSLContext
import java.util.*

class JooqOutboxRepository(
    private val primaryDsl: DSLContext,
    private val replicaDsl: DSLContext = primaryDsl
) : OutboxRepository {

    override fun save(entry: OutboxEntry) {
        primaryDsl.insertInto(OUTBOX)
            .set(OUTBOX.ID, entry.id)
            .set(OUTBOX.PAYLOAD_TYPE, entry.payloadType)
            .set(OUTBOX.PAYLOAD, entry.payload)
            .set(OUTBOX.STATUS, "PENDING")
            .execute()
    }

    override fun listPending(limit: Int): List<OutboxEntry> {
        return replicaDsl.selectFrom(OUTBOX)
            .where(OUTBOX.STATUS.eq("PENDING"))
            .orderBy(OUTBOX.CREATED_AT.asc())
            .limit(limit)
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    status = record.status!!,
                    createdAt = record.createdAt!!.toInstant(java.time.ZoneOffset.UTC)
                )
            }
    }

    override fun markProcessed(id: UUID) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.STATUS, "PROCESSED")
            .set(OUTBOX.PROCESSED_AT, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun markFailed(id: UUID, error: String) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.STATUS, "FAILED")
            .set(OUTBOX.LAST_ERROR, error)
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun getStats(): Map<String, Int> {
        val results = replicaDsl.select(OUTBOX.STATUS, org.jooq.impl.DSL.count())
            .from(OUTBOX)
            .groupBy(OUTBOX.STATUS)
            .fetch()
        
        return results.associate { it.value1()!! to it.value2() }
    }

    override fun listFailed(): List<OutboxEntry> {
        return replicaDsl.selectFrom(OUTBOX)
            .where(OUTBOX.STATUS.eq("FAILED"))
            .orderBy(OUTBOX.CREATED_AT.desc())
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    status = record.status!!,
                    createdAt = record.createdAt!!.toInstant(java.time.ZoneOffset.UTC)
                )
            }
    }
}
