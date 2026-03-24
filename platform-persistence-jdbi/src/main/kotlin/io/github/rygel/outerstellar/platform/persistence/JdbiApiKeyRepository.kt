package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ApiKey
import io.github.rygel.outerstellar.platform.security.ApiKeyRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiApiKeyRepository(private val jdbi: Jdbi) : ApiKeyRepository {

    override fun save(apiKey: ApiKey) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_api_keys (user_id, key_hash, key_prefix, name, enabled, created_at)
                    VALUES (:userId, :keyHash, :keyPrefix, :name, :enabled, :createdAt)
                    """
                )
                .bind("userId", apiKey.userId)
                .bind("keyHash", apiKey.keyHash)
                .bind("keyPrefix", apiKey.keyPrefix)
                .bind("name", apiKey.name)
                .bind("enabled", apiKey.enabled)
                .bind("createdAt", LocalDateTime.ofInstant(apiKey.createdAt, ZoneOffset.UTC))
                .execute()
        }
    }

    override fun findByKeyHash(keyHash: String): ApiKey? {
        return jdbi.withHandle<ApiKey?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_api_keys WHERE key_hash = :keyHash")
                .bind("keyHash", keyHash)
                .map { rs, _ -> mapApiKey(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findByUserId(userId: UUID): List<ApiKey> {
        return jdbi.withHandle<List<ApiKey>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_api_keys WHERE user_id = :userId ORDER BY created_at DESC")
                .bind("userId", userId)
                .map { rs, _ -> mapApiKey(rs) }
                .list()
        }
    }

    override fun delete(id: Long, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM plt_api_keys WHERE id = :id AND user_id = :userId")
                .bind("id", id)
                .bind("userId", userId)
                .execute()
        }
    }

    override fun updateLastUsed(id: Long) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_api_keys SET last_used_at = :lastUsedAt WHERE id = :id")
                .bind("lastUsedAt", LocalDateTime.now(ZoneOffset.UTC))
                .bind("id", id)
                .execute()
        }
    }

    private fun mapApiKey(rs: java.sql.ResultSet): ApiKey {
        val createdAt = rs.getTimestamp("created_at")
        val lastUsedAt = rs.getTimestamp("last_used_at")
        return ApiKey(
            id = rs.getLong("id"),
            userId = rs.getObject("user_id", UUID::class.java),
            keyHash = rs.getString("key_hash"),
            keyPrefix = rs.getString("key_prefix"),
            name = rs.getString("name"),
            enabled = rs.getBoolean("enabled"),
            createdAt = createdAt?.toInstant() ?: java.time.Instant.now(),
            lastUsedAt = lastUsedAt?.toInstant(),
        )
    }

    companion object {
        fun hashKey(key: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            return digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
