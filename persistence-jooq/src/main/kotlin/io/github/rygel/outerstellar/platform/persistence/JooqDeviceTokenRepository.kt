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

    private val table = DSL.table("DEVICE_TOKENS")
    private val idField = DSL.field(DSL.name("ID"), SQLDataType.BIGINT)
    private val userIdField = DSL.field(DSL.name("USER_ID"), SQLDataType.UUID)
    private val platformField = DSL.field(DSL.name("PLATFORM"), SQLDataType.VARCHAR)
    private val tokenField = DSL.field(DSL.name("TOKEN"), SQLDataType.VARCHAR)
    private val appBundleField = DSL.field(DSL.name("APP_BUNDLE"), SQLDataType.VARCHAR)
    private val lastSeenField = DSL.field(DSL.name("LAST_SEEN"), SQLDataType.TIMESTAMP)

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
        dsl.execute(
            "MERGE INTO DEVICE_TOKENS (USER_ID, PLATFORM, TOKEN, APP_BUNDLE, CREATED_AT, LAST_SEEN)" +
                " KEY(TOKEN) VALUES (?, ?, ?, ?, ?, ?)",
            deviceToken.userId,
            deviceToken.platform,
            deviceToken.token,
            deviceToken.appBundle,
            now,
            now,
        )
    }

    override fun delete(token: String) {
        dsl.deleteFrom(table).where(tokenField.eq(token)).execute()
    }

    override fun findByUserId(userId: UUID): List<DeviceToken> =
        dsl.select()
            .from(table)
            .where(userIdField.eq(userId))
            .orderBy(lastSeenField.desc())
            .fetch()
            .map { mapRecord(it) }

    override fun deleteAllForUser(userId: UUID) {
        dsl.deleteFrom(table).where(userIdField.eq(userId)).execute()
    }
}
