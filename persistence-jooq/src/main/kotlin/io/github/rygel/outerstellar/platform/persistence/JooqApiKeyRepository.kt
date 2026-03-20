package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ApiKey
import io.github.rygel.outerstellar.platform.security.ApiKeyRepository
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqApiKeyRepository(private val dsl: DSLContext) : ApiKeyRepository {

    private val table = DSL.table("API_KEYS")
    private val idField = DSL.field(DSL.name("ID"), SQLDataType.BIGINT)
    private val userIdField = DSL.field(DSL.name("USER_ID"), SQLDataType.UUID)
    private val keyHashField = DSL.field(DSL.name("KEY_HASH"), SQLDataType.VARCHAR)
    private val keyPrefixField = DSL.field(DSL.name("KEY_PREFIX"), SQLDataType.VARCHAR)
    private val nameField = DSL.field(DSL.name("NAME"), SQLDataType.VARCHAR)
    private val enabledField = DSL.field(DSL.name("ENABLED"), SQLDataType.BOOLEAN)
    private val createdAtField = DSL.field(DSL.name("CREATED_AT"), SQLDataType.TIMESTAMP)
    private val lastUsedAtField = DSL.field(DSL.name("LAST_USED_AT"), SQLDataType.TIMESTAMP)

    private fun mapRecord(record: Record): ApiKey {
        val createdAt = record.get(createdAtField)
        val lastUsedAt = record.get(lastUsedAtField)
        return ApiKey(
            id = record.get(idField)!!,
            userId = record.get(userIdField)!!,
            keyHash = record.get(keyHashField)!!,
            keyPrefix = record.get(keyPrefixField)!!,
            name = record.get(nameField)!!,
            enabled = record.get(enabledField) ?: true,
            createdAt = createdAt?.toInstant() ?: java.time.Instant.now(),
            lastUsedAt = lastUsedAt?.toInstant(),
        )
    }

    override fun save(apiKey: ApiKey) {
        dsl.insertInto(table)
            .set(userIdField, apiKey.userId)
            .set(keyHashField, apiKey.keyHash)
            .set(keyPrefixField, apiKey.keyPrefix)
            .set(nameField, apiKey.name)
            .set(enabledField, apiKey.enabled)
            .set(createdAtField, java.sql.Timestamp.from(apiKey.createdAt))
            .execute()
    }

    override fun findByKeyHash(keyHash: String): ApiKey? {
        return dsl.select().from(table).where(keyHashField.eq(keyHash)).fetchOne()?.let {
            mapRecord(it)
        }
    }

    override fun findByUserId(userId: UUID): List<ApiKey> {
        return dsl.select()
            .from(table)
            .where(userIdField.eq(userId))
            .orderBy(createdAtField.desc())
            .fetch()
            .map { mapRecord(it) }
    }

    override fun delete(id: Long, userId: UUID) {
        dsl.deleteFrom(table).where(idField.eq(id).and(userIdField.eq(userId))).execute()
    }

    override fun updateLastUsed(id: Long) {
        dsl.update(table)
            .set(lastUsedAtField, java.sql.Timestamp.from(java.time.Instant.now()))
            .where(idField.eq(id))
            .execute()
    }

    companion object {
        fun hashKey(key: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            return digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
