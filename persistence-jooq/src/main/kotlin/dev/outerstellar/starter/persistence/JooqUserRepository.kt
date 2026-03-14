package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.USERS
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class JooqUserRepository(private val dsl: DSLContext) : UserRepository {
    private val logger = LoggerFactory.getLogger(JooqUserRepository::class.java)

    private fun mapUser(record: dev.outerstellar.starter.jooq.tables.records.UsersRecord): User {
        return User(
            id = record.id!!,
            username = record.username!!,
            email = record.email!!,
            passwordHash = record.passwordHash!!,
            role = UserRole.valueOf(record.role!!),
            enabled = record.enabled!!,
            lastActivityAt = record.lastActivityAt?.toInstant(ZoneOffset.UTC),
        )
    }

    override fun findById(id: UUID): User? {
        return dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOne()?.let { mapUser(it) }
    }

    override fun findByUsername(username: String): User? {
        return dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOne()?.let {
            mapUser(it)
        }
    }

    override fun findByEmail(email: String): User? {
        return dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne()?.let { mapUser(it) }
    }

    override fun save(user: User) {
        dsl.insertInto(USERS)
            .set(USERS.ID, user.id)
            .set(USERS.USERNAME, user.username)
            .set(USERS.EMAIL, user.email)
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.ROLE, user.role.name)
            .set(USERS.ENABLED, user.enabled)
            .onDuplicateKeyUpdate()
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.ROLE, user.role.name)
            .set(USERS.ENABLED, user.enabled)
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
        return dsl.selectFrom(USERS).orderBy(USERS.USERNAME).fetch().map { mapUser(it) }
    }

    override fun updateRole(userId: UUID, role: UserRole) {
        dsl.update(USERS).set(USERS.ROLE, role.name).where(USERS.ID.eq(userId)).execute()
    }

    override fun updateEnabled(userId: UUID, enabled: Boolean) {
        dsl.update(USERS).set(USERS.ENABLED, enabled).where(USERS.ID.eq(userId)).execute()
    }

    override fun updateLastActivity(userId: UUID) {
        dsl.update(USERS)
            .set(USERS.LAST_ACTIVITY_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(USERS.ID.eq(userId))
            .execute()
    }
}
