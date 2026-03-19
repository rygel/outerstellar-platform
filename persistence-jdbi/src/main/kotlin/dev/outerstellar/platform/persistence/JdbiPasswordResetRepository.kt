package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.PasswordResetToken
import dev.outerstellar.platform.security.PasswordResetRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiPasswordResetRepository(private val jdbi: Jdbi) : PasswordResetRepository {
    override fun save(resetToken: PasswordResetToken) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """INSERT INTO password_reset_tokens (user_id, token, expires_at, used)
                   VALUES (:userId, :token, :expiresAt, :used)"""
                )
                .bind("userId", resetToken.userId)
                .bind("token", resetToken.token)
                .bind("expiresAt", LocalDateTime.ofInstant(resetToken.expiresAt, ZoneOffset.UTC))
                .bind("used", resetToken.used)
                .execute()
        }
    }

    override fun findByToken(tokenValue: String): PasswordResetToken? {
        return jdbi.withHandle<PasswordResetToken?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM password_reset_tokens WHERE token = :token")
                .bind("token", tokenValue)
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

    override fun markUsed(tokenValue: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE password_reset_tokens SET used = true WHERE token = :token")
                .bind("token", tokenValue)
                .execute()
        }
    }
}
