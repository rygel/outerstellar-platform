package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiDeviceTokenRepository(private val jdbi: Jdbi) : DeviceTokenRepository {

    override fun upsert(deviceToken: DeviceToken) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_device_tokens (user_id, platform, token, app_bundle, created_at, last_seen)
                    VALUES (:userId, :platform, :token, :appBundle, :now, :now)
                    ON CONFLICT (token) DO UPDATE SET
                        user_id = EXCLUDED.user_id,
                        platform = EXCLUDED.platform,
                        app_bundle = EXCLUDED.app_bundle,
                        last_seen = EXCLUDED.last_seen
                    """
                )
                .bind("userId", deviceToken.userId)
                .bind("platform", deviceToken.platform)
                .bind("token", deviceToken.token)
                .bind("appBundle", deviceToken.appBundle)
                .bind("now", LocalDateTime.now(ZoneOffset.UTC))
                .execute()
        }
    }

    override fun delete(token: String) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM plt_device_tokens WHERE token = :token").bind("token", token).execute()
        }
    }

    override fun deleteByTokenAndUserId(token: String, userId: UUID): Boolean =
        jdbi.withHandle<Boolean, Exception> { handle ->
            handle.execute("DELETE FROM plt_device_tokens WHERE token = ? AND user_id = ?", token, userId) > 0
        }

    override fun findByUserId(userId: UUID): List<DeviceToken> =
        jdbi.withHandle<List<DeviceToken>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, user_id, platform, token, app_bundle FROM plt_device_tokens WHERE user_id = :userId ORDER BY last_seen DESC"
                )
                .bind("userId", userId)
                .map { rs, _ -> mapRow(rs) }
                .list()
        }

    override fun deleteAllForUser(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM plt_device_tokens WHERE user_id = :userId")
                .bind("userId", userId)
                .execute()
        }
    }

    private fun mapRow(rs: ResultSet): DeviceToken =
        DeviceToken(
            id = rs.getLong("id"),
            userId = rs.getObject("user_id", UUID::class.java),
            platform = rs.getString("platform"),
            token = rs.getString("token"),
            appBundle = rs.getString("app_bundle"),
        )
}
