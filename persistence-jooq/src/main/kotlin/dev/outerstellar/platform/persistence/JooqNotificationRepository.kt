package dev.outerstellar.platform.persistence

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class JooqNotificationRepository(private val dsl: DSLContext) : NotificationRepository {

    private val table = DSL.table("notifications")
    private val idField = DSL.field("id", UUID::class.java)
    private val userIdField = DSL.field("user_id", UUID::class.java)
    private val titleField = DSL.field("title", String::class.java)
    private val bodyField = DSL.field("body", String::class.java)
    private val typeField = DSL.field("type", String::class.java)
    private val readAtField = DSL.field("read_at", LocalDateTime::class.java)
    private val createdAtField = DSL.field("created_at", LocalDateTime::class.java)

    private fun map(
        record: org.jooq.Record7<UUID, UUID, String, String, String, LocalDateTime, LocalDateTime>
    ): Notification =
        Notification(
            id = record.value1()!!,
            userId = record.value2()!!,
            title = record.value3()!!,
            body = record.value4()!!,
            type = record.value5() ?: "info",
            readAt = record.value6()?.toInstant(ZoneOffset.UTC),
            createdAt = record.value7()?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
        )

    override fun save(notification: Notification) {
        dsl.insertInto(table)
            .set(idField, notification.id)
            .set(userIdField, notification.userId)
            .set(titleField, notification.title)
            .set(bodyField, notification.body)
            .set(typeField, notification.type)
            .set(createdAtField, notification.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime())
            .execute()
    }

    override fun findByUserId(userId: UUID, limit: Int): List<Notification> =
        dsl.select(
                idField,
                userIdField,
                titleField,
                bodyField,
                typeField,
                readAtField,
                createdAtField,
            )
            .from(table)
            .where(userIdField.eq(userId))
            .orderBy(createdAtField.desc())
            .limit(limit)
            .fetch()
            .map { map(it) }

    override fun countUnread(userId: UUID): Int =
        dsl.selectCount()
            .from(table)
            .where(userIdField.eq(userId).and(readAtField.isNull))
            .fetchOne(0, Int::class.java) ?: 0

    override fun markRead(id: UUID, userId: UUID) {
        dsl.update(table)
            .set(readAtField, LocalDateTime.now(ZoneOffset.UTC))
            .where(idField.eq(id).and(userIdField.eq(userId)))
            .execute()
    }

    override fun markAllRead(userId: UUID) {
        dsl.update(table)
            .set(readAtField, LocalDateTime.now(ZoneOffset.UTC))
            .where(userIdField.eq(userId).and(readAtField.isNull))
            .execute()
    }

    override fun delete(id: UUID, userId: UUID) {
        dsl.deleteFrom(table).where(idField.eq(id).and(userIdField.eq(userId))).execute()
    }
}
