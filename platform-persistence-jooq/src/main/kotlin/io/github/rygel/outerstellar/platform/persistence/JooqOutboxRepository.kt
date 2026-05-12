package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_OUTBOX
import java.util.*
import org.jooq.DSLContext

class JooqOutboxRepository(private val dsl: DSLContext) : OutboxRepository {

    override fun save(entry: OutboxEntry) {
        dsl.insertInto(PLT_OUTBOX)
            .set(PLT_OUTBOX.ID, entry.id)
            .set(PLT_OUTBOX.PAYLOAD_TYPE, entry.payloadType)
            .set(PLT_OUTBOX.PAYLOAD, entry.payload)
            .set(PLT_OUTBOX.STATUS, OutboxStatus.PENDING.name)
            .execute()
    }

    override fun listPending(limit: Int): List<OutboxEntry> {
        return dsl.selectFrom(PLT_OUTBOX)
            .where(PLT_OUTBOX.STATUS.eq(OutboxStatus.PENDING.name))
            .orderBy(PLT_OUTBOX.CREATED_AT.asc())
            .limit(limit)
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    status = OutboxStatus.valueOf(record.status!!),
                    createdAt = record.createdAt!!.toInstant(java.time.ZoneOffset.UTC),
                )
            }
    }

    override fun markProcessed(id: UUID) {
        dsl.update(PLT_OUTBOX)
            .set(PLT_OUTBOX.STATUS, OutboxStatus.PROCESSED.name)
            .set(PLT_OUTBOX.PROCESSED_AT, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
            .where(PLT_OUTBOX.ID.eq(id))
            .execute()
    }

    override fun markFailed(id: UUID, error: String) {
        dsl.update(PLT_OUTBOX)
            .set(PLT_OUTBOX.STATUS, OutboxStatus.FAILED.name)
            .set(PLT_OUTBOX.LAST_ERROR, error)
            .where(PLT_OUTBOX.ID.eq(id))
            .execute()
    }

    override fun getStats(): Map<OutboxStatus, Int> {
        val results =
            dsl.select(PLT_OUTBOX.STATUS, org.jooq.impl.DSL.count()).from(PLT_OUTBOX).groupBy(PLT_OUTBOX.STATUS).fetch()

        return results.associate { OutboxStatus.valueOf(it.value1()!!) to it.value2() }
    }

    override fun listFailed(): List<OutboxEntry> {
        return dsl.selectFrom(PLT_OUTBOX)
            .where(PLT_OUTBOX.STATUS.eq(OutboxStatus.FAILED.name))
            .orderBy(PLT_OUTBOX.CREATED_AT.desc())
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    status = OutboxStatus.valueOf(record.status!!),
                    createdAt = record.createdAt!!.toInstant(java.time.ZoneOffset.UTC),
                )
            }
    }
}
