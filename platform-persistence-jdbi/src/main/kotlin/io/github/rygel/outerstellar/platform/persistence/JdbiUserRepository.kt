package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.security.UserRole
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory

class JdbiUserRepository(private val jdbi: Jdbi) : UserRepository {
    private val logger = LoggerFactory.getLogger(JdbiUserRepository::class.java)

    override fun findById(id: UUID): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_users WHERE id = :id")
                .bind("id", id)
                .map { rs, _ -> mapUser(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findByUsername(username: String): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_users WHERE username = :username")
                .bind("username", username)
                .map { rs, _ -> mapUser(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findByEmail(email: String): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_users WHERE email = :email")
                .bind("email", email)
                .map { rs, _ -> mapUser(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun save(user: User) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    MERGE INTO plt_users (id, username, email, password_hash, role, enabled)
                    KEY (id)
                    VALUES (:id, :username, :email, :passwordHash, :role, :enabled)
                    """
                )
                .bind("id", user.id)
                .bind("username", user.username)
                .bind("email", user.email)
                .bind("passwordHash", user.passwordHash)
                .bind("role", user.role.name)
                .bind("enabled", user.enabled)
                .execute()
        }
    }

    override fun seedAdminUser(passwordHash: String) {
        if (findByUsername("admin") == null) {
            logger.info("Seeding default admin user")
            save(
                User(
                    id = UUID.randomUUID(),
                    username = "admin",
                    email = "admin@outerstellar.de",
                    passwordHash = passwordHash,
                    role = UserRole.ADMIN,
                )
            )
        }
    }

    override fun findAll(): List<User> {
        return jdbi.withHandle<List<User>, Exception> { handle ->
            handle.createQuery("SELECT * FROM plt_users ORDER BY username").map { rs, _ -> mapUser(rs) }.list()
        }
    }

    override fun findPage(limit: Int, offset: Int): List<User> =
        jdbi.withHandle<List<User>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM plt_users ORDER BY username LIMIT :limit OFFSET :offset")
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapUser(rs) }
                .list()
        }

    override fun countAll(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle.createQuery("SELECT COUNT(*) FROM plt_users").mapTo(Long::class.java).one()
        }

    override fun countByRole(role: UserRole): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM plt_users WHERE role = :role")
                .bind("role", role.name)
                .mapTo(Long::class.java)
                .one()
        }

    override fun updateRole(userId: UUID, role: UserRole) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_users SET role = :role WHERE id = :id")
                .bind("role", role.name)
                .bind("id", userId)
                .execute()
        }
    }

    override fun updateEnabled(userId: UUID, enabled: Boolean) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_users SET enabled = :enabled WHERE id = :id")
                .bind("enabled", enabled)
                .bind("id", userId)
                .execute()
        }
    }

    override fun updateLastActivity(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_users SET last_activity_at = :lastActivity WHERE id = :id")
                .bind("lastActivity", LocalDateTime.now(ZoneOffset.UTC))
                .bind("id", userId)
                .execute()
        }
    }

    override fun deleteById(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM plt_users WHERE id = :id").bind("id", userId).execute()
        }
    }

    override fun updateUsername(userId: UUID, newUsername: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_users SET username = :username WHERE id = :id")
                .bind("username", newUsername)
                .bind("id", userId)
                .execute()
        }
    }

    override fun updateAvatarUrl(userId: UUID, avatarUrl: String?) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_users SET avatar_url = :avatarUrl WHERE id = :id")
                .bind("avatarUrl", avatarUrl)
                .bind("id", userId)
                .execute()
        }
    }

    override fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE plt_users SET email_notifications_enabled = :emailEnabled, push_notifications_enabled = :pushEnabled WHERE id = :id"
                )
                .bind("emailEnabled", emailEnabled)
                .bind("pushEnabled", pushEnabled)
                .bind("id", userId)
                .execute()
        }
    }

    override fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE plt_users SET language = :language, theme = :theme, layout = :layout WHERE id = :id"
                )
                .bind("language", language)
                .bind("theme", theme)
                .bind("layout", layout)
                .bind("id", userId)
                .execute()
        }
    }

    private fun mapUser(rs: java.sql.ResultSet): User {
        val lastActivity = rs.getTimestamp("last_activity_at")
        return User(
            id = rs.getObject("id", UUID::class.java),
            username = rs.getString("username"),
            email = rs.getString("email"),
            passwordHash = rs.getString("password_hash"),
            role = UserRole.valueOf(rs.getString("role")),
            enabled = rs.getBoolean("enabled"),
            lastActivityAt = lastActivity?.toInstant(),
            avatarUrl = rs.getString("avatar_url"),
            emailNotificationsEnabled = rs.getBoolean("email_notifications_enabled"),
            pushNotificationsEnabled = rs.getBoolean("push_notifications_enabled"),
            language = rs.getString("language"),
            theme = rs.getString("theme"),
            layout = rs.getString("layout"),
        )
    }
}
