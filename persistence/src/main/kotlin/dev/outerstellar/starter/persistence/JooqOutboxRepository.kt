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
            .set(OUTBOX.field("status", String::class.java), entry.status)
            .execute()
    }

    override fun fetchUnprocessed(limit: Int): List<OutboxEntry> {
        val statusField = OUTBOX.field("status", String::class.java)
        val deletedAtField = OUTBOX.field("deleted_at", java.time.LocalDateTime::class.java)

        return primaryDsl.selectFrom(OUTBOX)
            .where(statusField?.eq("PENDING") ?: OUTBOX.PROCESSED_AT.isNull)
            .and(deletedAtField?.isNull ?: org.jooq.impl.DSL.trueCondition())
            .orderBy(OUTBOX.CREATED_AT.asc())
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetch { record ->
                OutboxEntry(
                    id = record.id!!,
                    payloadType = record.payloadType!!,
                    payload = record.payload!!,
                    createdAt = record.createdAt!!.toInstant(ZoneOffset.UTC),
                    processedAt = record.processedAt?.toInstant(ZoneOffset.UTC),
                    retryCount = record.retryCount!!,
                    lastError = record.lastError,
                    status = record.get(statusField) ?: "PENDING"
                )
            }
    }

    override fun markProcessed(id: UUID) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.PROCESSED_AT, Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
            .set(OUTBOX.field("status", String::class.java), "PROCESSED")
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun markFailed(id: UUID, error: String, maxRetries: Int) {
        val currentRetryCount = primaryDsl.select(OUTBOX.RETRY_COUNT)
            .from(OUTBOX)
            .where(OUTBOX.ID.eq(id))
            .fetchOne(OUTBOX.RETRY_COUNT) ?: 0

        val newRetryCount = currentRetryCount + 1
        val newStatus = if (newRetryCount >= maxRetries) "FAILED" else "PENDING"

        primaryDsl.update(OUTBOX)
            .set(OUTBOX.RETRY_COUNT, newRetryCount)
            .set(OUTBOX.LAST_ERROR, error)
            .set(OUTBOX.field("status", String::class.java), newStatus)
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun softDelete(id: UUID) {
        primaryDsl.update(OUTBOX)
            .set(OUTBOX.field("deleted_at", java.time.LocalDateTime::class.java), Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
            .where(OUTBOX.ID.eq(id))
            .execute()
    }

    override fun countByStatus(status: String): Int {
        val statusField = OUTBOX.field("status", String::class.java)
        return replicaDsl.selectCount()
            .from(OUTBOX)
            .where(statusField?.eq(status) ?: if (status == "PENDING") OUTBOX.PROCESSED_AT.isNull else org.jooq.impl.DSL.falseCondition())
            .fetchOne(0, Int::class.java) ?: 0
    }
}

class JooqTransactionManager(private val dsl: DSLContext) : TransactionManager {
    override fun <T> inTransaction(block: () -> T): T {
        return dsl.transactionResult { _ -> block() }
    }
}
