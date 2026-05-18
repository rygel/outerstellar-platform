package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqDeviceTokenRepository(private val dsl: DSLContext) : DeviceTokenRepository {

    private val table = DSL.table("plt_device_tokens")
    private val idField = DSL.field(DSL.name("id"), SQLDataType.BIGINT)
    private val userIdField = DSL.field(DSL.name("user_id"), SQLDataType.UUID)
    private val platformField = DSL.field(DSL.name("platform"), SQLDataType.VARCHAR)
    private val tokenField = DSL.field(DSL.name("token"), SQLDataType.VARCHAR)
    private val appBundleField = DSL.field(DSL.name("app_bundle"), SQLDataType.VARCHAR)
    private val lastSeenField = DSL.field(DSL.name("last_seen"), SQLDataType.TIMESTAMP)

    private fun mapRecord(record: Record): DeviceToken =
        DeviceToken(
            id = record.get(idField)!!,
            userId = record.get(userIdField)!!,
            platform = record.get(platformField)!!,
            token = record.get(tokenField)!!,
            appBundle = record.get(appBundleField),
        )

    override fun upsert(deviceToken: DeviceToken) {
        val now = Timestamp.from(Instant.now())
        dsl.insertInto(
                table,
                userIdField,
                platformField,
                tokenField,
                appBundleField,
                DSL.field("created_at"),
                lastSeenField,
            )
            .values(deviceToken.userId, deviceToken.platform, deviceToken.token, deviceToken.appBundle, now, now)
            .onConflict(tokenField)
            .doUpdate()
            .set(userIdField, deviceToken.userId)
            .set(platformField, deviceToken.platform)
            .set(appBundleField, deviceToken.appBundle)
            .set(lastSeenField, now)
            .execute()
    }

    override fun delete(token: String) {
        dsl.deleteFrom(table).where(tokenField.eq(token)).execute()
    }

    override fun deleteByTokenAndUserId(token: String, userId: UUID): Boolean =
        dsl.deleteFrom(table).where(tokenField.eq(token)).and(userIdField.eq(userId)).execute() > 0

    override fun findByUserId(userId: UUID): List<DeviceToken> =
        dsl.select().from(table).where(userIdField.eq(userId)).orderBy(lastSeenField.desc()).fetch().map {
            mapRecord(it)
        }

    override fun deleteAllForUser(userId: UUID) {
        dsl.deleteFrom(table).where(userIdField.eq(userId)).execute()
    }
}
