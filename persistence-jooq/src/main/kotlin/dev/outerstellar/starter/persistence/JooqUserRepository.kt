package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.USERS
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory

class JooqUserRepository(private val dsl: DSLContext) : UserRepository {
    private val logger = LoggerFactory.getLogger(JooqUserRepository::class.java)

    private val lastActivityAtField =
        DSL.field(DSL.name("LAST_ACTIVITY_AT"), SQLDataType.LOCALDATETIME)

    private val userFields =
        listOf(
            USERS.ID,
            USERS.USERNAME,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.ROLE,
            USERS.ENABLED,
            lastActivityAtField,
        )

    private fun mapUser(record: org.jooq.Record): User {
        return User(
            id = record.get(USERS.ID)!!,
            username = record.get(USERS.USERNAME)!!,
            email = record.get(USERS.EMAIL)!!,
            passwordHash = record.get(USERS.PASSWORD_HASH)!!,
            role = UserRole.valueOf(record.get(USERS.ROLE)!!),
            enabled = record.get(USERS.ENABLED)!!,
            lastActivityAt = record.get(lastActivityAtField)?.toInstant(ZoneOffset.UTC),
        )
    }

    override fun findById(id: UUID): User? {
        return dsl.select(userFields).from(USERS).where(USERS.ID.eq(id)).fetchOne()?.let {
            mapUser(it)
        }
    }

    override fun findByUsername(username: String): User? {
        return dsl.select(userFields)
            .from(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOne()
            ?.let { mapUser(it) }
    }

    override fun findByEmail(email: String): User? {
        return dsl.select(userFields).from(USERS).where(USERS.EMAIL.eq(email)).fetchOne()?.let {
            mapUser(it)
        }
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
        return dsl.select(userFields).from(USERS).orderBy(USERS.USERNAME).fetch().map {
            mapUser(it)
        }
    }

    override fun updateRole(userId: UUID, role: UserRole) {
        dsl.update(USERS).set(USERS.ROLE, role.name).where(USERS.ID.eq(userId)).execute()
    }

    override fun updateEnabled(userId: UUID, enabled: Boolean) {
        dsl.update(USERS).set(USERS.ENABLED, enabled).where(USERS.ID.eq(userId)).execute()
    }

    override fun updateLastActivity(userId: UUID) {
        dsl.update(USERS)
            .set(lastActivityAtField, LocalDateTime.now(ZoneOffset.UTC))
            .where(USERS.ID.eq(userId))
            .execute()
    }
}
