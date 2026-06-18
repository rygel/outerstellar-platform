package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
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
                .bind("expiresAt", token.expiresAt)
                .bind("used", token.used)
                .execute()
        }
    }

    override fun findByToken(token: String): PasswordResetToken? {
        return jdbi.withHandle<PasswordResetToken?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, user_id, token, expires_at, used FROM plt_password_reset_tokens WHERE token = :token"
                )
                .bind("token", token)
                .map { rs, _ ->
                    PasswordResetToken(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        token = rs.getString("token"),
                        expiresAt = rs.getRequiredInstant("expires_at"),
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

    override fun claimToken(token: String): UUID? {
        // Atomic single-statement claim: only marks used if currently unused+unexpired, returning the owner.
        // The WHERE used = false guard makes concurrent claims race-safe (only one UPDATE matches),
        // closing the TOCTOU where the old findByToken + Java check + markUsed ran in separate
        // transactions and both observed used=false. Returns null when no row matched (invalid/expired/used).
        return jdbi.withHandle<UUID?, Exception> { handle ->
            handle
                .createQuery(
                    """
                    UPDATE plt_password_reset_tokens
                       SET used = true
                     WHERE token = :token
                       AND used = false
                       AND expires_at > now()
                    RETURNING user_id
                    """
                        .trimIndent()
                )
                .bind("token", token)
                .map { rs, _ -> rs.getObject("user_id", UUID::class.java) }
                .findOne()
                .orElse(null)
        }
    }

    override fun invalidateUnusedForUser(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE plt_password_reset_tokens SET used = true WHERE user_id = :userId AND used = false"
                )
                .bind("userId", userId)
                .execute()
        }
    }
}
