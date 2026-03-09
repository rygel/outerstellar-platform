package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.OUTBOX
import org.jooq.DSLContext
import java.time.Instant
import java.time.ZoneOffset
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
            .set(OUTBOX.CREATED_AT, entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime())
            .set(OUTBOX.RETRY_COUNT, entry.retryCount)
            .execute()
    }

    override fun fetchUnprocessed(limit: Int): List<OutboxEntry> {
        return replicaDsl.selectFrom(OUTBOX)
            .where(OUTBOX.PROCESSED_AT.isNull)
            .and(OUTBOX.field("deleted_at")!!.isNull)
            .orderBy(OUTBOX.CREATED_AT.asc())
            .limit(limit)
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    createdAt = record.createdAt!!.toInstant(ZoneOffset.UTC),
                    processedAt = record.processedAt?.toInstant(ZoneOffset.UTC),
                    retryCount = record.retryCount!!,
                    lastError = record.lastError
                )
            }
    }

    override fun markProcessed(id: UUID) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.PROCESSED_AT, Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun markFailed(id: UUID, error: String) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.RETRY_COUNT, OUTBOX.RETRY_COUNT.plus(1))
            .set(OUTBOX.LAST_ERROR, error)
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun softDelete(id: UUID) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.field("deleted_at", java.time.LocalDateTime::class.java), Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
            .where(OUTBOX.ID.eq(id))
            .execute()
    }
}

class JooqTransactionManager(private val dsl: DSLContext) : TransactionManager {
    override fun <T> inTransaction(block: () -> T): T {
        return dsl.transactionResult { _ -> block() }
    }
}
