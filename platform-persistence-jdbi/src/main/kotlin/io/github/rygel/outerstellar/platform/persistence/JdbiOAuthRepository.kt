package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.OAuthConnection
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import java.sql.ResultSet
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiOAuthRepository(private val jdbi: Jdbi) : OAuthRepository {

    override fun save(connection: OAuthConnection) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_oauth_connections (user_id, provider, subject, email)
                    VALUES (:userId, :provider, :subject, :email)
                    """
                )
                .bind("userId", connection.userId)
                .bind("provider", connection.provider)
                .bind("subject", connection.subject)
                .bind("email", connection.email)
                .execute()
        }
    }

    override fun findByProviderSubject(provider: String, subject: String): OAuthConnection? =
        jdbi.withHandle<OAuthConnection?, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT id, user_id, provider, subject, email FROM plt_oauth_connections
                    WHERE provider = :provider AND subject = :subject
                    """
                )
                .bind("provider", provider)
                .bind("subject", subject)
                .map { rs, _ -> mapRow(rs) }
                .findOne()
                .orElse(null)
        }

    override fun findByUserId(userId: UUID): List<OAuthConnection> =
        jdbi.withHandle<List<OAuthConnection>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, user_id, provider, subject, email FROM plt_oauth_connections WHERE user_id = :userId ORDER BY created_at DESC"
                )
                .bind("userId", userId)
                .map { rs, _ -> mapRow(rs) }
                .list()
        }

    override fun delete(id: Long, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM plt_oauth_connections WHERE id = :id AND user_id = :userId")
                .bind("id", id)
                .bind("userId", userId)
                .execute()
        }
    }

    private fun mapRow(rs: ResultSet): OAuthConnection =
        OAuthConnection(
            id = rs.getLong("id"),
            userId = rs.getObject("user_id", UUID::class.java),
            provider = rs.getString("provider"),
            subject = rs.getString("subject"),
            email = rs.getString("email"),
        )
}
