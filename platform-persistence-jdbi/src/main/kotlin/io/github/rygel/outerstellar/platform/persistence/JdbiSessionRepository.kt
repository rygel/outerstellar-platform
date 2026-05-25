package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Session
import java.time.Instant
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiSessionRepository(private val jdbi: Jdbi) : SessionRepository {

    override fun save(session: Session) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_sessions (token_hash, user_id, created_at, expires_at)
                    VALUES (:tokenHash, :userId, :createdAt, :expiresAt)
                    """
                )
                .bind("tokenHash", session.tokenHash)
                .bind("userId", session.userId)
                .bind("createdAt", session.createdAt)
                .bind("expiresAt", session.expiresAt)
                .execute()
        }
    }

    override fun findByTokenHash(tokenHash: String): Session? {
        return jdbi.withHandle<Session?, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT id, token_hash, user_id, created_at, expires_at FROM plt_sessions
                    WHERE token_hash = :tokenHash AND expires_at > :now
                    """
                )
                .bind("tokenHash", tokenHash)
                .bind("now", Instant.now())
                .map { rs, _ -> mapSession(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findByTokenHashIncludingExpired(tokenHash: String): Session? {
        return jdbi.withHandle<Session?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, token_hash, user_id, created_at, expires_at FROM plt_sessions WHERE token_hash = :tokenHash"
                )
                .bind("tokenHash", tokenHash)
                .map { rs, _ -> mapSession(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun updateExpiresAt(tokenHash: String, expiresAt: Instant) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_sessions SET expires_at = :expiresAt WHERE token_hash = :tokenHash")
                .bind("expiresAt", expiresAt)
                .bind("tokenHash", tokenHash)
                .execute()
        }
    }

    override fun deleteByTokenHash(tokenHash: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM plt_sessions WHERE token_hash = :tokenHash")
                .bind("tokenHash", tokenHash)
                .execute()
        }
    }

    override fun deleteByUserId(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM plt_sessions WHERE user_id = :userId").bind("userId", userId).execute()
        }
    }

    override fun deleteExpired() {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM plt_sessions WHERE expires_at <= :now")
                .bind("now", Instant.now())
                .execute()
        }
    }

    private fun mapSession(rs: java.sql.ResultSet): Session {
        return Session(
            id = rs.getLong("id"),
            tokenHash = rs.getString("token_hash"),
            userId = rs.getObject("user_id", UUID::class.java),
            createdAt = rs.getRequiredInstant("created_at"),
            expiresAt = rs.getRequiredInstant("expires_at"),
        )
    }
}
