package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_USERS
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class JooqUserRepository(private val dsl: DSLContext) : UserRepository {
    private val logger = LoggerFactory.getLogger(JooqUserRepository::class.java)

    private fun mapUser(record: io.github.rygel.outerstellar.platform.jooq.tables.records.PltUsersRecord): User {
        return User(
            id = record.id!!,
            username = record.username!!,
            email = record.email!!,
            passwordHash = record.passwordHash!!,
            role = UserRole.valueOf(record.role!!),
            enabled = record.enabled!!,
            lastActivityAt = record.lastActivityAt?.toInstant(ZoneOffset.UTC),
            avatarUrl = record.avatarUrl,
            emailNotificationsEnabled = record.emailNotificationsEnabled ?: true,
            pushNotificationsEnabled = record.pushNotificationsEnabled ?: true,
            language = record.language,
            theme = record.theme,
            layout = record.layout,
        )
    }

    override fun findById(id: UUID): User? {
        return dsl.selectFrom(PLT_USERS).where(PLT_USERS.ID.eq(id)).fetchOne()?.let { mapUser(it) }
    }

    override fun findByUsername(username: String): User? {
        return dsl.selectFrom(PLT_USERS).where(PLT_USERS.USERNAME.eq(username)).fetchOne()?.let { mapUser(it) }
    }

    override fun findByEmail(email: String): User? {
        return dsl.selectFrom(PLT_USERS).where(PLT_USERS.EMAIL.eq(email)).fetchOne()?.let { mapUser(it) }
    }

    override fun save(user: User) {
        dsl.insertInto(PLT_USERS)
            .set(PLT_USERS.ID, user.id)
            .set(PLT_USERS.USERNAME, user.username)
            .set(PLT_USERS.EMAIL, user.email)
            .set(PLT_USERS.PASSWORD_HASH, user.passwordHash)
            .set(PLT_USERS.ROLE, user.role.name)
            .set(PLT_USERS.ENABLED, user.enabled)
            .onDuplicateKeyUpdate()
            .set(PLT_USERS.EMAIL, user.email)
            .set(PLT_USERS.PASSWORD_HASH, user.passwordHash)
            .set(PLT_USERS.ROLE, user.role.name)
            .set(PLT_USERS.ENABLED, user.enabled)
            .execute()
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
        return dsl.selectFrom(PLT_USERS).orderBy(PLT_USERS.USERNAME).fetch().map { mapUser(it) }
    }

    override fun findPage(limit: Int, offset: Int): List<User> =
        dsl.selectFrom(PLT_USERS).orderBy(PLT_USERS.USERNAME).limit(limit).offset(offset).fetch().map { mapUser(it) }

    override fun countAll(): Long = dsl.selectCount().from(PLT_USERS).fetchOne(0, Long::class.java) ?: 0L

    override fun countByRole(role: UserRole): Long =
        dsl.selectCount().from(PLT_USERS).where(PLT_USERS.ROLE.eq(role.name)).fetchOne(0, Long::class.java) ?: 0L

    override fun updateRole(userId: UUID, role: UserRole) {
        dsl.update(PLT_USERS).set(PLT_USERS.ROLE, role.name).where(PLT_USERS.ID.eq(userId)).execute()
    }

    override fun updateEnabled(userId: UUID, enabled: Boolean) {
        dsl.update(PLT_USERS).set(PLT_USERS.ENABLED, enabled).where(PLT_USERS.ID.eq(userId)).execute()
    }

    override fun updateLastActivity(userId: UUID) {
        dsl.update(PLT_USERS)
            .set(PLT_USERS.LAST_ACTIVITY_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(PLT_USERS.ID.eq(userId))
            .execute()
    }

    override fun deleteById(userId: UUID) {
        dsl.deleteFrom(PLT_USERS).where(PLT_USERS.ID.eq(userId)).execute()
    }

    override fun updateUsername(userId: UUID, newUsername: String) {
        dsl.update(PLT_USERS).set(PLT_USERS.USERNAME, newUsername).where(PLT_USERS.ID.eq(userId)).execute()
    }

    override fun updateAvatarUrl(userId: UUID, avatarUrl: String?) {
        dsl.update(PLT_USERS).set(PLT_USERS.AVATAR_URL, avatarUrl).where(PLT_USERS.ID.eq(userId)).execute()
    }

    override fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        dsl.update(PLT_USERS)
            .set(PLT_USERS.EMAIL_NOTIFICATIONS_ENABLED, emailEnabled)
            .set(PLT_USERS.PUSH_NOTIFICATIONS_ENABLED, pushEnabled)
            .where(PLT_USERS.ID.eq(userId))
            .execute()
    }

    override fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?) {
        dsl.update(PLT_USERS)
            .set(PLT_USERS.LANGUAGE, language)
            .set(PLT_USERS.THEME, theme)
            .set(PLT_USERS.LAYOUT, layout)
            .where(PLT_USERS.ID.eq(userId))
            .execute()
    }

    override fun incrementFailedLoginAttempts(userId: UUID): Int {
        return dsl.resultQuery(
                "UPDATE plt_users SET failed_login_attempts = failed_login_attempts + 1 WHERE id = ? RETURNING failed_login_attempts",
                userId,
            )
            .fetchOne()
            ?.get(0, Int::class.java) ?: 0
    }

    override fun resetFailedLoginAttempts(userId: UUID) {
        dsl.execute("UPDATE plt_users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?", userId)
    }

    override fun updateLockedUntil(userId: UUID, lockedUntil: Instant?) {
        if (lockedUntil != null) {
            dsl.execute(
                "UPDATE plt_users SET locked_until = ? WHERE id = ?",
                lockedUntil.atZone(ZoneOffset.UTC).toOffsetDateTime(),
                userId,
            )
        } else {
            dsl.execute("UPDATE plt_users SET locked_until = NULL WHERE id = ?", userId)
        }
    }

    override fun countUsersSince(cutoff: LocalDateTime): Long {
        val cutoffOffset = cutoff.atZone(ZoneOffset.UTC).toOffsetDateTime()
        return dsl.selectCount()
            .from(PLT_USERS)
            .where(PLT_USERS.CREATED_AT.ge(cutoffOffset))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>? {
        return dsl.resultQuery(
                "SELECT totp_secret, totp_enabled, totp_backup_codes FROM plt_users WHERE id = ?",
                userId,
            )
            .fetchOne()
            ?.let {
                Triple(
                    it.get(0, String::class.java),
                    it.get(1, Boolean::class.java) ?: false,
                    it.get(2, String::class.java),
                )
            }
    }

    override fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?) {
        dsl.execute(
            "UPDATE plt_users SET totp_secret = ?, totp_backup_codes = ? WHERE id = ?",
            secret,
            backupCodes,
            userId,
        )
    }

    override fun enableTotp(userId: UUID) {
        dsl.execute("UPDATE plt_users SET totp_enabled = TRUE WHERE id = ?", userId)
    }

    override fun disableTotp(userId: UUID) {
        dsl.execute("UPDATE plt_users SET totp_enabled = FALSE WHERE id = ?", userId)
    }
}
