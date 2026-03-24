package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.security.PasswordResetRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiPasswordResetRepository(private val jdbi: Jdbi) : PasswordResetRepository {
    override fun save(token: PasswordResetToken) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """INSERT INTO plt_password_reset_tokens (user_id, token, expires_at, used)
                   VALUES (:userId, :token, :expiresAt, :used)"""
                )
                .bind("userId", token.userId)
                .bind("token", token.token)
                .bind("expiresAt", LocalDateTime.ofInstant(token.expiresAt, ZoneOffset.UTC))
                .bind("used", token.used)
                .execute()
        }
    }

    override fun findByToken(token: String): PasswordResetToken? {
        return jdbi.withHandle<PasswordResetToken?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_password_reset_tokens WHERE token = :token")
                .bind("token", token)
                .map { rs, _ ->
                    PasswordResetToken(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        token = rs.getString("token"),
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                        used = rs.getBoolean("used"),
                    )
                }
                .findOne()
                .orElse(null)
        }
    }

    override fun markUsed(token: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_password_reset_tokens SET used = true WHERE token = :token")
                .bind("token", token)
                .execute()
        }
    }
}
