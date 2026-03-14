package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.ApiKey
import dev.outerstellar.starter.security.ApiKeyRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

class JooqApiKeyRepository(private val dsl: DSLContext) : ApiKeyRepository {

    private val table = DSL.table("api_keys")
    private val idField = DSL.field("id", Long::class.java)
    private val userIdField = DSL.field("user_id", UUID::class.java)
    private val keyHashField = DSL.field("key_hash", String::class.java)
    private val keyPrefixField = DSL.field("key_prefix", String::class.java)
    private val nameField = DSL.field("name", String::class.java)
    private val enabledField = DSL.field("enabled", Boolean::class.java)
    private val createdAtField = DSL.field("created_at", LocalDateTime::class.java)
    private val lastUsedAtField = DSL.field("last_used_at", LocalDateTime::class.java)

    private fun mapRecord(record: Record): ApiKey {
        val createdAt = record.get(createdAtField)
        val lastUsedAt = record.get(lastUsedAtField)
        return ApiKey(
            id = record.get(idField),
            userId = record.get(userIdField),
            keyHash = record.get(keyHashField),
            keyPrefix = record.get(keyPrefixField),
            name = record.get(nameField),
            enabled = record.get(enabledField),
            createdAt = createdAt?.toInstant(ZoneOffset.UTC) ?: java.time.Instant.now(),
            lastUsedAt = lastUsedAt?.toInstant(ZoneOffset.UTC),
        )
    }

    override fun save(apiKey: ApiKey) {
        dsl.insertInto(table)
            .set(userIdField, apiKey.userId)
            .set(keyHashField, apiKey.keyHash)
            .set(keyPrefixField, apiKey.keyPrefix)
            .set(nameField, apiKey.name)
            .set(enabledField, apiKey.enabled)
            .set(createdAtField, LocalDateTime.ofInstant(apiKey.createdAt, ZoneOffset.UTC))
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
            .set(lastUsedAtField, LocalDateTime.now(ZoneOffset.UTC))
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
